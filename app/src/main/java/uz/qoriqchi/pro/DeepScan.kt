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
        var a11yAct = false
        var wipe = false
        var smsSend = false
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
                if (s.indexOf("api.telegram.org/bot") >= 0) tgBot = true
                if (s.indexOf("performGlobalAction") >= 0 || s.indexOf("dispatchGesture") >= 0) a11yAct = true
                if (s.indexOf("wipeData") >= 0) wipe = true
                if (s.indexOf("sendTextMessage") >= 0 || s.indexOf("sendMultipartTextMessage") >= 0) smsSend = true
                for (b in brands) {
                    if (s.indexOf(b) >= 0) foundBrands.add(b)
                }
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
        if (tgBot) out.add(Finding(25, "Ma'lumotni Telegram bot orqali yuboradigan kod bor (troyanlarda keng tarqalgan usul)"))
        if (dynLoad) out.add(Finding(12, "Tashqaridan kod yuklab ishga tushira oladi (DexClassLoader)"))
        if (a11yAct) out.add(Finding(12, "Ekranda o'zi bosish yoki ishora yuborish kodi bor"))
        if (wipe) out.add(Finding(12, "Telefon ma'lumotlarini o'chirish (wipeData) kodi bor"))
        if (smsSend) out.add(Finding(10, "SMS yuborish kodi bor"))
        if (foundBrands.size >= 2) {
            out.add(Finding(15, "Kodda bir nechta bank/to'lov nomlari bor: ${foundBrands.take(4).joinToString(", ")}"))
        }
        if (packed) out.add(Finding(10, "Kodni yashiruvchi himoya (packer) kutubxonasi bor"))
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
