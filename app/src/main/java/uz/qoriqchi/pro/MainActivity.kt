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
    private lateinit var homeBox: LinearLayout
    private var mainView: View? = null
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
    private lateinit var pwInfo: TextView
    private var scrollRoot: ScrollView? = null
    private lateinit var msgInput: EditText
    private lateinit var msgCard: View
    private var audit: Map<String, List<Pair<String, String>>>? = null
    private var updating = false
    private lateinit var alInfo: TextView
    private lateinit var alSwitch: Switch
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
        if (AppLock.enabled(this)) AppLock.setEnabled(this, false)
        if (!prefs.getBoolean("onb", false)) showOnboarding()
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
            PermWatch.checkIfDue(applicationContext)
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
        status.text = L("Ilovalar manbasi aniqlanmoqda…", "Определяется источник приложений…", "Detecting app sources…")
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
                    status.text = L("Ro'yxatni olib bo'lmadi.", "Не удалось получить список.", "Couldn't load the list.")
                    return@runOnUiThread
                }
                status.text = ""
                renderOrigin(list)
            }
        }.start()
    }

    private fun renderOrigin(list: List<AppOrigin>) {
        results.removeAllViews()
        val q = Quarantine.all(this)
        val store = list.count { it.kind == 0 }
        val manual = list.count { it.kind == 1 }
        val unknown = list.count { it.kind == 2 }
        val inQuar = list.count { it.pkg in q }
        val head = card(0)
        head.addView(cardHeader("🧭", L("Ilovalar nazorati", "Контроль приложений", "App control"), L("Ilovani kim o'rnatgan, qaysilari shubhali va karantinda. Do'kondan tashqari ilovalar xavfliroq.", "Кто установил приложение, какие подозрительны и какие в карантине. Приложения не из магазина опаснее.", "Who installed each app, which are suspicious and which are quarantined. Apps from outside the store are riskier."), 0xFF0EA5E9.toInt()))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(12)
        }
        row.addView(counterTile(store, L("Do'kondan", "Из магазина", "From store"), pal.good))
        row.addView(counterTile(manual, L("APK orqali", "Через APK", "Via APK"), pal.warn))
        row.addView(counterTile(unknown, L("Noma'lum", "Неизвестно", "Unknown"), pal.bad))
        row.addView(counterTile(inQuar, L("Karantinda", "В карантине", "Quarantined"), 0xFFE11D48.toInt()))
        head.addView(row)
        head.addView(
            tv(
                L("Eslatma: Android boshqa ilovani o'chirmasdan bloklashga ruxsat bermaydi. Karantin ro'yxat tutadi va xavfli ruxsatlarni ko'rsatadi; ularni olib tashlash yoki ilovani o'chirish o'zingizning tasdig'ingiz bilan bo'ladi.", "Примечание: Android не позволяет блокировать чужое приложение без удаления. Карантин ведёт список и показывает опасные разрешения; убрать их или удалить приложение можно только с вашего подтверждения.", "Note: Android does not let one app block another without uninstalling it. Quarantine keeps a list and shows risky permissions; removing them or uninstalling the app is done only with your confirmation."),
                11f, false, pal.sub
            ).apply { layoutParams = lp(10) }
        )
        results.addView(head)

        val shown = list.filter { it.kind != 0 || it.pkg in q }.sortedByDescending { it.pkg in q }
        if (shown.isEmpty()) {
            results.addView(card().apply {
                addView(tv(L("Hamma ilova rasmiy do'kondan o'rnatilgan. Yaxshi holat.", "Все приложения установлены из официального магазина. Всё хорошо.", "All apps are installed from the official store. All good."), 14f, true, pal.good))
            })
            return
        }
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val week = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        for (a in shown.take(60)) {
            val quar = a.pkg in q
            val col = if (quar) 0xFFE11D48.toInt() else if (a.kind == 1) pal.warn else pal.bad
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = shape(pal.card, 16, mix(pal.line, col, 0.5f))
                layoutParams = lp(10)
            }
            val fresh = if (a.installedAt >= week) "  🆕" else ""
            box.addView(tv((if (quar) "🧪  " else "") + a.label + fresh, 15f, true))
            box.addView(tv(a.via, 13f, true, if (a.kind == 0) pal.sub else col))
            box.addView(tv(L("O'rnatilgan", "Установлено", "Installed") + ": ${fmt.format(Date(a.installedAt))}  •  ${a.pkg}", 11f, false, pal.sub))
            if (quar) {
                val granted = Quarantine.grantedRisky(this, a.pkg)
                if (granted.isEmpty()) {
                    box.addView(tv(L("Hozir berilgan xavfli ruxsat yo'q.", "Сейчас опасных разрешений нет.", "No risky permissions granted now."), 13f, false, pal.good))
                } else {
                    box.addView(tv(L("Berilgan ruxsatlar: ", "Выданные разрешения: ", "Granted permissions: ") + granted.joinToString(", "), 13f, true, pal.bad))
                }
            }
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            r.addView(smallBtn(L("Tekshirish", "Проверить", "Check")) { checkOriginApp(a, box) })
            if (quar) {
                r.addView(smallBtn(L("Ruxsatlar", "Разрешения", "Permissions")) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                })
            } else {
                r.addView(smallBtn(L("Karantin", "В карантин", "Quarantine")) {
                    Quarantine.add(this, a.pkg)
                    toast(L("Karantinga qo'shildi", "Добавлено в карантин", "Added to quarantine"))
                    renderOrigin(list)
                })
            }
            val r2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(6)
            }
            if (quar) {
                r2.addView(smallBtn(L("Chiqarish", "Убрать", "Release")) {
                    Quarantine.remove(this, a.pkg)
                    renderOrigin(list)
                })
            }
            r2.addView(smallBtn(L("O'chirish", "Удалить", "Uninstall")) {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
            })
            box.addView(r)
            box.addView(r2)
            results.addView(box)
        }
    }

    private fun checkOriginApp(a: AppOrigin, box: View) {
        toast(L("Tekshirilmoqda…", "Проверяется…", "Checking…"))
        Thread {
            val rep = try {
                Scanner.analyzePackage(this, a.pkg)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (rep == null) {
                    toast(L("Ilovani tekshirib bo'lmadi", "Не удалось проверить приложение", "Couldn't check the app"))
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
        showOrigin()
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
            L("✅ Faol: kelgan SMS va xabarlar firibgarlikka tekshiriladi.", "✅ Активно: входящие SMS и сообщения проверяются на мошенничество.", "✅ Active: incoming SMS and messages are checked for scams.")
        } else {
            L("Hozir o'chiq. Yoqish uchun bildirishnomalarni o'qish ruxsatini bering.", "Сейчас выключено. Для включения дайте разрешение на чтение уведомлений.", "Currently off. Grant notification access to enable.")
        }
        shieldStatus.setTextColor(if (on) pal.good else pal.warn)
        shieldBtn.visibility = if (on) View.GONE else View.VISIBLE
        syncPills()
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
            L("✅ Faol: noma'lum yoki xavfli raqamdan qo'ng'iroq kelsa, ogohlantiriladi.", "✅ Активно: предупредит при звонке с неизвестного или опасного номера.", "✅ Active: warns about calls from unknown or dangerous numbers.")
        } else {
            L("Hozir o'chiq. Yoqish uchun Qo'riqchi Pro ni qo'ng'iroq filtri qilib tanlang.", "Сейчас выключено. Выберите Qo'riqchi Pro фильтром звонков.", "Currently off. Choose Qo'riqchi Pro as the call filter.")
        }
        callStatus.setTextColor(if (on) pal.good else pal.warn)
        callBtn.visibility = if (on) View.GONE else View.VISIBLE
        syncPills()
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
        toast(L("Bu telefonda to'g'ridan-to'g'ri yoqib bo'lmadi. Quyidagi yo'riqnomadan foydalaning", "Включить напрямую на этом телефоне не удалось. Воспользуйтесь инструкцией ниже", "Couldn't enable it directly on this phone. Use the instructions below"))
    }

    private fun addBlackNumber() {
        val n = callInput.text.toString().trim()
        if (Blocklist.addLocalPhone(this, n)) {
            callInput.setText("")
            baseInfo.text = Blocklist.info(this)
            toast(L("Qora ro'yxatga qo'shildi", "Добавлено в чёрный список", "Added to blacklist"))
        } else {
            toast(L("Raqam noto'g'ri", "Неверный номер", "Invalid number"))
        }
    }

    private fun refreshNetUi() {
        if (DnsShield.running) {
            netStatus.text = L("✅ Faol: ${DnsShield.total} ta so'rov ko'rildi, ${DnsShield.blockedCount} tasi to'sildi.", "✅ Активно: просмотрено запросов: ${DnsShield.total}, заблокировано: ${DnsShield.blockedCount}.", "✅ Active: ${DnsShield.total} requests seen, ${DnsShield.blockedCount} blocked.")
            netStatus.setTextColor(pal.good)
            netBtn.text = L("Nazoratni o'chirish", "Выключить контроль", "Disable monitoring")
        } else {
            netStatus.text = L("Hozir o'chiq.", "Сейчас выключено.", "Currently off.")
            netStatus.setTextColor(pal.warn)
            netBtn.text = L("Nazoratni yoqish", "Включить контроль", "Enable monitoring")
        }
        syncPills()
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

    private fun runPermWatch() {
        toast("Tekshirilmoqda…")
        Thread {
            val res = try {
                PermWatch.check(this)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                pwInfo.text = PermWatch.info(this)
                results.removeAllViews()
                if (res == null) {
                    status.text = "Tekshirib bo'lmadi."
                } else if (res.first) {
                    status.text = "Boshlang'ich holat saqlandi. Bundan keyin yangi ruxsatlar paydo bo'lsa, ogohlantiramiz."
                } else if (res.changes.isEmpty()) {
                    status.text = "O'zgarish topilmadi: ilovalar ruxsatlari avvalgidek."
                } else {
                    status.text = "${res.changes.size} ta ilovada yangi ruxsat topildi:"
                    for (c in res.changes) {
                        results.addView(card().apply {
                            addView(tv("⚠️ ${c.label}", 15f, true))
                            addView(tv("Yangi: " + c.added.joinToString(", "), 13f, true, if (c.critical) pal.bad else pal.warn))
                        })
                    }
                }
                scrollToResults()
            }
        }.start()
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

    /** Dalil to'plami: so'nggi hodisalar, shubhali ilovalar va karantin bitta matnli hisobotga yig'iladi. */
    private fun buildEvidence() {
        toast("Hisobot tayyorlanmoqda…")
        Thread {
            val sb = StringBuilder()
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val ver = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) {
                    ""
                }
                sb.append("QO'RIQCHI PRO: DALIL HISOBOTI\n")
                sb.append("Sana: ").append(fmt.format(Date())).append("\n")
                sb.append("Qurilma: ").append(android.os.Build.MANUFACTURER).append(" ")
                    .append(android.os.Build.MODEL).append(", Android ")
                    .append(android.os.Build.VERSION.RELEASE).append("\n")
                sb.append("Ilova versiyasi: ").append(ver).append("\n\n")

                val ev = Store.list(this)
                sb.append("1) SO'NGGI HODISALAR (").append(ev.size).append(")\n")
                if (ev.isEmpty()) sb.append("Hodisa yo'q.\n")
                for (e in ev) {
                    sb.append("- ").append(fmt.format(Date(e.time))).append("  [").append(e.score)
                        .append("/100]  ").append(e.title).append("\n")
                    if (e.detail.isNotBlank()) sb.append("    ").append(e.detail.take(300)).append("\n")
                }

                val q = Quarantine.all(this)
                val apps = try {
                    Origin.list(this)
                } catch (e: Exception) {
                    emptyList()
                }
                val risky = apps.filter { it.kind != 0 || it.pkg in q }
                sb.append("\n2) SHUBHALI VA KARANTINDAGI ILOVALAR (").append(risky.size).append(")\n")
                if (risky.isEmpty()) sb.append("Do'kondan tashqari ilova yo'q.\n")
                for (a in risky.take(60)) {
                    sb.append("- ").append(a.label).append(" (").append(a.pkg).append(")  ")
                        .append(a.via).append(", o'rnatilgan ")
                        .append(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(a.installedAt)))
                    if (a.pkg in q) {
                        val g = Quarantine.grantedRisky(this, a.pkg)
                        sb.append(" [KARANTINDA")
                        if (g.isNotEmpty()) sb.append("; ruxsatlar: ").append(g.joinToString(", "))
                        sb.append("]")
                    }
                    sb.append("\n")
                }

                sb.append("\n3) RUXSAT O'ZGARISHI\n").append(PermWatch.info(this)).append("\n")
                sb.append("\n4) XAVF BAZASI\n").append(Blocklist.info(this)).append("\n")
                sb.append("\nBu hisobot telefondan chiqmagan; uni faqat o'zingiz yuborasiz. ")
                sb.append("Qo'riqchi Pro evristik tekshiruv bo'lib, rasmiy xulosa emas.\n")
            } catch (e: Exception) {
                sb.append("\nHisobotni to'liq yig'ib bo'lmadi.\n")
            }
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Qo'riqchi Pro: dalil hisoboti")
                    putExtra(Intent.EXTRA_TEXT, sb.toString())
                }
                startActivity(Intent.createChooser(i, "Hisobotni yuborish"))
            }
        }.start()
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

    private var panels: List<LinearLayout> = emptyList()
    private var tabBtns: List<LinearLayout> = emptyList()
    private var evWrap: View? = null

    private fun panel(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    /** Kartaning sarlavhadan keyingi qismini yig'iladigan qiladi: sarlavhani bossangiz ochiladi. */
    private fun fold(c: LinearLayout, open: Boolean = false) {
        if (c.childCount < 2) return
        val head = c.getChildAt(0) as? LinearLayout ?: return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (open) View.VISIBLE else View.GONE
        }
        val kids = ArrayList<View>()
        for (i in 1 until c.childCount) kids.add(c.getChildAt(i))
        for (k in kids) {
            c.removeView(k)
            body.addView(k)
        }
        c.addView(body)
        val arrow = tv(if (open) "▾" else "▸", 18f, true, pal.sub)
        arrow.setPadding(dp(8), 0, 0, 0)
        head.addView(arrow)
        head.isClickable = true
        head.setOnClickListener {
            val show = body.visibility == View.GONE
            body.visibility = if (show) View.VISIBLE else View.GONE
            arrow.text = if (show) "▾" else "▸"
        }
    }

    private val pills = HashMap<TextView, TextView>()

    /** Qalqon qatoridagi rangli «Yoqilgan / O'chiq» belgisini holat matni rangiga qarab yangilaydi. */
    private fun syncPills() {
        for ((st, pill) in pills) {
            val on = st.currentTextColor == pal.good
            pill.text = if (on) L("Yoqilgan", "Включено", "On") else L("O'chiq", "Выключено", "Off")
            pill.background = shape(if (on) pal.good else pal.warn, 8)
        }
    }

    /** Qalqonlar kartasidagi bitta qator: sarlavha, holat, tugma va yig'ib qo'yiladigan batafsil qism. */
    private fun L(uz: String, ru: String, en: String): String =
        when (prefs.getString("lang", "uz")) {
            "ru" -> ru
            "en" -> en
            else -> uz
        }

    private fun shieldRow(parent: LinearLayout, title: String, status: TextView, btn: TextView?, detail: View?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = shape(pal.bg, 14, pal.line)
            layoutParams = lp(10)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val t = tv(title, 14f, true)
        t.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        top.addView(t)
        val pill = tv("…", 10f, true, Color.WHITE)
        pill.setPadding(dp(8), dp(2), dp(8), dp(2))
        pill.background = shape(pal.sub, 8)
        pills[status] = pill
        top.addView(pill)
        if (detail != null) {
            val more = tv(L("Batafsil", "Подробнее", "Details"), 12f, true, pal.accent)
            more.setPadding(dp(8), dp(4), 0, dp(4))
            more.setOnClickListener {
                detail.visibility = if (detail.visibility == View.GONE) View.VISIBLE else View.GONE
            }
            top.addView(more)
        }
        box.addView(top)
        status.layoutParams = lp(4)
        box.addView(status)
        if (btn != null) box.addView(btn)
        if (detail != null) {
            detail.visibility = View.GONE
            box.addView(detail)
        }
        parent.addView(box)
    }

    private fun selectTab(i: Int, clear: Boolean = true) {
        if (panels.isEmpty()) return
        for ((k, p) in panels.withIndex()) p.visibility = if (k == i) View.VISIBLE else View.GONE
        for ((k, b) in tabBtns.withIndex()) {
            val on = k == i
            val ic = b.getChildAt(0) as TextView
            val lb = b.getChildAt(1) as TextView
            ic.alpha = if (on) 1f else 0.55f
            lb.setTextColor(if (on) pal.accent else pal.sub)
            b.background = if (on) shape(mix(pal.card, pal.accent, 0.16f), 14) else null
        }
        evWrap?.visibility = if (i == 0) View.VISIBLE else View.GONE
        if (clear) {
            results.removeAllViews()
            status.text = ""
        }
        scrollRoot?.post { scrollRoot?.scrollTo(0, 0) }
        val p = panels[i]
        for (j in 0 until p.childCount) {
            val v = p.getChildAt(j)
            v.alpha = 0f
            v.translationY = dp(12).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(j * 30L).setDuration(260).start()
        }
    }

    /** Bitta maydon: havola bo'lsa havola tekshiriladi, aks holda xabar matni. */
    private fun checkSmart() {
        val t = msgInput.text.toString().trim()
        if (t.isEmpty()) {
            toast(L("Havola yoki xabar matnini kiriting", "Введите ссылку или текст сообщения", "Enter a link or message text"))
            return
        }
        val found = urlIn(t)
        results.removeAllViews()
        status.text = ""
        val onlyLink = (found != null && t.length <= found.length + 25) ||
            (found == null && t.length < 25 && !t.contains(' '))
        if (onlyLink) {
            showReport(linkReport(found ?: t), false)
        } else {
            lastText = t
            showReport(ScamText.analyze(t, this, null), false)
        }
        scrollToResults()
    }

    private fun buildUi() {
        val root = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isFillViewport = true
        }
        scrollRoot = root
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(26), dp(16), dp(24))
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
            addView(tv(L("Telefoningiz xavfsizlik qo'riqchisi", "Страж безопасности вашего телефона", "Your phone's security guardian"), 13f, false, pal.sub))
        }
        head.addView(logo)
        head.addView(titles)
        col.addView(head)

        val p0 = panel()
        val p1 = panel()
        val p2 = panel()
        val p3 = panel()
        panels = listOf(p0, p1, p2, p3)
        for (p in panels) col.addView(p)

        // =================== 1-bo'lim: HIMOYA ===================
        summaryBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        p0.addView(summaryBox)
        homeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        p0.addView(homeBox)

        // Fon himoyasi
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
        guardTitle = tv(L("Real vaqt himoyasi", "Защита в реальном времени", "Real-time protection"), 17f, true, Color.WHITE)
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
        p0.addView(guardCard)

        // Qalqonlar: xabar, qo'ng'iroq, tarmoq (bitta kartada)
        val shields = card()
        shields.addView(cardHeader("🛡️", L("Xabar qalqoni", "Щит сообщений", "Message shield"), L("Telegram, SMS va boshqa xabarlardagi firibgar havola, bot va fayllarni real vaqtda aniqlaydi.", "Находит мошеннические ссылки, ботов и файлы в Telegram, SMS и других сообщениях в реальном времени.", "Detects scam links, bots and files in Telegram, SMS and other messages in real time."), 0xFF16A34A.toInt()))

        shieldStatus = tv("", 13f, true)
        shieldBtn = button(L("Ruxsat berish", "Дать разрешение", "Grant access"), 0xFF16A34A.toInt()) {
            openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        val msgDetail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        msgDetail.addView(
            tv(
                L("SMS, Telegram, WhatsApp va boshqa xabarlar firibgarlikka tekshiriladi. Matn telefondan chiqmaydi va saqlanmaydi. Ruxsat tugmasi kulrang bo'lib, bosilmasa: Sozlamalar → Ilovalar → Qo'riqchi Pro → yuqori o'ngdagi ⋮ → «Cheklangan sozlamalarga ruxsat berish». Keyin qaytib, ruxsat bering.", "SMS, Telegram, WhatsApp и другие сообщения проверяются на мошенничество. Текст не покидает телефон и не сохраняется. Если кнопка разрешения серая и не нажимается: Настройки → Приложения → Qo'riqchi Pro → ⋮ вверху справа → «Разрешить ограниченные настройки». Затем вернитесь и дайте разрешение.", "SMS, Telegram, WhatsApp and other messages are checked for scams. The text never leaves your phone and is not stored. If the permission button is greyed out: Settings → Apps → Qo'riqchi Pro → ⋮ at the top right → «Allow restricted settings». Then come back and grant access."),
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        val shRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(8)
        }
        shRow.addView(smallBtn(L("Ilova sozlamalari", "Настройки приложения", "App settings")) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        msgDetail.addView(shRow)
        shieldRow(shields, L("💬 Xabar qalqoni", "💬 Щит сообщений", "💬 Message shield"), shieldStatus, shieldBtn, msgDetail)

        callStatus = tv("", 13f, true)
        callBtn = button(L("Qalqonni yoqish", "Включить щит", "Enable shield"), 0xFFF43F5E.toInt()) { requestCallRole() }
        val callDetail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        callDetail.addView(
            tv(
                L("Xavf bazasidagi yoki o'zingiz qo'shgan raqamdan qo'ng'iroq kelsa, ogohlantiradi yoki rad etadi. Raqam qo'shish: «Sozlamalar» bo'limi → Xavf bazasi.", "Если звонит номер из базы угроз или добавленный вами, приложение предупредит или отклонит звонок. Добавить номер: вкладка «Настройки» → База угроз.", "If a number from the threat database or one you added calls, the app warns you or rejects the call. Add a number: Settings tab → Threat database."),
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        val rej = Switch(this)
        rej.text = L("Bazadagi raqamni avtomatik rad etish", "Автоматически отклонять номера из базы", "Auto-reject numbers from the database")
        rej.setTextColor(pal.ink)
        rej.isChecked = prefs.getBoolean("call_reject", false)
        rej.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("call_reject", on).apply() }
        rej.layoutParams = lp(10)
        callDetail.addView(rej)
        callDetail.addView(
            tv(
                L("Yoqilmasa: Sozlamalar → Ilovalar → Standart ilovalar → «Qo'ng'iroqlarni aniqlash va spamdan himoya» ichidan Qo'riqchi Pro ni tanlang.", "Если не включается: Настройки → Приложения → Приложения по умолчанию → «Определитель номера и защита от спама» и выберите Qo'riqchi Pro.", "If it won't enable: Settings → Apps → Default apps → «Caller ID & spam» and choose Qo'riqchi Pro."),
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )

        netStatus = tv("", 13f, true)
        netBtn = button(L("Nazoratni yoqish", "Включить контроль", "Enable monitoring"), 0xFF0EA5E9.toInt()) { toggleNet() }
        val netDetail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        netDetail.addView(
            tv(
                L("Ilovalar qaysi manzillarga ulanayotganini ko'radi va xavfli manzillarni to'sadi. Faqat manzil nomlari ko'riladi, ma'lumotingiz o'qilmaydi. «Shaxsiy DNS» (Private DNS) yoqilgan bo'lsa, nazorat ishlamaydi. Yoqilganda tepada VPN belgisi (kalit) ko'rinadi, bu normal.", "Показывает, к каким адресам подключаются приложения, и блокирует опасные. Видны только названия адресов, ваши данные не читаются. Если включён «Частный DNS» (Private DNS), контроль не работает. При включении вверху появится значок VPN (ключ), это нормально.", "Shows which addresses apps connect to and blocks dangerous ones. Only address names are seen; your data is not read. If «Private DNS» is on, monitoring does not work. When enabled, a VPN icon (key) appears at the top; this is normal."),
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        val netRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(8)
        }
        netRow.addView(smallBtn(L("So'nggi ulanishlar", "Последние подключения", "Recent connections")) { showNet() })
        netDetail.addView(netRow)
        p0.addView(shields)

        // =================== 2-bo'lim: TEKSHIRISH ===================
        p1.addView(
            tileRow(
                tile("📱", L("Ilovalarim", "Мои приложения", "My apps"), L("Hammasini tekshir", "Проверить все", "Scan all"), 0xFF2563EB.toInt()) { scanInstalled() },
                tile("📦", L("APK fayl", "APK-файл", "APK file"), L("O'rnatishdan oldin", "Перед установкой", "Before installing"), 0xFF7C3AED.toInt()) { pickFile() }
            )
        )

        val smart = card()
        msgCard = smart
        smart.addView(cardHeader("🔎", L("Havola yoki xabar tekshiruvi", "Проверка ссылки или сообщения", "Link or message check"), L("Shubhali havola yoki SMS/Telegram xabarini qo'ying. Havola ochilmaydi, matn telefondan chiqmaydi.", "Вставьте подозрительную ссылку или сообщение из SMS/Telegram. Ссылка не открывается, текст не покидает телефон.", "Paste a suspicious link or an SMS/Telegram message. The link is not opened and the text never leaves your phone."), 0xFF0891B2.toInt()))
        msgInput = field(L("Havola yoki xabar matnini shu yerga qo'ying", "Вставьте ссылку или текст сообщения сюда", "Paste the link or message text here"))
        msgInput.setSingleLine(false)
        msgInput.minLines = 3
        msgInput.maxLines = 8
        msgInput.gravity = Gravity.TOP or Gravity.START
        msgInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        linkInput = msgInput
        smart.addView(msgInput)
        smart.addView(button(L("Tekshirish", "Проверить", "Check"), 0xFF0891B2.toInt()) { checkSmart() })
        p1.addView(smart)

        clipBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        p1.addView(clipBox)

        // =================== 3-bo'lim: TELEFON ===================
        p2.addView(
            tileRow(
                tile("🩺", L("Telefon sog'lig'i", "Здоровье телефона", "Phone health"), L("Qulf, yangilanish, xavfli ruxsatlar", "Блокировка, обновления, опасные разрешения", "Lock, updates, risky permissions"), 0xFF16A34A.toInt()) { showHealth() },
                tile("🧭", L("Ilovalar nazorati", "Контроль приложений", "App control"), L("Manba va karantin", "Источник и карантин", "Source & quarantine"), 0xFF0EA5E9.toInt()) { showOrigin() }
            )
        )
        p2.addView(
            tileRow(
                tile("🧾", L("Dalil to'plami", "Сбор доказательств", "Evidence report"), L("Hodisalar hisoboti", "Отчёт о событиях", "Events report"), 0xFF7C3AED.toInt()) { buildEvidence() },
                tile("🤝", L("Ishonchli ilovalar", "Доверенные", "Trusted apps"), L("Ignor ro'yxati", "Список игнора", "Ignore list"), 0xFF16A34A.toInt()) { showIgnored() }
            )
        )
        pwInfo = tv(PermWatch.info(this), 11f, false, pal.sub)
        pwInfo.layoutParams = lp(0)

        val auditCard = card()
        auditCard.addView(cardHeader("🔍", L("Ruxsatlar auditi", "Аудит разрешений", "Permission audit"), L("Qaysi ilova kamera, mikrofon, joylashuv va boshqalarga kira olishini ko'ring.", "Посмотрите, какие приложения имеют доступ к камере, микрофону, геолокации и другому.", "See which apps can access the camera, microphone, location and more."), 0xFFF59E0B.toInt()))
        for (chunk in Scanner.auditGroups.keys.toList().chunked(3)) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(8)
            }
            for (n in chunk) r.addView(smallBtn(n) { showAudit(n) })
            auditCard.addView(r)
        }

        // =================== 4-bo'lim: SOZLAMALAR ===================
        // Til tanlash
        val langCard = card()
        langCard.addView(cardHeader("🌐", "Til / Язык / Language", "O'zbekcha · Русский · English", 0xFF0EA5E9.toInt()))
        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(8)
        }
        for ((code, name) in listOf("uz" to "O'zbekcha", "ru" to "Русский", "en" to "English")) {
            langRow.addView(smallBtn(name) {
                prefs.edit().putString("lang", code).apply()
                recreate()
            })
        }
        langCard.addView(langRow)
        fold(langCard)
        p3.addView(langCard)

        // Oila himoyasi
        val fam = card()
        fam.addView(cardHeader("👪", L("Oila himoyasi", "Семейная защита", "Family protection"), L("Yaqiningiz telefonida xavf topilsa, sizning Telegram'ingizga xabar boradi.", "Если на телефоне близкого найдётся угроза, вам придёт сообщение в Telegram.", "If a threat is found on a relative's phone, you get a message in Telegram."), 0xFF8B5CF6.toInt()))
        val famBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        famBody.addView(
            tv(
                L("1) Telegram'da @BotFather ga /newbot yozing, bot yarating va tokenni oling.\n2) O'zingiz yaratgan botga kirib /start bosing.\n3) Tokenni shu yerga qo'ying va «Chat ID ni topish» ni bosing.\n4) Yaqiningiz telefonida ham xuddi shu ma'lumotni kiriting va «Saqlash va sinash» ni bosing.\nXabarda faqat jo'natuvchi nomi va xavf sababi bo'ladi, xabar matni yuborilmaydi.", "1) В Telegram напишите @BotFather команду /newbot, создайте бота и получите токен.\n2) Откройте созданного бота и нажмите /start.\n3) Вставьте токен сюда и нажмите «Найти Chat ID».\n4) На телефоне близкого введите те же данные и нажмите «Сохранить и проверить».\nВ сообщении будут только имя отправителя и причина угрозы, текст сообщения не отправляется.", "1) In Telegram, message @BotFather with /newbot, create a bot and get the token.\n2) Open your bot and press /start.\n3) Paste the token here and press «Find Chat ID».\n4) On your relative's phone enter the same data and press «Save and test».\nThe message contains only the sender name and the reason for the alert; message text is not sent."),
                12f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        famToken = field(L("Bot tokeni", "Токен бота", "Bot token"))
        famToken.setText(Family.token(this))
        famChat = field("Chat ID")
        famChat.inputType = InputType.TYPE_CLASS_TEXT
        famChat.setText(Family.chat(this))
        famName = field(L("Telefon nomi (masalan: Dadam telefoni)", "Название телефона (например: Телефон папы)", "Phone name (e.g. Dad's phone)"))
        famName.setText(Family.name(this))
        famBody.addView(famToken)
        famBody.addView(famChat)
        famBody.addView(famName)
        famBody.addView(button(L("Chat ID ni topish", "Найти Chat ID", "Find Chat ID"), 0xFF8B5CF6.toInt()) { findFamChat() })
        famBody.addView(button(L("Saqlash va sinash", "Сохранить и проверить", "Save and test"), 0xFF7C3AED.toInt()) { saveFamily() })
        famBody.addView(button(L("O'chirish", "Выключить", "Turn off"), pal.sub) {
            Family.disable(this)
            toast(L("Oila himoyasi o'chirildi", "Семейная защита выключена", "Family protection turned off"))
        })
        fam.addView(button(if (Family.isOn(this)) L("Sozlash (yoqilgan)", "Настроить (включено)", "Set up (on)") else L("Sozlash", "Настроить", "Set up"), 0xFF8B5CF6.toInt()) {
            famBody.visibility = if (famBody.visibility == View.GONE) View.VISIBLE else View.GONE
        })
        fam.addView(famBody)
        fold(fam)

        // Xavf bazasi va qora ro'yxat
        val base = card()
        base.addView(cardHeader("🌐", L("Xavf bazasi va qora ro'yxat", "База угроз и чёрный список", "Threat database & blacklist"), L("Firibgar raqam, havola va fayllar ro'yxati internetdan yangilanadi. O'zingiz ham raqam qo'shishingiz mumkin.", "Список мошеннических номеров, ссылок и файлов обновляется из интернета. Вы также можете добавлять свои номера.", "The list of scam numbers, links and files updates from the internet. You can add your own numbers too."), 0xFF0891B2.toInt()))
        baseInfo = tv(Blocklist.info(this), 12f, false, pal.sub)
        baseInfo.layoutParams = lp(8)
        base.addView(baseInfo)
        base.addView(button(L("Bazani yangilash", "Обновить базу", "Update database"), 0xFF0891B2.toInt()) { refreshBase() })
        callInput = field(L("Qora ro'yxatga raqam (+998...)", "Номер для чёрного списка (+998...)", "Number to blacklist (+998...)"))
        callInput.inputType = InputType.TYPE_CLASS_PHONE
        base.addView(
            tv(L("Firibgar raqam yoki havola topsangiz, hisobotdagi «Xabar berish» tugmasi bilan yuboring. Ro'yxatga qo'shilishini egasi tekshiradi.", "Нашли мошеннический номер или ссылку — отправьте кнопкой «Xabar berish» в отчёте. Добавление в список проверяет владелец.", "If you find a scam number or link, send it with the «Xabar berish» (Report) button in the result. The owner reviews additions to the list."), 11f, false, pal.sub)
        )
        fold(base)
        p3.addView(base)

        // VirusTotal
        val vtCard = card()
        vtCard.addView(
            cardHeader(
                "🔑", L("VirusTotal (ixtiyoriy)", "VirusTotal (по желанию)", "VirusTotal (optional)"),
                L("Kalit kiritilsa, 60+ antivirus bazasi bilan solishtiriladi. Faqat fayl xeshi yuboriladi. Kalitni virustotal.com saytida bepul ro'yxatdan o'tib, profil → API key bo'limidan olasiz.", "Если ввести ключ, файл сверяется с базами 60+ антивирусов. Отправляется только хеш файла. Ключ можно получить бесплатно на virustotal.com: профиль → API key.", "If you enter a key, files are compared against 60+ antivirus databases. Only the file hash is sent. Get a free key at virustotal.com: profile → API key."),
                0xFF4F46E5.toInt()
            )
        )
        keyInput = field(L("API kaliti", "API-ключ", "API key"))
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyInput.setText(prefs.getString("vt", ""))
        vtCard.addView(keyInput)
        vtCard.addView(button(L("Saqlash", "Сохранить", "Save"), 0xFF4F46E5.toInt()) {
            prefs.edit().putString("vt", keyInput.text.toString().trim()).apply()
            toast(L("Saqlandi", "Сохранено", "Saved"))
        })
        fold(vtCard)
        p3.addView(vtCard)

        // Ilovani qulflash
        val lockCard = card()
        val lockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lockText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            addView(cardHeader("🔒", L("Ilovani qulflash", "Блокировка приложения", "App lock"), L("Ochishda telefon ekran qulfi (PIN, barmoq izi) so'raladi.", "При открытии запрашивается блокировка экрана (PIN, отпечаток).", "Asks for the screen lock (PIN, fingerprint) when opening."), 0xFFDB2777.toInt()))
        }
        lockSwitch = Switch(this)
        lockSwitch.isChecked = prefs.getBoolean("lock", false)
        lockSwitch.setOnCheckedChangeListener { btn, on ->
            if (on && !deviceSecure()) {
                btn.isChecked = false
                toast(L("Avval telefonda ekran qulfini (PIN yoki barmoq izi) o'rnating", "Сначала установите блокировку экрана на телефоне (PIN или отпечаток)", "First set up a screen lock (PIN or fingerprint) on your phone"))
            } else {
                prefs.edit().putBoolean("lock", on).apply()
            }
        }
        lockRow.addView(lockText)
        lockRow.addView(lockSwitch)
        lockCard.addView(lockRow)
        p3.addView(lockCard)

        // Boshqa ilovalarni qulflash
        val al = card()
        al.addView(
            cardHeader(
                "🔐", L("Ilovalarni qulflash", "Блокировка приложений", "Lock other apps"),
                L("Tanlangan ilovalar (Telegram, galereya, bank va boshqalar) ochilishdan oldin telefon ekran qulfini so'raydi.",
                    "Выбранные приложения (Telegram, галерея, банк и др.) запрашивают блокировку экрана при открытии.",
                    "Selected apps (Telegram, gallery, bank and others) ask for the screen lock before opening."),
                0xFFEA580C.toInt()
            )
        )
        alSwitch = Switch(this)
        alSwitch.text = L("Ilova qulfini yoqish", "Включить блокировку", "Enable app lock")
        alSwitch.setTextColor(pal.ink)
        alSwitch.layoutParams = lp(8)
        alSwitch.setOnCheckedChangeListener { btn, on ->
            if (updating) return@setOnCheckedChangeListener
            if (on && !AppLock.ready(this)) {
                updating = true
                btn.isChecked = false
                updating = false
                toast(L("Avval pastdagi ikkala ruxsatni bering", "Сначала выдайте оба разрешения ниже", "Grant both permissions below first"))
            } else {
                AppLock.setEnabled(this, on)
            }
        }
        al.addView(alSwitch)
        alInfo = tv("", 12f, true)
        alInfo.layoutParams = lp(8)
        al.addView(alInfo)
        al.addView(button(L("1) Foydalanish statistikasi ruxsati", "1) Доступ к статистике использования", "1) Usage access permission"), 0xFFEA580C.toInt()) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                toast(L("Sozlamani ochib bo'lmadi", "Не удалось открыть настройки", "Couldn't open the setting"))
            }
        })
        al.addView(button(L("2) Boshqa ilovalar ustidan ko'rsatish", "2) Показ поверх других приложений", "2) Display over other apps"), 0xFFF59E0B.toInt()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                toast(L("Sozlamani ochib bo'lmadi", "Не удалось открыть настройки", "Couldn't open the setting"))
            }
        })
        al.addView(button(L("3) Ilovalarni tanlash", "3) Выбрать приложения", "3) Choose apps"), 0xFF16A34A.toInt()) { showAppLockPicker() })
        al.addView(
            tv(
                L(
                    "Xiaomi/Redmi: Ilova sozlamalari → Boshqa ruxsatlar → «Fonda oyna ko'rsatish» (Display pop-up windows while running in the background) ni ham yoqing, aks holda qulf oynasi chiqmasligi mumkin. Telefonda ekran qulfi (PIN yoki barmoq izi) o'rnatilgan bo'lishi shart.",
                    "Xiaomi/Redmi: Настройки приложения → Другие разрешения → включите также «Всплывающие окна в фоне» (Display pop-up windows while running in the background), иначе окно блокировки может не появиться. На телефоне должна быть установлена блокировка экрана (PIN или отпечаток).",
                    "Xiaomi/Redmi: App settings → Other permissions → also enable «Display pop-up windows while running in the background», otherwise the lock window may not appear. The phone must have a screen lock (PIN or fingerprint)."
                ),
                11f, false, pal.sub
            ).apply { layoutParams = lp(8) }
        )
        fold(al)

        // =================== Umumiy: natijalar, hodisalar ===================
        status = tv("", 14f, true)
        status.layoutParams = lp(16)
        col.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(results)

        val ev = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        evWrap = ev
        val evHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(22)
        }
        val evTitle = tv(L("So'nggi hodisalar", "Последние события", "Recent events"), 17f, true)
        evTitle.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val clear = tv(L("Tozalash", "Очистить", "Clear"), 13f, true, pal.accent)
        clear.isClickable = true
        clear.setPadding(dp(8), dp(6), dp(8), dp(6))
        clear.setOnClickListener {
            Store.clear(this)
            refreshEvents()
        }
        evHead.addView(evTitle)
        evHead.addView(clear)
        ev.addView(evHead)
        events = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ev.addView(events)
        col.addView(ev)

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

        // Pastki menyu
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(pal.card, 0, pal.line)
            setPadding(dp(6), dp(6), dp(6), dp(10))
        }
        val tabs = listOf("🛡️" to L("Himoya", "Защита", "Protection"), "🔎" to L("Tekshirish", "Проверка", "Check"), "📱" to L("Telefon", "Телефон", "Phone"), "⚙️" to L("Sozlamalar", "Настройки", "Settings"))
        val btns = ArrayList<LinearLayout>()
        for ((i, t) in tabs.withIndex()) {
            val b = LinearLayout(this)
            b.orientation = LinearLayout.VERTICAL
            b.gravity = Gravity.CENTER
            b.setPadding(dp(4), dp(6), dp(4), dp(6))
            b.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            b.isClickable = true
            b.setOnClickListener { selectTab(i) }
            b.addView(tv(t.first, 20f, false).apply { gravity = Gravity.CENTER })
            b.addView(tv(t.second, 11f, true, pal.sub).apply { gravity = Gravity.CENTER })
            nav.addView(b)
            btns.add(b)
        }
        tabBtns = btns

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
        }
        root.layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        outer.addView(root)
        outer.addView(nav)

        refreshShieldUi()
        refreshCallUi()
        refreshNetUi()
        selectTab(0, false)
        mainView = outer
        setContentView(outer)
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
        guardTitle.text = if (on) L("Himoya yoqilgan", "Защита включена", "Protection on") else L("Himoya o'chiq", "Защита выключена", "Protection off")
        guardSub.text = if (on) {
            L("Yangi ilova va yuklangan APK fayllar avtomatik tekshiriladi.", "Новые приложения и загруженные APK проверяются автоматически.", "New apps and downloaded APKs are scanned automatically.")
        } else {
            L("Yoqing: yangi o'rnatilgan ilovalar darrov tekshiriladi.", "Включите: новые приложения будут проверяться сразу.", "Turn on: newly installed apps are scanned right away.")
        }

        permBox.removeAllViews()
        if (on && !notifGranted()) {
            permBox.addView(button(L("🔔  Ogohlantirishlar uchun ruxsat", "🔔  Разрешение для уведомлений", "🔔  Permission for alerts"), Color.WHITE, 0xFF0F172A.toInt()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
            })
        }
        if (on && !hasFilesAccess(this)) {
            permBox.addView(button(L("📂  Yuklangan APK'larni kuzatish uchun ruxsat", "📂  Разрешение на отслеживание загруженных APK", "📂  Permission to watch downloaded APKs"), Color.WHITE, 0xFF0F172A.toInt()) {
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

    // ---------- Ilovalarni qulflash ----------

    private fun refreshAppLockUi() {
        if (!::alInfo.isInitialized) return
        val u = AppLock.hasUsage(this)
        val o = AppLock.hasOverlay(this)
        val n = AppLock.pkgs(this).size
        val ok = L("✅", "✅", "✅")
        val no = "⚠️"
        alInfo.text = L(
            (if (u) ok else no) + " Foydalanish statistikasi   " + (if (o) ok else no) + " Ustidan ko'rsatish\nQulflangan ilovalar: $n",
            (if (u) ok else no) + " Статистика использования   " + (if (o) ok else no) + " Поверх других\nЗаблокировано приложений: $n",
            (if (u) ok else no) + " Usage access   " + (if (o) ok else no) + " Display over apps\nLocked apps: $n"
        )
        alInfo.setTextColor(if (u && o) pal.good else pal.warn)
        updating = true
        alSwitch.isChecked = AppLock.enabled(this) && u && o
        updating = false
    }

    private fun showAppLockPicker() {
        results.removeAllViews()
        status.text = L("Ilovalar ro'yxati olinmoqda…", "Загружается список приложений…", "Loading the app list…")
        status.setTextColor(pal.ink)
        scrollToResults()
        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = try {
                pm.queryIntentActivities(launcher, 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .filter { it.first != packageName }
                    .distinctBy { it.first }
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                status.text = ""
                if (apps == null) {
                    status.text = L("Ro'yxatni olib bo'lmadi.", "Не удалось получить список.", "Couldn't load the list.")
                    return@runOnUiThread
                }
                val locked = AppLock.pkgs(this)
                val sorted = apps.sortedWith(compareBy({ if (it.first in locked) 0 else 1 }, { it.second.lowercase() }))
                val head = card(0)
                head.addView(
                    cardHeader(
                        "🔐", L("Qaysi ilovalar qulflansin?", "Какие приложения блокировать?", "Which apps to lock?"),
                        L("Yoqilgan ilovalar ochilganda ekran qulfi so'raladi.", "Включённые приложения запрашивают блокировку экрана при открытии.", "Enabled apps ask for the screen lock when opened."),
                        0xFFEA580C.toInt()
                    )
                )
                results.addView(head)
                for ((pkg, label) in sorted) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(14), dp(8), dp(14), dp(8))
                        background = shape(pal.card, 14, pal.line)
                        layoutParams = lp(8)
                    }
                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    }
                    col.addView(tv(label, 14f, true))
                    col.addView(tv(pkg, 10f, false, pal.sub))
                    val sw = Switch(this)
                    sw.isChecked = pkg in locked
                    sw.setOnCheckedChangeListener { _, on ->
                        AppLock.setLocked(this, pkg, on)
                        refreshAppLockUi()
                    }
                    row.addView(col)
                    row.addView(sw)
                    results.addView(row)
                }
            }
        }.start()
    }

    // ---------- Bosh ekran: hisoblagichlar, Wi-Fi, ignor ro'yxati, tanishtiruv ----------

    private class StatRow(val count: TextView, val badge: TextView)

    private fun addStat(parent: LinearLayout, icon: String, title: String, more: String, onMore: () -> Unit): StatRow {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = shape(pal.bg, 14, pal.line)
            layoutParams = lp(10)
            isClickable = true
            setOnClickListener { onMore() }
        }
        row.addView(tv(icon, 24f))
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(10) }
        }
        mid.addView(tv(title, 14f, true))
        val badge = tv("…", 11f, true, Color.WHITE)
        badge.setPadding(dp(8), dp(3), dp(8), dp(3))
        badge.background = shape(pal.sub, 8)
        badge.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) }
        mid.addView(badge)
        row.addView(mid)
        val count = tv("", 24f, true)
        count.setPadding(dp(8), 0, dp(8), 0)
        row.addView(count)
        row.addView(tv(more + " ›", 12f, true, pal.accent))
        parent.addView(row)
        return StatRow(count, badge)
    }

    private fun setBadge(h: StatRow, state: Int, text: String) {
        h.badge.text = text
        h.badge.background = shape(stateColor(state), 8)
    }

    private fun setStat(h: StatRow, n: Int) {
        h.count.text = "$n"
        when {
            n == 0 -> setBadge(h, 0, L("Xavfsiz", "Безопасно", "Safe"))
            n < 3 -> setBadge(h, 1, L("Diqqat!", "Внимание!", "Attention!"))
            else -> setBadge(h, 2, L("Xavfli!", "Опасно!", "Dangerous!"))
        }
        h.count.setTextColor(if (n == 0) pal.good else if (n < 3) pal.warn else pal.bad)
    }

    private fun refreshHome() {
        homeBox.removeAllViews()
        val scanned = prefs.getLong("lastScan", 0L) != 0L
        val danger = prefs.getInt("lastDanger", 0)
        val susp = prefs.getInt("lastSusp", 0)
        val details = L("Batafsil", "Подробнее", "Details")

        val c = card()
        c.addView(
            cardHeader(
                "📊", L("Xavf hisoblagichlari", "Счётчики угроз", "Threat counters"),
                L("Telefoningizdagi xavflar toifalar bo'yicha. Qatorni bosing.", "Угрозы на вашем телефоне по категориям. Нажмите на строку.", "Threats on your phone by category. Tap a row."),
                0xFF2563EB.toInt()
            )
        )
        val apps = addStat(c, "🦠", L("Ilovalar xavfi", "Угрозы приложений", "App threats"), L("Tekshirish", "Проверить", "Scan")) { scanInstalled() }
        if (scanned) {
            setStat(apps, danger + susp)
            if (danger > 0) setBadge(apps, 2, L("Xavfli!", "Опасно!", "Dangerous!"))
        } else {
            apps.count.text = "–"
            apps.badge.text = L("Tekshirilmagan", "Не проверено", "Not scanned")
        }
        val vuln = addStat(c, "🩺", L("Telefon zaifliklari", "Уязвимости телефона", "Phone weaknesses"), details) { showHealth() }
        val priv = addStat(c, "🕵️", L("Maxfiylik tahdidlari", "Угрозы приватности", "Privacy threats"), details) { showOrigin() }
        c.addView(
            tv(
                L(
                    "«Maxfiylik»: do'kondan tashqari o'rnatilgan va kamera, mikrofon, joylashuv, SMS yoki kontaktga ruxsati bor ilovalar soni.",
                    "«Приватность»: число приложений не из магазина с доступом к камере, микрофону, геолокации, SMS или контактам.",
                    "«Privacy»: number of apps installed outside the store that can access the camera, microphone, location, SMS or contacts."
                ),
                11f, false, pal.sub
            ).apply { layoutParams = lp(4) }
        )
        homeBox.addView(c)

        Thread {
            val v = try {
                HomeStats.vulnerabilities(this)
            } catch (e: Exception) {
                -1
            }
            val p = try {
                HomeStats.privacy(this)
            } catch (e: Exception) {
                -1
            }
            runOnUiThread {
                if (v >= 0) setStat(vuln, v)
                if (p >= 0) setStat(priv, p)
            }
        }.start()
    }

    private fun showWifi() {
        results.removeAllViews()
        status.text = ""
        val w = WifiCheck.run(this)
        val tone = stateColor(w.state)
        val head = card(0)
        head.background = gradBg(mix(pal.card, tone, 0.28f), pal.card, 22, mix(pal.line, tone, 0.6f))
        head.addView(tv("📶", 40f).apply { gravity = Gravity.CENTER })
        head.addView(tv(w.title, 17f, true, tone).apply { gravity = Gravity.CENTER })
        results.addView(head)
        for (l in w.lines) {
            val col = stateColor(l.state)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = shape(pal.card, 16, mix(pal.line, col, 0.5f))
                layoutParams = lp(10)
            }
            box.addView(tv(stateIcon(l.state) + "  " + l.text, 13f, l.state == 2, if (l.state == 0) pal.ink else col))
            results.addView(box)
        }
        results.addView(
            tv(
                L(
                    "Wi-Fi nomi va paroli o'qilmaydi va saqlanmaydi. Faqat shifrlash turi tekshiriladi.",
                    "Название и пароль Wi-Fi не читаются и не сохраняются. Проверяется только тип шифрования.",
                    "The Wi-Fi name and password are not read or stored. Only the encryption type is checked."
                ),
                11f, false, pal.sub
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            }
        )
        scrollToResults()
    }

    private fun showIgnored() {
        results.removeAllViews()
        status.text = ""
        val list = Trust.all(this).toList().sorted()
        val head = card(0)
        head.addView(
            cardHeader(
                "🤝", L("Ishonchli ilovalar", "Доверенные приложения", "Trusted apps"),
                L("«Ishonaman» deb belgilangan ilovalar tekshiruv natijasida ko'rsatilmaydi. Xohlasangiz, qaytarishingiz mumkin.",
                    "Приложения, отмеченные как доверенные, не показываются в результатах проверки. Их можно вернуть.",
                    "Apps marked as trusted are hidden from scan results. You can restore them."),
                0xFF16A34A.toInt()
            )
        )
        results.addView(head)
        if (list.isEmpty()) {
            results.addView(card().apply {
                addView(tv(L("Ro'yxat bo'sh.", "Список пуст.", "The list is empty."), 14f, true))
            })
        }
        val pm = packageManager
        for (pkg in list) {
            val label = try {
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            val box = card(8)
            box.addView(tv(label, 15f, true))
            box.addView(tv(pkg, 11f, false, pal.sub))
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = lp(6)
            }
            r.addView(smallBtn(L("Qaytarish", "Вернуть", "Restore")) {
                IgnoreList.remove(this, pkg)
                showIgnored()
            })
            box.addView(r)
            results.addView(box)
        }
        scrollToResults()
    }

    private fun showOnboarding() {
        var page = 0
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(pal.bg)
            setPadding(dp(28), dp(40), dp(28), dp(28))
        }
        fun endOnb() {
            prefs.edit().putBoolean("onb", true).apply()
            mainView?.let { setContentView(it) }
        }
        fun render() {
            box.removeAllViews()
            val skip = tv(L("O'tkazib yuborish", "Пропустить", "Skip"), 13f, true, pal.accent)
            skip.gravity = Gravity.END
            skip.setPadding(dp(8), dp(8), dp(8), dp(8))
            skip.isClickable = true
            skip.setOnClickListener { endOnb() }
            skip.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            if (page < 2) box.addView(skip) else box.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(36)) })

            val icon = TextView(this).apply {
                text = listOf("🛡️", "🔔", "✅")[page]
                textSize = 54f
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt())
                ).apply { cornerRadius = dp(36).toFloat() }
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply { topMargin = dp(24) }
            }
            box.addView(icon)
            val title = when (page) {
                0 -> L("Xush kelibsiz!", "Добро пожаловать!", "Welcome!")
                1 -> L("Fon rejimida himoya", "Защита в фоне", "Background protection")
                else -> L("Ruxsatlarni bering", "Выдайте разрешения", "Grant permissions")
            }
            val body = when (page) {
                0 -> L(
                    "Qo'riqchi Pro telefoningizni firibgar ilova, havola, xabar va qo'ng'iroqlardan himoya qiladi. Barcha tekshiruv telefoningizning o'zida bajariladi.",
                    "Qo'riqchi Pro защищает телефон от мошеннических приложений, ссылок, сообщений и звонков. Вся проверка выполняется на самом телефоне.",
                    "Qo'riqchi Pro protects your phone from scam apps, links, messages and calls. All checks run on your phone itself."
                )
                1 -> L(
                    "24/7 himoya ishlashi uchun bir necha ruxsat kerak: bildirishnomalar va fonda ishlash. Har bir ruxsat nima uchun kerakligi ilova ichida yozilgan.",
                    "Для защиты 24/7 нужны несколько разрешений: уведомления и работа в фоне. Зачем нужно каждое, написано в приложении.",
                    "Round-the-clock protection needs a few permissions: notifications and background work. The app explains why each one is needed."
                )
                else -> L(
                    "Hozir ruxsat bering yoki keyinroq «Himoya» bo'limidan yoqing.",
                    "Дайте разрешения сейчас или включите их позже на вкладке «Защита».",
                    "Grant permissions now or enable them later in the Protection tab."
                )
            }
            box.addView(tv(title, 24f, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(8))
            })
            box.addView(tv(body, 14f, false, pal.sub).apply { gravity = Gravity.CENTER })
            if (page == 0) {
                val langRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(20) }
                }
                for ((code, name) in listOf("uz" to "O'zbekcha", "ru" to "Русский", "en" to "English")) {
                    langRow.addView(smallBtn(name) {
                        prefs.edit().putString("lang", code).apply()
                        recreate()
                    })
                }
                box.addView(langRow)
            }
            if (page == 2) {
                box.addView(button(L("🔔  Bildirishnomalarga ruxsat", "🔔  Разрешение на уведомления", "🔔  Allow notifications"), pal.accent) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 5)
                    } else {
                        toast(L("Bu Android versiyasida shart emas", "На этой версии Android не требуется", "Not needed on this Android version"))
                    }
                })
                box.addView(button(L("🔋  Fonda ishlash sozlamalari", "🔋  Настройки работы в фоне", "🔋  Background settings"), 0xFF0EA5E9.toInt()) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                })
            }
            box.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) })
            val dots = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
            }
            for (i in 0..2) {
                dots.addView(tv("●", 14f, false, if (i == page) pal.accent else pal.line).apply {
                    setPadding(dp(4), 0, dp(4), 0)
                })
            }
            box.addView(dots)
            box.addView(
                button(if (page < 2) L("Davom etish", "Продолжить", "Continue") else L("Boshlash", "Начать", "Start"), pal.accent) {
                    if (page < 2) {
                        page++
                        render()
                    } else {
                        endOnb()
                    }
                }
            )
        }
        render()
        setContentView(ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isFillViewport = true
            addView(box)
        })
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
        refreshHome()
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
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
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
                selectTab(1, false)
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
