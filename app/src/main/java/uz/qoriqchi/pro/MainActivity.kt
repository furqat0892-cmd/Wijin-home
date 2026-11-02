package uz.qoriqchi.pro

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    /** Rang palitrasi: telefon sozlamasiga qarab qorong'i yoki yorug'. */
    private class Pal(val dark: Boolean) {
        val bg = if (dark) 0xFF0E1621.toInt() else 0xFFF1F5F9.toInt()
        val card = if (dark) 0xFF182433.toInt() else 0xFFFFFFFF.toInt()
        val ink = if (dark) 0xFFF1F5F9.toInt() else 0xFF0F172A.toInt()
        val sub = if (dark) 0xFF94A3B8.toInt() else 0xFF64748B.toInt()
        val field = if (dark) 0xFF0E1621.toInt() else 0xFFF1F5F9.toInt()
        val line = if (dark) 0xFF263548.toInt() else 0xFFE2E8F0.toInt()
        val accent = 0xFF2563EB.toInt()
        val good = 0xFF16A34A.toInt()
        val warn = 0xFFF59E0B.toInt()
        val bad = 0xFFDC2626.toInt()
    }

    private lateinit var pal: Pal
    private lateinit var results: LinearLayout
    private lateinit var events: LinearLayout
    private lateinit var status: TextView
    private lateinit var linkInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var guardCard: LinearLayout
    private lateinit var guardTitle: TextView
    private lateinit var guardSub: TextView
    private lateinit var guardSwitch: Switch
    private lateinit var permBox: LinearLayout
    private lateinit var clipBox: LinearLayout
    private lateinit var lockSwitch: Switch
    private var updating = false
    private var started = false
    private var pendingIntent: Intent? = null

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val prefs get() = getSharedPreferences("qp", Context.MODE_PRIVATE)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        pal = Pal(night)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        if (!night) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        pendingIntent = intent
        if (needLock()) {
            setContentView(View(this).apply { setBackgroundColor(pal.bg) })
            askLock()
        } else {
            startApp()
        }
    }

    private fun deviceSecure(): Boolean {
        val km = getSystemService(KeyguardManager::class.java)
        return km != null && km.isDeviceSecure
    }

    private fun needLock(): Boolean = prefs.getBoolean("lock", false) && deviceSecure()

    private fun askLock() {
        val km = getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val i = km?.createConfirmDeviceCredentialIntent(
            "Qo'riqchi Pro",
            "Davom etish uchun ekran qulfini kiriting"
        )
        if (i == null) startApp() else startActivityForResult(i, 31)
    }

    private fun startApp() {
        if (started) return
        started = true
        buildUi()
        handleIntent(pendingIntent)
        pendingIntent = null
        if (prefs.getBoolean("guard", false)) startGuard()
        refreshGuardUi()
        refreshEvents()
        checkClipboard()
    }

    override fun onResume() {
        super.onResume()
        if (started) {
            refreshGuardUi()
            refreshEvents()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && started) checkClipboard()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (started) handleIntent(intent) else pendingIntent = intent
    }

    // ---------- Dizayn yordamchilari ----------

    private fun shape(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun ripple(content: Drawable): Drawable =
        RippleDrawable(ColorStateList.valueOf(0x33808080), content, null)

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun tv(s: String, size: Float, bold: Boolean = false, color: Int = pal.ink) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.12f)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun card(top: Int = 12): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = shape(pal.card, 18, pal.line)
        layoutParams = lp(top)
    }

    private fun button(label: String, fill: Int, textColor: Int = Color.WHITE, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = ripple(shape(fill, 14))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = lp(10)
        }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(pal.sub)
        setTextColor(pal.ink)
        textSize = 15f
        setSingleLine(true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = shape(pal.field, 12, pal.line)
        layoutParams = lp(10)
    }

    private fun tile(icon: String, title: String, sub: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = ripple(shape(pal.card, 18, pal.line))
            isClickable = true
            setOnClickListener { onClick() }
            addView(tv(icon, 26f))
            addView(tv(title, 15f, true))
            addView(tv(sub, 12f, false, pal.sub))
        }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- Asosiy ekran ----------

    private fun buildUi() {
        val root = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(26), dp(16), dp(32))
        }
        root.addView(col)

        // Sarlavha
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "🛡️"
            textSize = 26f
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt())
            ).apply { cornerRadius = dp(18).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(14) }
            addView(tv("Qo'riqchi Pro", 24f, true))
            addView(tv("Telefoningiz xavfsizlik qo'riqchisi", 13f, false, pal.sub))
        }
        head.addView(logo)
        head.addView(titles)
        col.addView(head)

        // Real vaqt himoyasi kartochkasi
        guardCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = lp(18)
        }
        val grow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gtext = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        guardTitle = tv("Real vaqt himoyasi", 17f, true, Color.WHITE)
        guardSub = tv("", 13f, false, 0xFFE2E8F0.toInt())
        gtext.addView(guardTitle)
        gtext.addView(guardSub)
        guardSwitch = Switch(this)
        guardSwitch.setOnCheckedChangeListener { _, on ->
            if (!updating) setGuard(on)
        }
        grow.addView(gtext)
        grow.addView(guardSwitch)
        guardCard.addView(grow)
        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        guardCard.addView(permBox)
        col.addView(guardCard)

        // Amallar
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(12)
        }
        val t1 = tile("📱", "Ilovalarim", "Hammasini tekshir") { scanInstalled() }
        val t2 = tile("📦", "APK fayl", "O'rnatishdan oldin") { pickFile() }
        t1.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) }
        t2.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(6) }
        row.addView(t1)
        row.addView(t2)
        col.addView(row)

        clipBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(clipBox)

        // Havola
        val linkCard = card()
        linkCard.addView(tv("🔗  Havola tekshiruvi", 16f, true))
        linkCard.addView(tv("Havola ochilmaydi, faqat shakli tahlil qilinadi.", 12f, false, pal.sub))
        linkInput = field("Havolani shu yerga qo'ying")
        linkInput.inputType = InputType.TYPE_TEXT_VARIATION_URI
        linkCard.addView(linkInput)
        linkCard.addView(button("Tekshirish", pal.accent) { checkLink() })
        col.addView(linkCard)

        // VirusTotal
        val vtCard = card()
        vtCard.addView(tv("🔑  VirusTotal (ixtiyoriy)", 16f, true))
        vtCard.addView(
            tv("Kalit kiritilsa, 60+ antivirus bazasi bilan solishtiriladi. Faqat fayl xeshi yuboriladi.", 12f, false, pal.sub)
        )
        keyInput = field("API kaliti")
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setText(prefs.getString("vt", ""))
        vtCard.addView(keyInput)
        vtCard.addView(button("Saqlash", pal.accent) {
            prefs.edit().putString("vt", keyInput.text.toString().trim()).apply()
            toast("Saqlandi")
        })
        col.addView(vtCard)

        // Ilovani qulflash
        val lockCard = card()
        val lockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lockText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            addView(tv("🔒  Ilovani qulflash", 16f, true))
            addView(tv("Ochishda telefon ekran qulfi (PIN, barmoq izi) so'raladi.", 12f, false, pal.sub))
        }
        lockSwitch = Switch(this)
        lockSwitch.isChecked = prefs.getBoolean("lock", false)
        lockSwitch.setOnCheckedChangeListener { btn, on ->
            if (on && !deviceSecure()) {
                btn.isChecked = false
                toast("Avval telefonda ekran qulfini (PIN yoki barmoq izi) o'rnating")
            } else {
                prefs.edit().putBoolean("lock", on).apply()
            }
        }
        lockRow.addView(lockText)
        lockRow.addView(lockSwitch)
        lockCard.addView(lockRow)
        col.addView(lockCard)

        // Natijalar
        status = tv("", 14f, true)
        status.layoutParams = lp(16)
        col.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(results)

        // Hodisalar
        val evHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(22)
        }
        val evTitle = tv("So'nggi hodisalar", 17f, true)
        evTitle.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val clear = tv("Tozalash", 13f, true, pal.accent)
        clear.isClickable = true
        clear.setPadding(dp(8), dp(6), dp(8), dp(6))
        clear.setOnClickListener {
            Store.clear(this)
            refreshEvents()
        }
        evHead.addView(evTitle)
        evHead.addView(clear)
        col.addView(evHead)
        events = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(events)

        setContentView(root)
    }

    // ---------- Real vaqt himoyasi ----------

    private fun notifGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun startGuard() {
        try {
            startForegroundService(Intent(this, GuardService::class.java))
        } catch (e: Exception) {
            toast("Himoyani yoqib bo'lmadi: ${e.message}")
        }
    }

    private fun setGuard(on: Boolean) {
        prefs.edit().putBoolean("guard", on).apply()
        if (on) {
            if (!notifGranted()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
            }
            startGuard()
        } else {
            stopService(Intent(this, GuardService::class.java))
        }
        refreshGuardUi()
    }

    private fun refreshGuardUi() {
        val on = prefs.getBoolean("guard", false)
        updating = true
        guardSwitch.isChecked = on
        updating = false
        val colors = if (on) {
            intArrayOf(0xFF059669.toInt(), 0xFF10B981.toInt())
        } else {
            intArrayOf(0xFF475569.toInt(), 0xFF64748B.toInt())
        }
        guardCard.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
            .apply { cornerRadius = dp(20).toFloat() }
        guardTitle.text = if (on) "Himoya yoqilgan" else "Himoya o'chiq"
        guardSub.text = if (on) {
            "Yangi ilova va yuklangan APK fayllar avtomatik tekshiriladi."
        } else {
            "Yoqing: yangi o'rnatilgan ilovalar darrov tekshiriladi."
        }

        permBox.removeAllViews()
        if (on && !notifGranted()) {
            permBox.addView(button("🔔  Ogohlantirishlar uchun ruxsat", Color.WHITE, 0xFF0F172A.toInt()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
            })
        }
        if (on && !hasFilesAccess(this)) {
            permBox.addView(button("📂  Yuklangan APK'larni kuzatish uchun ruxsat", Color.WHITE, 0xFF0F172A.toInt()) {
                if (Build.VERSION.SDK_INT >= 30) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 22)
                }
            })
        }
        if (on) {
            val hint = tv(
                "Xiaomi/Redmi: Sozlamalar → Ilovalar → Qo'riqchi Pro → Avtoishga tushirish va " +
                    "Batareya: \"Cheklovsiz\" ni yoqing, aks holda tizim himoyani o'chirib qo'yishi mumkin.",
                11f, false, 0xFFE2E8F0.toInt()
            )
            hint.layoutParams = lp(10)
            permBox.addView(hint)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshGuardUi()
    }

    private fun refreshEvents() {
        events.removeAllViews()
        val list = Store.list(this)
        if (list.isEmpty()) {
            events.addView(
                tv(
                    "Hozircha hodisa yo'q. Himoyani yoqing: yangi ilova yoki APK paydo bo'lganda shu yerda ko'rinadi.",
                    13f, false, pal.sub
                )
            )
            return
        }
        val fmt = SimpleDateFormat("dd.MM  HH:mm", Locale.getDefault())
        for (e in list) {
            val c = card(8)
            val dot = when {
                e.score >= 50 -> pal.bad
                e.score >= 25 -> pal.warn
                else -> pal.good
            }
            c.addView(tv(e.title, 14f, true, dot))
            if (e.detail.isNotBlank()) c.addView(tv(e.detail, 12f, false, pal.ink))
            c.addView(tv(fmt.format(Date(e.time)), 11f, false, pal.sub))
            events.addView(c)
        }
    }

    // ---------- Hisobot kartochkasi ----------

    private fun smallBtn(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pal.ink)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), dp(10))
            background = ripple(shape(pal.field, 10, pal.line))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            }
        }

    private fun shareText(r: Report): String {
        val sb = StringBuilder()
        sb.append("Qo'riqchi Pro hisoboti\n")
        sb.append("${r.label} (${r.pkg})\n")
        sb.append("${r.level.title}: ${r.score}/100\n")
        r.findings.forEach { sb.append(if (it.points > 0) "⚠ " else "✔ ").append(it.text).append('\n') }
        r.sha256?.let { sb.append("SHA-256: ").append(it).append('\n') }
        r.vt?.let { sb.append("VirusTotal: ").append(it).append('\n') }
        return sb.toString()
    }

    private fun share(r: Report) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText(r))
        }
        startActivity(Intent.createChooser(i, "Hisobotni ulashish"))
    }

    private fun deepForInstalled(r: Report, wrap: View) {
        val path = r.apkPath
        if (path == null) {
            toast("Ilova fayli topilmadi")
            return
        }
        toast("Chuqur tahlil boshlandi…")
        Thread {
            val deep = try {
                DeepScan.apply(r, path)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (deep == null) {
                    toast("Chuqur tahlil bajarilmadi")
                    return@runOnUiThread
                }
                val idx = results.indexOfChild(wrap)
                if (idx >= 0) {
                    results.removeViewAt(idx)
                    showReport(deep, true, idx)
                }
            }
        }.start()
    }

    private fun showReport(r: Report, installed: Boolean, index: Int = -1): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(pal.card, 18, pal.line)
            clipToOutline = true
            layoutParams = lp(12)
        }
        val bar = View(this).apply {
            setBackgroundColor(r.level.color)
            layoutParams = LinearLayout.LayoutParams(dp(6), MATCH)
        }
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        wrap.addView(bar)
        wrap.addView(c)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = tv(r.label, 16f, true)
        name.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val chip = TextView(this).apply {
            text = "${r.score}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = shape(r.level.color, 12)
        }
        top.addView(name)
        top.addView(chip)
        c.addView(top)
        c.addView(tv(r.pkg, 12f, false, pal.sub))
        c.addView(tv("${r.level.title}  •  ${r.score}/100", 14f, true, r.level.color))
        r.findings.forEach {
            c.addView(tv((if (it.points > 0) "⚠  " else "✔  ") + it.text, 13f))
        }
        r.cert?.let { c.addView(tv("Imzo SHA-256: ${it.take(32)}…", 11f, false, pal.sub)) }
        r.sha256?.let { c.addView(tv("Fayl SHA-256: $it", 11f, false, pal.sub)) }

        val vtView = tv(r.vt?.let { "VirusTotal: $it" } ?: "", 13f, true)
        c.addView(vtView)

        if (installed) {
            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            row1.addView(smallBtn("Sozlamalar") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${r.pkg}"))
                )
            })
            row1.addView(smallBtn("O'chirish") {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${r.pkg}")))
            })
            row1.addView(smallBtn("VirusTotal") { vtForInstalled(r, vtView) })
            c.addView(row1)

            val row2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(6)
            }
            row2.addView(smallBtn("Chuqur tahlil") { deepForInstalled(r, wrap) })
            row2.addView(smallBtn("Ishonaman") {
                Trust.add(this, r.pkg)
                results.removeView(wrap)
                toast("Ishonchli ro'yxatga qo'shildi")
            })
            row2.addView(smallBtn("Ulashish") { share(r) })
            c.addView(row2)
        } else {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            row.addView(smallBtn("Ulashish") { share(r) })
            c.addView(row)
        }
        results.addView(wrap, index)
        return wrap
    }

    // ---------- Amallar ----------

    private fun scanInstalled() {
        results.removeAllViews()
        status.text = "Tekshirilmoqda…"
        status.setTextColor(pal.ink)
        Thread {
            val list = try {
                val trust = Trust.all(this)
                Scanner.scanInstalled(this).filter { it.pkg !in trust }
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
        if (requestCode == 31) {
            if (resultCode == RESULT_OK) startApp() else finish()
            return
        }
        if (requestCode == 11 && resultCode == RESULT_OK) {
            data?.data?.let { scanUri(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    private fun urlIn(t: String): String? =
        Regex("(https?://\\S+|www\\.\\S+)", RegexOption.IGNORE_CASE).find(t)?.value

    private fun checkClipboard() {
        try {
            val cm = getSystemService(ClipboardManager::class.java) ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val t = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            val url = urlIn(t) ?: return
            if (url == prefs.getString("lastClip", "")) return
            prefs.edit().putString("lastClip", url).apply()
            val r = LinkCheck.analyze(url)
            clipBox.removeAllViews()
            val c = card()
            c.addView(tv("📋  Nusxalangan havola tekshirildi", 14f, true))
            c.addView(tv(url.take(80), 12f, false, pal.sub))
            c.addView(tv("${r.level.title}  •  ${r.score}/100", 14f, true, r.level.color))
            r.findings.filter { it.points > 0 }.take(3).forEach { c.addView(tv("⚠  " + it.text, 12f)) }
            clipBox.addView(c)
        } catch (e: Exception) {
            // bufer o'qilmasa, e'tibor bermaymiz
        }
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT)
            val u = if (t == null) null else (urlIn(t) ?: t.trim())
            if (u != null && u.isNotEmpty()) {
                setIntent(Intent())
                linkInput.setText(u)
                results.removeAllViews()
                status.text = ""
                showReport(LinkCheck.analyze(u), false)
            }
            return
        }
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
        status.setTextColor(pal.ink)
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
                    val deep = DeepScan.apply(cur, tmp.absolutePath)
                    rep = deep
                    val key = prefs.getString("vt", "") ?: ""
                    if (key.isNotBlank()) rep = applyVt(deep, key, sha)
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

    private fun vtForInstalled(r: Report, view: TextView) {
        val key = prefs.getString("vt", "") ?: ""
        val path = r.apkPath
        if (key.isBlank()) {
            toast("Avval VirusTotal kalitini kiriting")
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
