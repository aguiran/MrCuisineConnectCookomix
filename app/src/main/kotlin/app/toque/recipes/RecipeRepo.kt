package app.toque.recipes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.toque.ToqueApp
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeCodec
import app.toque.core.recipes.RecipeSource
import app.toque.core.recipes.SearchIndex
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Recettes copiées localement (SQLite) + index de recherche en mémoire.
 * L'appli fonctionne entièrement hors ligne ; seule la synchro passe par le réseau.
 *
 * Démarrage : l'index est relu d'un fichier (quelques secondes) au lieu d'être reconstruit en
 * relisant toutes les recettes (environ une minute pour 4 000 recettes sur la tablette du robot).
 * Le fichier porte le numéro de révision de la base, augmenté à chaque écriture de recettes :
 * s'il ne correspond plus, l'index est reconstruit puis réenregistré.
 */
class RecipeRepo(private val context: Context) {
    private val helper = object : SQLiteOpenHelper(context, "recettes.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE recipes (id TEXT PRIMARY KEY, title TEXT NOT NULL, json TEXT NOT NULL, updated INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE favorites (id TEXT PRIMARY KEY)")
            db.execSQL(CREATE_META)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL(CREATE_META)
        }
    }
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "toque-recettes").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<() -> Unit>()
    private val indexFile = File(context.filesDir, "index-recettes.bin")

    /** Protège [favorites] et la publication de [index] (interface et fil des recettes). */
    private val lock = Any()
    private val favorites = HashSet<String>()
    @Volatile private var favoritesLoaded = false

    @Volatile var index: SearchIndex = SearchIndex(emptyList())
        private set
    @Volatile var loaded = false
        private set
    /** Révision de la base à laquelle correspond [index] (fil des recettes uniquement). */
    private var indexRev = -1L

    val imagesDir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    fun onChange(l: () -> Unit): () -> Unit {
        listeners += l
        return { listeners -= l }
    }

    private fun notifyChanged() {
        main.post { for (l in ArrayList(listeners)) l() }
    }

    private fun log(line: String) {
        (context.applicationContext as? ToqueApp)?.log?.write("recettes", line)
    }

    fun load() = exec.execute {
        try {
            val t0 = SystemClock.elapsedRealtime()
            val db = helper.readableDatabase
            loadFavorites(db)
            val rev = revision(db)
            val cached = readIndexFile(rev)
            if (cached == null) rebuild()
            else {
                synchronized(lock) { index = cached.withFavorites(favorites) }
                indexRev = rev
                loaded = true
                log("index relu : ${cached.entries.size} recettes en ${SystemClock.elapsedRealtime() - t0} ms")
                notifyChanged()
            }
        } catch (e: Exception) {
            // Base illisible : l'appli reste utilisable (cuisson manuelle, programmes).
            log("chargement impossible : $e")
            loaded = true
            notifyChanged()
        }
    }

    private fun loadFavorites(db: SQLiteDatabase) {
        val favs = HashSet<String>()
        db.rawQuery("SELECT id FROM favorites", null).use { c -> while (c.moveToNext()) favs += c.getString(0) }
        synchronized(lock) {
            favorites.clear()
            favorites += favs
        }
        favoritesLoaded = true
    }

    /** Révision de la base : augmente à chaque écriture de recettes. */
    private fun revision(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT v FROM meta WHERE k = 'rev'", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    /** À appeler dans la transaction de toute écriture dans la table des recettes. */
    private fun bumpRevision(db: SQLiteDatabase) {
        db.execSQL("INSERT OR REPLACE INTO meta (k, v) VALUES ('rev', COALESCE((SELECT v FROM meta WHERE k = 'rev'), 0) + 1)")
    }

    /** Reconstruit l'index seulement si des recettes ont été écrites depuis sa construction. */
    private fun rebuildIfChanged() {
        if (!loaded || revision(helper.readableDatabase) != indexRev) rebuild()
    }

    /** Relit toutes les recettes et reconstruit l'index (lent : fil des recettes uniquement). */
    private fun rebuild() {
        val t0 = SystemClock.elapsedRealtime()
        val db = helper.readableDatabase
        if (!favoritesLoaded) loadFavorites(db)
        val rev = revision(db)
        val builder = SearchIndex.Builder()
        db.rawQuery("SELECT json FROM recipes", null).use { c ->
            while (c.moveToNext()) {
                val r = try {
                    RecipeCodec.decode(c.getString(0), RecipeSource.IMPORTED)
                } catch (_: Exception) {
                    null
                } ?: continue
                builder.add(r.copy(favorite = false))
            }
        }
        val built = builder.build()
        // Les favoris ont pu changer pendant la reconstruction : on applique ceux du moment.
        synchronized(lock) { index = built.withFavorites(favorites) }
        indexRev = rev
        loaded = true
        writeIndexFile(built, rev)
        log("index reconstruit : ${built.entries.size} recettes en ${SystemClock.elapsedRealtime() - t0} ms")
        notifyChanged()
    }

    private val appVersion: Long by lazy {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    /** Index enregistré pour cette version de l'appli et cette révision de la base, sinon null. */
    private fun readIndexFile(rev: Long): SearchIndex? {
        if (!indexFile.isFile) return null
        return try {
            val buf = ByteBuffer.wrap(indexFile.readBytes())
            if (buf.int != INDEX_MAGIC || buf.long != appVersion || buf.long != rev) null
            else SearchIndex.read(buf, emptySet())
        } catch (e: Exception) {
            log("index enregistré illisible (${e.message}) : reconstruction")
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun writeIndexFile(built: SearchIndex, rev: Long) {
        val tmp = File(indexFile.path + ".part")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { out ->
                out.writeInt(INDEX_MAGIC)
                out.writeLong(appVersion)
                out.writeLong(rev)
                built.write(out)
            }
            if (!tmp.renameTo(indexFile)) tmp.delete()
        } catch (e: Exception) {
            tmp.delete()
            log("index non enregistré : $e")
        }
    }

    val count: Int get() = index.entries.size

    /** Lecture d'une recette (rapide : une seule ligne). */
    fun get(id: String): Recipe? {
        helper.readableDatabase.rawQuery("SELECT json FROM recipes WHERE id = ?", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            val r = try {
                RecipeCodec.decode(c.getString(0), RecipeSource.IMPORTED)
            } catch (_: Exception) {
                null
            } ?: return null
            return r.copy(favorite = isFavorite(id))
        }
    }

    fun isFavorite(id: String): Boolean = synchronized(lock) { id in favorites }

    /** Visible tout de suite dans les listes (l'index n'est pas reconstruit) ; l'écriture suit. */
    fun setFavorite(id: String, favorite: Boolean) {
        val apply = {
            synchronized(lock) {
                if (favorite) favorites += id else favorites -= id
                index = index.withFavorites(favorites)
            }
            notifyChanged()
        }
        // Avant la fin du chargement, la liste des favoris n'est pas encore lue : on attend son tour.
        if (favoritesLoaded) apply() else exec.execute(apply)
        exec.execute {
            val db = helper.writableDatabase
            if (favorite) db.execSQL("INSERT OR IGNORE INTO favorites (id) VALUES (?)", arrayOf(id))
            else db.execSQL("DELETE FROM favorites WHERE id = ?", arrayOf(id))
        }
    }

    /**
     * Remplace tout le catalogue Lidl par l'import du jour (miroir de l'appli d'origine).
     * Les favoris sont conservés ; ceux marqués dans l'appli d'origine sont ajoutés.
     */
    fun replaceAll(recipes: List<Recipe>) {
        exec.submit { replaceAllNow(recipes) }.get()
    }

    private fun replaceAllNow(recipes: List<Recipe>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            // Les recettes Cookomix ne viennent pas de l'appli d'origine : on les garde.
            db.delete("recipes", "id NOT LIKE ?", arrayOf(Cookomix.ID_PREFIX + "%"))
            val now = System.currentTimeMillis()
            for (r in recipes) {
                val v = ContentValues()
                v.put("id", r.id)
                v.put("title", r.title)
                v.put("json", RecipeCodec.encode(r.copy(favorite = false)))
                v.put("updated", now)
                db.insertWithOnConflict("recipes", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                if (r.favorite) db.execSQL("INSERT OR IGNORE INTO favorites (id) VALUES (?)", arrayOf(r.id))
            }
            bumpRevision(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        loadFavorites(db)
        rebuild()
    }

    /** Ajoute ou met à jour des recettes (favoris conservés). [rebuild] : recharger l'index tout de suite. */
    fun upsertAll(recipes: List<Recipe>, rebuild: Boolean = true) {
        exec.submit {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val now = System.currentTimeMillis()
                for (r in recipes) {
                    val v = ContentValues()
                    v.put("id", r.id)
                    v.put("title", r.title)
                    v.put("json", RecipeCodec.encode(r.copy(favorite = false)))
                    v.put("updated", now)
                    db.insertWithOnConflict("recipes", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                }
                if (recipes.isNotEmpty()) bumpRevision(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (rebuild) rebuildIfChanged()
        }.get()
    }

    /** Identifiants de toutes les recettes enregistrées. */
    fun ids(): Set<String> = exec.submit<Set<String>> {
        val out = HashSet<String>()
        helper.readableDatabase.rawQuery("SELECT id FROM recipes", null).use { c -> while (c.moveToNext()) out += c.getString(0) }
        out
    }.get()

    /** Familles de recettes : une synchro ne touche que la sienne (Lidl ou Cookomix). */
    enum class Scope {
        LIDL, COOKOMIX;

        fun owns(id: String): Boolean = id.startsWith(Cookomix.ID_PREFIX) == (this == COOKOMIX)
    }

    /** Supprime les recettes de [scope] absentes de [keep] (catalogue miroir de la source). */
    fun removeAllExcept(keep: Set<String>, rebuild: Boolean = true, scope: Scope = Scope.LIDL) {
        exec.submit {
            val db = helper.writableDatabase
            val gone = ArrayList<String>()
            db.rawQuery("SELECT id FROM recipes", null).use { c -> while (c.moveToNext()) c.getString(0).let { if (scope.owns(it) && it !in keep) gone += it } }
            db.beginTransaction()
            try {
                for (id in gone) db.delete("recipes", "id = ?", arrayOf(id))
                if (gone.isNotEmpty()) bumpRevision(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (rebuild) rebuildIfChanged()
        }.get()
    }

    /** Recharge l'index de recherche (après des mises à jour sans [rebuild]). */
    fun refresh() {
        exec.submit { rebuildIfChanged() }.get()
    }

    fun imageFile(name: String): File? {
        if (name.isBlank()) return null
        val clean = name.substringAfterLast('/')
        for (candidate in listOf("large-$clean", clean, "small-$clean")) {
            val f = File(imagesDir, candidate)
            if (f.exists() && f.length() > 0) return f
        }
        return null
    }

    fun runInBackground(block: () -> Unit) = exec.execute(block)

    private companion object {
        const val CREATE_META = "CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v INTEGER NOT NULL)"
        /** « TQIX » : en-tête du fichier d'index. */
        const val INDEX_MAGIC = 0x54514958
    }
}
