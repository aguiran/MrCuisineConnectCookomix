package app.toque.ui

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import app.toque.core.machine.Snapshot
import app.toque.recipes.CookomixSync
import app.toque.recipes.OnlineSync

/**
 * Mise en veille de l'écran : après [minutes] sans contact (écran ou bouton rotatif), fond noir,
 * heure peu lumineuse et rétroéclairage au minimum. L'écran reste allumé au sens d'Android
 * (pas d'écran de verrouillage, bouton rotatif toujours actif) ; le premier geste ne fait que
 * réveiller. Jamais pendant un programme, un Turbo, une alerte de la base, ni pendant une
 * synchro de recettes (la synchro continuerait, mais sa progression doit rester visible).
 */
class Standby(private val activity: Activity, root: FrameLayout, private val minutes: () -> Int, private val snapshot: () -> Snapshot) {
    private val main = Handler(Looper.getMainLooper())
    private var lastActivity = System.currentTimeMillis()
    private var running = false

    var sleeping = false
        private set

    private val clock = TextView(activity).apply {
        setTextColor(0xFF4A4F57.toInt())
        textSize = 96f
        typeface = Ui.THIN
        gravity = Gravity.CENTER
    }

    private val view = FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        // Absorbe les touchers (le réveil est géré par l'activité, avant la distribution).
        isClickable = true
        addView(clock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    init {
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (sleeping) clock.text = MainActivity.now()
            else if (shouldSleep()) sleep()
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun canSleep(s: Snapshot) = !s.busy && !s.jobActive && !s.turboActive && !s.hasFault &&
        !OnlineSync.isRunning && !CookomixSync.isRunning

    private fun shouldSleep(): Boolean {
        val m = minutes()
        return m > 0 && System.currentTimeMillis() - lastActivity >= m * 60_000L && canSleep(snapshot())
    }

    fun start() {
        if (running) return
        running = true
        lastActivity = System.currentTimeMillis()
        main.postDelayed(tick, TICK_MS)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
    }

    /** Un geste de l'utilisateur : remet le compteur à zéro. */
    fun touched() {
        lastActivity = System.currentTimeMillis()
    }

    /** L'état de la base a changé : programme lancé ou alerte → on réveille pour qu'elle se voie. */
    fun onSnapshot(s: Snapshot) {
        if (sleeping && !canSleep(s)) wake()
    }

    private fun sleep() {
        sleeping = true
        clock.text = MainActivity.now()
        view.visibility = View.VISIBLE
        view.bringToFront()
        setBrightness(0.02f)
    }

    fun wake() {
        lastActivity = System.currentTimeMillis()
        if (!sleeping) return
        sleeping = false
        view.visibility = View.GONE
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun setBrightness(value: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = value
        activity.window.attributes = lp
    }

    private companion object {
        const val TICK_MS = 15_000L
    }
}
