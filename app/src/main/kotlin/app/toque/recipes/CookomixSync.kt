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
 *
 * Panne ou changement du site : la synchro s'arrête avec un code d'erreur ([Cookomix.Error],
 * docs/COOKOMIX-ERREURS.md) et ne supprime alors aucune recette. Une page illisible n'efface
 * jamais la version déjà enregistrée : elle sera relue à la synchro suivante.
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
        prefs.cookomixSyncPending = true
        try {
            // Progression partagée : un écran Réglages rouvert pendant la synchro la retrouve.
            return finish(run { msg -> current = msg; progress(msg) })
        } catch (e: Exception) {
            log.crash("cookomix", e)
            return finish(failure(Cookomix.Error.INTERNAL, e.message ?: e.javaClass.simpleName))
        } finally {
            current = ""
            running.set(false)
        }
    }

    /** Échec codé : les recettes déjà enregistrées restent, et le message le dit. */
    private fun failure(error: Cookomix.Error, detail: String = ""): LidlSync.Report {
        log.write("cookomix", "${error.code} $detail")
        return LidlSync.Report(false, tr("Recettes Cookomix non mises à jour, %s. Les recettes déjà enregistrées restent disponibles.", error.message(detail)))
    }

    private fun networkFailure(e: IOException): LidlSync.Report =
        if (e is HttpStatusException) failure(Cookomix.Error.HTTP, tr("réponse %d du serveur", e.code))
        else failure(Cookomix.Error.NETWORK, e.message ?: e.javaClass.simpleName)

    private fun run(progress: (String) -> Unit): LidlSync.Report {
        val withAdapted = prefs.cookomixAdapted
        val index = HashMap(readIndex())
        val before = HashMap(index)

        // 1. Liste des recettes (sitemaps). Liste vide ou anormalement courte : on s'arrête sans rien toucher.
        progress(tr("Liste des recettes Cookomix…"))
        val entries = ArrayList<Cookomix.Entry>()
        try {
            val maps = Cookomix.recipeSitemaps(text(Cookomix.SITEMAP_INDEX))
            if (maps.isEmpty()) return failure(Cookomix.Error.LIST_UNREADABLE, tr("aucun sitemap de recettes"))
            for ((i, url) in maps.withIndex()) {
                progress(tr("Liste des recettes Cookomix… %d / %d", i + 1, maps.size))
                entries += Cookomix.parseSitemap(text(url))
                pause(600)
            }
        } catch (e: IOException) {
            return networkFailure(e)
        }
        if (entries.isEmpty()) return failure(Cookomix.Error.LIST_UNREADABLE, tr("aucune recette dans les sitemaps"))
        if (Cookomix.listLooksWrong(index.size, entries.size))
            return failure(Cookomix.Error.LIST_SHRUNK, tr("%d recettes au lieu de %d", entries.size, index.size))

        // 2. Pages nouvelles ou modifiées (ou compatibles mais absentes du catalogue).
        val present = repo.ids()
        // Page illisible ou en échec : on garde la version déjà enregistrée (identifiant conservé).
        fun wanted(m: Mark) = m.status == OK || (m.status == ADAPTED && withAdapted) ||
            ((m.status == UNREADABLE || m.status == ERROR) && m.id.isNotEmpty())
        val todo = entries.filter { e ->
            val m = Mark.of(index[e.slug])
            m == null || m.lastmod != e.lastmod || m.status == ERROR || m.status == UNREADABLE ||
                ((m.status == OK || m.status == ADAPTED) && wanted(m) && m.id !in present)
        }
        val batch = ArrayList<Recipe>()
        val breaker = Cookomix.Breaker()
        val touched = ArrayList<String>()
        var failures = 0
        var unreadable = 0
        var inARow = 0
        for ((i, e) in todo.withIndex()) {
            // Total du site et déjà traitées, pour qu'une reprise ne ressemble pas à une perte.
            progress(tr("Recettes Cookomix : %d / %d (déjà à jour : %d)", entries.size - todo.size + i + 1, entries.size, entries.size - todo.size))
            val previousId = Mark.of(index[e.slug])?.id.orEmpty()
            try {
                val res = Cookomix.parsePage(text(e.url), e.url)
                val r = res.recipe
                val status = when (res.compat) {
                    Cookomix.Compat.OK -> OK
                    Cookomix.Compat.ADAPTED -> ADAPTED
                    Cookomix.Compat.INCOMPATIBLE -> INCOMPATIBLE
                    Cookomix.Compat.UNREADABLE -> UNREADABLE
                }
                index[e.slug] = Mark(e.lastmod, status, r?.id ?: previousId).toString()
                touched += e.slug
                if (r != null && (status == OK || (status == ADAPTED && withAdapted))) {
                    batch += r
                    // Photo tout de suite (et non en fin de synchro) : la recette s'affiche avec elle.
                    photoNow(r.image)
                }
                when (status) {
                    INCOMPATIBLE -> log.write("cookomix", "${e.slug} : ${res.reasons.joinToString("; ")}")
                    UNREADABLE -> {
                        unreadable++
                        log.write("cookomix", "${Cookomix.Error.PAGES_UNREADABLE.code} ${e.slug} : ${res.reasons.joinToString("; ")}")
                    }
                }
                breaker.record(res.compat)
                inARow = 0
            } catch (ex: IOException) {
                failures++
                inARow++
                index[e.slug] = Mark(e.lastmod, ERROR, previousId).toString()
                log.write("cookomix", "${e.slug} : ${ex.message}")
                if (inARow >= 5) {
                    save(batch, index)
                    return networkFailure(ex)
                }
            }
            // Le site semble avoir changé : on annule les verdicts négatifs de cette synchro et on s'arrête.
            breaker.verdict()?.let { error ->
                for (slug in touched) {
                    val status = Mark.of(index[slug])?.status
                    if (status == INCOMPATIBLE || status == UNREADABLE) {
                        val old = before[slug]
                        if (old == null) index.remove(slug) else index[slug] = old
                    }
                }
                save(batch, index)
                return failure(error, tr("%d pages lues", breaker.pages))
            }
            if (batch.size >= 25) save(batch, index)
            pause(PAGE_PAUSE_MS)
        }
        save(batch, index)

        // 3. Le catalogue Cookomix suit le site et le réglage « recettes adaptées ».
        val slugs = entries.map { it.slug }.toHashSet()
        index.keys.retainAll(slugs)
        // Une recette adaptée mise en favori reste, même si l'option « recettes adaptées » est coupée.
        val keep = index.values.mapNotNull { Mark.of(it) }
            .filter { it.id.isNotEmpty() && (wanted(it) || (it.status == ADAPTED && repo.isFavorite(it.id))) }
            .map { it.id }.toHashSet()
        repo.removeAllExcept(keep, rebuild = false, scope = RecipeRepo.Scope.COOKOMIX)
        writeIndex(index)
        repo.refresh()

        // 4. Photos manquantes.
        photos(keep, progress)

        val marks = index.values.mapNotNull { Mark.of(it) }
        val ok = marks.count { it.status == OK }
        val adapted = marks.count { it.status == ADAPTED }
        val ko = marks.count { it.status == INCOMPATIBLE }
        var note = if (failures > 0) " " + tr("(%d en échec, réessayées à la prochaine synchro)", failures) else ""
        if (unreadable > 0) note += " " + tr("(%d illisibles, code %s, réessayées à la prochaine synchro)", unreadable, Cookomix.Error.PAGES_UNREADABLE.code)
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
            return networkFailure(e)
        }
        if (!Cookomix.looksLikeCookbook(html)) {
            val msg = Cookomix.Error.COOKBOOK_UNREADABLE.message()
            log.write("cookomix", msg)
            return LidlSync.Report(false, msg)
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

    /** Recettes enregistrées depuis le dernier rechargement de la liste. */
    private var unseen = 0

    private fun save(batch: MutableList<Recipe>, index: Map<String, String>) {
        if (batch.isNotEmpty()) {
            unseen += batch.size
            // La première synchro dure environ une heure : les recettes apparaissent par paquets
            // dans la liste au lieu d'attendre la fin.
            repo.upsertAll(ArrayList(batch), rebuild = unseen >= REFRESH_EVERY)
            if (unseen >= REFRESH_EVERY) unseen = 0
        }
        batch.clear()
        writeIndex(index)
    }

    /** Photo d'une recette qui vient d'être lue ; un échec est rattrapé en fin de synchro. */
    private fun photoNow(url: String) {
        if (!url.startsWith("http") || repo.imageFile(url) != null) return
        try {
            fetcher.savePhoto(url)
        } catch (e: IOException) {
            log.write("cookomix", "photo $url : ${e.message}")
        }
        pause(300)
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
        // Terminée (réussie ou arrêtée sur une erreur) : pas de reprise automatique au démarrage.
        prefs.cookomixSyncPending = false
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
        private const val UNREADABLE = "illisible"
        /** Une page toutes les ~1,2 s : environ 50 minutes pour tout le site la première fois. */
        private const val PAGE_PAUSE_MS = 1_200L
        private const val REFRESH_EVERY = 200
        private val running = AtomicBoolean(false)

        val isRunning: Boolean get() = running.get()

        /** Dernier message de progression de la synchro en cours (vide sinon). */
        @Volatile var current: String = ""
            private set

        /** Connexion par email (ou pseudo) et mot de passe : seul le jeton est gardé. Renvoie (ok, message). */
        fun login(prefs: Prefs, user: String, password: String): Pair<Boolean, String> {
            if (user.isBlank() || password.isEmpty()) return false to tr("Email et mot de passe requis")
            val raw = try {
                Http.postJson(Cookomix.TOKEN_URL, Cookomix.tokenBody(user, password))
            } catch (e: IOException) {
                return false to Cookomix.Error.NETWORK.message(e.message ?: e.javaClass.simpleName)
            }
            val login = Cookomix.parseLogin(raw.body, raw.code)
            val account = login.account
                ?: return false to (login.error ?: Cookomix.Error.LOGIN_UNAVAILABLE).message(login.detail.ifEmpty { tr("réponse %d du serveur", raw.code) })
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
