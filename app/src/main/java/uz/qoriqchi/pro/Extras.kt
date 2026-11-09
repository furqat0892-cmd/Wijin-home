package uz.qoriqchi.pro

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.system.OsConstants
import android.telecom.Call
import android.telecom.CallScreeningService
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
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

        // Masofadan boshqarish ilovalari
        val remoteApps = try {
            val pm = ctx.packageManager
            val list = ArrayList<String>()
            var untrusted = false
            for (p in pm.getInstalledPackages(0)) {
                val ai = p.applicationInfo ?: continue
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                if (p.packageName == ctx.packageName) continue
                val lb = ai.loadLabel(pm).toString()
                if (RemoteGuard.isRemote(p.packageName, lb.lowercase(Locale.ROOT))) {
                    list.add(lb)
                    if (!Origin.isStore(Origin.installerOf(ctx, p.packageName))) untrusted = true
                }
            }
            Pair(list, untrusted)
        } catch (e: Exception) {
            Pair(ArrayList<String>(), false)
        }
        if (remoteApps.first.isEmpty()) {
            add(HealthCheck("Masofadan boshqarish ilovalari", 0, "AnyDesk, TeamViewer, RustDesk kabi ilovalar topilmadi."), 0, 0)
        } else {
            val st = if (remoteApps.second) 2 else 1
            add(
                HealthCheck(
                    "Masofadan boshqarish ilovalari", st,
                    "Topildi: ${remoteApps.first.joinToString(", ")}. Firibgarlar «bank xodimi» nomidan shu ilovalarni o'rnatdirib, telefonni boshqaradi. O'zingiz o'rnatmagan bo'lsangiz, o'chiring."
                ), 15, 8
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

    private val phoneRe = Regex("\\+?\\d[\\d\\s\\-()]{7,16}\\d")

    fun analyze(raw: String): Report = analyze(raw, null, null)

    fun analyze(raw: String, ctx: Context?, sender: String?): Report {
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

        // Umumiy xavf bazasi: ibora va raqamlar
        if (ctx != null) {
            for (ph in Blocklist.phraseHits(ctx, text).take(2)) {
                f.add(Finding(25, "Umumiy xavf bazasidagi ibora: «$ph»"))
                total += 25
            }
            val nums = LinkedHashSet<String>()
            phoneRe.findAll(raw).forEach { nums.add(it.value) }
            if (!sender.isNullOrBlank()) nums.add(sender)
            for (n in nums) {
                if (Blocklist.hasPhone(ctx, n)) {
                    f.add(Finding(40, "Raqam firibgarlikda ko'rilgan: ${n.trim().take(20)}"))
                    total += 40
                    break
                }
            }
        }

        // Havolalar
        val urls = urlRe.findAll(raw).map { it.value }.distinct().take(3).toList()
        for (u in urls) {
            val bhit = if (ctx != null) Blocklist.domainHit(ctx, u) else null
            if (bhit != null) {
                f.add(Finding(50, "Havola xavf bazasida bor: $bhit"))
                total += 50
            }
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

// =====================================================================
// 6. Umumiy xavf bazasi (GitHub'dan yangilanadi)
// =====================================================================

object Blocklist {
    private const val RAW_URL = "https://raw.githubusercontent.com/furqat0892-cmd/Wijin-home/main/blocklist.json"

    private class BlData(
        val phones: Set<String>,
        val domains: Set<String>,
        val hashes: Set<String>,
        val phrases: List<String>,
        val updated: String
    )

    @Volatile
    private var cache: BlData? = null

    private fun prefs(c: Context) = c.getSharedPreferences("qp", Context.MODE_PRIVATE)

    private fun digits9(s: String): String {
        val d = s.filter { it.isDigit() }
        return if (d.length >= 7) d.takeLast(9) else ""
    }

    private fun hostOf(url: String): String =
        url.trim().lowercase(Locale.ROOT)
            .removePrefix("http://").removePrefix("https://").removePrefix("www.")
            .substringBefore('/').substringBefore('?').substringBefore(':')

    private fun parse(json: String): BlData? {
        return try {
            val o = JSONObject(json)
            fun strings(name: String, f: (String) -> String): Set<String> {
                val a = o.optJSONArray(name) ?: return emptySet()
                val out = HashSet<String>()
                for (i in 0 until a.length()) {
                    val v = f(a.optString(i, ""))
                    if (v.isNotEmpty()) out.add(v)
                }
                return out
            }
            val phrases = ArrayList<String>()
            val pa = o.optJSONArray("phrases")
            if (pa != null) {
                for (i in 0 until pa.length()) {
                    val v = pa.optString(i, "").trim().lowercase(Locale.ROOT)
                    if (v.length >= 4) phrases.add(v)
                }
            }
            BlData(
                strings("phones") { digits9(it) },
                strings("domains") { hostOf(it) },
                strings("apk_sha256") { it.trim().lowercase(Locale.ROOT) },
                phrases,
                o.optString("updated", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun data(c: Context): BlData? {
        val cur = cache
        if (cur != null) return cur
        val raw = prefs(c).getString("bl_json", null) ?: return null
        val d = parse(raw)
        cache = d
        return d
    }

    private fun localPhones(c: Context): Set<String> =
        HashSet(prefs(c).getStringSet("bl_local", emptySet()) ?: emptySet())

    /** Foydalanuvchi o'zi qo'shgan raqam. Raqam noto'g'ri bo'lsa false. */
    fun addLocalPhone(c: Context, number: String): Boolean {
        val k = digits9(number)
        if (k.isEmpty()) return false
        val s = HashSet(localPhones(c))
        s.add(k)
        prefs(c).edit().putStringSet("bl_local", s).apply()
        return true
    }

    fun hasPhone(c: Context, number: String): Boolean {
        val k = digits9(number)
        if (k.isEmpty()) return false
        if (k in localPhones(c)) return true
        val d = data(c) ?: return false
        return k in d.phones
    }

    fun hasHash(c: Context, sha: String?): Boolean {
        if (sha == null) return false
        val d = data(c) ?: return false
        return sha.lowercase(Locale.ROOT) in d.hashes
    }

    fun domainHit(c: Context, url: String): String? {
        val d = data(c) ?: return null
        val h = hostOf(url)
        if (h.isEmpty()) return null
        for (x in d.domains) {
            if (h == x || h.endsWith(".$x")) return x
        }
        return null
    }

    fun phraseHits(c: Context, normText: String): List<String> {
        val d = data(c) ?: return emptyList()
        return d.phrases.filter { normText.contains(it) }
    }

    fun info(c: Context): String {
        val own = localPhones(c).size
        val d = data(c) ?: return "Baza hali yuklanmagan. «Bazani yangilash» ni bosing.  •  Sizning raqamlaringiz: $own"
        return "Yangilangan: ${d.updated}  •  ${d.phones.size} raqam, ${d.domains.size} havola, ${d.hashes.size} fayl, ${d.phrases.size} ibora  •  Sizning raqamlaringiz: $own"
    }

    /** Internetdan yuklaydi. Natija matni qaytariladi. Tarmoq oqimida chaqiring. */
    fun refresh(c: Context): String {
        prefs(c).edit().putLong("bl_try", System.currentTimeMillis()).apply()
        return try {
            val conn = URL(RAW_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            if (code != 200) {
                "Yangilab bo'lmadi (server javobi: $code)"
            } else {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val d = if (text.length > 1_000_000) null else parse(text)
                if (d == null) {
                    "Baza fayli noto'g'ri"
                } else {
                    prefs(c).edit()
                        .putString("bl_json", text)
                        .putLong("bl_time", System.currentTimeMillis())
                        .apply()
                    cache = d
                    "Baza yangilandi"
                }
            }
        } catch (e: Exception) {
            "Internet yo'q yoki baza topilmadi"
        }
    }

    /** Eskirgan bo'lsa, fonda yangilaydi. */
    fun maybeRefresh(c: Context) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (now - p.getLong("bl_time", 0L) < 20L * 3600 * 1000) return
        if (now - p.getLong("bl_try", 0L) < 3600L * 1000) return
        Thread { refresh(c) }.start()
    }
}

// =====================================================================
// 7. Oila himoyasi (Telegram bot orqali ogohlantirish)
// =====================================================================

object Family {
    private fun prefs(c: Context) = c.getSharedPreferences("qp", Context.MODE_PRIVATE)

    fun token(c: Context): String = prefs(c).getString("fam_token", "") ?: ""
    fun chat(c: Context): String = prefs(c).getString("fam_chat", "") ?: ""
    fun name(c: Context): String = prefs(c).getString("fam_name", "") ?: ""

    fun isOn(c: Context): Boolean =
        prefs(c).getBoolean("fam_on", false) && token(c).isNotBlank() && chat(c).isNotBlank()

    fun save(c: Context, token: String, chat: String, name: String) {
        prefs(c).edit()
            .putString("fam_token", token.trim())
            .putString("fam_chat", chat.trim())
            .putString("fam_name", name.trim())
            .putBoolean("fam_on", true)
            .apply()
    }

    fun disable(c: Context) {
        prefs(c).edit().putBoolean("fam_on", false).apply()
    }

    fun validToken(t: String): Boolean = Regex("^\\d{5,15}:[A-Za-z0-9_-]{20,60}$").matches(t.trim())

    private fun post(token: String, chat: String, text: String): String? {
        return try {
            val conn = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            val body = "chat_id=" + URLEncoder.encode(chat, "UTF-8") + "&text=" + URLEncoder.encode(text, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 200) null else "Telegram javobi: $code. Token yoki chat ID noto'g'ri bo'lishi mumkin."
        } catch (e: Exception) {
            "Internet yo'q yoki Telegram ochilmadi"
        }
    }

    /** Sinov xabari yuboradi. null: muvaffaqiyatli, aks holda xato matni. */
    fun sendTest(c: Context): String? {
        val t = token(c)
        if (!validToken(t)) return "Token shakli noto'g'ri"
        val n = name(c).ifBlank { "Telefon" }
        return post(t, chat(c), "✅ Qo'riqchi Pro ulandi: $n\nEndi xavf topilsa, shu yerga xabar keladi.")
    }

    /** Botga yozilgan oxirgi xabardan chat ID ni topadi. */
    fun findChatId(token: String): String? {
        if (!validToken(token)) return null
        return try {
            val conn = URL("https://api.telegram.org/bot${token.trim()}/getUpdates").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) {
                null
            } else {
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val arr = o.optJSONArray("result")
                var id: String? = null
                if (arr != null) {
                    var i = arr.length() - 1
                    while (i >= 0 && id == null) {
                        val chat = arr.getJSONObject(i).optJSONObject("message")?.optJSONObject("chat")
                        if (chat != null) id = chat.optLong("id").toString()
                        i--
                    }
                }
                id
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Jiddiy hodisa bo'lsa (ball 50+), egasiga Telegram orqali xabar yuboradi. */
    fun alertAsync(c: Context, title: String, detail: String, score: Int) {
        if (!isOn(c)) return
        val p = prefs(c)
        val key = "$title|$detail"
        val now = System.currentTimeMillis()
        if (key == p.getString("fam_lastkey", "") && now - p.getLong("fam_lasttime", 0L) < 10L * 60 * 1000) return
        p.edit().putString("fam_lastkey", key).putLong("fam_lasttime", now).apply()
        val n = name(c).ifBlank { "Telefon" }
        val time = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(java.util.Date(now))
        val msg = "🛡 Qo'riqchi Pro • $n\n⚠️ $title\n$detail\nXavf bahosi: $score/100\n$time"
        val t = token(c)
        val ch = chat(c)
        Thread { post(t, ch, msg) }.start()
    }
}

// =====================================================================
// 8. Xabar qalqoni (kelgan SMS/Telegram xabarlarini firibgarlikka tekshiradi)
// =====================================================================

class MessageShield : NotificationListenerService() {

    private val seen = LinkedHashMap<String, Long>()

    private val msgApps = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca",
        "com.viber.voip",
        "com.imo.android.imoim"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            // qalqon hech qachon ilovani yiqitmasligi kerak
        }
    }

    private fun labelOf(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        pkg
    }

    @Suppress("DEPRECATION")
    private fun textOf(n: Notification): String {
        val ex = n.extras ?: return ""
        val parts = ArrayList<String>()
        ex.getCharSequence(Notification.EXTRA_TEXT)?.let { parts.add(it.toString()) }
        ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { parts.add(it.toString()) }
        ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { parts.add(it.toString()) }
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (msgs != null) {
            for (m in msgs) {
                if (m is android.os.Bundle) {
                    m.getCharSequence("text")?.let { parts.add(it.toString()) }
                }
            }
        }
        return parts.distinct().joinToString("\n")
    }

    private fun handle(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        if (pkg == packageName) return
        if (RemoteGuard.isRemotePkg(pkg)) {
            RemoteGuard.onActivity(this, pkg)
            return
        }
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.category != Notification.CATEGORY_MESSAGE && pkg !in msgApps) return

        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = textOf(n).trim()
        if (text.length < 8) return

        val key = pkg + "|" + text.hashCode()
        val now = System.currentTimeMillis()
        synchronized(seen) {
            val old = seen[key]
            if (old != null && now - old < 10L * 60 * 1000) return
            seen[key] = now
            while (seen.size > 60) {
                val first = seen.keys.iterator().next()
                seen.remove(first)
            }
        }

        val r = ScamText.analyze(text, this, title)
        if (r.score < 40) return

        val why = r.findings.filter { it.points > 0 }
            .sortedByDescending { it.points }
            .take(2)
            .joinToString("; ") { it.text.trim() }
        val who = if (title.isNotBlank()) title else labelOf(pkg)

        Notifier.channels(this)
        val open = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val id = (now % 100000L).toInt() + 5000
        val pi = PendingIntent.getActivity(
            this, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nt = Notification.Builder(this, Notifier.CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Firibgar xabar belgisi (${r.score}/100)")
            .setContentText("$who: $why")
            .setStyle(Notification.BigTextStyle().bigText("Kimdan: $who\n$why\nHech qachon kod, karta raqami yoki parol aytmang."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(id, nt)

        Store.add(this, "Firibgar xabar: $who", why, r.score)
    }
}

// =====================================================================
// 9. Qo'ng'iroq qalqoni
// =====================================================================

class CallShield : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        var resp = CallScreeningService.CallResponse.Builder().build()
        try {
            val num = callDetails.handle?.schemeSpecificPart ?: ""
            val incoming = if (Build.VERSION.SDK_INT >= 29) {
                callDetails.callDirection == Call.Details.DIRECTION_INCOMING
            } else {
                true
            }
            if (incoming && num.isNotEmpty() && Blocklist.hasPhone(this, num)) {
                val reject = getSharedPreferences("qp", Context.MODE_PRIVATE).getBoolean("call_reject", false)
                alert(num, reject)
                if (reject) {
                    resp = CallScreeningService.CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                        .build()
                }
            }
        } catch (e: Exception) {
            // xatolikda qo'ng'iroqqa aralashmaymiz
        }
        respondToCall(callDetails, resp)
    }

    private fun alert(num: String, rejected: Boolean) {
        Notifier.channels(this)
        val id = (System.currentTimeMillis() % 100000L).toInt() + 7000
        val pi = PendingIntent.getActivity(
            this, id, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val extra = if (rejected) " Qo'ng'iroq avtomatik rad etildi." else ""
        val nt = Notification.Builder(this, Notifier.CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Firibgar raqamdan qo'ng'iroq")
            .setContentText("$num xavf bazasida bor.$extra")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "$num raqami firibgarlikda ko'rilgan.$extra\nHech qachon kod, karta raqami yoki parol aytmang. Bank xodimi bunday narsani so'ramaydi."
                )
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(id, nt)
        Store.add(this, "Firibgar raqamdan qo'ng'iroq", num + if (rejected) " (rad etildi)" else "", 70)
    }
}

// =====================================================================
// 10. Tarmoq nazorati (faqat DNS so'rovlarini ko'radigan mahalliy VPN)
// =====================================================================

data class DnsEntry(
    val time: Long,
    val app: String,
    val domain: String,
    val blocked: Boolean,
    val flagged: Boolean
)

class DnsShield : VpnService() {

    companion object {
        const val ACTION_STOP = "uz.qoriqchi.pro.DNS_STOP"
        private const val VPN_DNS = "10.77.0.1"

        @Volatile
        var running = false

        @Volatile
        var total = 0

        @Volatile
        var blockedCount = 0

        private val log = ArrayList<DnsEntry>()

        fun recent(): List<DnsEntry> = synchronized(log) { ArrayList(log) }

        private fun record(e: DnsEntry) {
            synchronized(log) {
                log.add(0, e)
                while (log.size > 150) log.removeAt(log.size - 1)
            }
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val pool = Executors.newCachedThreadPool()
    private val outLock = Any()
    private val alertedAt = HashMap<String, Long>()

    private val telegramApps = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "org.telegram.plus",
        "nekox.messenger"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) start()
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun start() {
        val fd: ParcelFileDescriptor? = try {
            Builder()
                .setSession("Qo'riqchi Pro")
                .addAddress("10.77.0.2", 32)
                .addDnsServer(VPN_DNS)
                .addRoute(VPN_DNS, 32)
                .establish()
        } catch (e: Exception) {
            null
        }
        if (fd == null) {
            running = false
            return
        }
        tun = fd
        total = 0
        blockedCount = 0
        running = true
        getSharedPreferences("qp", Context.MODE_PRIVATE).edit().putBoolean("dns_on", true).apply()
        val t = Thread { loop(fd) }
        worker = t
        t.start()
    }

    private fun shutdown() {
        running = false
        try {
            tun?.close()
        } catch (e: Exception) {
            // yopilgan
        }
        tun = null
        getSharedPreferences("qp", Context.MODE_PRIVATE).edit().putBoolean("dns_on", false).apply()
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                handlePacket(buf.copyOf(n), output)
            }
        } catch (e: Exception) {
            // tun yopildi
        }
        running = false
    }

    private fun u16(b: ByteArray, i: Int): Int = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun handlePacket(pkt: ByteArray, out: FileOutputStream) {
        if (pkt.size < 28) return
        if ((pkt[0].toInt() shr 4) != 4) return
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        if (pkt[9].toInt() != 17 || pkt.size < ihl + 8) return
        val srcPort = u16(pkt, ihl)
        val dstPort = u16(pkt, ihl + 2)
        if (dstPort != 53) return
        val dns = pkt.copyOfRange(ihl + 8, pkt.size)
        val q = parseName(dns) ?: return
        val domain = q.first
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)

        val uid = ownerUid(srcIp, srcPort, dstIp)
        val pkg = pkgOf(uid)
        val app = if (pkg != null) labelOf(pkg) else if (uid in 0..9999) "tizim" else "noma'lum ilova"
        val now = System.currentTimeMillis()
        total++

        val hit = Blocklist.domainHit(this, domain)
        if (hit != null) {
            blockedCount++
            record(DnsEntry(now, app, domain, true, false))
            notifyOnce("blk|$app|$hit", "Xavfli manzil bloklandi", "$app → $domain", 60)
            val resp = nxdomain(dns, q.second)
            if (resp != null) reply(out, srcIp, srcPort, dstIp, resp)
            return
        }

        var flagged = false
        if (domain == "api.telegram.org" && pkg != null && pkg !in telegramApps && pkg != packageName) {
            flagged = true
            notifyOnce(
                "tg|$pkg", "Shubhali: Telegram bot manziliga ulanish",
                "$app ilovasi Telegram bot serveriga ulanmoqda. Zararli dasturlar ma'lumotni shu yo'l bilan yuboradi. Ilovani taniysizmi?",
                55
            )
        }
        record(DnsEntry(now, app, domain, false, flagged))
        pool.execute { forward(dns, out, srcIp, srcPort, dstIp) }
    }

    private fun notifyOnce(key: String, title: String, detail: String, score: Int) {
        val now = System.currentTimeMillis()
        val last = alertedAt[key]
        if (last != null && now - last < 6L * 3600 * 1000) return
        alertedAt[key] = now
        try {
            Store.add(this, title, detail, score)
        } catch (e: Exception) {
            // yozilmasa ham davom etadi
        }
    }

    private fun ownerUid(srcIp: ByteArray, srcPort: Int, dstIp: ByteArray): Int {
        if (Build.VERSION.SDK_INT < 29) return -1
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(srcIp), srcPort),
                InetSocketAddress(InetAddress.getByAddress(dstIp), 53)
            )
        } catch (e: Exception) {
            -1
        }
    }

    private fun pkgOf(uid: Int): String? {
        if (uid < 10000) return null
        return try {
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun labelOf(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        pkg
    }

    /** DNS so'rovidan domen nomi va savol qismi tugaydigan joyni oladi. */
    private fun parseName(d: ByteArray): Pair<String, Int>? {
        if (d.size < 17) return null
        var i = 12
        val sb = StringBuilder()
        while (true) {
            if (i >= d.size) return null
            val len = d[i].toInt() and 0xFF
            if (len == 0) {
                i++
                break
            }
            if (len > 63 || i + 1 + len > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        if (i + 4 > d.size) return null
        return Pair(sb.toString().lowercase(Locale.ROOT), i + 4)
    }

    /** "Bunday domen yo'q" javobi. */
    private fun nxdomain(dns: ByteArray, qEnd: Int): ByteArray? {
        if (dns.size < qEnd) return null
        val r = dns.copyOf(qEnd)
        r[2] = 0x81.toByte()
        r[3] = 0x83.toByte()
        for (k in 6..11) r[k] = 0
        return r
    }

    private fun forward(dns: ByteArray, out: FileOutputStream, clientIp: ByteArray, clientPort: Int, serverIp: ByteArray) {
        val servers = arrayOf("1.1.1.1", "8.8.8.8")
        for (s in servers) {
            try {
                val sock = DatagramSocket()
                try {
                    protect(sock)
                    sock.soTimeout = 2500
                    sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(s), 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    sock.receive(rp)
                    reply(out, clientIp, clientPort, serverIp, rb.copyOf(rp.length))
                    return
                } finally {
                    sock.close()
                }
            } catch (e: Exception) {
                // keyingi serverni sinaymiz
            }
        }
    }

    /** DNS javobini IPv4/UDP paket qilib, tun ga yozadi. */
    private fun reply(out: FileOutputStream, clientIp: ByteArray, clientPort: Int, serverIp: ByteArray, payload: ByteArray) {
        val total = 28 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45.toByte()
        p[2] = (total shr 8).toByte()
        p[3] = total.toByte()
        p[6] = 0x40.toByte()
        p[8] = 64
        p[9] = 17
        System.arraycopy(serverIp, 0, p, 12, 4)
        System.arraycopy(clientIp, 0, p, 16, 4)
        var sum = 0
        var i = 0
        while (i < 20) {
            sum += u16(p, i)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        p[10] = (ck shr 8).toByte()
        p[11] = ck.toByte()
        p[21] = 53
        p[22] = (clientPort shr 8).toByte()
        p[23] = clientPort.toByte()
        val ul = 8 + payload.size
        p[24] = (ul shr 8).toByte()
        p[25] = ul.toByte()
        System.arraycopy(payload, 0, p, 28, payload.size)
        synchronized(outLock) {
            try {
                out.write(p)
            } catch (e: Exception) {
                // tun yopilgan
            }
        }
    }
}

// =====================================================================
// 11. Masofadan boshqarish ilovalari (AnyDesk, TeamViewer va boshqalar)
// =====================================================================

object RemoteGuard {
    private val prefixes = listOf(
        "com.anydesk.", "com.teamviewer.", "com.carriez.flutter_hbb", "com.sand.air",
        "com.splashtop.", "com.rsupport.", "com.logmein.", "com.zoho.assist"
    )
    private val words = listOf("anydesk", "teamviewer", "rustdesk", "airdroid", "splashtop", "quicksupport")
    private val last = HashMap<String, Long>()

    private const val PI = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun isRemotePkg(pkg: String): Boolean = prefixes.any { pkg.startsWith(it) }

    fun isRemote(pkg: String, labelLower: String): Boolean =
        isRemotePkg(pkg) || words.any { labelLower.contains(it) }

    private fun labelOf(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Yangi ilova o'rnatilganda. */
    fun onInstalled(ctx: Context, pkg: String) {
        val label = labelOf(ctx, pkg)
        if (!isRemote(pkg, label.lowercase(Locale.ROOT))) return
        show(
            ctx, pkg, "install", 60L * 1000,
            "🚨 Masofadan boshqarish ilovasi o'rnatildi",
            "$label o'rnatildi. Bank, soliq yoki «xavfsizlik xodimi» nomidan kimdir shu ilovani o'rnatishni so'ragan bo'lsa, bu firibgarlik: DARHOL o'chiring va hech qanday kod aytmang.",
            "Masofadan boshqarish ilovasi o'rnatildi: $label"
        )
    }

    /** Ilovaning bildirishnomasi ko'ringanda (faol sessiya belgisi). */
    fun onActivity(ctx: Context, pkg: String) {
        val label = labelOf(ctx, pkg)
        show(
            ctx, pkg, "session", 6L * 3600 * 1000,
            "🚨 Telefoningiz hozir boshqa odamga ochiq bo'lishi mumkin",
            "$label faol. Agar buni o'zingiz boshlamagan bo'lsangiz, ilovani darhol o'chiring va bankka o'zingiz qo'ng'iroq qiling.",
            "Masofadan boshqarish ilovasi faol: $label"
        )
    }

    private fun show(ctx: Context, pkg: String, kind: String, gapMs: Long, title: String, text: String, eventTitle: String) {
        val key = "$kind|$pkg"
        val now = System.currentTimeMillis()
        synchronized(last) {
            val prev = last[key]
            if (prev != null && now - prev < gapMs) return
            last[key] = now
        }
        Notifier.channels(ctx)
        val id = key.hashCode()
        val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PI)
        val delIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val del = PendingIntent.getActivity(ctx, id + 1, delIntent, PI)
        val nt = Notification.Builder(ctx, Notifier.CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, android.R.drawable.ic_menu_delete),
                    "Ilovani o'chirish",
                    del
                ).build()
            )
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, nt)
        Store.add(ctx, eventTitle, "Bank xodimi bunday ilova o'rnatishni so'ramaydi", 70)
    }
}

// =====================================================================
// 12. Ruxsat o'zgarishi kuzatuvi
// =====================================================================

data class PermChange(val label: String, val pkg: String, val added: List<String>, val critical: Boolean)

data class PermResult(val first: Boolean, val changes: List<PermChange>)

object PermWatch {

    private val names = linkedMapOf(
        "android.permission.READ_SMS" to "SMS o'qish",
        "android.permission.RECEIVE_SMS" to "SMS qabul qilish",
        "android.permission.SEND_SMS" to "SMS yuborish",
        "android.permission.READ_CALL_LOG" to "Qo'ng'iroqlar tarixi",
        "android.permission.READ_CONTACTS" to "Kontaktlar",
        "android.permission.CAMERA" to "Kamera",
        "android.permission.RECORD_AUDIO" to "Mikrofon",
        "android.permission.ACCESS_FINE_LOCATION" to "Aniq joylashuv",
        "android.permission.SYSTEM_ALERT_WINDOW" to "Boshqa ilovalar ustidan chizish",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "Boshqa APK o'rnatish",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "Barcha fayllarga kirish",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "Maxsus imkoniyatlar xizmati",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to "Bildirishnomalarni o'qish",
        "android.permission.BIND_DEVICE_ADMIN" to "Qurilma administratori"
    )

    private val critical = setOf(
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN"
    )

    private fun prefs(c: Context) = c.getSharedPreferences("qp", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun snapshot(ctx: Context): Map<String, Set<String>> {
        val pm = ctx.packageManager
        val out = HashMap<String, Set<String>>()
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        for (p in pm.getInstalledPackages(flags)) {
            val ai = p.applicationInfo ?: continue
            if ((ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue
            if (p.packageName == ctx.packageName) continue
            val s = HashSet<String>()
            val req = p.requestedPermissions
            if (req != null) {
                for (r in req) if (r in names) s.add(r)
            }
            val svs = p.services
            if (svs != null) {
                for (sv in svs) {
                    val pr = sv.permission
                    if (pr != null && pr in names) s.add(pr)
                }
            }
            val rcs = p.receivers
            if (rcs != null) {
                for (rc in rcs) {
                    val pr = rc.permission
                    if (pr != null && pr in names) s.add(pr)
                }
            }
            out[p.packageName] = s
        }
        return out
    }

    private fun parse(raw: String): Map<String, Set<String>>? {
        return try {
            val o = JSONObject(raw)
            val out = HashMap<String, Set<String>>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = o.getJSONArray(k)
                val s = HashSet<String>()
                for (i in 0 until a.length()) s.add(a.getString(i))
                out[k] = s
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    private fun save(ctx: Context, m: Map<String, Set<String>>) {
        val o = JSONObject()
        for ((k, v) in m) {
            val a = JSONArray()
            for (x in v) a.put(x)
            o.put(k, a)
        }
        prefs(ctx).edit()
            .putString("pw_snap", o.toString())
            .putLong("pw_time", System.currentTimeMillis())
            .putInt("pw_n", m.size)
            .apply()
    }

    fun info(ctx: Context): String {
        val p = prefs(ctx)
        val t = p.getLong("pw_time", 0L)
        if (t == 0L) return "Hali tekshirilmagan. Birinchi tekshiruv boshlang'ich holatni saqlaydi."
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return "Oxirgi tekshiruv: ${fmt.format(java.util.Date(t))}  •  ${p.getInt("pw_n", 0)} ta ilova kuzatilmoqda"
    }

    /** 3 soatdan ko'p o'tgan bo'lsa, fonda tekshiradi. */
    fun checkIfDue(ctx: Context) {
        if (System.currentTimeMillis() - prefs(ctx).getLong("pw_time", 0L) < 3L * 3600 * 1000) return
        Thread {
            try {
                check(ctx)
            } catch (e: Exception) {
                // keyingi safar
            }
        }.start()
    }

    @Synchronized
    fun check(ctx: Context): PermResult {
        val cur = snapshot(ctx)
        val raw = prefs(ctx).getString("pw_snap", null)
        val old: Map<String, Set<String>>? = if (raw == null) null else parse(raw)
        val changes = ArrayList<PermChange>()
        if (old != null) {
            val pm = ctx.packageManager
            for ((pkg, now) in cur) {
                val before = old[pkg] ?: continue
                val added = now.filter { it !in before }
                if (added.isEmpty()) continue
                val label = try {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
                changes.add(PermChange(label, pkg, added.map { names[it] ?: it }, added.any { it in critical }))
            }
        }
        save(ctx, cur)
        for (c in changes) {
            val detail = "Yangi ruxsat: " + c.added.joinToString(", ")
            Store.add(ctx, "Ruxsat qo'shildi: ${c.label}", detail, if (c.critical) 75 else 40)
            if (c.critical) {
                Notifier.channels(ctx)
                val id = ("pw|" + c.pkg).hashCode()
                val pi = PendingIntent.getActivity(
                    ctx, id, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val nt = Notification.Builder(ctx, Notifier.CH_ALERT)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("⚠️ ${c.label} yangi xavfli ruxsat so'radi")
                    .setContentText(detail)
                    .setStyle(Notification.BigTextStyle().bigText("$detail\nIlova yangilanganidan keyin paydo bo'ldi. Bu ruxsat nima uchun kerakligini bilmasangiz, ilovani o'chiring."))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                ctx.getSystemService(NotificationManager::class.java).notify(id, nt)
            }
        }
        return PermResult(old == null, changes)
    }
}
