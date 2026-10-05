package ec.novagimnasia.timbre

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Configuración local y copia del horario descargado del Google Sheet. */
object Store {

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("timbre", Context.MODE_PRIVATE)

    fun url(c: Context): String = prefs(c).getString("url", "") ?: ""
    fun token(c: Context): String = prefs(c).getString("token", "") ?: ""
    fun setConfig(c: Context, url: String, token: String) {
        prefs(c).edit().putString("url", url.trim()).putString("token", token.trim()).apply()
    }

    fun pausado(c: Context): Boolean = prefs(c).getBoolean("pausado", false)
    fun setPausado(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("pausado", v).apply()
    }

    fun velocidad(c: Context): Float = prefs(c).getFloat("velocidad", 0.95f)
    fun setVelocidad(c: Context, v: Float) {
        prefs(c).edit().putFloat("velocidad", v).apply()
    }

    fun volumen(c: Context): Float = prefs(c).getFloat("volumen", 1.0f)
    fun setVolumen(c: Context, v: Float) {
        prefs(c).edit().putFloat("volumen", v).apply()
    }

    /** Milisegundos entre el final del sonido y la voz (negativo = la voz empieza antes). */
    fun separacion(c: Context): Int = prefs(c).getInt("separacion", -300)
    fun setSeparacion(c: Context, v: Int) {
        prefs(c).edit().putInt("separacion", v).apply()
    }

    fun canal(c: Context): String = prefs(c).getString("canal", Audio.MULTIMEDIA) ?: Audio.MULTIMEDIA
    fun setCanal(c: Context, v: String) {
        prefs(c).edit().putString("canal", v).apply()
    }

    fun ultimo(c: Context): String = prefs(c).getString("ultimo", "—") ?: "—"
    fun registrar(c: Context, texto: String) {
        val f = SimpleDateFormat("EEE d/M HH:mm", Locale("es", "EC")).format(Date())
        prefs(c).edit().putString("ultimo", "$f · $texto").apply()
    }

    private fun cacheFile(c: Context) = File(c.filesDir, "horario.json")

    fun cargar(c: Context): Horario? = try {
        val f = cacheFile(c)
        if (f.exists()) Horario.parse(f.readText()) else null
    } catch (e: Exception) {
        null
    }

    /** Descarga el horario del Sheet y lo guarda. Lanza excepción si falla. */
    fun descargar(c: Context): Horario = pedir(c, emptyMap())

    /** Activa o desactiva un bloque en el Sheet y guarda el horario resultante. */
    fun setBloque(c: Context, nombre: String, activo: Boolean): Horario =
        pedir(c, mapOf("action" to "setBloque", "bloque" to nombre, "activo" to activo.toString()))

    private fun pedir(c: Context, params: Map<String, String>): Horario {
        val base = url(c)
        if (base.isBlank()) throw IllegalStateException("Falta el enlace del Sheet en Ajustes")
        val todos = params + ("token" to token(c))
        val query = todos.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val txt = get(base + (if (base.contains("?")) "&" else "?") + query)
        val h = Horario.parse(txt) // valida antes de guardar
        cacheFile(c).writeText(txt)
        return h
    }

    private fun get(u: String): String {
        val con = URL(u).openConnection() as HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 25000
        con.instanceFollowRedirects = true
        try {
            val code = con.responseCode
            val stream = if (code in 200..299) con.inputStream else con.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code")
            return body
        } finally {
            con.disconnect()
        }
    }
}
