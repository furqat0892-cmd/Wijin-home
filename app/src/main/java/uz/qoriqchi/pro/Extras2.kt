package uz.qoriqchi.pro

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build

/** Tilga qarab matn tanlash (Activity'dan tashqarida ishlatiladi). */
fun tr(c: Context, uz: String, ru: String, en: String): String =
    when (c.getSharedPreferences("qp", Context.MODE_PRIVATE).getString("lang", "uz")) {
        "ru" -> ru
        "en" -> en
        else -> uz
    }

/** Ishonchli (ignor qilingan) ilovalar ro'yxatini boshqarish. Ro'yxat Trust bilan bir xil joyda saqlanadi. */
object IgnoreList {
    fun remove(c: Context, pkg: String) {
        val p = c.getSharedPreferences("qp", Context.MODE_PRIVATE)
        val s = HashSet(p.getStringSet("trust", emptySet()) ?: emptySet())
        s.remove(pkg)
        p.edit().putStringSet("trust", s).apply()
    }
}

/** state: 0 yaxshi, 1 ehtiyot, 2 xavfli, 3 noma'lum. */
data class WifiLine(val state: Int, val text: String)

data class WifiResult(val state: Int, val title: String, val lines: List<WifiLine>)

object WifiCheck {

    @Suppress("DEPRECATION")
    fun run(ctx: Context): WifiResult {
        val lines = ArrayList<WifiLine>()
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var wifi: Network? = null
            var vpn = false
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = n
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true
            }
            if (wifi == null) {
                return WifiResult(
                    3,
                    tr(ctx, "Wi-Fi ulanmagan", "Wi-Fi не подключён", "Wi-Fi is not connected"),
                    listOf(
                        WifiLine(
                            3,
                            tr(
                                ctx,
                                "Hozir mobil internet ishlatilmoqda. Wi-Fi'ga ulanganda qayta tekshiring.",
                                "Сейчас используется мобильный интернет. Проверьте снова, когда подключитесь к Wi-Fi.",
                                "Mobile data is in use now. Check again once you connect to Wi-Fi."
                            )
                        )
                    )
                )
            }
            val caps = cm.getNetworkCapabilities(wifi)
            var secType = -1
            if (Build.VERSION.SDK_INT >= 31 && caps != null) {
                val wi = caps.transportInfo as? WifiInfo
                if (wi != null) secType = wi.currentSecurityType
            }
            when (secType) {
                0 -> lines.add(
                    WifiLine(
                        2,
                        tr(
                            ctx,
                            "Tarmoq ochiq (parolsiz). Boshqalar trafigingizni ko'rishi mumkin. Bank, parol va kodlarni kiritmang.",
                            "Сеть открытая (без пароля). Другие могут видеть ваш трафик. Не вводите банковские данные, пароли и коды.",
                            "The network is open (no password). Others may see your traffic. Do not enter bank details, passwords or codes."
                        )
                    )
                )
                5 -> lines.add(
                    WifiLine(
                        1,
                        tr(
                            ctx,
                            "Tarmoq parolsiz, lekin trafik shifrlangan (OWE). Parolni baribir ehtiyot bo'lib kiriting.",
                            "Сеть без пароля, но трафик шифруется (OWE). Всё равно вводите пароли осторожно.",
                            "The network has no password but traffic is encrypted (OWE). Still be careful with passwords."
                        )
                    )
                )
                1 -> lines.add(
                    WifiLine(
                        2,
                        tr(
                            ctx,
                            "Eski WEP shifrlash: u osongina buzib kiriladi. Router sozlamasida WPA2 yoki WPA3 ni yoqing.",
                            "Устаревшее шифрование WEP легко взламывается. Включите WPA2 или WPA3 в настройках роутера.",
                            "Outdated WEP encryption is easy to break. Enable WPA2 or WPA3 in the router settings."
                        )
                    )
                )
                2 -> lines.add(
                    WifiLine(
                        0,
                        tr(
                            ctx,
                            "WPA2 shifrlash: yaxshi. Parol murakkab bo'lsa, xavfsiz.",
                            "Шифрование WPA2: хорошо. Безопасно, если пароль сложный.",
                            "WPA2 encryption: good. Safe if the password is strong."
                        )
                    )
                )
                4 -> lines.add(
                    WifiLine(
                        0,
                        tr(
                            ctx,
                            "WPA3 shifrlash: eng yaxshi himoya.",
                            "Шифрование WPA3: лучшая защита.",
                            "WPA3 encryption: the best protection."
                        )
                    )
                )
                3, 9, 10, 11 -> lines.add(
                    WifiLine(
                        0,
                        tr(
                            ctx,
                            "Korporativ (WPA-Enterprise) tarmoq: odatda xavfsiz.",
                            "Корпоративная сеть (WPA-Enterprise): обычно безопасна.",
                            "Enterprise (WPA-Enterprise) network: usually safe."
                        )
                    )
                )
                else -> lines.add(
                    WifiLine(
                        3,
                        tr(
                            ctx,
                            "Shifrlash turini aniqlab bo'lmadi (Android 12 va undan yangisida aniqlanadi).",
                            "Не удалось определить тип шифрования (определяется на Android 12 и новее).",
                            "Couldn't detect the encryption type (works on Android 12 and newer)."
                        )
                    )
                )
            }

            val lp = cm.getLinkProperties(wifi)
            if (lp != null && lp.httpProxy != null) {
                lines.add(
                    WifiLine(
                        1,
                        tr(
                            ctx,
                            "Tarmoq proksi orqali ishlayapti. Sizga tanish bo'lmasa, bu trafikni kuzatish belgisi bo'lishi mumkin.",
                            "Сеть работает через прокси. Если он вам незнаком, это может быть признаком слежки за трафиком.",
                            "The network goes through a proxy. If you don't recognise it, it may be traffic snooping."
                        )
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= 28 && lp != null && lp.isPrivateDnsActive) {
                lines.add(
                    WifiLine(
                        3,
                        tr(
                            ctx,
                            "«Shaxsiy DNS» yoqilgan: DNS so'rovlari shifrlanadi, lekin Qo'riqchi tarmoq nazorati ishlamaydi.",
                            "Включён «Частный DNS»: запросы шифруются, но сетевой контроль Qo'riqchi не работает.",
                            "«Private DNS» is on: DNS queries are encrypted, but the Qo'riqchi network monitor does not work."
                        )
                    )
                )
            }
            if (vpn) {
                lines.add(
                    WifiLine(
                        0,
                        tr(
                            ctx,
                            "VPN yoki DNS qalqon faol.",
                            "VPN или DNS-щит активен.",
                            "A VPN or DNS shield is active."
                        )
                    )
                )
            }
        } catch (e: SecurityException) {
            return WifiResult(
                3,
                tr(ctx, "Ruxsat yetishmayapti", "Не хватает разрешения", "Permission missing"),
                listOf(
                    WifiLine(
                        3,
                        tr(
                            ctx,
                            "Tarmoq holatini o'qish uchun yangilangan AndroidManifest.xml kerak.",
                            "Для чтения состояния сети нужен обновлённый AndroidManifest.xml.",
                            "An updated AndroidManifest.xml is needed to read the network state."
                        )
                    )
                )
            )
        } catch (e: Exception) {
            return WifiResult(
                3,
                tr(ctx, "Tekshirib bo'lmadi", "Не удалось проверить", "Couldn't check"),
                emptyList()
            )
        }
        val worst = lines.filter { it.state in 0..2 }.maxOfOrNull { it.state } ?: 3
        val title = when (worst) {
            0 -> tr(ctx, "Wi-Fi xavfsiz ko'rinadi", "Wi-Fi выглядит безопасным", "Wi-Fi looks safe")
            1 -> tr(ctx, "Wi-Fi'da ehtiyot bo'ling", "Будьте осторожны с этим Wi-Fi", "Be careful on this Wi-Fi")
            2 -> tr(ctx, "Xavfli Wi-Fi tarmog'i", "Опасная сеть Wi-Fi", "Dangerous Wi-Fi network")
            else -> tr(ctx, "Wi-Fi to'liq aniqlanmadi", "Wi-Fi определён не полностью", "Wi-Fi only partly detected")
        }
        return WifiResult(worst, title, lines)
    }
}

/** Bosh ekrandagi hisoblagichlar uchun (orqa oqimda chaqiring). */
object HomeStats {
    /** Telefon sozlamalaridagi muammolar soni. */
    fun vulnerabilities(ctx: Context): Int =
        PhoneHealth.run(ctx).checks.count { it.state == 1 || it.state == 2 }

    /** Do'kondan tashqari o'rnatilgan va sezgir ruxsati bor ilovalar soni. */
    fun privacy(ctx: Context): Int {
        val outside = Origin.list(ctx).filter { it.kind != 0 }.map { it.pkg }.toSet()
        if (outside.isEmpty()) return 0
        val audit = Scanner.permissionAudit(ctx)
        val hit = HashSet<String>()
        for ((_, apps) in audit) for ((_, pkg) in apps) if (pkg in outside) hit.add(pkg)
        return hit.size
    }
}

/** Telefon ishlab chiqaruvchilarining o'z ilovalari: o'rnatish manbasi bo'sh bo'lishi odatiy hol. */
object Vendor {
    private val prefixes = listOf(
        "com.xiaomi.", "com.miui.", "com.mi.", "com.android.", "com.google.android.", "com.samsung.", "com.sec.",
        "com.huawei.", "com.hihonor.", "com.oppo.", "com.coloros.", "com.heytap.", "com.oplus.", "com.vivo.",
        "com.bbk.", "com.realme.", "com.transsion."
    )

    fun isVendor(pkg: String): Boolean = prefixes.any { pkg.startsWith(it) }
}
