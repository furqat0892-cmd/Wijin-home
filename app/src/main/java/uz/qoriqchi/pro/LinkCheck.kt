package uz.qoriqchi.pro

/** Havolani ochmasdan, faqat shaklini tahlil qiladi (internet kerak emas). */
object LinkCheck {

    private val brands = listOf(
        "click", "payme", "uzum", "paynet", "humocard", "uzcard", "mygov", "egov",
        "kapitalbank", "xalqbank", "agrobank", "asakabank", "hamkorbank",
        "ipotekabank", "davrbank", "anorbank", "tbcbank"
    )
    private val badTlds = listOf(".xyz", ".top", ".click", ".site", ".online", ".icu", ".live", ".buzz", ".cfd", ".sbs", ".vip", ".work")
    private val shorteners = listOf("bit.ly", "tinyurl.com", "cutt.ly", "is.gd", "t.ly", "rb.gy", "shorturl.at", "clck.ru")

    fun analyze(raw: String): Report {
        val s = raw.trim()
        val f = mutableListOf<Finding>()
        fun add(p: Int, t: String) { f += Finding(p, t) }

        val uri = try {
            java.net.URI(if (s.contains("://")) s else "https://$s")
        } catch (e: Exception) {
            null
        }
        val host = (uri?.host ?: "").lowercase()
        val path = (uri?.path ?: "").lowercase()

        if (uri == null || host.isEmpty()) add(15, "Havola noto'g'ri tuzilgan")
        if (s.lowercase().startsWith("http://")) add(15, "Shifrlanmagan (http) ulanish")
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) add(30, "Domen o'rniga IP manzil ishlatilgan")
        if (host.contains("xn--")) add(30, "Boshqa alifbo belgilari bilan yashirilgan domen (punycode)")
        if (uri?.userInfo != null) add(30, "Havola ichida '@' bor: haqiqiy manzil yashirilgan")
        if (path.endsWith(".apk")) add(45, "Havola to'g'ridan-to'g'ri APK faylga olib boradi")
        if (shorteners.any { host == it }) add(20, "Qisqartirilgan havola: manzil yashirin")
        if (badTlds.any { host.endsWith(it) }) add(15, "Firibgarlikda ko'p uchraydigan domen zonasi")
        if (host.count { it == '.' } >= 4) add(10, "Juda ko'p subdomen")
        if (host.count { it == '-' } >= 3) add(10, "Domenda ko'p chiziqcha bor")

        val brand = brands.firstOrNull { host.contains(it) }
        if (brand != null && !host.endsWith(".uz")) {
            add(40, "'$brand' nomi ishlatilgan, lekin domen .uz emas: soxta sayt bo'lishi mumkin")
        }

        if (f.none { it.points > 0 }) add(0, "Tashqi belgilar bo'yicha shubhali narsa yo'q (bu 100% kafolat emas)")

        return Report(
            label = s.take(80),
            pkg = "Havola",
            score = minOf(100, f.sumOf { it.points }),
            findings = f
        )
    }
}
