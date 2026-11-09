package uz.qoriqchi.pro

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class Finding(val points: Int, val text: String)

enum class Level(val title: String, val color: Int) {
    LOW("Xavfsiz", 0xFF2E7D32.toInt()),
    MEDIUM("Ehtiyot bo'ling", 0xFFF9A825.toInt()),
    HIGH("Xavfli", 0xFFEF6C00.toInt()),
    CRITICAL("Juda xavfli", 0xFFC62828.toInt())
}

data class Report(
    val label: String,
    val pkg: String,
    val score: Int,
    val findings: List<Finding>,
    val sha256: String? = null,
    val cert: String? = null,
    val apkPath: String? = null,
    val vt: String? = null,
    val source: String? = null
) {
    val level: Level
        get() = when {
            score >= 75 -> Level.CRITICAL
            score >= 50 -> Level.HIGH
            score >= 25 -> Level.MEDIUM
            else -> Level.LOW
        }
}

object Scanner {

    @Suppress("DEPRECATION")
    private const val FLAGS =
        PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_SIGNING_CERTIFICATES

    private val trustedStores = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.mipicks",
        "com.heytap.market",
        "com.amazon.venezia",
        "org.fdroid.fdroid"
    )

    private val sideloadInstallers = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    private val knownNames = mapOf(
        "telegram" to "org.telegram",
        "whatsapp" to "com.whatsapp",
        "google play" to "com.android.vending",
        "play market" to "com.android.vending",
        "chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "instagram" to "com.instagram",
        "facebook" to "com.facebook"
    )

    /** Taqlid qilinadigan brendlar (nomi shu so'zni o'z ichiga olib, do'kondan emas bo'lsa shubhali). */
    private val brandWords = listOf(
        "click", "payme", "uzum", "humo", "uzcard", "myid", "my id", "soliq", "mygov", "my.gov",
        "egov", "telegram", "whatsapp", "google play", "instagram", "facebook", "davlat xizmat",
        "paynet", "apelsin", "kapitalbank", "xalq bank", "xalqbank", "agrobank", "asakabank", "ipoteka",
        "hamkorbank", "infinbank", "anorbank", "davrbank", "turonbank", "aloqabank", "orient finans",
        "ucell", "beeline", "uzmobile", "mobiuz"
    )

    /** O'zbekistonga xos bo'lmagan, jahon brendlari. */
    private val globalBrands = setOf("telegram", "whatsapp", "google play", "instagram", "facebook")

    /** Tekshirib topilgan rasmiy ilova paket boshlanishlari (to'liq emas: noto'g'ri signal bermaslik uchun ehtiyotkor ishlatiladi). */
    private val officialPrefixes = mapOf(
        "click" to listOf("air.com.ssdsoftwaresolutions", "uz.click"),
        "payme" to listOf("uz.dida."),
        "uzum" to listOf("uz.uzum"),
        "kapitalbank" to listOf("uz.kapitalbank")
    )

    /** Aldov/yangilanish niqobidagi ilovalar uchun xos so'zlar. */
    private val lureWords = listOf(
        "aksiya", "sovg'a", "sovga", "bonus", "lotereya", "yutuq", "kredit", "pul ", "bepul",
        "yangilanish", "update", "obnovlenie", "подарок", "акция", "бонус", "выигрыш", "кредит"
    )

    private val systemWords = listOf("google", "android system", "system update", "security", "tizim", "yangilanish")

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun sha256File(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(65536)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    private fun certs(info: PackageInfo): Set<String> {
        val si = info.signingInfo ?: return emptySet()
        val arr = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return arr.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    private fun isSystem(info: PackageInfo): Boolean {
        val fl = info.applicationInfo?.flags ?: 0
        return (fl and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }

    @Suppress("DEPRECATION")
    fun scanInstalled(ctx: Context, onProgress: ((Int, Int, String) -> Unit)? = null): List<Report> {
        val pm = ctx.packageManager
        val pkgs = pm.getInstalledPackages(FLAGS)
            .filter { !isSystem(it) && it.packageName != ctx.packageName }
        val out = ArrayList<Report>(pkgs.size)
        for ((i, p) in pkgs.withIndex()) {
            val label = p.applicationInfo?.loadLabel(pm)?.toString() ?: p.packageName
            onProgress?.invoke(i + 1, pkgs.size, label)
            out.add(analyze(ctx, p, label, false, null))
        }
        return out.sortedByDescending { it.score }
    }

    val auditGroups: Map<String, List<String>> = linkedMapOf(
        "📷 Kamera" to listOf("android.permission.CAMERA"),
        "🎤 Mikrofon" to listOf("android.permission.RECORD_AUDIO"),
        "📍 Joylashuv" to listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"
        ),
        "✉ SMS" to listOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS"
        ),
        "👥 Kontaktlar" to listOf("android.permission.READ_CONTACTS"),
        "📞 Qo'ng'iroqlar" to listOf(
            "android.permission.READ_CALL_LOG",
            "android.permission.PROCESS_OUTGOING_CALLS"
        )
    )

    /** Har bir sezgir ruxsat bo'yicha: shu ruxsat HOZIR berilgan ilovalar (nomi, paketi). */
    @Suppress("DEPRECATION")
    fun permissionAudit(ctx: Context): Map<String, List<Pair<String, String>>> {
        val pm = ctx.packageManager
        val res = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        for (k in auditGroups.keys) res[k] = ArrayList()
        for (info in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (isSystem(info) || info.packageName == ctx.packageName) continue
            val req = info.requestedPermissions ?: continue
            val flags = info.requestedPermissionsFlags ?: continue
            val granted = HashSet<String>()
            for (i in req.indices) {
                if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    granted.add(req[i])
                }
            }
            val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: info.packageName
            for ((g, perms) in auditGroups) {
                if (perms.any { it in granted }) res[g]?.add(Pair(label, info.packageName))
            }
        }
        return res.mapValues { e -> e.value.sortedBy { it.first.lowercase() } }
    }

    @Suppress("DEPRECATION")
    fun analyzePackage(ctx: Context, pkg: String): Report? {
        val pm = ctx.packageManager
        val info = try {
            pm.getPackageInfo(pkg, FLAGS)
        } catch (e: Exception) {
            return null
        }
        val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
        return analyze(ctx, info, label, false, null)
    }

    @Suppress("DEPRECATION")
    fun scanArchive(ctx: Context, file: File, sha: String): Report? {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, FLAGS) ?: return null
        val ai = info.applicationInfo
        ai?.sourceDir = file.absolutePath
        ai?.publicSourceDir = file.absolutePath
        val label = try {
            ai?.loadLabel(pm)?.toString() ?: info.packageName
        } catch (e: Exception) {
            info.packageName
        }
        return analyze(ctx, info, label, true, sha)
    }

    @Suppress("DEPRECATION")
    fun analyze(ctx: Context, info: PackageInfo, label: String, fromFile: Boolean, fileSha: String?): Report {
        val pm = ctx.packageManager
        val pkg = info.packageName
        val f = mutableListOf<Finding>()
        fun add(p: Int, t: String) { f += Finding(p, t) }

        val perms = (info.requestedPermissions ?: emptyArray())
            .filter { it.startsWith("android.permission.") }
            .map { it.removePrefix("android.permission.") }
            .toSet()

        val a11y = info.services?.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" } == true
        val admin = info.receivers?.any { it.permission == "android.permission.BIND_DEVICE_ADMIN" } == true
        val notif = info.services?.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" } == true
        val readSms = "READ_SMS" in perms || "RECEIVE_SMS" in perms
        val sendSms = "SEND_SMS" in perms
        val overlay = "SYSTEM_ALERT_WINDOW" in perms

        if (a11y) add(35, "Maxsus imkoniyatlar (Accessibility) xizmati bor: ekranni o'qiy va o'zi bosa oladi")
        if (admin) add(25, "Qurilma administratori bo'lishni so'raydi: o'chirib tashlash qiyinlashadi")
        if (notif) add(20, "Bildirishnomalarni o'qiydi (tasdiq kodlarini ushlashi mumkin)")
        if (readSms) add(25, "SMS'larni o'qiydi (bank tasdiq kodlari xavf ostida)")
        if (sendSms) add(15, "Sizning nomingizdan SMS yubora oladi")
        if (overlay) add(15, "Boshqa ilovalar ustidan oyna chiza oladi (soxta kirish oynasi uchun ishlatiladi)")
        if ("REQUEST_INSTALL_PACKAGES" in perms) add(10, "Boshqa APK'larni o'rnatishni so'raydi")
        if ("MANAGE_EXTERNAL_STORAGE" in perms) add(6, "Barcha fayllarga kirish so'raydi")

        val privacy = listOf("READ_CALL_LOG", "PROCESS_OUTGOING_CALLS", "RECORD_AUDIO", "READ_CONTACTS").filter { it in perms }
        if (privacy.isNotEmpty()) {
            add(minOf(12, 4 * privacy.size), "Shaxsiy ma'lumotlarga kirish: ${privacy.joinToString(", ")}")
        }

        if (a11y && (readSms || overlay)) {
            add(20, "Bank troyanlariga xos kombinatsiya: Accessibility + SMS/oyna ustidan chizish")
        }

        val system = !fromFile && isSystem(info)
        val lower = label.lowercase().trim()

        if (!system) {
            for ((name, prefix) in knownNames) {
                if (lower == name && !pkg.startsWith(prefix)) {
                    add(30, "Nomi \"$label\" ga o'xshash, lekin paket nomi rasmiy emas ($pkg)")
                }
            }
            if (systemWords.any { lower.contains(it) }) {
                add(12, "Tizim ilovasiga o'xshash nom bilan yashirinayotgan bo'lishi mumkin")
            }
        }

        var fromStore = false
        var sideloaded = fromFile
        if (!fromFile) {
            val installer: String? = try {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
                else pm.getInstallerPackageName(pkg)
            } catch (e: Exception) {
                null
            }
            when {
                installer != null && installer in trustedStores -> {
                    fromStore = true
                    add(0, "Rasmiy do'kondan o'rnatilgan ($installer)")
                }
                installer == null -> {
                    sideloaded = true
                    add(10, "O'rnatish manbasi noma'lum (do'kondan emas)")
                }
                installer in sideloadInstallers -> {
                    sideloaded = true
                    add(12, "APK fayldan qo'lda o'rnatilgan")
                }
                else -> {
                    sideloaded = true
                    add(12, "Do'kondan tashqari manba orqali o'rnatilgan: $installer")
                }
            }
            if (pm.getLaunchIntentForPackage(pkg) == null && (a11y || admin || readSms || notif)) {
                add(15, "Ikonkasi yo'q: yashirin ishlaydi")
            }
        } else {
            try {
                val installed = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val a = certs(info)
                val b = certs(installed)
                if (a.isNotEmpty() && b.isNotEmpty() && a.intersect(b).isEmpty()) {
                    add(50, "DIQQAT: telefonda shu paket nomli ilova bor, lekin imzosi BOSHQA. Bu soxta nusxa yoki \"yangilanish\" niqobi bo'lishi mumkin")
                } else {
                    add(0, "Telefondagi shu ilova bilan imzosi mos")
                }
            } catch (e: PackageManager.NameNotFoundException) {
                add(0, "Bu ilova hali telefonda o'rnatilmagan")
            }
        }

        if (!system) {
            val brand = brandWords.firstOrNull { lower.contains(it) }
            if (brand != null) {
                val prefixes = officialPrefixes[brand]
                val official = (prefixes != null && prefixes.any { pkg.startsWith(it) }) ||
                    knownNames.any { (n, pre) -> lower == n && pkg.startsWith(pre) }
                val uz = brand !in globalBrands
                if (sideloaded && !official) {
                    add(
                        if (uz) 55 else 30,
                        "Rasmiy do'kondan emas, lekin \"$brand\" nomini ishlatmoqda va paket nomi ($pkg) rasmiy emas: soxta nusxa bo'lishi ehtimoli yuqori"
                    )
                } else if (!sideloaded && uz && prefixes != null && !official) {
                    add(30, "\"$brand\" nomli ilova, lekin paket nomi ($pkg) rasmiy ilovanikiga o'xshamaydi: tekshirib ko'ring")
                }
            }
            if (sideloaded && lureWords.any { lower.contains(it) }) {
                add(15, "Nomi aldov/yangilanish niqobida (\"$label\")")
            }
        }

        val remote = !system && RemoteGuard.isRemote(pkg, lower)
        val remotePts = if (remote) 30 else 0
        if (remote) {
            add(30, "Masofadan boshqarish ilovasi: firibgarlar telefoningizni shu orqali boshqaradi. Faqat o'zingiz o'rnatgan va ishongan odamga ishlating")
        }

        if (f.none { it.points > 0 }) add(0, "Xavfli belgilar topilmadi")

        var raw = f.sumOf { it.points }
        if (fromStore && raw > 0) {
            raw = (raw - remotePts) / 2 + remotePts
            add(0, "Rasmiy do'kondan o'rnatilgani uchun xavf bali ikki baravar kamaytirildi")
        }
        val score = minOf(100, raw)
        return Report(
            label = label,
            pkg = pkg,
            score = score,
            findings = f,
            sha256 = fileSha,
            cert = certs(info).firstOrNull(),
            apkPath = info.applicationInfo?.sourceDir
        )
    }
}
