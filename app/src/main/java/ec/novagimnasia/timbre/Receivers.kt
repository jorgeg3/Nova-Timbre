package ec.novagimnasia.timbre

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Se dispara a la hora exacta de un aviso. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ahora = System.currentTimeMillis()
        val t = i.getLongExtra("t", ahora)
        val id = i.getStringExtra("id")
        val h = Store.cargar(c)
        val e = h?.eventosActivos()?.firstOrNull { it.id == id }
        // Si el aviso llega con más de 5 minutos de atraso, se omite.
        if (e != null && !Store.pausado(c) && ahora - t < 5 * 60_000L) {
            AnnounceService.start(c, e.sonido, e.voz, refrescar = true)
        }
        Scheduler.programar(c, maxOf(ahora, t) + 1000L)
    }
}

/** Vuelve a programar después de reiniciar, actualizar la app o cambiar la hora. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.programar(c)
    }
}
