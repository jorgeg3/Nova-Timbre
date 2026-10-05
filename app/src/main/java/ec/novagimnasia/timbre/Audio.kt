package ec.novagimnasia.timbre

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.text.Normalizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Sonidos MP3 incluidos en la app y salida de audio elegida (multimedia o notificaciones). */
object Audio {

    const val MULTIMEDIA = "multimedia"
    const val NOTIFICACIONES = "notificaciones"

    /** Nombre en la columna Sonido del Sheet → archivo en res/raw. */
    val SONIDOS: LinkedHashMap<String, Int> = linkedMapOf(
        "Inicio 1" to R.raw.inicio_1,
        "Inicio 2" to R.raw.inicio_2,
        "Final 1" to R.raw.final_1,
        "Final 2" to R.raw.final_2,
        "Notificación 1" to R.raw.notificacion_1,
        "Notificación 2" to R.raw.notificacion_2,
        "Notificación 3" to R.raw.notificacion_3
    )

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(" ", "")

    fun resId(nombre: String): Int {
        val k = norm(nombre)
        return SONIDOS.entries.firstOrNull { norm(it.key) == k }?.value ?: R.raw.notificacion_1
    }

    fun atributos(c: Context, voz: Boolean): AudioAttributes {
        val usage = if (Store.canal(c) == NOTIFICACIONES) {
            AudioAttributes.USAGE_NOTIFICATION
        } else {
            AudioAttributes.USAGE_MEDIA
        }
        val tipo = if (voz) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC
        return AudioAttributes.Builder().setUsage(usage).setContentType(tipo).build()
    }

    private val activos = java.util.Collections.synchronizedSet(HashSet<MediaPlayer>())

    /**
     * Reproduce el MP3 y vuelve cuando llega el momento de empezar la voz:
     * al final del sonido + separación (negativa = la voz arranca antes de que termine el sonido).
     * El sonido sigue sonando hasta el final aunque la función ya haya vuelto.
     * Llamar fuera del hilo principal.
     */
    fun tocar(c: Context, nombre: String, volumen: Float, separacionMs: Int) {
        val afd = try {
            c.resources.openRawResourceFd(resId(nombre))
        } catch (e: Exception) {
            null
        } ?: return
        val mp = MediaPlayer()
        val fin = CountDownLatch(1)
        var iniciado = false
        try {
            mp.setAudioAttributes(atributos(c, false))
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mp.setVolume(volumen, volumen)
            mp.setOnCompletionListener {
                fin.countDown()
                liberar(it)
            }
            mp.setOnErrorListener { p, _, _ ->
                fin.countDown()
                liberar(p)
                true
            }
            mp.prepare()
            activos.add(mp)
            mp.start()
            iniciado = true
            val espera = (mp.duration.toLong() + separacionMs).coerceAtLeast(0L)
            if (separacionMs >= 0) {
                // esperar el final del sonido y después la pausa elegida
                fin.await(mp.duration.toLong() + 1500L, TimeUnit.MILLISECONDS)
                if (separacionMs > 0) Thread.sleep(separacionMs.toLong())
            } else {
                fin.await(espera, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            if (!iniciado) liberar(mp)
        } finally {
            try {
                afd.close()
            } catch (e: Exception) {
            }
        }
    }

    private fun liberar(mp: MediaPlayer) {
        activos.remove(mp)
        try {
            mp.release()
        } catch (e: Exception) {
        }
    }
}
