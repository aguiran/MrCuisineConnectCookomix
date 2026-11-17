package app.toque

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import app.toque.core.i18n.I18n
import app.toque.machine.MachineClient
import app.toque.recipes.RecipeRepo
import app.toque.ui.MainActivity
import java.util.Locale

class ToqueApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var log: Logbook
        private set
    val machine: MachineClient by lazy { MachineClient(this) }
    val recipes: RecipeRepo by lazy { RecipeRepo(this) }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        log = Logbook(this)
        LanguageFiles.install(this, log)
        I18n.select(I18n.resolve(prefs.language, Locale.getDefault().language).code)
        if (!isMainProcess()) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                log.crash("interface", error)
                val now = System.currentTimeMillis()
                val recent = now - prefs.lastCrashMs < 10_000
                prefs.lastCrashMs = now
                if (recent) throw error // évite une boucle de relances
                // Relance discrète de l'interface ; le processus machine, lui, continue.
                val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_ONE_SHOT)
                (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 800, pi)
                Process.killProcess(Process.myPid())
                System.exit(10)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
        log.write("interface", "démarrage")
        prefs.forgetCookomixAccount()
        TimeSync.start(this, prefs, log)
        resumeInterruptedSyncs()
    }

    /**
     * Synchro interrompue par un arrêt de l'appli (mise à jour, coupure de courant, plantage) :
     * elle reprend toute seule, là où elle s'était arrêtée (index enregistré au fil de l'eau).
     */
    private fun resumeInterruptedSyncs() {
        if (!prefs.lidlSyncPending && !prefs.cookomixSyncPending) return
        // Une synchro qui fait elle-même tomber l'appli ne doit pas reprendre à chaque démarrage.
        if (prefs.syncResumes >= MAX_RESUMES) {
            log.write("synchro", "synchro interrompue $MAX_RESUMES fois de suite : plus de reprise automatique")
            prefs.lidlSyncPending = false
            prefs.cookomixSyncPending = false
            prefs.syncResumes = 0
            return
        }
        prefs.syncResumes = prefs.syncResumes + 1
        // Les deux synchros reprennent en même temps, comme elles avaient été lancées.
        if (prefs.lidlSyncPending) resume("synchro", "toque-reprise-lidl") {
            app.toque.recipes.OnlineSync(this, prefs, recipes, log).sync()
        }
        if (prefs.cookomixSyncPending) resume("cookomix", "toque-reprise-cookomix") {
            app.toque.recipes.CookomixSync(this, prefs, recipes, log).sync()
        }
    }

    private fun resume(tag: String, name: String, sync: () -> Unit) {
        resumesScheduled.incrementAndGet()
        Thread({
            try {
                Thread.sleep(RESUME_DELAY_MS)
                log.write(tag, "reprise de la synchro interrompue")
                sync()
            } catch (e: Exception) {
                log.crash(tag, e)
            } finally {
                resumesScheduled.decrementAndGet()
            }
        }, name).start()
    }

    private fun isMainProcess(): Boolean {
        val pid = Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: return true
        return name == packageName
    }

    companion object {
        fun of(c: Context): ToqueApp = c.applicationContext as ToqueApp

        /** Délai avant la reprise d'une synchro interrompue (laisse l'appli démarrer). */
        const val RESUME_DELAY_MS = 8_000L
        private const val MAX_RESUMES = 3

        /** Reprises programmées et pas encore lancées (pour l'affichage dans Réglages). */
        val resumesScheduled = java.util.concurrent.atomic.AtomicInteger(0)
    }
}
