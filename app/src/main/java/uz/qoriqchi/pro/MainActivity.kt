package uz.qoriqchi.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.security.MessageDigest

class MainActivity : Activity() {

    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var linkInput: EditText
    private lateinit var keyInput: EditText

    private val gray = 0xFF6B7280.toInt()
    private val ink = 0xFF1B1F23.toInt()
    private val prefs get() = getSharedPreferences("qp", Context.MODE_PRIVATE)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // ---------- UI ----------

    private fun text(s: String, size: Float, bold: Boolean = false, color: Int = ink) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
        }

    private fun btn(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(12).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }

    private fun buildUi() {
        val root = ScrollView(this).apply { setBackgroundColor(0xFFF4F6F8.toInt()) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }
        root.addView(col)

        col.addView(text("Qo'riqchi Pro", 26f, true))
        col.addView(
            text(
                "Zararli APK, soxta ilova va firibgar havolalarni tekshiradi. " +
                    "Tahlil telefonning o'zida bajariladi, fayllaringiz yuborilmaydi.",
                14f, false, gray
            )
        )

        col.addView(btn("📱  O'rnatilgan ilovalarni tekshir") { scanInstalled() })
        col.addView(btn("📦  APK faylni tekshir") { pickFile() })

        linkInput = EditText(this).apply {
            hint = "Havolani shu yerga qo'ying"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        col.addView(linkInput)
        col.addView(btn("🔗  Havolani tekshir") { checkLink() })

        col.addView(text("VirusTotal API kaliti (ixtiyoriy)", 13f, true, gray))
        keyInput = EditText(this).apply {
            hint = "Kalit (faqat xesh yuboriladi)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(prefs.getString("vt", ""))
        }
        col.addView(keyInput)
        col.addView(btn("Kalitni saqlash") {
            prefs.edit().putString("vt", keyInput.text.toString().trim()).apply()
            toast("Saqlandi")
        })

        status = text("", 14f, true)
        col.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(results)

        setContentView(root)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun showReport(r: Report, installed: Boolean) {
        val c = card()
        c.addView(text(r.label, 16f, true))
        c.addView(text(r.pkg, 12f, false, gray))
        c.addView(text("${r.level.title}  •  ${r.score}/100", 15f, true, r.level.color))
        r.findings.forEach {
            c.addView(text((if (it.points > 0) "⚠ " else "✔ ") + it.text, 13f))
        }
        r.cert?.let { c.addView(text("Imzo SHA-256: ${it.take(32)}…", 11f, false, gray)) }
        r.sha256?.let { c.addView(text("Fayl SHA-256: $it", 11f, false, gray)) }

        val vtView = text(r.vt?.let { "VirusTotal: $it" } ?: "", 13f, true)
        c.addView(vtView)

        if (installed) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun rowBtn(label: String, action: () -> Unit) {
                val b = btn(label, action)
                b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(b)
            }
            rowBtn("Sozlamalar") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${r.pkg}"))
                )
            }
            rowBtn("O'chirish") {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${r.pkg}")))
            }
            rowBtn("VirusTotal") { vtForInstalled(r, vtView) }
            c.addView(row)
        }
        results.addView(c)
    }

    // ---------- Amallar ----------

    private fun scanInstalled() {
        results.removeAllViews()
        status.text = "Tekshirilmoqda…"
        Thread {
            val list = try {
                Scanner.scanInstalled(this)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (list == null) {
                    status.text = "Ilovalar ro'yxatini olib bo'lmadi."
                    return@runOnUiThread
                }
                val risky = list.filter { it.score >= 25 }
                status.text = "${list.size} ta ilova tekshirildi: ${risky.size} tasi e'tibor talab qiladi."
                val show = if (risky.isEmpty()) list.take(5) else risky.take(40)
                show.forEach { showReport(it, true) }
            }
        }.start()
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, 11)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11 && resultCode == RESULT_OK) {
            data?.data?.let { scanUri(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val uri: Uri? = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> streamOf(i)
            else -> null
        }
        if (uri != null) {
            setIntent(Intent())
            scanUri(uri)
        }
    }

    private fun scanUri(uri: Uri) {
        results.removeAllViews()
        status.text = "APK tekshirilmoqda…"
        Thread {
            val tmp = File(cacheDir, "scan.apk")
            var rep: Report? = null
            var err: String? = null
            try {
                val md = MessageDigest.getInstance("SHA-256")
                var total = 0L
                val ins = contentResolver.openInputStream(uri) ?: throw Exception("fayl ochilmadi")
                ins.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            md.update(buf, 0, n)
                            out.write(buf, 0, n)
                            total += n
                            if (total > 400L * 1024 * 1024) throw Exception("fayl juda katta")
                        }
                    }
                }
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                rep = Scanner.scanArchive(this, tmp, sha)
                val cur = rep
                if (cur == null) {
                    err = "Bu to'g'ri APK emas (XAPK/APKS arxiv yoki buzilgan fayl bo'lishi mumkin)."
                } else {
                    val key = prefs.getString("vt", "") ?: ""
                    if (key.isNotBlank()) rep = withVt(cur, key, sha)
                }
            } catch (e: Exception) {
                err = "Faylni o'qib bo'lmadi: ${e.message}"
            } finally {
                tmp.delete()
            }
            val finalRep = rep
            val finalErr = err
            runOnUiThread {
                if (finalRep == null) {
                    status.text = finalErr ?: "Xatolik"
                } else {
                    status.text = ""
                    showReport(finalRep, false)
                }
            }
        }.start()
    }

    private fun withVt(r: Report, key: String, sha: String): Report {
        val (msg, mal) = VT.lookup(key, sha)
        val extra = when {
            mal >= 3 -> 60
            mal >= 1 -> 30
            else -> 0
        }
        val findings = if (extra > 0) r.findings + Finding(extra, "VirusTotal antiviruslari xavf topdi") else r.findings
        return r.copy(score = minOf(100, r.score + extra), findings = findings, vt = msg)
    }

    private fun vtForInstalled(r: Report, view: TextView) {
        val key = prefs.getString("vt", "") ?: ""
        val path = r.apkPath
        if (key.isBlank()) {
            toast("Avval pastda VirusTotal kalitini kiriting")
            return
        }
        if (path == null) {
            toast("Ilova fayli topilmadi")
            return
        }
        view.text = "VirusTotal tekshirmoqda…"
        Thread {
            val msg = try {
                val sha = Scanner.sha256File(File(path))
                VT.lookup(key, sha).first
            } catch (e: Exception) {
                "Xatolik: ${e.message}"
            }
            runOnUiThread { view.text = "VirusTotal: $msg" }
        }.start()
    }

    private fun checkLink() {
        val s = linkInput.text.toString().trim()
        if (s.isEmpty()) {
            toast("Havolani kiriting")
            return
        }
        results.removeAllViews()
        status.text = ""
        showReport(LinkCheck.analyze(s), false)
    }
}
