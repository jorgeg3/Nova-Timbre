package ec.novagimnasia.timbre

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Programa una sola alarma exacta: la del próximo aviso activo. */
object Scheduler {

    const val ACTION_FIRE = "ec.novagimnasia.timbre.FIRE"

    data class Proximo(val evento: Evento, val millis: Long)

    /** 1 = lunes … 6 = sábado, 7 = domingo (igual que la columna Dias del Sheet). */
    fun diaNova(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }

    fun proximo(h: Horario, desde: Long): Proximo? {
        val activos = h.eventosActivos()
        if (activos.isEmpty()) return null
        for (d in 0..7) {
            val dia = Calendar.getInstance()
            dia.timeInMillis = desde
            dia.add(Calendar.DAY_OF_YEAR, d)
            val n = diaNova(dia)
            for (e in activos) {
                if (n !in e.dias) continue
                val c = dia.clone() as Calendar
                c.set(Calendar.HOUR_OF_DAY, e.hora / 60)
                c.set(Calendar.MINUTE, e.hora % 60)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (c.timeInMillis > desde) return Proximo(e, c.timeInMillis)
            }
        }
        return null
    }

    private fun firePI(c: Context, id: String?, t: Long): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        if (id != null) i.putExtra("id", id).putExtra("t", t)
        return PendingIntent.getBroadcast(
            c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun programar(c: Context, desde: Long = System.currentTimeMillis()): Proximo? {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(firePI(c, null, 0L))
        if (Store.pausado(c)) return null
        val h = Store.cargar(c) ?: return null
        val p = proximo(h, desde) ?: return null

        val fire = firePI(c, p.evento.id, p.millis)
        val abrir = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val exacto = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exacto) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(p.millis, abrir), fire)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.millis, fire)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.millis, fire)
        }
        return p
    }
}
