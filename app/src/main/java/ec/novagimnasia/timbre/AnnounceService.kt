package ec.novagimnasia.timbre

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Servicio corto: toca el sonido, lee el aviso y se cierra.
 * Pide foco de audio "transitorio con atenuación", así Spotify baja el volumen y luego vuelve.
 */
class AnnounceService : Service(), TextToSpeech.OnInitListener {

    private class Aviso(val sonido: String, val voz: String, val refrescar: Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val cola = ArrayDeque<Aviso>()
    private var tts: TextToSpeech? = null
    private var ttsEstado = 0 // 0 iniciando, 1 listo, -1 no disponible
    private var ocupado = false
    private var actual: Aviso? = null
    private var debeRefrescar = false
    private var cerrando = false
    private var focus: AudioFocusRequest? = null
    private var wake: PowerManager.WakeLock? = null
    private lateinit var am: AudioManager


    private val seguridad = Runnable { finalizar() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Avisos del timbre", NotificationManager.IMPORTANCE_LOW)
        )
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimbreNova:aviso")
            .apply { acquire(3 * 60_000L) }
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val texto = intent?.getStringExtra("voz")?.takeIf { it.isNotBlank() } ?: "Aviso"
        val n = Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Timbre Nova")
            .setContentText(texto)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(7, n)
        }
        if (intent != null) {
            cola.addLast(
                Aviso(
                    intent.getStringExtra("sonido") ?: "Notificación 1",
                    intent.getStringExtra("voz") ?: "",
                    intent.getBooleanExtra("refrescar", false)
                )
            )
        }
        cerrando = false
        handler.removeCallbacks(seguridad)
        handler.postDelayed(seguridad, 90_000L)
        siguiente()
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        val t = tts
        if (status == TextToSpeech.SUCCESS && t != null) {
            val opciones = listOf(
                Locale("es", "EC"), Locale("es", "US"), Locale("es", "MX"),
                Locale("es", "ES"), Locale("es")
            )
            val loc = opciones.firstOrNull { t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            if (loc != null) t.language = loc
            t.setSpeechRate(Store.velocidad(this))
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    handler.post { terminarAviso() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post { terminarAviso() }
                }
            })
            ttsEstado = 1
        } else {
            ttsEstado = -1
        }
        siguiente()
    }

    private fun siguiente() {
        if (ocupado || ttsEstado == 0) return
        val a = cola.removeFirstOrNull()
        if (a == null) {
            finalizar()
            return
        }
        ocupado = true
        actual = a
        if (a.refrescar) debeRefrescar = true
        pedirFoco()
        Thread {
            Audio.tocar(this, a.sonido, Store.volumen(this), if (a.voz.isBlank()) 0 else Store.separacion(this))
            handler.post {
                val t = tts
                if (ttsEstado == 1 && t != null && a.voz.isNotBlank()) {
                    t.setSpeechRate(Store.velocidad(this))
                    t.setAudioAttributes(Audio.atributos(this, true))
                    val r = t.speak(a.voz, TextToSpeech.QUEUE_FLUSH, null, "aviso-" + System.nanoTime())
                    if (r != TextToSpeech.SUCCESS) terminarAviso()
                } else {
                    terminarAviso()
                }
            }
        }.start()
    }

    private fun terminarAviso() {
        if (!ocupado) return
        ocupado = false
        actual?.let { if (it.voz.isNotBlank()) Store.registrar(this, it.voz) }
        actual = null
        soltarFoco()
        siguiente()
    }

    private fun finalizar() {
        if (cerrando) return
        cerrando = true
        val refrescar = debeRefrescar
        debeRefrescar = false
        Thread {
            if (refrescar) {
                try {
                    Store.descargar(this)
                    Scheduler.programar(this)
                } catch (e: Exception) {
                    // sin internet: se sigue con la copia guardada
                }
            }
            handler.post {
                if (cerrando && cola.isEmpty() && !ocupado) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }.start()
    }

    private fun pedirFoco() {
        val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(Audio.atributos(this, true))
            .build()
        focus = f
        am.requestAudioFocus(f)
    }

    private fun soltarFoco() {
        focus?.let { am.abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        soltarFoco()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val CANAL = "avisos"

        fun start(c: Context, sonido: String, voz: String, refrescar: Boolean = false) {
            val i = Intent(c, AnnounceService::class.java)
                .putExtra("sonido", sonido)
                .putExtra("voz", voz)
                .putExtra("refrescar", refrescar)
            try {
                c.startForegroundService(i)
            } catch (e: Exception) {
                // Android puede negar el inicio en casos raros; el próximo aviso igual queda programado.
            }
        }
    }
}
