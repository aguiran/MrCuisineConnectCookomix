package app.toque.recipes

import android.content.Context
import app.toque.Http
import app.toque.Logbook
import app.toque.Prefs
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.obj
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Recipe
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Recettes de cookomix.com compatibles avec le Monsieur Cuisine connect.
 *
 * Liste complète par les sitemaps du site (avec la date de mise à jour de chaque recette), puis
 * seules les pages nouvelles ou modifiées sont téléchargées et converties ([Cookomix.parsePage]).
 * Les recettes incompatibles sont mémorisées pour ne pas être retéléchargées tant qu'elles ne
 * changent pas. Requêtes espacées (plus d'une seconde) ; reprend où elle s'est arrêtée.
 */
class CookomixSync(private val context: Context, private val prefs: Prefs, private val repo: RecipeRepo, private val log: Logbook) {
    private val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
    private val indexFile = File(context.filesDir, "recettes-cookomix.json")
    private val fetcher = Fetcher(version, repo)

    /** Ligne d'index : date de mise à jour du sitemap, verdict, identifiant Toque. */
    private class Mark(val lastmod: String, val status: String, val id: String) {
        override fun toString() = "$lastmod|$status|$id"

        companion object {
            fun of(s: String?): Mark? {
                val p = s?.split('|') ?: return null
                return if (p.size < 3) null else Mark(p[0], p[1], p[2])
            }
        }
    }

    fun sync(progress: (String) -> Unit = {}): LidlSync.Report {
        if (!running.compareAndSet(false, true)) return LidlSync.Report(false, tr("Synchronisation déjà en cours"))
        try {
            return finish(run(progress))
        } catch (e: Exception) {
            log.crash("cookomix", e)
            return finish(LidlSync.Report(false, tr("Synchronisation Cookomix impossible : %s", e.message ?: e.toString())))
        } finally {
            running.set(false)
        }
    }

    private fun run(progress: (String) -> Unit): LidlSync.Report {
        val withAdapted = prefs.cookomixAdapted
        val index = HashMap(readIndex())

        // 1. Liste des recettes (sitemaps).
        progress(tr("Liste des recettes Cookomix…"))
        val entries = ArrayList<Cookomix.Entry>()
        try {
            val maps = Cookomix.recipeSitemaps(text(Cookomix.SITEMAP_INDEX))
            if (maps.isEmpty()) return LidlSync.Report(false, tr("Liste des recettes Cookomix introuvable"))
            for ((i, url) in maps.withIndex()) {
                progress(tr("Liste des recettes Cookomix… %d / %d", i + 1, maps.size))
                entries += Cookomix.parseSitemap(text(url))
                pause(600)
            }
        } catch (e: IOException) {
            return LidlSync.Report(false, tr("cookomix.com injoignable : %s", e.message ?: e.toString()))
        }
        if (entries.isEmpty()) return LidlSync.Report(false, tr("Aucune recette reçue de cookomix.com"))

        // 2. Pages nouvelles ou modifiées (ou compatibles mais absentes du catalogue).
        val present = repo.ids()
        fun wanted(m: Mark) = m.status == OK || (m.status == ADAPTED && withAdapted)
        val todo = entries.filter { e ->
            val m = Mark.of(index[e.slug])
            m == null || m.lastmod != e.lastmod || m.status == ERROR || (wanted(m) && m.id !in present)
        }
        val batch = ArrayList<Recipe>()
        var failures = 0
        var inARow = 0
        for ((i, e) in todo.withIndex()) {
            progress(tr("Recettes Cookomix : %d / %d", i + 1, todo.size))
            try {
                val res = Cookomix.parsePage(text(e.url), e.url)
                val r = res.recipe
                val status = when (res.compat) {
                    Cookomix.Compat.OK -> OK
                    Cookomix.Compat.ADAPTED -> ADAPTED
                    Cookomix.Compat.INCOMPATIBLE -> INCOMPATIBLE
                }
                index[e.slug] = Mark(e.lastmod, status, r?.id ?: "").toString()
                if (r != null && (status == OK || (status == ADAPTED && withAdapted))) batch += r
                if (status == INCOMPATIBLE) log.write("cookomix", "${e.slug} : ${res.reasons.joinToString("; ")}")
                inARow = 0
            } catch (ex: IOException) {
                failures++
                inARow++
                index[e.slug] = Mark(e.lastmod, ERROR, "").toString()
                log.write("cookomix", "${e.slug} : ${ex.message}")
                if (inARow >= 5) {
                    save(batch, index)
                    return LidlSync.Report(false, tr("cookomix.com injoignable : %s", ex.message ?: ex.toString()))
                }
            }
            if (batch.size >= 25) save(batch, index)
            pause(PAGE_PAUSE_MS)
        }
        save(batch, index)

        // 3. Le catalogue Cookomix suit le site et le réglage « recettes adaptées ».
        val slugs = entries.map { it.slug }.toHashSet()
        index.keys.retainAll(slugs)
        val keep = index.values.mapNotNull { Mark.of(it) }.filter { wanted(it) && it.id.isNotEmpty() }.map { it.id }.toHashSet()
        repo.removeAllExcept(keep, rebuild = false, scope = RecipeRepo.Scope.COOKOMIX)
        writeIndex(index)
        repo.refresh()

        // 4. Photos manquantes.
        photos(keep, progress)

        val marks = index.values.mapNotNull { Mark.of(it) }
        val ok = marks.count { it.status == OK }
        val adapted = marks.count { it.status == ADAPTED }
        val ko = marks.count { it.status == INCOMPATIBLE }
        val note = if (failures > 0) " " + tr("(%d en échec, réessayées à la prochaine synchro)", failures) else ""
        return LidlSync.Report(true, tr("Cookomix : %d compatibles, %d adaptées, %d incompatibles", ok, adapted, ko) + note, keep.size)
    }

    /**
     * Favoris du carnet de recettes du compte connecté (page publique du membre) : les recettes
     * déjà importées dans Toque sont ajoutées aux favoris. Renvoie un message à afficher.
     */
    fun importFavorites(): LidlSync.Report {
        val login = prefs.cookomixLogin
        if (prefs.cookomixToken.isEmpty() || login.isEmpty()) return LidlSync.Report(false, tr("Connectez-vous d'abord à Cookomix"))
        val html = try {
            text(Cookomix.memberCookbookUrl(login), Cookomix.authHeader(prefs.cookomixToken))
        } catch (e: IOException) {
            return LidlSync.Report(false, tr("cookomix.com injoignable : %s", e.message ?: e.toString()))
        }
        val slugs = Cookomix.recipeSlugs(html)
        val index = readIndex()
        var added = 0
        for (slug in slugs) {
            val id = Mark.of(index[slug])?.id?.takeIf { it.isNotEmpty() } ?: continue
            if (repo.get(id) != null && !repo.isFavorite(id)) {
                repo.setFavorite(id, true)
                added++
            }
        }
        val msg = if (slugs.isEmpty()) tr("Aucune recette trouvée dans votre carnet Cookomix")
        else tr("%d recette(s) du carnet, %d ajoutée(s) aux favoris", slugs.size, added)
        log.write("cookomix", msg)
        return LidlSync.Report(slugs.isNotEmpty(), msg, added)
    }

    private fun save(batch: MutableList<Recipe>, index: Map<String, String>) {
        if (batch.isNotEmpty()) repo.upsertAll(ArrayList(batch), rebuild = false)
        batch.clear()
        writeIndex(index)
    }

    private fun photos(ids: Collection<String>, progress: (String) -> Unit) {
        val missing = ArrayList<String>()
        for (id in ids) {
            val url = repo.get(id)?.image ?: continue
            if (url.startsWith("http") && repo.imageFile(url) == null) missing += url
        }
        var inARow = 0
        for ((i, url) in missing.withIndex()) {
            progress(tr("Photos : %d / %d", i + 1, missing.size))
            try {
                fetcher.savePhoto(url)
                inARow = 0
            } catch (e: IOException) {
                log.write("cookomix", "photo $url : ${e.message}")
                if (++inARow >= 5) return
            }
            pause(300)
        }
    }

    private fun text(url: String, headers: Map<String, String> = emptyMap()): String =
        String(fetcher.download(url, mapOf("Accept-Language" to "fr-FR") + headers, 4 * 1024 * 1024), Charsets.UTF_8)

    private fun readIndex(): Map<String, String> = try {
        Json.parse(indexFile.readText()).obj()["recipes"].obj().mapValues { (_, v) -> v?.toString().orEmpty() }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun writeIndex(index: Map<String, String>) {
        try {
            val tmp = File(indexFile.path + ".part")
            tmp.writeText(Json.write(linkedMapOf("format" to 1, "recipes" to index)))
            tmp.renameTo(indexFile)
        } catch (e: Exception) {
            log.write("cookomix", "index non enregistré : $e")
        }
    }

    private fun finish(r: LidlSync.Report): LidlSync.Report {
        val stamp = SimpleDateFormat(tr("dd/MM à HH:mm"), I18n.locale).format(Date())
        prefs.cookomixLastSyncMs = System.currentTimeMillis()
        prefs.cookomixLastResult = "$stamp : ${r.message}"
        log.write("cookomix", r.message)
        return r
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    companion object {
        private const val OK = "ok"
        private const val ADAPTED = "adaptee"
        private const val INCOMPATIBLE = "incompatible"
        private const val ERROR = "erreur"
        /** Une page toutes les ~1,2 s : environ 50 minutes pour tout le site la première fois. */
        private const val PAGE_PAUSE_MS = 1_200L
        private val running = AtomicBoolean(false)

        val isRunning: Boolean get() = running.get()

        /** Connexion par email (ou pseudo) et mot de passe : seul le jeton est gardé. Renvoie (ok, message). */
        fun login(prefs: Prefs, user: String, password: String): Pair<Boolean, String> {
            if (user.isBlank() || password.isEmpty()) return false to tr("Email et mot de passe requis")
            val raw = try {
                Http.postJson(Cookomix.TOKEN_URL, Cookomix.tokenBody(user, password))
            } catch (e: IOException) {
                return false to tr("cookomix.com injoignable : %s", e.message ?: e.toString())
            }
            val (account, error) = Cookomix.parseToken(raw.body)
            if (account == null) return false to tr("Connexion refusée : %s", error.ifEmpty { tr("réponse %d du serveur", raw.code) })
            prefs.cookomixToken = account.token
            prefs.cookomixLogin = account.login
            prefs.cookomixName = account.displayName.ifEmpty { account.login.ifEmpty { account.email } }
            return true to tr("Connecté à Cookomix : %s", prefs.cookomixName)
        }

        /** Le jeton est-il encore accepté par le site ? (false aussi si le site est injoignable) */
        fun validate(prefs: Prefs): Boolean {
            if (prefs.cookomixToken.isEmpty()) return false
            return try {
                Http.postJson(Cookomix.VALIDATE_URL, "{}", Cookomix.authHeader(prefs.cookomixToken)).code == 200
            } catch (_: IOException) {
                false
            }
        }

        fun logout(prefs: Prefs) {
            prefs.cookomixToken = ""
            prefs.cookomixLogin = ""
            prefs.cookomixName = ""
        }
    }
}
