package ec.novagimnasia.timbre

import org.json.JSONObject
import java.util.Locale

data class Evento(
    val id: String,
    val bloque: String,
    val dias: List<Int>,
    val habilitado: Boolean,
    val hora: Int, // minutos desde medianoche
    val sonido: String,
    val etiqueta: String,
    val voz: String
) {
    val horaTexto: String
        get() = String.format(Locale.US, "%02d:%02d", hora / 60, hora % 60)
}

data class Bloque(val nombre: String, val activo: Boolean)

data class Horario(val bloques: List<Bloque>, val eventos: List<Evento>, val actualizado: String) {

    fun bloqueActivo(nombre: String): Boolean =
        bloques.firstOrNull { it.nombre == nombre }?.activo ?: true

    fun eventosActivos(): List<Evento> =
        eventos.filter { it.habilitado && bloqueActivo(it.bloque) }

    companion object {
        private val REGEX_HORA = Regex("""(\d{1,2}):(\d{2})""")

        fun parseHora(s: String): Int? {
            val m = REGEX_HORA.find(s) ?: return null
            val h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            if (h > 23 || mi > 59) return null
            return h * 60 + mi
        }

        fun parse(txt: String): Horario {
            val o = JSONObject(txt)
            if (!o.optBoolean("ok", false)) {
                throw IllegalStateException(o.optString("error", "Respuesta inválida del Sheet"))
            }
            val bl = o.getJSONArray("bloques")
            val bloques = ArrayList<Bloque>()
            for (i in 0 until bl.length()) {
                val b = bl.getJSONObject(i)
                bloques.add(Bloque(b.getString("nombre"), b.optBoolean("activo", true)))
            }
            val ev = o.getJSONArray("eventos")
            val eventos = ArrayList<Evento>()
            for (i in 0 until ev.length()) {
                val e = ev.getJSONObject(i)
                val hora = parseHora(e.optString("hora")) ?: continue
                val da = e.getJSONArray("dias")
                val dias = ArrayList<Int>()
                for (j in 0 until da.length()) {
                    val d = da.getInt(j)
                    if (d in 1..7) dias.add(d)
                }
                eventos.add(
                    Evento(
                        id = e.optString("id", "f$i"),
                        bloque = e.optString("bloque"),
                        dias = dias,
                        habilitado = e.optBoolean("habilitado", true),
                        hora = hora,
                        sonido = e.optString("sonido", "Notificación 1"),
                        etiqueta = e.optString("etiqueta"),
                        voz = e.optString("voz")
                    )
                )
            }
            eventos.sortBy { it.hora }
            return Horario(bloques, eventos, o.optString("actualizado"))
        }
    }
}
