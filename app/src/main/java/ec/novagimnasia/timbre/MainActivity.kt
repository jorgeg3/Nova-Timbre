package ec.novagimnasia.timbre

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    // Paleta del logo de Nova
    private val cFondo = Color.parseColor("#000000")
    private val cTarjeta = Color.parseColor("#150D2B")
    private val cBorde = Color.parseColor("#2E1F57")
    private val cMorado = Color.parseColor("#7C3AED")
    private val cLila = Color.parseColor("#A78BFA")
    private val cTexto = Color.parseColor("#EDE9FE")
    private val cSuave = Color.parseColor("#B9AEDB")
    private val cApagado = Color.parseColor("#5B4E80")

    private val dias = arrayOf("", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val abiertos = mutableSetOf<String>()

    private lateinit var txtHora: TextView
    private lateinit var txtProximo: TextView
    private lateinit var txtEstado: TextView
    private lateinit var swActivo: Switch
    private lateinit var btnBateria: Button
    private lateinit var contBloques: LinearLayout

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        construir()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        if (Store.url(this).isBlank()) mostrarAjustes() else actualizar(silencioso = true)
    }

    override fun onResume() {
        super.onResume()
        Scheduler.programar(this)
        refrescarVista()
    }

    // ---------- piezas visuales ----------

    private fun fondoRedondeado(color: Int, borde: Int? = null, radio: Int = 16): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = px(radio).toFloat()
        if (borde != null) g.setStroke(px(1), borde)
        return g
    }

    private fun tarjeta(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.background = fondoRedondeado(cTarjeta, cBorde)
        l.setPadding(px(18), px(14), px(18), px(16))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = px(12)
        l.layoutParams = lp
        return l
    }

    private fun etiqueta(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 12f
        v.letterSpacing = 0.12f
        v.setTextColor(cLila)
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        v.setPadding(0, 0, 0, px(6))
        return v
    }

    private fun texto(t: String, tam: Float = 15f, color: Int = cTexto): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = tam
        v.setTextColor(color)
        return v
    }

    private fun boton(t: String, principal: Boolean = true, accion: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.textSize = 15f
        b.setTextColor(if (principal) Color.WHITE else cTexto)
        b.background = if (principal) fondoRedondeado(cMorado, null, 12) else fondoRedondeado(cTarjeta, cBorde, 12)
        b.setPadding(px(14), px(10), px(14), px(10))
        b.stateListAnimator = null
        b.setOnClickListener { accion() }
        return b
    }

    private fun conMargen(v: View, ancho: Int, peso: Float = 0f, margen: Int = 4): View {
        val lp = LinearLayout.LayoutParams(ancho, LinearLayout.LayoutParams.WRAP_CONTENT, peso)
        lp.setMargins(px(margen), px(margen), px(margen), px(margen))
        v.layoutParams = lp
        return v
    }

    private fun colorearSwitch(s: Switch) {
        val estados = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        s.thumbTintList = ColorStateList(estados, intArrayOf(cLila, cSuave))
        s.trackTintList = ColorStateList(estados, intArrayOf(cMorado, cApagado))
    }

    private fun barra(min: Int, max: Int, valor: Int, cambio: (Int) -> Unit): SeekBar {
        val s = SeekBar(this)
        s.max = max - min
        s.progress = (valor - min).coerceIn(0, max - min)
        s.progressTintList = ColorStateList.valueOf(cLila)
        s.thumbTintList = ColorStateList.valueOf(cLila)
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) cambio(p + min)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        return s
    }

    // ---------- construcción de la pantalla ----------

    private fun construir() {
        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(px(16), px(8), px(16), px(40))
        raiz.setBackgroundColor(cFondo)

        // Logo
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_nova)
        logo.adjustViewBounds = true
        logo.maxHeight = px(230)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        logo.contentDescription = "Nova Gimnasia"
        val lpLogo = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpLogo.gravity = Gravity.CENTER_HORIZONTAL
        logo.layoutParams = lpLogo
        raiz.addView(logo)

        val titulo = texto("TIMBRE", 13f, cSuave)
        titulo.letterSpacing = 0.5f
        titulo.gravity = Gravity.CENTER_HORIZONTAL
        titulo.setPadding(0, 0, 0, px(4))
        raiz.addView(titulo)

        // Próximo aviso
        val tProx = tarjeta()
        tProx.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#2A1660"), Color.parseColor("#150D2B"))
        ).apply { cornerRadius = px(16).toFloat(); setStroke(px(1), cBorde) }
        tProx.addView(etiqueta("PRÓXIMO AVISO"))
        txtHora = texto("--:--", 44f, cLila)
        txtHora.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        tProx.addView(txtHora)
        txtProximo = texto("", 18f)
        tProx.addView(txtProximo)
        txtEstado = texto("", 13f, cSuave)
        txtEstado.setPadding(0, px(8), 0, 0)
        tProx.addView(txtEstado)
        raiz.addView(tProx)

        // Controles
        val tCtl = tarjeta()
        swActivo = Switch(this)
        swActivo.text = "Timbre activo"
        swActivo.textSize = 18f
        swActivo.setTextColor(cTexto)
        colorearSwitch(swActivo)
        swActivo.isChecked = !Store.pausado(this)
        swActivo.setOnCheckedChangeListener { _, on ->
            Store.setPausado(this, !on)
            Scheduler.programar(this)
            refrescarVista()
        }
        tCtl.addView(swActivo)
        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL
        fila.setPadding(0, px(10), 0, 0)
        fila.addView(conMargen(boton("Actualizar") { actualizar(silencioso = false) }, 0, 1f))
        fila.addView(conMargen(boton("Probar") {
            AnnounceService.start(this, "Notificación 1", "Prueba de sonido del gimnasio Nova")
        }, 0, 1f))
        fila.addView(conMargen(boton("Ajustes", false) { mostrarAjustes() }, 0, 1f))
        tCtl.addView(fila)
        btnBateria = boton("Permitir que funcione con la pantalla apagada", false) { pedirBateria() }
        tCtl.addView(conMargen(btnBateria, LinearLayout.LayoutParams.MATCH_PARENT))
        raiz.addView(tCtl)

        // Bloques
        val tBl = tarjeta()
        tBl.addView(etiqueta("BLOQUES"))
        contBloques = LinearLayout(this)
        contBloques.orientation = LinearLayout.VERTICAL
        tBl.addView(contBloques)
        raiz.addView(tBl)

        // Audio
        val tAu = tarjeta()
        tAu.addView(etiqueta("SALIDA DE AUDIO"))
        val grupo = RadioGroup(this)
        grupo.orientation = LinearLayout.VERTICAL
        val rbMedia = RadioButton(this)
        rbMedia.id = View.generateViewId()
        rbMedia.text = "Volumen multimedia (el mismo de Spotify)"
        val rbNotif = RadioButton(this)
        rbNotif.id = View.generateViewId()
        rbNotif.text = "Volumen de notificaciones"
        for (rb in listOf(rbMedia, rbNotif)) {
            rb.setTextColor(cTexto)
            rb.textSize = 15f
            rb.buttonTintList = ColorStateList.valueOf(cLila)
            grupo.addView(rb)
        }
        grupo.check(if (Store.canal(this) == Audio.NOTIFICACIONES) rbNotif.id else rbMedia.id)
        grupo.setOnCheckedChangeListener { _, id ->
            Store.setCanal(this, if (id == rbNotif.id) Audio.NOTIFICACIONES else Audio.MULTIMEDIA)
            toast(if (id == rbNotif.id) "Usando el volumen de notificaciones" else "Usando el volumen multimedia")
        }
        tAu.addView(grupo)
        val notaAudio = texto(
            "Con «notificaciones» el timbre no depende del volumen de Spotify, pero no suena si la tablet " +
                "está en No molestar o en silencio.", 12f, cSuave
        )
        notaAudio.setPadding(0, px(4), 0, px(10))
        tAu.addView(notaAudio)

        tAu.addView(etiqueta("VOLUMEN DEL SONIDO"))
        tAu.addView(barra(10, 100, (Store.volumen(this) * 100).toInt()) { Store.setVolumen(this, it / 100f) })
        val lblSep = etiqueta(textoSeparacion(Store.separacion(this))).apply { setPadding(0, px(10), 0, px(6)) }
        tAu.addView(lblSep)
        tAu.addView(barra(-15, 10, Store.separacion(this) / 100) {
            Store.setSeparacion(this, it * 100)
            lblSep.text = textoSeparacion(it * 100)
        })
        tAu.addView(texto("Hacia la izquierda la voz entra antes, encima del final del sonido.", 12f, cSuave))
        tAu.addView(etiqueta("VELOCIDAD DE LA VOZ").apply { setPadding(0, px(10), 0, px(6)) })
        tAu.addView(barra(70, 120, (Store.velocidad(this) * 100).toInt()) { Store.setVelocidad(this, it / 100f) })
        raiz.addView(tAu)

        // Sonidos
        val tSo = tarjeta()
        tSo.addView(etiqueta("ESCUCHAR SONIDOS"))
        val nombres = Audio.SONIDOS.keys.toList()
        var filaS: LinearLayout? = null
        nombres.forEachIndexed { i, n ->
            if (i % 2 == 0) {
                filaS = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                tSo.addView(filaS)
            }
            filaS?.addView(conMargen(boton("▶  $n", false) { AnnounceService.start(this, n, "") }, 0, 1f))
        }
        if (nombres.size % 2 == 1) filaS?.addView(conMargen(View(this), 0, 1f))
        raiz.addView(tSo)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(cFondo)
        scroll.addView(raiz)
        setContentView(scroll)
    }

    private fun textoSeparacion(ms: Int): String {
        val seg = String.format(Locale.US, "%.1f", kotlin.math.abs(ms) / 1000f).replace('.', ',')
        return when {
            ms < 0 -> "PAUSA SONIDO → VOZ: LA VOZ ENTRA $seg S ANTES DEL FINAL"
            ms == 0 -> "PAUSA SONIDO → VOZ: SIN PAUSA"
            else -> "PAUSA SONIDO → VOZ: $seg S"
        }
    }

    // ---------- estado ----------

    private fun diaTexto(millis: Long): String {
        val hoy = Calendar.getInstance()
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        if (hoy.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            hoy.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
        ) return "Hoy"
        return dias[Scheduler.diaNova(c)] + " " +
            SimpleDateFormat("d/M", Locale("es", "EC")).format(Date(millis))
    }

    private fun refrescarVista() {
        val h = Store.cargar(this)
        val pausado = Store.pausado(this)
        val p = if (h != null && !pausado) Scheduler.proximo(h, System.currentTimeMillis()) else null
        when {
            h == null -> {
                txtHora.text = "--:--"
                txtProximo.text = "Sin horario. Configurá el enlace del Sheet en Ajustes."
            }
            pausado -> {
                txtHora.text = "Pausa"
                txtProximo.text = "El timbre está en pausa"
            }
            p == null -> {
                txtHora.text = "--:--"
                txtProximo.text = "No hay avisos activos"
            }
            else -> {
                txtHora.text = "${diaTexto(p.millis)} · ${p.evento.horaTexto}"
                txtProximo.text = p.evento.voz
            }
        }
        txtEstado.text = "Último aviso: ${Store.ultimo(this)}\n" +
            "Horario descargado: ${h?.actualizado?.ifBlank { "—" } ?: "—"}"

        val pm = getSystemService(PowerManager::class.java)
        btnBateria.visibility =
            if (pm.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE

        contBloques.removeAllViews()
        if (h != null) {
            h.bloques.forEachIndexed { i, b ->
                if (i > 0) contBloques.addView(separador())
                contBloques.addView(vistaBloque(h, b))
            }
        }
    }

    private fun separador(): View {
        val v = View(this)
        v.setBackgroundColor(cBorde)
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1))
        return v
    }

    private fun vistaBloque(h: Horario, b: Bloque): View {
        val caja = LinearLayout(this)
        caja.orientation = LinearLayout.VERTICAL
        caja.setPadding(0, px(10), 0, px(10))

        val evs = h.eventos.filter { it.bloque == b.nombre }
        val diasTxt = evs.flatMap { it.dias }.distinct().sorted().joinToString(", ") { dias[it] }
        val rango = if (evs.isEmpty()) {
            "sin avisos"
        } else {
            "${evs.first().horaTexto}–${evs.last().horaTexto} · ${evs.size} avisos · $diasTxt"
        }

        val sw = Switch(this)
        sw.text = b.nombre
        sw.textSize = 17f
        sw.setTextColor(if (b.activo) cTexto else cApagado)
        sw.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        colorearSwitch(sw)
        sw.isChecked = b.activo
        sw.setOnCheckedChangeListener { v, on ->
            v.isEnabled = false
            cambiarBloque(b.nombre, on)
        }
        caja.addView(sw)
        caja.addView(texto(rango, 13f, if (b.activo) cSuave else cApagado))

        val abierto = b.nombre in abiertos
        val ver = texto(if (abierto) "Ocultar avisos ▲" else "Ver avisos ▼", 14f, cLila)
        ver.setPadding(0, px(6), 0, px(2))
        ver.setOnClickListener {
            if (!abiertos.remove(b.nombre)) abiertos.add(b.nombre)
            refrescarVista()
        }
        caja.addView(ver)

        if (abierto) {
            evs.forEach { e ->
                val fila = LinearLayout(this)
                fila.orientation = LinearLayout.HORIZONTAL
                fila.gravity = Gravity.CENTER_VERTICAL
                fila.setPadding(px(4), px(6), 0, px(6))

                val hora = texto(e.horaTexto, 17f, cLila)
                hora.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                hora.layoutParams = LinearLayout.LayoutParams(px(62), LinearLayout.LayoutParams.WRAP_CONTENT)
                fila.addView(hora)

                val t = TextView(this)
                val extra = if (e.habilitado) "" else " · desactivado en el Sheet"
                val diasEv = e.dias.joinToString(", ") { dias[it] }
                t.text = "${e.voz}\n${e.sonido} · $diasEv$extra"
                t.textSize = 14f
                t.setTextColor(if (e.habilitado) cTexto else cApagado)
                t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                fila.addView(t)
                fila.addView(boton("▶", false) { AnnounceService.start(this, e.sonido, e.voz) })
                caja.addView(fila)
            }
        }
        return caja
    }

    // ---------- acciones ----------

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private fun actualizar(silencioso: Boolean) {
        Thread {
            val error = try {
                Store.descargar(this)
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            runOnUiThread {
                Scheduler.programar(this)
                refrescarVista()
                if (error != null) toast("No se pudo actualizar: $error")
                else if (!silencioso) toast("Horario actualizado")
            }
        }.start()
    }

    private fun cambiarBloque(nombre: String, activo: Boolean) {
        Thread {
            val error = try {
                Store.setBloque(this, nombre, activo)
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            runOnUiThread {
                if (error != null) toast("No se pudo guardar en el Sheet: $error")
                Scheduler.programar(this)
                refrescarVista()
            }
        }.start()
    }

    private fun mostrarAjustes() {
        val caja = LinearLayout(this)
        caja.orientation = LinearLayout.VERTICAL
        caja.setPadding(px(20), px(8), px(20), 0)

        val url = EditText(this)
        url.hint = "Enlace de la aplicación web (termina en /exec)"
        url.inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
        url.setText(Store.url(this))
        caja.addView(url)

        val token = EditText(this)
        token.hint = "Clave (TOKEN del Apps Script)"
        token.inputType = InputType.TYPE_CLASS_TEXT
        token.setText(Store.token(this))
        caja.addView(token)

        AlertDialog.Builder(this)
            .setTitle("Conexión con el Google Sheet")
            .setView(caja)
            .setPositiveButton("Guardar") { _, _ ->
                Store.setConfig(this, url.text.toString(), token.text.toString())
                actualizar(silencioso = false)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pedirBateria() {
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
