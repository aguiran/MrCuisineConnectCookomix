package app.toque

import android.content.Context
import app.toque.core.i18n.tr
import app.toque.core.machine.RootShell
import app.toque.core.time.ZoneLookup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date et heure du robot, réglées toutes seules :
 *  - le fuseau horaire est trouvé d'après la connexion Internet (géolocalisation de l'adresse IP),
 *    ou choisi à la main dans les réglages, puis appliqué au système (root) ;
 *  - l'heure vient du réseau (NTP d'Android, activé ici) ; si elle est encore fausse de plus de
 *    deux minutes, elle est corrigée d'après l'en-tête Date des serveurs interrogés.
 * Le robot n'a pas de pile d'horloge : à chaque démarrage il repart d'une date ancienne. Tant que
 * le réseau n'a pas répondu, l'horloge est remise à la dernière heure connue (notée toutes les
 * cinq minutes) : la date est alors assez proche pour que les connexions sécurisées (HTTPS)
 * fonctionnent, et l'interface affiche « --:-- » plutôt qu'une heure fausse.
 */
class TimeSync(private val context: Context, private val prefs: Prefs, private val log: Logbook) {
    class Result(val ok: Boolean, val zone: String, val message: String)

    fun run(): Result = synchronized(LOCK) { runLocked() }

    private fun runLocked(): Result {
        val wanted = prefs.timeZone
        var zone: String? = null
        var source = tr("choix manuel")
        var serverMs = 0L
        if (wanted == ZoneLookup.AUTO) {
            for (p in ZoneLookup.PROVIDERS) {
                try {
                    val (body, date) = get(p.url)
                    if (date > 0) serverMs = date
                    val z = ZoneLookup.zoneOf(body)
                    if (z != null && z in available) {
                        zone = z
                        source = p.name
                        break
                    }
                    log.write("heure", "${p.name} : pas de fuseau dans la réponse")
                } catch (e: Exception) {
                    log.write("heure", "${p.name} : ${e.message ?: e.toString()}")
                }
            }
            if (zone == null) return Result(false, current(), tr("Fuseau horaire introuvable : vérifiez le Wi-Fi."))
        } else {
            zone = wanted.takeIf { it in available } ?: return Result(false, current(), tr("Fuseau horaire inconnu : %s", wanted))
            // Fuseau choisi à la main : il faut quand même l'heure d'un serveur pour régler l'horloge.
            for (p in ZoneLookup.PROVIDERS) {
                try {
                    serverMs = get(p.url).second
                } catch (e: Exception) {
                    log.write("heure", "${p.name} : ${e.message ?: e.toString()}")
                }
                if (serverMs >= MIN_PLAUSIBLE_MS) break
            }
        }
        val z = zone!!
        if (!RootShell.available()) return Result(false, current(), tr("Accès root nécessaire pour régler l'heure du robot."))

        // Heure réseau d'Android (NTP), au cas où elle aurait été désactivée.
        RootShell.run("settings put global auto_time 1")
        if (z != current()) {
            val r = RootShell.run("service call alarm 3 s16 $z")
            if (!r.ok || prop() != z) {
                // Repli : propriété système puis annonce du changement aux applis.
                RootShell.run("setprop persist.sys.timezone $z && am broadcast -a android.intent.action.TIMEZONE_CHANGED --es time-zone $z")
            }
            TimeZone.setDefault(null)
            log.write("heure", "fuseau $z ($source)")
        }
        val fixed = fixClock(serverMs)
        if (serverMs >= MIN_PLAUSIBLE_MS && Math.abs(System.currentTimeMillis() - serverMs) < 120_000L) {
            // Horloge confirmée par le réseau : affichable, et notée pour le prochain démarrage.
            approximate = false
            remember(context)
        }
        val ok = prop() == z || current() == z
        prefs.lastZone = z
        val now = clock()
        return if (ok) Result(true, z, if (fixed) tr("Heure corrigée : %s (%s)", now, z) else tr("Il est %s (%s)", now, z))
        else Result(false, current(), tr("Le système a refusé le fuseau %s", z))
    }

    /** Corrige l'horloge si elle s'écarte de plus de 2 min de l'heure donnée par un serveur. */
    private fun fixClock(serverMs: Long): Boolean {
        if (serverMs < MIN_PLAUSIBLE_MS) return false
        if (Math.abs(System.currentTimeMillis() - serverMs) < 120_000L) return false
        val ok = setClock(serverMs)
        log.write("heure", if (ok) "horloge corrigée d'après le réseau" else "correction de l'horloge refusée")
        return ok
    }

    /**
     * Au démarrage, avant toute réponse du réseau : remet l'horloge à la dernière heure connue si
     * elle est en retard dessus (root nécessaire). Sans effet si l'horloge est déjà plus récente.
     */
    private fun restoreLastKnown() = synchronized(LOCK) {
        val saved = lastKnown(context)
        if (saved < MIN_PLAUSIBLE_MS || System.currentTimeMillis() >= saved) return@synchronized
        if (!RootShell.available()) return@synchronized
        if (setClock(saved)) {
            approximate = true
            log.write("heure", "horloge remise à la dernière heure connue, en attendant le réseau")
        }
    }

    private fun get(url: String): Pair<String, Long> {
        val r = Http.get(url, mapOf("Accept" to "application/json"), timeoutMs = 10_000, maxBytes = 64 * 1024)
        return r.body to r.serverMs
    }

    private fun prop(): String = RootShell.run("getprop persist.sys.timezone").out.trim()

    companion object {
        /** 1er janvier 2025 : en dessous, une heure (serveur ou horloge du robot) n'est pas crédible. */
        private const val MIN_PLAUSIBLE_MS = 1_735_689_600_000L
        private val LOCK = Any()
        private const val REMEMBER_EVERY_MS = 5 * 60_000L

        /** Horloge remise à la dernière heure connue, pas encore confirmée par le réseau. */
        @Volatile private var approximate = false

        /** L'heure du robot peut-elle être affichée ? (sinon : pas encore réglée depuis le démarrage) */
        val trusted: Boolean get() = !approximate && System.currentTimeMillis() >= MIN_PLAUSIBLE_MS

        /** Règle l'horloge du système (root) ; vrai si elle est ensuite à moins d'une minute de [ms]. */
        private fun setClock(ms: Long): Boolean {
            RootShell.run("service call alarm 2 i64 $ms")
            if (Math.abs(System.currentTimeMillis() - ms) > 60_000L) {
                val utc = SimpleDateFormat("MMddHHmmyyyy.ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                RootShell.run("date -u ${utc.format(Date(ms))}")
            }
            return Math.abs(System.currentTimeMillis() - ms) < 60_000L
        }

        private fun file(context: Context) = java.io.File(context.filesDir, "derniere-heure.txt")

        /** Dernière heure connue, notée par [remember] (0 si aucune). */
        private fun lastKnown(context: Context): Long = try {
            file(context).readText().trim().toLong()
        } catch (_: Exception) {
            0L
        }

        /** Note l'heure courante si elle est fiable, pour le prochain démarrage. */
        private fun remember(context: Context) {
            if (!trusted) return
            try {
                val f = file(context)
                val tmp = java.io.File(f.path + ".part")
                tmp.writeText(System.currentTimeMillis().toString())
                if (!tmp.renameTo(f)) tmp.delete()
            } catch (_: Exception) {
            }
        }
        private val available: Set<String> by lazy { TimeZone.getAvailableIDs().toHashSet() }

        fun current(): String = TimeZone.getDefault().id

        fun clock(): String = SimpleDateFormat("HH:mm", Locale.FRANCE).apply { timeZone = TimeZone.getDefault() }.format(Date())

        @Volatile private var started = false

        /**
         * Au lancement de l'interface : réglage dès que le réseau répond (le Wi-Fi met un moment à
         * se connecter au démarrage du robot), puis toutes les 12 heures. Entre-temps, l'heure est
         * notée toutes les cinq minutes pour le prochain démarrage.
         */
        fun start(context: Context, prefs: Prefs, log: Logbook) {
            if (started) return
            started = true
            val t = Thread {
                val retries = longArrayOf(3_000, 5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)
                var i = 0
                while (true) {
                    val sync = TimeSync(context, prefs, log)
                    val r = try {
                        sync.restoreLastKnown()
                        sync.run()
                    } catch (e: Throwable) {
                        log.write("heure", "erreur : ${e.message}")
                        Result(false, current(), "")
                    }
                    // Réussi seulement si l'horloge est fiable : sinon on réessaie vite.
                    val done = r.ok && trusted
                    var wait = if (done) 12 * 3600_000L else retries[minOf(i++, retries.size - 1)]
                    if (done) i = 0
                    while (wait > 0) {
                        val slice = minOf(wait, REMEMBER_EVERY_MS)
                        try {
                            Thread.sleep(slice)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                        wait -= slice
                        remember(context)
                    }
                }
            }
            t.isDaemon = true
            t.name = "toque-heure"
            t.start()
        }
    }
}
