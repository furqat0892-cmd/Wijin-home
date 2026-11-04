package uz.qoriqchi.pro

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** APK ichini (ZIP tuzilishi va DEX satrlari) tekshiradi. Bu evristika: kafolat emas. */
object DeepScan {

    private const val MAX_DEX = 24L * 1024 * 1024

    private val brands = listOf(
        "click.uz", "payme", "uzum", "humocard", "uzcard", "my.soliq.uz", "mygov", "egov.uz",
        "myid", "kapitalbank", "xalqbank", "agrobank", "asakabank", "hamkorbank",
        "ipotekabank", "davrbank", "anorbank"
    )
    private val packers = listOf(
        "libjiagu", "libsecneo", "libdexhelper", "libprotectclass", "libmobisec", "libddog", "libbangcle"
    )
    private val dexName = Regex("classes\\d*\\.dex")

    /** Telegram bot manzili: faqat bot raqami olinadi, maxfiy qismi (token) saqlanmaydi. */
    private val botRe = Regex("api\\.telegram\\.org/bot(\\d{6,12}):[A-Za-z0-9_-]{30,}")

    /** Kod ichidagi veb-manzillar (faqat domen/IP qismi olinadi, yo'l va parametrlar saqlanmaydi). */
    private val hostRe = Regex("https?://([A-Za-z0-9][A-Za-z0-9.-]{3,80})")
    private val apkLinkRe = Regex("https?://[A-Za-z0-9./_%-]{4,120}\\.apk")
    private val ipRe = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Oddiy kutubxonalar va tizim manzillari: ularni "shubhali manzil" qatoriga qo'shmaymiz. */
    private val commonHosts = listOf(
        "google", "gstatic", "android.com", "googleapis", "firebase", "crashlytics", "app-measurement",
        "doubleclick", "youtube", "w3.org", "apache.org", "xmlpull", "mozilla", "kotlinlang", "jetbrains",
        "sun.com", "java.com", "oracle.com", "unicode.org", "ietf.org", "example.com", "localhost",
        "schemas.", "facebook.com", "fbcdn", "github.com", "microsoft.com", "mozilla.org", "openssl.org",
        "unity3d", "applovin", "admob", "adjust.com", "appsflyer", "yandex", "t.me", "telegram.org"
    )

    fun analyze(path: String): List<Finding> {
        val out = ArrayList<Finding>()
        val zip = try {
            ZipFile(path)
        } catch (e: Exception) {
            return listOf(Finding(0, "Chuqur tahlil: fayl APK sifatida ochilmadi"))
        }

        val hidden = ArrayList<String>()
        val dexes = ArrayList<ZipEntry>()
        var packed = false
        var skipped = 0
        var dynLoad = false
        var tgBot = false
        var botId: String? = null
        var a11yAct = false
        var wipe = false
        var smsSend = false
        var apkLink = false
        val hosts = LinkedHashSet<String>()
        val foundBrands = LinkedHashSet<String>()

        try {
            val en = zip.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement()
                val name = e.name.lowercase()
                val inData = name.startsWith("assets/") || name.startsWith("res/raw/")
                if (inData && (name.endsWith(".apk") || name.endsWith(".dex") || name.endsWith(".jar"))) {
                    hidden.add(e.name.substringAfterLast('/'))
                }
                if (name.startsWith("lib/") && packers.any { name.contains(it) }) packed = true
                if (dexName.matches(name)) dexes.add(e)
            }

            for (e in dexes.take(6)) {
                if (e.size > MAX_DEX) {
                    skipped++
                    continue
                }
                val bytes = zip.getInputStream(e).use { it.readBytes() }
                val s = String(bytes, Charsets.ISO_8859_1)
                if (s.indexOf("Ldalvik/system/DexClassLoader;") >= 0 || s.indexOf("InMemoryDexClassLoader") >= 0) dynLoad = true
                if (s.indexOf("api.telegram.org/bot") >= 0) {
                    tgBot = true
                    if (botId == null) botId = botRe.find(s)?.groupValues?.get(1)
                }
                if (s.indexOf("performGlobalAction") >= 0 || s.indexOf("dispatchGesture") >= 0) a11yAct = true
                if (s.indexOf("wipeData") >= 0) wipe = true
                if (s.indexOf("sendTextMessage") >= 0 || s.indexOf("sendMultipartTextMessage") >= 0) smsSend = true
                for (b in brands) {
                    if (s.indexOf(b) >= 0) foundBrands.add(b)
                }
                if (hosts.size < 8) {
                    var n = 0
                    for (m in hostRe.findAll(s)) {
                        if (++n > 400 || hosts.size >= 8) break
                        val h = m.groupValues[1].lowercase().trimEnd('.', '-')
                        if (h.contains('.') && commonHosts.none { h.contains(it) }) hosts.add(h)
                    }
                }
                if (!apkLink && apkLinkRe.containsMatchIn(s)) apkLink = true
            }
        } catch (e: Exception) {
            out.add(Finding(0, "Chuqur tahlil to'liq bajarilmadi: ${e.message}"))
        } finally {
            try {
                zip.close()
            } catch (e: Exception) {
                // e'tibor bermaymiz
            }
        }

        if (hidden.isNotEmpty()) {
            out.add(Finding(25, "Ichida yashirin kod/APK fayli bor (dropper belgisi): ${hidden.take(3).joinToString(", ")}"))
        }
        if (tgBot) {
            val id = botId?.let { " Bot ID: $it (to'liq token ko'rsatilmaydi). Shu ID bilan Telegram'ga shikoyat qilish mumkin." } ?: ""
            out.add(Finding(25, "Ma'lumotni Telegram bot orqali yuboradigan kod bor (troyanlarda keng tarqalgan usul).$id"))
        }
        if (dynLoad) out.add(Finding(12, "Tashqaridan kod yuklab ishga tushira oladi (DexClassLoader)"))
        if (a11yAct) out.add(Finding(12, "Ekranda o'zi bosish yoki ishora yuborish kodi bor"))
        if (wipe) out.add(Finding(12, "Telefon ma'lumotlarini o'chirish (wipeData) kodi bor"))
        if (smsSend) out.add(Finding(10, "SMS yuborish kodi bor"))
        if (foundBrands.size >= 2) {
            out.add(Finding(15, "Kodda bir nechta bank/to'lov nomlari bor: ${foundBrands.take(4).joinToString(", ")}"))
        }
        if (packed) out.add(Finding(10, "Kodni yashiruvchi himoya (packer) kutubxonasi bor"))
        val ipHosts = hosts.filter { ipRe.matches(it) }
        if (ipHosts.isNotEmpty()) {
            out.add(Finding(8, "Kodda to'g'ridan-to'g'ri IP manzil bor (ma'lumot yuboriladigan server bo'lishi mumkin): ${ipHosts.take(3).joinToString(", ")}"))
        }
        if (apkLink) out.add(Finding(8, "Kodda boshqa .apk faylni yuklab olish havolasi bor"))
        val domains = hosts.filter { !ipRe.matches(it) }
        if (domains.isNotEmpty()) {
            out.add(Finding(0, "Kod ichida topilgan manzillar (dalil sifatida saqlang): ${domains.take(5).joinToString(", ")}"))
        }
        if (skipped > 0) out.add(Finding(0, "$skipped ta kod fayli juda katta bo'lgani uchun tekshirilmadi"))
        if (out.none { it.points > 0 }) out.add(Finding(0, "Chuqur tahlil: qo'shimcha xavfli belgilar topilmadi"))
        return out
    }

    /** Hisobotga chuqur tahlil natijalarini qo'shadi. */
    fun apply(r: Report, path: String): Report {
        val fs = analyze(path)
        val extra = fs.sumOf { it.points }
        val kept = r.findings.filter { !(it.points == 0 && it.text.startsWith("Xavfli belgilar topilmadi")) }
        return r.copy(score = minOf(100, r.score + extra), findings = kept + fs)
    }
}
