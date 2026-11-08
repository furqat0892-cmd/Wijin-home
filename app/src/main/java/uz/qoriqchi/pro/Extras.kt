package uz.qoriqchi.pro

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// =====================================================================
// 1. Telefon sog'lig'i
// =====================================================================

/** state: 0 yaxshi, 1 ogohlantirish, 2 xavfli, 3 aniqlab bo'lmadi */
data class HealthCheck(
    val title: String,
    val state: Int,
    val detail: String,
    val fix: String? = null
)

data class HealthResult(val score: Int, val checks: List<HealthCheck>)

object PhoneHealth {

    private fun appLabel(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun isSystemPkg(ctx: Context, pkg: String): Boolean = try {
        (ctx.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: Exception) {
        false
    }

    private fun pkgsOf(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<String>()
        for (part in raw.split(':')) {
            val s = part.trim()
            if (s.isEmpty()) continue
            val p = s.substringBefore('/')
            if (p.isNotEmpty() && p !in out) out.add(p)
        }
        return out
    }

    private fun securePatchMonths(): Int? {
        return try {
            val s = Build.VERSION.SECURITY_PATCH
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s) ?: return null
            val days = (System.currentTimeMillis() - d.time) / 86400000L
            (days / 30L).toInt()
        } catch (e: Exception) {
            null
        }
    }

    /** Foydalanuvchi o'zi qo'ygan (do'kondan kelmagan) ilovalar bormi: shunda xavfliroq. */
    private fun anyUntrusted(ctx: Context, pkgs: List<String>): Boolean {
        for (p in pkgs) if (!Origin.isStore(Origin.installerOf(ctx, p))) return true
        return false
    }

    fun run(ctx: Context): HealthResult {
        val cr = ctx.contentResolver
        val checks = ArrayList<HealthCheck>()
        var penalty = 0

        fun add(c: HealthCheck, bad: Int, warn: Int) {
            checks.add(c)
            if (c.state == 2) penalty += bad
            if (c.state == 1) penalty += warn
        }

        // Ekran qulfi
        val kg = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (kg != null) {
            if (kg.isDeviceSecure) {
                add(HealthCheck("Ekran qulfi", 0, "PIN, parol yoki barmoq izi o'rnatilgan."), 30, 0)
            } else {
                add(
                    HealthCheck(
                        "Ekran qulfi", 2,
                        "Telefon qulfsiz: yo'qolsa yoki qo'lga tushsa, hamma narsa ochiq bo'ladi.",
                        Settings.ACTION_SECURITY_SETTINGS
                    ), 30, 0
                )
            }
        }

        // Xavfsizlik yangilanishi
        val months = securePatchMonths()
        if (months == null) {
            add(HealthCheck("Xavfsizlik yangilanishi", 3, "Sanani aniqlab bo'lmadi."), 0, 0)
        } else {
            val patch = Build.VERSION.SECURITY_PATCH
            when {
                months <= 8 -> add(HealthCheck("Xavfsizlik yangilanishi", 0, "So'nggi yangilanish: $patch."), 0, 0)
                months <= 14 -> add(
                    HealthCheck(
                        "Xavfsizlik yangilanishi", 1,
                        "So'nggi yangilanish: $patch (taxminan $months oy oldin). Yangilanish bor-yo'qligini tekshiring.",
                        Settings.ACTION_DEVICE_INFO_SETTINGS
                    ), 15, 6
                )
                else -> add(
                    HealthCheck(
                        "Xavfsizlik yangilanishi", 2,
                        "So'nggi yangilanish: $patch (taxminan $months oy oldin). Telefon eskirgan, ma'lum teshiklar yopilmagan bo'lishi mumkin.",
                        Settings.ACTION_DEVICE_INFO_SETTINGS
                    ), 15, 6
                )
            }
        }

        // Play Protect
        val pp = try {
            Settings.Secure.getInt(cr, "package_verifier_user_consent", 0)
        } catch (e: Exception) {
            0
        }
        when (pp) {
            1 -> add(HealthCheck("Play Protect", 0, "Yoqilgan: Google ilovalarni tekshirib turadi."), 20, 0)
            -1 -> add(
                HealthCheck(
                    "Play Protect", 2,
                    "O'chirilgan. Play Market → profil rasmi → Play Protect → sozlamalardan yoqing."
                ), 20, 0
            )
            else -> add(
                HealthCheck(
                    "Play Protect", 3,
                    "Holatini aniqlab bo'lmadi. Play Market → profil → Play Protect ichidan o'zingiz ko'ring."
                ), 0, 0
            )
        }

        // USB nosozliklarni tuzatish
        val adb = try {
            Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
        } catch (e: Exception) {
            0
        }
        if (adb == 1) {
            add(
                HealthCheck(
                    "USB nosozlik rejimi", 1,
                    "Yoqilgan. Kerak bo'lmasa, o'chiring: kompyuter orqali telefonga kirish yo'li ochiq qoladi.",
                    Settings.ACTION_DEVICE_INFO_SETTINGS
                ), 12, 8
            )
        } else {
            add(HealthCheck("USB nosozlik rejimi", 0, "O'chirilgan."), 0, 0)
        }

        // Maxsus imkoniyatlar (Accessibility)
        val acc = pkgsOf(
            try {
                Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            } catch (e: Exception) {
                null
            }
        ).filter { it != ctx.packageName && !isSystemPkg(ctx, it) }
        if (acc.isEmpty()) {
            add(HealthCheck("Maxsus imkoniyatlar", 0, "Begona ilovaga ekranni o'qish ruxsati berilmagan."), 0, 0)
        } else {
            val names = acc.joinToString(", ") { appLabel(ctx, it) }
            val risky = anyUntrusted(ctx, acc)
            val extra = if (risky) " Do'kondan kelmaganlari bor, tanimasangiz o'chiring." else ""
            val st = if (risky) 2 else 1
            add(
                HealthCheck(
                    "Maxsus imkoniyatlar", st,
                    "Bu ruxsat bilan ilova ekraningizni o'qiy va tugmalarni o'zi bosa oladi. Zararli dasturlar eng ko'p shuni so'raydi. Yoqilgan: $names.$extra",
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                ), 25, 8
            )
        }

        // Bildirishnomalarni o'qish (SMS-kodlarni o'g'irlash uchun ishlatiladi)
        val nl = pkgsOf(
            try {
                Settings.Secure.getString(cr, "enabled_notification_listeners")
            } catch (e: Exception) {
                null
            }
        ).filter { it != ctx.packageName && !isSystemPkg(ctx, it) }
        if (nl.isEmpty()) {
            add(HealthCheck("Bildirishnomalarni o'qish", 0, "Begona ilovaga xabarlarni o'qish ruxsati berilmagan."), 0, 0)
        } else {
            val names = nl.joinToString(", ") { appLabel(ctx, it) }
            val risky = anyUntrusted(ctx, nl)
            val extra = if (risky) " Do'kondan kelmaganlari bor, tanimasangiz o'chiring." else ""
            val st = if (risky) 2 else 1
            add(
                HealthCheck(
                    "Bildirishnomalarni o'qish", st,
                    "Bu ruxsat bilan ilova kelgan xabarlarni, shu jumladan SMS-kodlarni o'qiy oladi. Yoqilgan: $names.$extra",
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                ), 20, 6
            )
        }

        // Qurilma administratorlari
        val adminPkgs: List<String> = try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val list = dpm?.activeAdmins
            list?.map { it.packageName } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val admins = adminPkgs.filter { it != ctx.packageName && !isSystemPkg(ctx, it) }
        if (admins.isEmpty()) {
            add(HealthCheck("Qurilma administratorlari", 0, "Begona administrator ilova yo'q."), 0, 0)
        } else {
            val names = admins.joinToString(", ") { appLabel(ctx, it) }
            val risky = anyUntrusted(ctx, admins)
            val st = if (risky) 2 else 1
            add(
                HealthCheck(
                    "Qurilma administratorlari", st,
                    "Administrator ilovani oddiy usulda o'chirib bo'lmaydi, zararli dasturlar shundan foydalanadi. Faol: $names.",
                    Settings.ACTION_SECURITY_SETTINGS
                ), 15, 5
            )
        }

        // Root
        val su = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk")
            .any { File(it).exists() }
        val testKeys = Build.TAGS?.contains("test-keys") == true
        if (su || testKeys) {
            add(
                HealthCheck(
                    "Root / o'zgartirilgan tizim", 1,
                    "Telefon tizimi o'zgartirilgan ko'rinadi. Bunday telefonda himoya zaifroq bo'ladi."
                ), 10, 10
            )
        } else {
            add(HealthCheck("Root / o'zgartirilgan tizim", 0, "Belgi topilmadi."), 0, 0)
        }

        val score = (100 - penalty).coerceIn(0, 100)
        return HealthResult(score, checks)
    }
}

// =====================================================================
// 2. Ilova kelib chiqishi
// =====================================================================

/** kind: 0 rasmiy do'kon, 1 messenjer/brauzer/fayl orqali, 2 manba noma'lum */
data class AppOrigin(
    val label: String,
    val pkg: String,
    val kind: Int,
    val via: String,
    val installedAt: Long
)

object Origin {

    private val stores = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.mipicks",
        "com.heytap.market",
        "com.amazon.venezia",
        "org.fdroid.fdroid"
    )

    private val packageInstallers = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    private val names = mapOf(
        "com.android.vending" to "Google Play",
        "com.sec.android.app.samsungapps" to "Samsung Galaxy Store",
        "com.huawei.appmarket" to "Huawei AppGallery",
        "com.xiaomi.mipicks" to "Xiaomi GetApps",
        "com.heytap.market" to "OPPO App Market",
        "com.amazon.venezia" to "Amazon Appstore",
        "org.fdroid.fdroid" to "F-Droid",
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram",
        "org.thunderdog.challegram" to "Telegram X",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.android.chrome" to "Chrome brauzeri",
        "com.mi.globalbrowser" to "Mi brauzeri",
        "com.android.browser" to "Brauzer",
        "org.mozilla.firefox" to "Firefox",
        "com.opera.browser" to "Opera",
        "com.opera.mini.native" to "Opera Mini",
        "com.sec.android.app.sbrowser" to "Samsung brauzeri",
        "com.UCMobile.intl" to "UC Browser",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.google.android.apps.docs" to "Google Drive",
        "com.google.android.gm" to "Gmail",
        "com.android.documentsui" to "Fayl menejeri",
        "com.google.android.documentsui" to "Fayl menejeri",
        "com.mi.android.globalFileexplorer" to "Mi Fayllar",
        "com.google.android.apps.nbu.files" to "Files by Google",
        "com.android.shell" to "kompyuter (ADB)"
    )

    fun isStore(installer: String?): Boolean = installer != null && installer in stores

    @Suppress("DEPRECATION")
    private fun legacyInstaller(ctx: Context, pkg: String): String? = try {
        ctx.packageManager.getInstallerPackageName(pkg)
    } catch (e: Exception) {
        null
    }

    fun installerOf(ctx: Context, pkg: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                ctx.packageManager.getInstallSourceInfo(pkg).installingPackageName
            } else {
                legacyInstaller(ctx, pkg)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun initiatorOf(ctx: Context, pkg: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                ctx.packageManager.getInstallSourceInfo(pkg).initiatingPackageName
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun nameOf(ctx: Context, pkg: String): String {
        val known = names[pkg]
        if (known != null) return known
        return try {
            val pm = ctx.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun list(ctx: Context): List<AppOrigin> {
        val pm = ctx.packageManager
        val out = ArrayList<AppOrigin>()
        for (p in pm.getInstalledPackages(0)) {
            val ai = p.applicationInfo ?: continue
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            if (p.packageName == ctx.packageName) continue
            val inst = installerOf(ctx, p.packageName)
            val init = initiatorOf(ctx, p.packageName)
            val kind: Int
            val via: String
            if (inst != null && inst in stores) {
                kind = 0
                via = nameOf(ctx, inst)
            } else if (inst == null && init == null) {
                kind = 2
                via = "Manba noma'lum"
            } else {
                kind = 1
                val who = if (init != null && init != inst) init else inst
                via = if (who == null || who in packageInstallers) {
                    "APK fayldan qo'lda o'rnatilgan"
                } else {
                    nameOf(ctx, who) + " orqali kelgan APK"
                }
            }
            out.add(
                AppOrigin(
                    ai.loadLabel(pm).toString(),
                    p.packageName,
                    kind,
                    via,
                    p.firstInstallTime
                )
            )
        }
        return out.sortedWith(
            compareBy<AppOrigin> { if (it.kind == 0) 1 else 0 }.thenByDescending { it.installedAt }
        )
    }
}

// =====================================================================
// 3. Karantin
// =====================================================================

object Quarantine {
    private fun prefs(c: Context) = c.getSharedPreferences("qp", Context.MODE_PRIVATE)

    fun all(c: Context): Set<String> = HashSet(prefs(c).getStringSet("quar", emptySet()) ?: emptySet())

    fun has(c: Context, pkg: String): Boolean = pkg in all(c)

    fun add(c: Context, pkg: String) {
        val s = HashSet(all(c))
        s.add(pkg)
        prefs(c).edit().putStringSet("quar", s).apply()
    }

    fun remove(c: Context, pkg: String) {
        val s = HashSet(all(c))
        s.remove(pkg)
        prefs(c).edit().putStringSet("quar", s).apply()
    }

    private fun installed(c: Context, pkg: String): Boolean = try {
        c.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** O'chirib yuborilgan ilovalarni ro'yxatdan olib tashlab, qolganlarini qaytaradi. */
    fun active(c: Context): List<String> {
        val a = all(c)
        val keep = a.filter { installed(c, it) }
        if (keep.size != a.size) prefs(c).edit().putStringSet("quar", HashSet(keep)).apply()
        return keep
    }

    private val risky = linkedMapOf(
        "android.permission.READ_SMS" to "SMS o'qish",
        "android.permission.RECEIVE_SMS" to "SMS qabul qilish",
        "android.permission.SEND_SMS" to "SMS yuborish",
        "android.permission.READ_CONTACTS" to "Kontaktlar",
        "android.permission.READ_CALL_LOG" to "Qo'ng'iroqlar tarixi",
        "android.permission.CAMERA" to "Kamera",
        "android.permission.RECORD_AUDIO" to "Mikrofon",
        "android.permission.ACCESS_FINE_LOCATION" to "Aniq joylashuv"
    )

    /** Ilovaga hozir berilgan xavfli ruxsatlar. */
    fun grantedRisky(c: Context, pkg: String): List<String> {
        val out = ArrayList<String>()
        try {
            val pi = c.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            val req = pi.requestedPermissions ?: return out
            val fl = pi.requestedPermissionsFlags ?: return out
            for (i in req.indices) {
                val name = risky[req[i]] ?: continue
                if (i < fl.size && (fl[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) out.add(name)
            }
        } catch (e: Exception) {
            // ilova topilmasa, bo'sh ro'yxat
        }
        return out
    }
}

// =====================================================================
// 4. Firibgar xabar tekshiruvi
// =====================================================================

object ScamText {

    private class Rule(val points: Int, val text: String, val words: List<String>)

    private val rules = listOf(
        Rule(
            25, "Karta raqami, SMS-kod yoki parolni so'rayapti",
            listOf(
                "sms kod", "sms-kod", "tasdiqlash kodi", "tasdiqlovchi kod", "kodni ayting", "kodni yuboring",
                "kodni yozing", "karta raqam", "karta ma'lumot", "cvv", "cvc", "amal qilish muddati",
                "parolni", "pin kod", "pin-kod", "код из смс", "код подтверждения", "номер карты",
                "срок действия карты"
            )
        ),
        Rule(
            15, "Shoshiltirish: «darhol», «bugun oxirgi» kabi bosim",
            listOf(
                "shoshiling", "zudlik bilan", "darhol", "hoziroq", "bugun oxirgi", "oxirgi imkoniyat",
                "soat ichida", "vaqt tugayapti", "срочно", "немедленно", "последний шанс", "в течение 24"
            )
        ),
        Rule(
            20, "Yutuq yoki sovg'a va'dasi",
            listOf(
                "yutib oldingiz", "yutuq", "sovrin", "tabriklaymiz", "sovg'a", "bepul pul", "mukofot",
                "приз", "выиграли", "выигрыш", "поздравляем", "лотерея", "lotereya"
            )
        ),
        Rule(
            20, "Hisob bloklanishi yoki xavfsizlik bahonasi",
            listOf(
                "bloklandi", "bloklanadi", "bloklangan", "cheklandi", "muzlatildi", "shubhali operatsiya",
                "shubhali tranzaksiya", "заблокирован", "подозрительн"
            )
        ),
        Rule(
            15, "Bank, to'lov tizimi yoki davlat idorasi nomidan yozyapti",
            listOf(
                "uzcard", "humo", "payme", "paynet", "xalq banki", "ipoteka bank", "kapitalbank", "agrobank",
                "anorbank", "bank xodimi", "bank nomidan", "soliq", "prokuratura", "ichki ishlar", "jarima",
                "my.gov", "mygov", "банк", "налог", "прокуратура", "штраф"
            )
        ),
        Rule(
            20, "Tez pul topish, kredit yoki investitsiya taklifi",
            listOf(
                "tez pul", "pul ishlash", "investitsiya", "kripto", "bitcoin", "usdt", "foizsiz kredit",
                "kredit rasmiylashtir", "ish taklifi", "uydan turib ishlash", "oylik daromad",
                "инвестиц", "крипт", "заработок"
            )
        ),
        Rule(
            25, "Ilova (APK) o'rnatishga undayapti",
            listOf(
                ".apk", "ilovani o'rnating", "ilovani yuklab oling", "faylni o'rnating",
                "установите приложение", "скачайте приложение"
            )
        ),
        Rule(
            20, "Qarindosh nomidan pul so'rash (yangi raqam, telefon buzildi)",
            listOf(
                "telefonim buzildi", "yangi raqamim", "raqamim o'zgardi", "pul o'tkazib bering",
                "pulni tashlab bering", "kartaga pul tashlang", "новый номер", "переведи деньги", "скинь деньги"
            )
        ),
        Rule(
            10, "Telegram yoki boshqa raqamga o'tishga chaqiryapti",
            listOf(
                "t.me/", "telegramga yozing", "whatsappga yozing", "shu raqamga qo'ng'iroq", "bog'laning:"
            )
        ),
        Rule(
            10, "Oldindan to'lov yoki komissiya so'rayapti",
            listOf(
                "oldindan to'lov", "avval to'lov", "komissiya to'lang", "yetkazib berish uchun to'lang",
                "предоплата", "комиссия"
            )
        )
    )

    private val urlRe = Regex("(https?://\\S+|www\\.\\S+)", RegexOption.IGNORE_CASE)

    private fun norm(s: String): String =
        s.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace('ʻ', '\'')
            .replace('ʼ', '\'')
            .replace('‘', '\'')
            .replace('`', '\'')
            .replace('´', '\'')

    fun analyze(raw: String): Report {
        val text = norm(raw)
        val f = ArrayList<Finding>()
        var total = 0
        val hit = HashSet<Int>()

        for ((idx, r) in rules.withIndex()) {
            if (r.words.any { text.contains(it) }) {
                f.add(Finding(r.points, r.text))
                total += r.points
                hit.add(idx)
            }
        }

        // Klassik kombinatsiya: kod so'rash + bosim yoki bloklash qo'rquvi
        if (0 in hit && (1 in hit || 3 in hit)) {
            f.add(Finding(15, "Kod so'rash va bosim birga: bu klassik firibgarlik usuli"))
            total += 15
        }

        // Havolalar
        val urls = urlRe.findAll(raw).map { it.value }.distinct().take(3).toList()
        for (u in urls) {
            val lr = try {
                LinkCheck.analyze(u)
            } catch (e: Exception) {
                null
            }
            if (lr != null) {
                val add = minOf(30, lr.score / 2)
                if (add > 0) {
                    f.add(Finding(add, "Havola xavfli ko'rinadi: ${u.take(50)}"))
                    total += add
                    lr.findings.filter { it.points > 0 }.take(2).forEach {
                        f.add(Finding(0, "   ↳ ${it.text}"))
                    }
                }
            }
        }
        if (urls.isNotEmpty() && urls.none { u -> f.any { it.text.contains(u.take(50)) } }) {
            f.add(Finding(0, "Matnda havola bor, lekin alohida xavf belgisi topilmadi"))
        }

        if (f.isEmpty()) f.add(Finding(0, "Ma'lum firibgarlik iboralari topilmadi"))
        return Report("Xabar matni", "matn", minOf(100, total), f)
    }
}

// =====================================================================
// 5. Radar (tekshiruv paytida)
// =====================================================================

/** Aylanuvchi radar: tekshirilgan har bir ilova nuqta bo'lib paydo bo'ladi. Vaqtga asoslangan chizish. */
class RadarView(
    ctx: Context,
    private val accent: Int,
    private val line: Int
) : View(ctx) {

    private class Blip(val ang: Float, val rad: Float, val born: Long)

    private val blips = ArrayList<Blip>()
    private var running = false
    private var t0 = 0L
    private var sweep = 0f
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun start() {
        running = true
        t0 = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        invalidate()
    }

    fun addBlip(seed: String) {
        val h = seed.hashCode()
        val ang = ((h and 0x7fffffff) % 360).toFloat()
        val rad = 0.22f + (((h ushr 9) and 0xff) / 255f) * 0.68f
        blips.add(Blip(ang, rad, SystemClock.uptimeMillis()))
        if (blips.size > 90) blips.removeAt(0)
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (running) sweep = ((now - t0) % 2200L) / 2200f * 360f
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f - 4f * d
        val rgb = accent and 0x00FFFFFF

        p.style = Paint.Style.FILL
        p.color = (line and 0x00FFFFFF) or 0x44000000
        canvas.drawCircle(cx, cy, r, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = rgb or 0x66000000
        for (k in 1..4) canvas.drawCircle(cx, cy, r * k / 4f, p)
        canvas.drawLine(cx - r, cy, cx + r, cy, p)
        canvas.drawLine(cx, cy - r, cx, cy + r, p)

        val box = RectF(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.FILL
        for (k in 0 until 28) {
            val a = (0x77 * (1f - k / 28f)).toInt()
            p.color = rgb or (a shl 24)
            canvas.drawArc(box, sweep - k * 2.5f - 2.5f, 2.7f, true, p)
        }

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * d
        p.color = accent
        val rad = Math.toRadians(sweep.toDouble())
        canvas.drawLine(
            cx, cy,
            cx + (r * cos(rad)).toFloat(),
            cy + (r * sin(rad)).toFloat(),
            p
        )

        p.style = Paint.Style.FILL
        for (b in blips) {
            val age = (now - b.born).coerceAtLeast(0L)
            val pulse = if (age < 700L) 1f + (1f - age / 700f) * 1.4f else 1f
            val br = Math.toRadians(b.ang.toDouble())
            val bx = cx + (r * b.rad * cos(br)).toFloat()
            val by = cy + (r * b.rad * sin(br)).toFloat()
            p.color = 0xFF22D3EE.toInt()
            canvas.drawCircle(bx, by, 3.2f * d * pulse, p)
        }

        p.color = accent
        canvas.drawCircle(cx, cy, 4f * d, p)

        if (running) postInvalidateOnAnimation()
    }
}
