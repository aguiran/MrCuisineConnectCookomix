package app.toque.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import app.toque.Http
import app.toque.Logbook
import app.toque.Prefs
import app.toque.core.i18n.tr
import app.toque.core.photos.SharedAlbum
import java.util.concurrent.Executors

/**
 * Cadre photo de la veille : fait défiler, dans un ordre mélangé, les photos d'un album Google
 * Photos partagé par lien. Chaque photo est lue en ligne à la taille de l'écran au moment de
 * l'afficher ; rien n'est enregistré sur le robot. La suivante est chargée pendant que la
 * précédente est à l'écran, puis remplace celle-ci par un fondu.
 */
class PhotoFrame(private val activity: Activity, private val prefs: Prefs, private val log: Logbook) {
    private val main = Handler(Looper.getMainLooper())

    /** Album illisible ou réseau absent : la veille repasse à l'heure et réessaiera plus tard. */
    var onUnavailable: () -> Unit = {}

    private val front = image()
    private val back = image()
    private val clock = TextView(activity).apply {
        setTextColor(0xCCFFFFFF.toInt())
        textSize = 30f
        typeface = Ui.LIGHT
        setShadowLayer(8f, 0f, 1f, Color.BLACK)
    }
    private val hint = Ui.text(activity, tr("Touchez encore pour quitter le diaporama"), 19f, Ui.TEXT, Ui.MEDIUM).apply {
        setPadding(Ui.u(24), Ui.u(14), Ui.u(24), Ui.u(14))
        background = Ui.round(0xD9101215.toInt(), 40f)
        visibility = View.GONE
    }

    val view = FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        addView(back, Ui.flp())
        addView(front, Ui.flp())
        addView(clock, Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.END).apply { rightMargin = Ui.u(28); bottomMargin = Ui.u(20) })
        addView(hint, Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = Ui.u(90) })
    }

    /** Change à chaque [start] et [stop] : un chargement lancé avant n'affiche plus rien. */
    private var session = 0
    private var order: List<SharedAlbum.Photo> = emptyList()
    private var position = 0
    /** Photo suivante déjà chargée, en attente de son tour. */
    private var ready: Bitmap? = null
    /** La photo affichée a fait son temps : la suivante s'affiche dès qu'elle est chargée. */
    private var due = true
    private var failures = 0
    /** Pas de nouvelle lecture de la liste des photos avant cet instant (après un échec). */
    private var relistAfter = 0L

    private val turn = Runnable {
        val next = ready
        if (next != null) show(next) else due = true
    }

    private fun image() = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }

    fun start() {
        session++
        due = true
        failures = 0
        relistAfter = 0L
        refreshClock()
        list()
    }

    /** Lit la liste des photos puis charge la suivante ; en cas d'échec, continue avec la liste déjà connue. */
    private fun list() {
        val current = session
        val code = prefs.photoAlbum
        loader.execute {
            val album = try {
                album(code)
            } catch (e: Exception) {
                log.write("photos", "album illisible : ${e.message ?: e.javaClass.simpleName}")
                null
            }
            main.post {
                if (current != session) return@post
                if (album != null) {
                    order = album.photos.shuffled()
                    position = 0
                } else {
                    relistAfter = SystemClock.elapsedRealtime() + RELIST_RETRY_MS
                    if (order.isEmpty()) return@post onUnavailable()
                }
                loadNext()
            }
        }
    }

    fun stop() {
        session++
        main.removeCallbacks(turn)
        showHint(false)
        for (v in listOf(front, back)) {
            v.animate().cancel()
            release(v)
        }
        ready?.recycle()
        ready = null
        order = emptyList()
    }

    fun showHint(on: Boolean) {
        hint.visibility = if (on) View.VISIBLE else View.GONE
    }

    /** Heure discrète dans le coin ; masquée tant qu'elle n'est pas réglée. */
    fun refreshClock() {
        val now = MainActivity.now()
        clock.text = if (now.startsWith("-")) "" else now
    }

    private fun loadNext() {
        if (order.isEmpty()) return
        val current = session
        val now = SystemClock.elapsedRealtime()
        if (albumAge() > ALBUM_MAX_AGE_MS && now >= relistAfter) return list()
        if (position >= order.size) {
            // Tour de l'album terminé : nouvel ordre.
            order = order.shuffled()
            position = 0
        }
        val photo = order[position++]
        val dm = activity.resources.displayMetrics
        val url = photo.sized(dm.widthPixels, dm.heightPixels)
        loader.execute {
            val bitmap = try {
                val bytes = Http.bytes(url, timeoutMs = 20_000, maxBytes = MAX_PHOTO_BYTES)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            main.post {
                if (current != session) {
                    bitmap?.recycle()
                    return@post
                }
                if (bitmap == null) failed(current) else loaded(bitmap)
            }
        }
    }

    private fun loaded(bitmap: Bitmap) {
        failures = 0
        if (due) show(bitmap) else ready = bitmap
    }

    /** Photo illisible : on passe à la suivante ; plusieurs de suite, le réseau ou l'album a un problème. */
    private fun failed(current: Int) {
        if (++failures >= MAX_FAILURES) {
            log.write("photos", "photos illisibles $MAX_FAILURES fois de suite")
            forget()
            onUnavailable()
        } else main.postDelayed({ if (current == session) loadNext() }, RETRY_MS)
    }

    /** Fondu de la photo affichée vers [bitmap], puis chargement de la suivante. */
    private fun show(bitmap: Bitmap) {
        ready = null
        due = false
        release(back)
        back.setImageDrawable(front.drawable)
        back.alpha = 1f
        front.alpha = 0f
        front.setImageBitmap(bitmap)
        front.animate().alpha(1f).setDuration(FADE_MS).withEndAction { release(back) }.start()
        back.animate().alpha(0f).setDuration(FADE_MS).start()
        refreshClock()
        main.removeCallbacks(turn)
        main.postDelayed(turn, prefs.photoSeconds.coerceIn(10, 60) * 1000L)
        loadNext()
    }

    /** Libère la photo d'une vue, sauf si l'autre vue l'affiche encore (le temps du fondu). */
    private fun release(v: ImageView) {
        val shown = v.drawable
        v.setImageDrawable(null)
        val other = if (v === front) back else front
        if (shown != null && shown !== other.drawable) (shown as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
    }

    companion object {
        /** Un seul fil de chargement pour l'appli, quel que soit le nombre d'écrans créés. */
        private val loader = Executors.newSingleThreadExecutor { r ->
            Thread(r, "toque-photos").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
        }
        private const val FADE_MS = 1_200L
        private const val RETRY_MS = 15_000L
        private const val RELIST_RETRY_MS = 10 * 60_000L
        private const val MAX_FAILURES = 3
        private const val MAX_PHOTO_BYTES = 6 * 1024 * 1024
        private const val MAX_PAGE_BYTES = 6 * 1024 * 1024
        /** Les adresses des photos ne valent qu'un temps : la liste est relue au bout d'une heure. */
        private const val ALBUM_MAX_AGE_MS = 3600_000L

        private var cached: SharedAlbum.Album? = null
        private var cachedCode = ""
        private var cachedAt = 0L

        /** Photos de l'album partagé [code] (requête réseau, sauf si la liste vient d'être lue). */
        @Synchronized
        fun album(code: String, fresh: Boolean = false): SharedAlbum.Album {
            cached?.let { if (!fresh && code == cachedCode && albumAge() <= ALBUM_MAX_AGE_MS) return it }
            val page = Http.get(SharedAlbum.url(code), timeoutMs = 20_000, maxBytes = MAX_PAGE_BYTES).body
            val album = SharedAlbum.parse(page)
            cached = album
            cachedCode = code
            cachedAt = SystemClock.elapsedRealtime()
            return album
        }

        @Synchronized
        private fun albumAge(): Long = if (cached == null) Long.MAX_VALUE else SystemClock.elapsedRealtime() - cachedAt

        @Synchronized
        private fun forget() {
            cached = null
        }
    }
}
