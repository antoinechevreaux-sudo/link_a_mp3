package com.tony.linkamp3

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var etUrl: EditText
    private lateinit var rgMode: RadioGroup
    private lateinit var cbAuto: CheckBox
    private lateinit var jobsBox: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvEngine: TextView
    private lateinit var btnUpdate: Button
    private val prefs by lazy { getSharedPreferences("ajustes", Context.MODE_PRIVATE) }
    private val rows = HashMap<String, Row>()
    private val listener: () -> Unit = { render() }

    private class Row(
        val root: View, val title: TextView, val status: TextView,
        val bar: ProgressBar, val actions: LinearLayout, var shownState: State? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        etUrl = findViewById(R.id.etUrl)
        rgMode = findViewById(R.id.rgMode)
        cbAuto = findViewById(R.id.cbAuto)
        jobsBox = findViewById(R.id.jobs)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvEngine = findViewById(R.id.tvEngine)
        btnUpdate = findViewById(R.id.btnUpdate)

        // Opciones de formato
        val saved = prefs.getInt("modo", 0)
        Mode.entries.forEachIndexed { i, m ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = m.label
                tag = i
                setTextColor(getColor(R.color.texto))
                textSize = 15f
                buttonTintList = getColorStateList(R.color.acento)
                isChecked = i == saved
            }
            rgMode.addView(rb)
        }
        rgMode.setOnCheckedChangeListener { g, checkedId ->
            val i = g.findViewById<RadioButton>(checkedId)?.tag as? Int ?: 0
            prefs.edit().putInt("modo", i).apply()
        }

        cbAuto.isChecked = prefs.getBoolean("auto", false)
        cbAuto.setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("auto", c).apply() }

        findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteFromClipboard() }
        findViewById<Button>(R.id.btnDownload).setOnClickListener { startFromField() }
        findViewById<View>(R.id.btnClear).setOnClickListener { Jobs.clearFinished() }
        etUrl.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO) { startFromField(); true } else false
        }
        btnUpdate.setOnClickListener { updateEngine() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        Jobs.listen(listener)
        render()
        refreshEngineLabel()
    }

    override fun onStop() {
        Jobs.unlisten(listener)
        super.onStop()
    }

    // ------------------------------------------------------------ acciones

    private fun currentMode(): Mode {
        val i = rgMode.findViewById<RadioButton>(rgMode.checkedRadioButtonId)?.tag as? Int ?: 0
        return Mode.entries.getOrElse(i) { Mode.MP3_ALTA }
    }

    private fun extractUrl(text: String?): String? =
        text?.let { Regex("https?://\\S+").find(it)?.value?.trimEnd('.', ',', ')', '»', '"', '\'') }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val url = extractUrl(text)
        if (url == null) {
            Toast.makeText(this, "No he encontrado ningún enlace en lo compartido", Toast.LENGTH_LONG).show()
            return
        }
        etUrl.setText(url)
        if (prefs.getBoolean("auto", false)) {
            DownloadService.start(this, url, currentMode())
            Toast.makeText(this, "Descargando: ${currentMode().label}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        val url = extractUrl(text)
        if (url != null) etUrl.setText(url)
        else Toast.makeText(this, "No hay ningún enlace en el portapapeles", Toast.LENGTH_SHORT).show()
    }

    private fun startFromField() {
        var url = etUrl.text.toString().trim()
        if (url.isEmpty()) { pasteFromClipboard(); url = etUrl.text.toString().trim() }
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        DownloadService.start(this, url, currentMode())
        etUrl.setText("")
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(etUrl.windowToken, 0)
    }

    private fun updateEngine() {
        btnUpdate.isEnabled = false
        tvEngine.text = "Actualizando yt-dlp…"
        Thread {
            val msg = try {
                if (Engine.update(this)) "Motor actualizado" else "El motor ya estaba al día"
            } catch (e: Throwable) {
                "No se pudo actualizar: ${e.message ?: "sin conexión"}"
            }
            runOnUiThread {
                btnUpdate.isEnabled = true
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                refreshEngineLabel()
            }
        }.start()
    }

    private fun refreshEngineLabel() {
        Thread {
            val txt = try {
                Engine.ensureInit(this)
                "Motor: yt-dlp ${Engine.version(this) ?: "?"}"
            } catch (e: Throwable) {
                "Error al preparar el motor: ${e.message}"
            }
            runOnUiThread { tvEngine.text = txt }
        }.start()
    }

    // ------------------------------------------------------------ lista

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun render() {
        val jobs = Jobs.all.toList()
        tvEmpty.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
        val ids = jobs.map { it.id }.toSet()
        rows.keys.filter { it !in ids }.forEach { id -> rows.remove(id)?.let { jobsBox.removeView(it.root) } }
        jobs.forEachIndexed { index, job ->
            val row = rows.getOrPut(job.id) { createRow().also { jobsBox.addView(it.root, index) } }
            if (jobsBox.indexOfChild(row.root) != index) { jobsBox.removeView(row.root); jobsBox.addView(row.root, index) }
            bind(row, job)
        }
    }

    private fun createRow(): Row {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(4))
        }
        val title = TextView(this).apply {
            setTextColor(getColor(R.color.texto)); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        val status = TextView(this).apply { setTextColor(getColor(R.color.gris)); textSize = 13f }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = getColorStateList(R.color.acento)
            indeterminateTintList = getColorStateList(R.color.acento)
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        root.addView(title)
        root.addView(status)
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(6) })
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        return Row(root, title, status, bar, actions)
    }

    private fun bind(row: Row, job: Job) {
        row.title.text = job.title
        row.status.text = job.status
        row.status.setTextColor(getColor(when (job.state) {
            State.DONE -> R.color.ok; State.ERROR -> R.color.error; else -> R.color.gris
        }))
        val running = !job.finished
        row.bar.visibility = if (running) View.VISIBLE else View.GONE
        if (running) {
            row.bar.isIndeterminate = job.progress < 0f
            if (job.progress >= 0f) row.bar.progress = job.progress.toInt()
        }
        if (row.shownState == job.state) return
        row.shownState = job.state
        row.actions.removeAllViews()
        when (job.state) {
            State.QUEUED, State.RUNNING -> addAction(row, "Cancelar") { Jobs.cancel(job.id) }
            State.DONE -> {
                addAction(row, "▶ Abrir") { open(job) }
                addAction(row, "Compartir") { share(job) }
            }
            State.ERROR, State.CANCELED -> addAction(row, "Reintentar") { DownloadService.start(this, job.url, job.mode) }
        }
    }

    private fun addAction(row: Row, label: String, onClick: () -> Unit) {
        val b = Button(this, null, 0, R.style.BotonSecundario).apply {
            text = label; textSize = 13f
            setOnClickListener { onClick() }
        }
        row.actions.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginStart = dp(8) })
    }

    private fun open(job: Job) {
        val uri = job.results.firstOrNull() ?: return
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (job.isVideo) "video/*" else "audio/mpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(i) } catch (_: Throwable) {
            Toast.makeText(this, "No hay ninguna app para reproducirlo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(job: Job) {
        if (job.results.isEmpty()) return
        val type = if (job.isVideo) "video/mp4" else "audio/mpeg"
        val i = if (job.results.size == 1) {
            Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, job.results[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType(type)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(job.results))
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, "Compartir"))
    }
}
