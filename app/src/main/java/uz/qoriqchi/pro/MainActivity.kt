package uz.qoriqchi.pro

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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

    /** Xavfsizlik halqasi: foiz, ravon to'lish va tekshiruv paytidagi aylanuvchi yoy. */
    private class ScoreRing(
        ctx: Context,
        private val color: Int,
        private val track: Int,
        private val labelColor: Int
    ) : View(ctx) {
        var ratio = 0f
        var customLabel: String? = null
        private var angle = 0f
        private var spinning = false
        private var spinStart = 0L
        private var filling = false
        private var fillStart = 0L
        private var fillTarget = 0f
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // Vaqtga asoslangan chizish: tizimdagi "animatsiya o'chirilgan" / quvvat tejash
        // sozlamasi ta'sir qilmaydi.
        fun startSpin() {
            if (spinning) return
            spinning = true
            spinStart = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        }

        fun stopSpin() {
            spinning = false
            invalidate()
        }

        /** Halqani 0 dan berilgan qiymatgacha ravon to'ldiradi. */
        fun fillTo(target: Float) {
            fillTarget = target
            fillStart = SystemClock.uptimeMillis()
            filling = true
            ratio = 0f
            postInvalidateOnAnimation()
        }

        override fun onDetachedFromWindow() {
            spinning = false
            filling = false
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            if (spinning) angle = ((now - spinStart) % 1100L) / 1100f * 360f
            if (filling) {
                val t = ((now - fillStart) / 900f).coerceIn(0f, 1f)
                ratio = fillTarget * (1f - (1f - t) * (1f - t))
                if (t >= 1f) filling = false
            }
            val w = width.toFloat()
            val h = height.toFloat()
            val side = minOf(w, h)
            val stroke = side * 0.085f
            arc.strokeWidth = stroke
            val left = (w - side) / 2f + stroke
            val top = (h - side) / 2f + stroke
            val box = RectF(left, top, left + side - 2 * stroke, top + side - 2 * stroke)
            arc.color = track
            canvas.drawArc(box, 0f, 360f, false, arc)
            arc.color = color
            val sweep = 360f * ratio.coerceIn(0f, 1f)
            if (sweep > 0f) canvas.drawArc(box, -90f, sweep, false, arc)
            if (spinning) {
                arc.color = color
                canvas.drawArc(box, -90f + angle, 100f, false, arc)
            }
            txt.color = labelColor
            txt.textSize = side * 0.25f
            val label = customLabel ?: "${(ratio.coerceIn(0f, 1f) * 100).toInt()}%"
            canvas.drawText(label, w / 2f, h / 2f + txt.textSize * 0.35f, txt)
            if (spinning || filling) postInvalidateOnAnimation()
        }
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
    private lateinit var summaryBox: LinearLayout
    private var scanRing: ScoreRing? = null
    private var scanSub: TextView? = null
    private var scanBar: ProgressBar? = null
    private var scanRadar: RadarView? = null
    private lateinit var shieldStatus: TextView
    private lateinit var shieldBtn: TextView
    private lateinit var baseInfo: TextView
    private lateinit var famToken: EditText
    private lateinit var famChat: EditText
    private lateinit var famName: EditText
    private var lastText = ""
    private lateinit var callStatus: TextView
    private lateinit var callBtn: TextView
    private lateinit var callInput: EditText
    private lateinit var netStatus: TextView
    private lateinit var netBtn: TextView
    private var scrollRoot: ScrollView? = null
    private lateinit var msgInput: EditText
    private lateinit var msgCard: View
    private var audit: Map<String, List<Pair<String, String>>>? = null
    private var updating = false
    private var started = false
    private var pendingIntent: Intent? = null

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val prefs get() = getSharedPreferences("qp", Context.MODE_PRIVATE)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showCrash(err: String) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "Oldingi ishga tushirishda xato bo'lgan. Shu matnni yuboring:"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(this).apply {
            text = err
            textSize = 10f
            setTextIsSelectable(true)
        })
        col.addView(Button(this).apply {
            text = "Ulashish"
            setOnClickListener {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, err)
                }
                startActivity(Intent.createChooser(i, "Xato matnini ulashish"))
            }
        })
        col.addView(Button(this).apply {
            text = "Tozalash va qayta ochish"
            setOnClickListener {
                CrashLog.clear(this@MainActivity)
                recreate()
            }
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        val lastErr = CrashLog.read(this)
        if (lastErr != null) {
            showCrash(lastErr)
            return
        }
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
        if (prefs.getBoolean("guard", false)) DailyScanService.schedule(this)
        refreshSummary()
        refreshGuardUi()
        refreshEvents()
        checkClipboard()
    }

    override fun onResume() {
        super.onResume()
        if (started) {
            refreshSummary()
            refreshGuardUi()
            refreshEvents()
            refreshShieldUi()
            refreshCallUi()
            refreshNetUi()
            Blocklist.maybeRefresh(applicationContext)
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

    /** a va b ranglarni t (0..1) nisbatda aralashtiradi. */
    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = (Color.red(a) * (1 - k) + Color.red(b) * k).toInt()
        val g = (Color.green(a) * (1 - k) + Color.green(b) * k).toInt()
        val bl = (Color.blue(a) * (1 - k) + Color.blue(b) * k).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun gradBg(c1: Int, c2: Int, radius: Int, stroke: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    /** Rangli ikonka nishoni, sarlavha va izoh. */
    private fun cardHeader(icon: String, title: String, sub: String, color: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val badge = TextView(this@MainActivity).apply {
                text = icon
                textSize = 18f
                gravity = Gravity.CENTER
                background = shape(mix(pal.card, color, 0.22f), 12)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            }
            val texts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(12) }
                addView(tv(title, 16f, true))
                addView(tv(sub, 12f, false, pal.sub))
            }
            addView(badge)
            addView(texts)
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

    private fun tile(icon: String, title: String, sub: String, color: Int, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = ripple(gradBg(mix(pal.card, color, 0.26f), pal.card, 18, mix(pal.line, color, 0.55f)))
            isClickable = true
            setOnClickListener { onClick() }
            val badge = TextView(this@MainActivity).apply {
                text = icon
                textSize = 22f
                gravity = Gravity.CENTER
                background = shape(color, 14)
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { bottomMargin = dp(8) }
            }
            addView(badge)
            addView(tv(title, 15f, true))
            addView(tv(sub, 12f, false, pal.sub))
        }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun tileRow(a: LinearLayout, b: LinearLayout): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(12)
        }
        a.layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f).apply { rightMargin = dp(6) }
        b.layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f).apply { leftMargin = dp(6) }
        row.addView(a)
        row.addView(b)
        return row
    }

    /** Natijalar ko'rinadigan joyga sahifani suradi. */
    private fun scrollToResults() {
        val sv = scrollRoot ?: return
        sv.post { sv.smoothScrollTo(0, (status.top - dp(8)).coerceAtLeast(0)) }
    }

    private fun focusMessage() {
        val sv = scrollRoot ?: return
        sv.post { sv.smoothScrollTo(0, (msgCard.top - dp(8)).coerceAtLeast(0)) }
        msgInput.requestFocus()
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            toast("Sozlamani ochib bo'lmadi")
        }
    }

    private fun stateColor(state: Int): Int = when (state) {
        0 -> pal.good
        1 -> pal.warn
        2 -> pal.bad
        else -> pal.sub
    }

    private fun stateIcon(state: Int): String = when (state) {
        0 -> "✅"
        1 -> "⚠️"
        2 -> "⛔"
        else -> "❔"
    }

    // ---------- Telefon sog'lig'i ----------

    private fun showHealth() {
        results.removeAllViews()
        status.text = "Telefon sog'lig'i tekshirilmoqda…"
        status.setTextColor(pal.ink)
        scrollToResults()
        Thread {
            val h = try {
                PhoneHealth.run(this)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (h == null) {
                    status.text = "Tekshirib bo'lmadi."
                    return@runOnUiThread
                }
                status.text = ""
                renderHealth(h)
            }
        }.start()
    }

    private fun renderHealth(h: HealthResult) {
        results.removeAllViews()
        val tone = when {
            h.score >= 80 -> pal.good
            h.score >= 55 -> pal.warn
            else -> pal.bad
        }
        val head = card(0)
        head.background = gradBg(mix(pal.card, tone, 0.28f), pal.card, 22, mix(pal.line, tone, 0.6f))
        val ring = ScoreRing(this, tone, pal.line, pal.ink)
        ring.customLabel = "${h.score}"
        ring.layoutParams = LinearLayout.LayoutParams(dp(130), dp(130)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        head.addView(ring)
        ring.fillTo(h.score / 100f)
        val title = when {
            h.score >= 80 -> "Telefoningiz yaxshi himoyalangan"
            h.score >= 55 -> "Bir necha joyni tuzatish kerak"
            else -> "Telefon himoyasi zaif"
        }
        head.addView(tv(title, 17f, true, tone).apply { gravity = Gravity.CENTER })
        head.addView(tv("Telefon sozlamalari bo'yicha ball: ${h.score} / 100", 12f, false, pal.sub).apply { gravity = Gravity.CENTER })
        results.addView(head)

        // Avval muammolar, keyin yaxshilari
        val sorted = h.checks.sortedWith(compareBy<HealthCheck> { if (it.state == 0) 1 else 0 })
        for (c in sorted) {
            val col = stateColor(c.state)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = shape(pal.card, 16, mix(pal.line, col, 0.5f))
                layoutParams = lp(10)
            }
            box.addView(tv(stateIcon(c.state) + "  " + c.title, 15f, true, if (c.state == 0) pal.ink else col))
            box.addView(tv(c.detail, 13f, false, pal.sub))
            val fix = c.fix
            if (fix != null && (c.state == 1 || c.state == 2)) {
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = lp(8)
                }
                r.addView(smallBtn("Sozlamani ochish") { openSettings(fix) })
                box.addView(r)
            }
            results.addView(box)
        }
        results.addView(tv("Bu tekshiruv telefon sozlamalariga qaraydi. Zararli ilova bo'lmasligiga kafolat emas.", 11f, false, pal.sub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }

    // ---------- Ilova kelib chiqishi ----------

    private fun showOrigin() {
        results.removeAllViews()
        status.text = "Ilovalar manbasi aniqlanmoqda…"
        status.setTextColor(pal.ink)
        scrollToResults()
        Thread {
            val list = try {
                Origin.list(this)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (list == null) {
                    status.text = "Ro'yxatni olib bo'lmadi."
                    return@runOnUiThread
                }
                status.text = ""
                renderOrigin(list)
            }
        }.start()
    }

    private fun renderOrigin(list: List<AppOrigin>) {
        results.removeAllViews()
        val store = list.count { it.kind == 0 }
        val manual = list.count { it.kind == 1 }
        val unknown = list.count { it.kind == 2 }
        val head = card(0)
        head.addView(cardHeader("🧭", "Ilovalar qayerdan kelgan", "Do'kondan tashqari o'rnatilgan ilovalar xavfliroq: ularni kim o'rnatganini bilib qo'ying.", 0xFF0EA5E9.toInt()))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(12)
        }
        row.addView(counterTile(store, "Do'kondan", pal.good))
        row.addView(counterTile(manual, "APK orqali", pal.warn))
        row.addView(counterTile(unknown, "Noma'lum", pal.bad))
        head.addView(row)
        results.addView(head)

        val shown = list.filter { it.kind != 0 }
        if (shown.isEmpty()) {
            results.addView(card().apply {
                addView(tv("Hamma ilova rasmiy do'kondan o'rnatilgan. Yaxshi holat.", 14f, true, pal.good))
            })
            return
        }
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val week = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        for (a in shown.take(60)) {
            val col = if (a.kind == 1) pal.warn else pal.bad
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = shape(pal.card, 16, mix(pal.line, col, 0.5f))
                layoutParams = lp(10)
            }
            val fresh = if (a.installedAt >= week) "  🆕" else ""
            box.addView(tv(a.label + fresh, 15f, true))
            box.addView(tv(a.via, 13f, true, col))
            box.addView(tv("O'rnatilgan: ${fmt.format(Date(a.installedAt))}  •  ${a.pkg}", 11f, false, pal.sub))
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            r.addView(smallBtn("Tekshirish") { checkOriginApp(a, box) })
            r.addView(smallBtn("Karantin") {
                Quarantine.add(this, a.pkg)
                toast("Karantinga qo'shildi")
            })
            r.addView(smallBtn("O'chirish") {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
            })
            box.addView(r)
            results.addView(box)
        }
    }

    private fun checkOriginApp(a: AppOrigin, box: View) {
        toast("Tekshirilmoqda…")
        Thread {
            val rep = try {
                Scanner.analyzePackage(this, a.pkg)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (rep == null) {
                    toast("Ilovani tekshirib bo'lmadi")
                    return@runOnUiThread
                }
                val idx = results.indexOfChild(box)
                if (idx >= 0) {
                    results.removeViewAt(idx)
                    showReport(rep, true, idx)
                }
            }
        }.start()
    }

    // ---------- Karantin ----------

    private fun showQuarantine() {
        results.removeAllViews()
        status.text = ""
        renderQuarantine()
        scrollToResults()
    }

    private fun renderQuarantine() {
        results.removeAllViews()
        val list = Quarantine.active(this)
        val head = card(0)
        head.addView(cardHeader("🧪", "Karantin", "Shubhali ilovalar shu yerda yig'ib qo'yiladi: ruxsatlarini olib, keyin o'chirasiz.", 0xFFE11D48.toInt()))
        head.addView(
            tv(
                "Eslatma: Android boshqa ilovani o'chirmasdan bloklashga ruxsat bermaydi. Karantin ro'yxat tutadi va ilovaning xavfli ruxsatlarini ko'rsatadi, ularni olib tashlash yoki ilovani o'chirish o'zingizning tasdig'ingiz bilan bo'ladi.",
                12f, false, pal.sub
            )
        )
        results.addView(head)
        if (list.isEmpty()) {
            results.addView(card().apply {
                addView(tv("Karantin bo'sh.", 15f, true))
                addView(tv("Ilova hisobotidagi «Karantin» tugmasi yoki «Kelib chiqishi» ro'yxati orqali ilova qo'shiladi.", 12f, false, pal.sub))
            })
            return
        }
        val pm = packageManager
        for (pkg in list) {
            val label = try {
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            val granted = Quarantine.grantedRisky(this, pkg)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = shape(pal.card, 16, mix(pal.line, 0xFFE11D48.toInt(), 0.5f))
                layoutParams = lp(10)
            }
            box.addView(tv("🧪  $label", 15f, true))
            box.addView(tv(pkg, 11f, false, pal.sub))
            if (granted.isEmpty()) {
                box.addView(tv("Hozir berilgan xavfli ruxsat yo'q.", 13f, false, pal.good))
            } else {
                box.addView(tv("Berilgan ruxsatlar: " + granted.joinToString(", "), 13f, true, pal.bad))
            }
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            r.addView(smallBtn("Ruxsatlarni olish") {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                )
            })
            r.addView(smallBtn("O'chirish") {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
            })
            r.addView(smallBtn("Chiqarish") {
                Quarantine.remove(this, pkg)
                renderQuarantine()
            })
            box.addView(r)
            results.addView(box)
        }
    }

    // ---------- Xabar qalqoni, oila, baza ----------

    private fun shieldEnabled(): Boolean {
        val s = try {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        } catch (e: Exception) {
            null
        }
        return s != null && s.contains(packageName)
    }

    private fun refreshShieldUi() {
        val on = shieldEnabled()
        shieldStatus.text = if (on) {
            "✅ Faol: kelgan SMS va xabarlar firibgarlikka tekshiriladi."
        } else {
            "Hozir o'chiq. Yoqish uchun bildirishnomalarni o'qish ruxsatini bering."
        }
        shieldStatus.setTextColor(if (on) pal.good else pal.warn)
        shieldBtn.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun callShieldOn(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
        } catch (e: Exception) {
            false
        }
    }

    private fun refreshCallUi() {
        val on = callShieldOn()
        callStatus.text = if (on) {
            "✅ Faol: noma'lum yoki xavfli raqamdan qo'ng'iroq kelsa, ogohlantiriladi."
        } else {
            "Hozir o'chiq. Yoqish uchun Qo'riqchi Pro ni qo'ng'iroq filtri qilib tanlang."
        }
        callStatus.setTextColor(if (on) pal.good else pal.warn)
        callBtn.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun requestCallRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING), 21)
                    return
                }
            } catch (e: Exception) {
                // pastdagi xabar ko'rsatiladi
            }
        }
        toast("Bu telefonda to'g'ridan-to'g'ri yoqib bo'lmadi. Quyidagi yo'riqnomadan foydalaning")
    }

    private fun addBlackNumber() {
        val n = callInput.text.toString().trim()
        if (Blocklist.addLocalPhone(this, n)) {
            callInput.setText("")
            baseInfo.text = Blocklist.info(this)
            toast("Qora ro'yxatga qo'shildi")
        } else {
            toast("Raqam noto'g'ri")
        }
    }

    private fun refreshNetUi() {
        if (DnsShield.running) {
            netStatus.text = "✅ Faol: ${DnsShield.total} ta so'rov ko'rildi, ${DnsShield.blockedCount} tasi to'sildi."
            netStatus.setTextColor(pal.good)
            netBtn.text = "Nazoratni o'chirish"
        } else {
            netStatus.text = "Hozir o'chiq."
            netStatus.setTextColor(pal.warn)
            netBtn.text = "Nazoratni yoqish"
        }
    }

    private fun toggleNet() {
        if (DnsShield.running) {
            startService(Intent(this, DnsShield::class.java).setAction(DnsShield.ACTION_STOP))
            netStatus.postDelayed({ refreshNetUi() }, 800)
            return
        }
        val i = VpnService.prepare(this)
        if (i != null) {
            startActivityForResult(i, 41)
        } else {
            startNet()
        }
    }

    private fun startNet() {
        startService(Intent(this, DnsShield::class.java))
        netStatus.postDelayed({ refreshNetUi() }, 800)
    }

    private fun showNet() {
        results.removeAllViews()
        status.text = ""
        refreshNetUi()
        val list = DnsShield.recent()
        val head = card(0)
        head.addView(cardHeader("🛰️", "So'nggi ulanishlar", "Qaysi ilova qaysi manzilni so'ragani. Ilova nomi ba'zi telefonlarda aniqlanmasligi mumkin.", 0xFF0EA5E9.toInt()))
        head.addView(
            tv(
                if (DnsShield.running) "Nazorat faol: ${DnsShield.total} ta so'rov, ${DnsShield.blockedCount} tasi to'sildi."
                else "Nazorat o'chiq. Avval «Nazoratni yoqish» ni bosing.",
                13f, true, if (DnsShield.running) pal.good else pal.warn
            ).apply { layoutParams = lp(8) }
        )
        results.addView(head)
        if (list.isEmpty()) {
            results.addView(card().apply {
                addView(tv("Hali ma'lumot yo'q. Nazorat yoqilgach, bir oz internetdan foydalaning.", 13f, false, pal.sub))
            })
            scrollToResults()
            return
        }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val box = card()
        var lastKey = ""
        var shown = 0
        for (e in list) {
            val key = e.app + "|" + e.domain
            if (key == lastKey) continue
            lastKey = key
            if (shown >= 80) break
            shown++
            val mark = if (e.blocked) "⛔ " else if (e.flagged) "⚠️ " else ""
            val color = if (e.blocked) pal.bad else if (e.flagged) pal.warn else pal.ink
            box.addView(tv("$mark${fmt.format(Date(e.time))}  ${e.app} → ${e.domain}", 12f, e.blocked || e.flagged, color))
        }
        results.addView(box)
        scrollToResults()
    }

    private fun findFamChat() {
        val t = famToken.text.toString().trim()
        if (!Family.validToken(t)) {
            toast("Token shakli noto'g'ri")
            return
        }
        toast("Qidirilmoqda…")
        Thread {
            val id = Family.findChatId(t)
            runOnUiThread {
                if (id == null) {
                    toast("Topilmadi. Avval botingizga kirib /start yozing")
                } else {
                    famChat.setText(id)
                    toast("Chat ID topildi")
                }
            }
        }.start()
    }

    private fun saveFamily() {
        val t = famToken.text.toString().trim()
        val c = famChat.text.toString().trim()
        if (!Family.validToken(t) || c.isEmpty()) {
            toast("Token va Chat ID ni to'g'ri kiriting")
            return
        }
        Family.save(this, t, c, famName.text.toString())
        toast("Saqlandi, sinov xabari yuborilmoqda…")
        Thread {
            val err = Family.sendTest(this)
            runOnUiThread { toast(err ?: "Sinov xabari yuborildi. Telegram'ni tekshiring") }
        }.start()
    }

    private fun refreshBase() {
        toast("Yangilanmoqda…")
        Thread {
            val msg = Blocklist.refresh(this)
            runOnUiThread {
                baseInfo.text = Blocklist.info(this)
                toast(msg)
            }
        }.start()
    }

    /** Hisobotni egasiga (Telegram yoki boshqa joyga) yuborish uchun matn tayyorlaydi. */
    private fun reportThreat(r: Report) {
        val kind = when {
            r.pkg == "matn" -> "Xabar matni"
            r.sha256 != null -> "APK fayl"
            else -> "Havola"
        }
        val what = when {
            r.pkg == "matn" -> lastText.take(600)
            r.sha256 != null -> "${r.label}\nSHA-256: ${r.sha256}"
            else -> r.label
        }
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val body = "Qo'riqchi Pro: xavf haqida xabar\nTur: $kind\n$what\nBall: ${r.score}/100\nSana: $date"
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(i, "Kimga yuborish"))
    }

    private fun linkReport(u: String): Report {
        val r = LinkCheck.analyze(u)
        val hit = Blocklist.domainHit(this, u) ?: return r
        return r.copy(
            score = maxOf(r.score, 90),
            findings = listOf(Finding(60, "Umumiy xavf bazasida bor: $hit")) + r.findings
        )
    }

    // ---------- Xabar matni ----------

    private fun checkMessage() {
        val t = msgInput.text.toString().trim()
        if (t.isEmpty()) {
            toast("Xabar matnini kiriting")
            return
        }
        results.removeAllViews()
        status.text = ""
        lastText = t
        showReport(ScamText.analyze(t, this, null), false)
        scrollToResults()
    }

    // ---------- Asosiy ekran ----------

    private fun buildUi() {
        val root = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isFillViewport = true
        }
        scrollRoot = root
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

        summaryBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(summaryBox)

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

        // Xabar qalqoni
        val shield = card()
        shield.addView(cardHeader("🛡️", "Xabar qalqoni", "SMS yoki Telegram'dan firibgar xabar kelsa, darrov ogohlantiradi. Matn telefondan chiqmaydi va saqlanmaydi.", 0xFF16A34A.toInt()))
        shieldStatus = tv("", 13f, true)
        shieldStatus.layoutParams = lp(8)
        shield.addView(shieldStatus)
        shieldBtn = button("Ruxsat berish", 0xFF16A34A.toInt()) {
            openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        shield.addView(shieldBtn)
        shield.addView(
            tv(
                "Ruxsat tugmasi kulrang bo'lib, bosilmasa: Sozlamalar → Ilovalar → Qo'riqchi Pro → yuqori o'ngdagi ⋮ → «Cheklangan sozlamalarga ruxsat berish». Keyin qaytib, ruxsat bering.",
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        val shRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(8)
        }
        shRow.addView(smallBtn("Ilova sozlamalari") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        shield.addView(shRow)
        col.addView(shield)

        // Amallar
        col.addView(
            tileRow(
                tile("📱", "Ilovalarim", "Hammasini tekshir", 0xFF2563EB.toInt()) { scanInstalled() },
                tile("📦", "APK fayl", "O'rnatishdan oldin", 0xFF7C3AED.toInt()) { pickFile() }
            )
        )
        col.addView(
            tileRow(
                tile("🩺", "Telefon sog'lig'i", "Qulf, yangilanish, xavfli ruxsatlar", 0xFF16A34A.toInt()) { showHealth() },
                tile("💬", "Xabar tekshiruvi", "SMS yoki Telegram matni", 0xFFEA580C.toInt()) { focusMessage() }
            )
        )
        col.addView(
            tileRow(
                tile("🧭", "Kelib chiqishi", "Ilovani kim o'rnatgan", 0xFF0EA5E9.toInt()) { showOrigin() },
                tile("🧪", "Karantin", "Shubhali ilovalar ro'yxati", 0xFFE11D48.toInt()) { showQuarantine() }
            )
        )

        clipBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(clipBox)

        // Ruxsatlar auditi
        val auditCard = card()
        auditCard.addView(cardHeader("🔍", "Ruxsatlar auditi", "Qaysi ilova kamera, mikrofon, joylashuv va boshqalarga kira olishini ko'ring.", 0xFFF59E0B.toInt()))
        for (chunk in Scanner.auditGroups.keys.toList().chunked(3)) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            for (n in chunk) r.addView(smallBtn(n) { showAudit(n) })
            auditCard.addView(r)
        }
        col.addView(auditCard)

        // Havola
        val linkCard = card()
        linkCard.addView(cardHeader("🔗", "Havola tekshiruvi", "Havola ochilmaydi, faqat shakli tahlil qilinadi.", 0xFF0891B2.toInt()))
        linkInput = field("Havolani shu yerga qo'ying")
        linkInput.inputType = InputType.TYPE_TEXT_VARIATION_URI
        linkCard.addView(linkInput)
        linkCard.addView(button("Tekshirish", 0xFF0891B2.toInt()) { checkLink() })
        col.addView(linkCard)

        // Xabar matni
        val msgBox = card()
        msgCard = msgBox
        msgBox.addView(cardHeader("💬", "Firibgar xabar tekshiruvi", "Shubhali SMS yoki Telegram xabarini qo'ying: firibgarlik belgilari topiladi. Matn telefondan chiqmaydi.", 0xFFEA580C.toInt()))
        msgInput = field("Xabar matnini shu yerga qo'ying")
        msgInput.setSingleLine(false)
        msgInput.minLines = 3
        msgInput.maxLines = 8
        msgInput.gravity = Gravity.TOP or Gravity.START
        msgInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        msgBox.addView(msgInput)
        msgBox.addView(button("Xabarni tekshirish", 0xFFEA580C.toInt()) { checkMessage() })
        col.addView(msgBox)

        // Oila himoyasi
        val fam = card()
        fam.addView(cardHeader("👪", "Oila himoyasi", "Yaqiningiz telefonida xavf topilsa, sizning Telegram'ingizga xabar boradi.", 0xFF8B5CF6.toInt()))
        val famBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        famBody.addView(
            tv(
                "1) Telegram'da @BotFather ga /newbot yozing, bot yarating va tokenni oling.\n2) O'zingiz yaratgan botga kirib /start bosing.\n3) Tokenni shu yerga qo'ying va «Chat ID ni topish» ni bosing.\n4) Yaqiningiz telefonida ham xuddi shu ma'lumotni kiriting va «Saqlash va sinash» ni bosing.\nXabarda faqat jo'natuvchi nomi va xavf sababi bo'ladi, xabar matni yuborilmaydi.",
                12f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        famToken = field("Bot tokeni")
        famToken.setText(Family.token(this))
        famChat = field("Chat ID")
        famChat.inputType = InputType.TYPE_CLASS_TEXT
        famChat.setText(Family.chat(this))
        famName = field("Telefon nomi (masalan: Dadam telefoni)")
        famName.setText(Family.name(this))
        famBody.addView(famToken)
        famBody.addView(famChat)
        famBody.addView(famName)
        famBody.addView(button("Chat ID ni topish", 0xFF8B5CF6.toInt()) { findFamChat() })
        famBody.addView(button("Saqlash va sinash", 0xFF7C3AED.toInt()) { saveFamily() })
        famBody.addView(button("O'chirish", pal.sub) {
            Family.disable(this)
            toast("Oila himoyasi o'chirildi")
        })
        fam.addView(button(if (Family.isOn(this)) "Sozlash (yoqilgan)" else "Sozlash", 0xFF8B5CF6.toInt()) {
            famBody.visibility = if (famBody.visibility == View.GONE) View.VISIBLE else View.GONE
        })
        fam.addView(famBody)
        col.addView(fam)

        // Qo'ng'iroq qalqoni
        val call = card()
        call.addView(cardHeader("📞", "Qo'ng'iroq qalqoni", "Xavf bazasidagi yoki o'zingiz qo'shgan raqamdan qo'ng'iroq kelsa, ogohlantiradi yoki rad etadi.", 0xFFF43F5E.toInt()))
        callStatus = tv("", 13f, true)
        callStatus.layoutParams = lp(8)
        call.addView(callStatus)
        callBtn = button("Qalqonni yoqish", 0xFFF43F5E.toInt()) { requestCallRole() }
        call.addView(callBtn)
        val rej = Switch(this)
        rej.text = "Bazadagi raqamni avtomatik rad etish"
        rej.setTextColor(pal.ink)
        rej.isChecked = prefs.getBoolean("call_reject", false)
        rej.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("call_reject", on).apply() }
        rej.layoutParams = lp(10)
        call.addView(rej)
        callInput = field("Qora ro'yxatga raqam (+998...)")
        callInput.inputType = InputType.TYPE_CLASS_PHONE
        call.addView(callInput)
        call.addView(button("Raqamni qora ro'yxatga qo'shish", 0xFFF43F5E.toInt()) { addBlackNumber() })
        call.addView(
            tv(
                "Yoqilmasa: Sozlamalar → Ilovalar → Standart ilovalar → «Qo'ng'iroqlarni aniqlash va spamdan himoya» ichidan Qo'riqchi Pro ni tanlang.",
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        col.addView(call)

        // Tarmoq nazorati
        val net = card()
        net.addView(cardHeader("🛰️", "Tarmoq nazorati", "Ilovalar qaysi manzillarga ulanayotganini ko'radi va xavfli manzillarni to'sadi. Faqat manzil nomlari ko'riladi, ma'lumotingiz o'qilmaydi.", 0xFF0EA5E9.toInt()))
        netStatus = tv("", 13f, true)
        netStatus.layoutParams = lp(8)
        net.addView(netStatus)
        netBtn = button("Nazoratni yoqish", 0xFF0EA5E9.toInt()) { toggleNet() }
        net.addView(netBtn)
        net.addView(button("So'nggi ulanishlar", 0xFF0284C7.toInt()) { showNet() })
        net.addView(
            tv(
                "Eslatma: tizim sozlamasidagi «Shaxsiy DNS» (Private DNS) yoqilgan bo'lsa, nazorat ishlamaydi: Sozlamalar → Ulanishlar → Shaxsiy DNS → O'chiq. Yoqilganda tepada VPN belgisi (kalit) ko'rinadi, bu normal.",
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        col.addView(net)

        // Umumiy xavf bazasi
        val base = card()
        base.addView(cardHeader("🌐", "Umumiy xavf bazasi", "Firibgar raqam, havola va fayllar ro'yxati internetdan yangilanadi va tekshiruvlarda ishlatiladi.", 0xFF0891B2.toInt()))
        baseInfo = tv(Blocklist.info(this), 12f, false, pal.sub)
        baseInfo.layoutParams = lp(8)
        base.addView(baseInfo)
        base.addView(button("Bazani yangilash", 0xFF0891B2.toInt()) { refreshBase() })
        base.addView(
            tv("Firibgar raqam yoki havola topsangiz, hisobotdagi «Xabar berish» tugmasi bilan yuboring. Ro'yxatga qo'shilishini egasi tekshiradi.", 11f, false, pal.sub)
        )
        col.addView(base)

        // VirusTotal
        val vtCard = card()
        vtCard.addView(
            cardHeader(
                "🔑", "VirusTotal (ixtiyoriy)",
                "Kalit kiritilsa, 60+ antivirus bazasi bilan solishtiriladi. Faqat fayl xeshi yuboriladi. Kalitni virustotal.com saytida bepul ro'yxatdan o'tib, profil → API key bo'limidan olasiz.",
                0xFF4F46E5.toInt()
            )
        )
        keyInput = field("API kaliti")
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setText(prefs.getString("vt", ""))
        vtCard.addView(keyInput)
        vtCard.addView(button("Saqlash", 0xFF4F46E5.toInt()) {
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
            addView(cardHeader("🔒", "Ilovani qulflash", "Ochishda telefon ekran qulfi (PIN, barmoq izi) so'raladi.", 0xFFDB2777.toInt()))
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

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        col.addView(
            tv("Qo'riqchi Pro ${ver ?: ""}  •  evristik tekshiruv, antivirus o'rnini bosmaydi", 11f, false, pal.sub).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, 0)
            }
        )

        for (i in 0 until col.childCount) {
            val v = col.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(16).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 40L).setDuration(350).start()
        }

        refreshShieldUi()
        refreshCallUi()
        refreshNetUi()
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
            DailyScanService.schedule(this)
        } else {
            stopService(Intent(this, GuardService::class.java))
            DailyScanService.cancel(this)
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

    private fun counterTile(num: Int, label: String, color: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(12), dp(6), dp(12))
            background = gradBg(mix(pal.card, color, 0.22f), pal.card, 14, mix(pal.line, color, 0.55f))
            addView(tv("$num", 22f, true, color).apply { gravity = Gravity.CENTER })
            addView(tv(label, 12f, false, pal.sub).apply { gravity = Gravity.CENTER })
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            }
        }

    private fun refreshSummary() {
        scanRing?.stopSpin()
        scanRing = null
        scanRadar?.stop()
        scanRadar = null
        scanSub = null
        scanBar = null
        summaryBox.removeAllViews()
        val last = prefs.getLong("lastScan", 0L)
        val c = card(14)
        if (last == 0L) {
            c.background = gradBg(mix(pal.card, pal.accent, 0.2f), pal.card, 22, mix(pal.line, pal.accent, 0.5f))
            val ring = ScoreRing(this, pal.accent, pal.line, pal.ink)
            ring.customLabel = "🛡️"
            ring.layoutParams = LinearLayout.LayoutParams(dp(130), dp(130)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            c.addView(ring)
            c.addView(tv("Telefoningiz hali tekshirilmagan", 17f, true).apply { gravity = Gravity.CENTER })
            c.addView(tv("Bir bosishda barcha o'rnatilgan ilovalarni tekshiring.", 12f, false, pal.sub).apply { gravity = Gravity.CENTER })
            c.addView(button("Hozir tekshirish", pal.accent) { scanInstalled() })
        } else {
            val total = prefs.getInt("lastTotal", 0)
            val risky = prefs.getInt("lastRisky", 0)
            val dangerSaved = prefs.getInt("lastDanger", -1)
            val danger = if (dangerSaved >= 0) dangerSaved else 0
            val susp = if (dangerSaved >= 0) prefs.getInt("lastSusp", 0) else risky
            val safe = (total - danger - susp).coerceAtLeast(0)
            val ratio = if (total > 0) safe.toFloat() / total else 1f
            val tone = when {
                danger > 0 -> pal.bad
                susp > 0 -> pal.warn
                else -> pal.good
            }
            val title = when {
                danger > 0 -> "Xavfli ilovalar topildi"
                susp > 0 -> "Ba'zi ilovalar e'tibor talab qiladi"
                else -> "Xavfli belgilar topilmadi"
            }
            c.background = gradBg(mix(pal.card, tone, 0.28f), pal.card, 22, mix(pal.line, tone, 0.6f))

            val ring = ScoreRing(this, tone, pal.line, pal.ink)
            ring.layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            c.addView(ring)
            ring.fillTo(ratio)
            c.addView(tv(title, 17f, true, tone).apply { gravity = Gravity.CENTER })
            val fmt = SimpleDateFormat("dd.MM  HH:mm", Locale.getDefault())
            c.addView(
                tv("$safe / $total ta ilovada xavf belgisi yo'q  •  ${fmt.format(Date(last))}", 12f, false, pal.sub)
                    .apply { gravity = Gravity.CENTER }
            )

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(14)
            }
            row.addView(counterTile(danger, "Xavfli", pal.bad))
            row.addView(counterTile(susp, "Shubhali", pal.warn))
            row.addView(counterTile(safe, "Toza", pal.good))
            c.addView(row)
            c.addView(button("Qayta tekshirish", tone) { scanInstalled() })
            c.addView(tv("Bu evristik tekshiruv: \"toza\" degani kafolat emas.", 11f, false, pal.sub).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }
        summaryBox.addView(c)
    }

    /** Tekshiruv paytida: aylanuvchi halqa, joriy ilova nomi va progress chizig'i. */
    private fun showScanProgress() {
        scanRing?.stopSpin()
        scanRadar?.stop()
        summaryBox.removeAllViews()
        val c = card(14)
        c.background = gradBg(mix(pal.card, pal.accent, 0.3f), pal.card, 22, mix(pal.line, pal.accent, 0.6f))
        val radar = RadarView(this, pal.accent, pal.line)
        radar.layoutParams = LinearLayout.LayoutParams(dp(210), dp(210)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        radar.start()
        c.addView(radar)
        c.addView(tv("Tekshirilmoqda…", 17f, true, pal.accent).apply { gravity = Gravity.CENTER })
        val sub = tv("Ilovalar ro'yxati o'qilmoqda", 12f, false, pal.sub).apply { gravity = Gravity.CENTER }
        c.addView(sub)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(pal.accent)
            progressBackgroundTintList = ColorStateList.valueOf(pal.line)
            layoutParams = lp(12)
        }
        c.addView(bar)
        summaryBox.addView(c)
        scanRing = null
        scanRadar = radar
        scanSub = sub
        scanBar = bar
    }

    private fun updateScanProgress(i: Int, n: Int, name: String) {
        val radar = scanRadar ?: return
        radar.addBlip(name)
        scanSub?.text = "$i / $n  •  $name"
        scanBar?.max = n
        scanBar?.progress = i
    }

    private fun showAudit(group: String) {
        results.removeAllViews()
        status.text = "Ruxsatlar tekshirilmoqda…"
        status.setTextColor(pal.ink)
        Thread {
            val a = audit ?: try {
                Scanner.permissionAudit(this)
            } catch (e: Exception) {
                null
            }
            if (a != null) audit = a
            runOnUiThread {
                if (a == null) {
                    status.text = "Ruxsatlarni o'qib bo'lmadi."
                    return@runOnUiThread
                }
                val list = a[group] ?: emptyList()
                status.text = "$group ruxsati berilgan ilovalar: ${list.size} ta"
                for ((label, pkg) in list) {
                    val c = card(8)
                    c.addView(tv(label, 15f, true))
                    c.addView(tv(pkg, 11f, false, pal.sub))
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = lp(6)
                    }
                    row.addView(smallBtn("Ruxsatni o'zgartirish") {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                        )
                    })
                    c.addView(row)
                    results.addView(c)
                }
            }
        }.start()
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
        sb.append("Sana: ")
            .append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()))
            .append('\n')
        sb.append("${r.label} (${r.pkg})\n")
        sb.append("${r.level.title}: ${r.score}/100\n")
        r.source?.let { sb.append("Manba: ").append(it).append('\n') }
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

    /** Oddiy tilda "endi nima qilay?" maslahati. */
    private fun adviceFor(r: Report, installed: Boolean): String {
        val hot = r.level == Level.CRITICAL || r.level == Level.HIGH
        if (r.pkg == "matn") {
            return when {
                hot ->
                    "Nima qilish kerak: xabarga javob bermang va havolani ochmang. SMS-kod, karta raqami va parolni HECH KIMGA aytmang: haqiqiy bank xodimi ham so'ramaydi. Raqamni bloklang."
                r.level == Level.MEDIUM ->
                    "Nima qilish kerak: ehtiyot bo'ling. Xabardagi raqam yoki havoladan foydalanmang, bank yoki idorani rasmiy raqami/ilovasi orqali o'zingiz tekshiring."
                else ->
                    "Aniq firibgarlik belgisi topilmadi. Baribir kod yoki karta ma'lumotini hech kimga bermang."
            }
        }
        val isFile = !installed && r.sha256 != null
        return when {
            isFile && hot ->
                "Nima qilish kerak: bu faylni O'RNATMANG va o'chiring. Yuboruvchiga ishonmang, agar o'rnatgan bo'lsangiz, darhol ilovani o'chiring."
            isFile && r.level == Level.MEDIUM ->
                "Nima qilish kerak: ishonchli manbadan bo'lmasa, o'rnatmang. Yuboruvchidan fayl nima ekanini so'rang."
            isFile ->
                "Aniq xavf belgisi topilmadi. Baribir faqat ishonchli manbadan o'rnating."
            installed && hot ->
                "Nima qilish kerak: ilovani tanimasangiz, darhol o'chiring. Bank va Telegram parollarini boshqa qurilmadan almashtirishni o'ylab ko'ring."
            installed && r.level == Level.MEDIUM ->
                "Nima qilish kerak: ilovani taniysizmi? Tanimasangiz, o'chiring yoki ruxsatlarini cheklang."
            installed ->
                "Hozircha xavfli belgi yo'q. Ilovani yangilab turing."
            hot ->
                "Nima qilish kerak: havolani OCHMANG va hech kimga yubormang. Bank ma'lumotlarini kiritmang."
            r.level == Level.MEDIUM ->
                "Nima qilish kerak: havolani ochishdan oldin manzilini rasmiy sayt bilan solishtiring."
            else ->
                "Aniq xavf belgisi topilmadi, lekin shubhali xabar bo'lsa, ehtiyot bo'ling."
        }
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
        c.addView(tv(adviceFor(r, installed), 13f, false, pal.ink).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = shape(pal.field, 10, r.level.color)
            layoutParams = lp(6)
        })
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

            val row3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(6)
            }
            row3.addView(smallBtn("Karantin") {
                Quarantine.add(this, r.pkg)
                toast("Karantinga qo'shildi")
            })
            c.addView(row3)
        } else {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            row.addView(smallBtn("Ulashish") { share(r) })
            if (r.score >= 25) row.addView(smallBtn("Xabar berish") { reportThreat(r) })
            c.addView(row)
        }
        wrap.alpha = 0f
        results.addView(wrap, index)
        wrap.animate().alpha(1f).setDuration(250).start()
        return wrap
    }

    // ---------- Amallar ----------

    private fun scanInstalled() {
        results.removeAllViews()
        status.text = ""
        status.setTextColor(pal.ink)
        showScanProgress()
        val t0 = SystemClock.elapsedRealtime()
        Thread {
            val list = try {
                val trust = Trust.all(this)
                Scanner.scanInstalled(this) { i, n, name ->
                    runOnUiThread { updateScanProgress(i, n, name) }
                }.filter { it.pkg !in trust }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                // Animatsiya kamida 2,6 soniya ko'rinsin: tekshiruv sezilib tursin
                val wait = (2600L - (SystemClock.elapsedRealtime() - t0)).coerceAtLeast(0L)
                summaryBox.postDelayed({ finishScan(list) }, wait)
            }
        }.start()
    }

    private fun finishScan(list: List<Report>?) {
        if (list == null) {
            refreshSummary()
            status.text = "Ilovalar ro'yxatini olib bo'lmadi."
            return
        }
        val risky = list.filter { it.score >= 25 }
        prefs.edit()
            .putLong("lastScan", System.currentTimeMillis())
            .putInt("lastTotal", list.size)
            .putInt("lastRisky", risky.size)
            .putInt("lastDanger", list.count { it.score >= 50 })
            .putInt("lastSusp", list.count { it.score in 25..49 })
            .apply()
        refreshSummary()
        status.text = "${list.size} ta ilova tekshirildi: ${risky.size} tasi e'tibor talab qiladi."
        val show = if (risky.isEmpty()) list.take(5) else risky.take(40)
        show.forEach { showReport(it, true) }
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
        if (requestCode == 41) {
            if (resultCode == RESULT_OK) startNet() else toast("Ruxsat berilmadi")
            return
        }
        if (requestCode == 21) {
            refreshCallUi()
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
            val r = linkReport(url)
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
            if (t != null && t.trim().isNotEmpty()) {
                setIntent(Intent())
                val found = urlIn(t)
                results.removeAllViews()
                status.text = ""
                if (found != null && t.trim().length <= found.length + 25) {
                    linkInput.setText(found)
                    showReport(linkReport(found), false)
                } else if (found == null && t.trim().length < 25 && !t.contains(' ')) {
                    linkInput.setText(t.trim())
                    showReport(linkReport(t.trim()), false)
                } else {
                    msgInput.setText(t.trim())
                    lastText = t.trim()
                    showReport(ScamText.analyze(t, this, null), false)
                }
                scrollToResults()
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

    /** Fayl qaysi ilovadan kelganini Uri egasidan (authority) aniqlaydi. Yuboruvchi shaxsni Android bermaydi. */
    private fun sourceOf(uri: Uri): String {
        val a = (uri.authority ?: "").lowercase()
        return when {
            a.contains("telegram") -> "Telegram"
            a.contains("whatsapp") -> "WhatsApp"
            a.contains("chrome") || a.contains("browser") -> "Brauzer"
            a.contains("downloads") -> "Yuklamalar (brauzer yoki boshqa ilovadan yuklangan)"
            a.contains("documentsui") || a.contains("externalstorage") -> "Telefon xotirasidan qo'lda tanlandi"
            a.isEmpty() -> "Noma'lum"
            else -> a
        }
    }

    private fun scanUri(uri: Uri) {
        val src = sourceOf(uri)
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
                    val deep0 = DeepScan.apply(cur, tmp.absolutePath)
                    val senderNote = if (src == "Telegram" || src == "WhatsApp") {
                        listOf(Finding(0, "Yuboruvchi kimligini Android ilovaga bermaydi. Chatni ochib, yuboruvchi profili (nomi, raqami) va xabarni skrinshot qilib saqlang"))
                    } else emptyList()
                    val deep1 = deep0.copy(
                        source = src,
                        findings = listOf(Finding(0, "Fayl manbasi: $src")) + senderNote + deep0.findings
                    )
                    val deep = if (Blocklist.hasHash(this, sha)) {
                        deep1.copy(
                            score = 100,
                            findings = listOf(Finding(80, "Umumiy xavf bazasida bor: bu fayl xavfli deb belgilangan")) + deep1.findings
                        )
                    } else deep1
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
            if (finalRep != null) {
                try {
                    val why = finalRep.findings.filter { it.points > 0 }.maxByOrNull { it.points }?.text
                        ?: "Xavfli belgilar topilmadi"
                    Store.add(this, "Fayl: ${finalRep.level.title}, ${finalRep.label}", why, finalRep.score)
                } catch (e: Exception) {
                    // tarixga yozilmasa ham natija ko'rsatilaveradi
                }
            }
            runOnUiThread {
                if (finalRep == null) {
                    status.text = finalErr ?: "Xatolik"
                } else {
                    status.text = ""
                    showReport(finalRep, false)
                    refreshEvents()
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
        showReport(linkReport(s), false)
    }
}
