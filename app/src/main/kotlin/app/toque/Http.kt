package app.toque

import app.toque.core.i18n.tr
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** GET minimal (heure, avis) : corps, et heure du serveur (en-tête Date, 0 si absente). */
object Http {
    class Response(val body: String, val serverMs: Long)

    var userAgent = "Toque (Android; +https://github.com/aguiran/MrCuisineConnectCookomix)"

    /** Réponse avec son code HTTP (le corps est lu même en cas d'erreur : messages des API). */
    class Raw(val code: Int, val body: String)

    /** POST JSON (connexion Cookomix). */
    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000): Raw {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val body = stream?.use { input -> String(input.readBytes(), Charsets.UTF_8) }.orEmpty()
            return Raw(code, body)
        } finally {
            c.disconnect()
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15_000, maxBytes: Int = 1024 * 1024): Response {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", userAgent)
            for ((k, v) in headers) c.setRequestProperty(k, v)
            val code = c.responseCode
            val date = c.date
            if (code != 200) throw IOException(tr("réponse %d du serveur", code))
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException(tr("réponse trop grande"))
                }
                return Response(String(out.toByteArray(), Charsets.UTF_8), date)
            }
        } finally {
            c.disconnect()
        }
    }
}
