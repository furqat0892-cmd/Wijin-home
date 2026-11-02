package uz.qoriqchi.pro

import java.net.HttpURLConnection
import java.net.URL

/** VirusTotal'ga faqat fayl SHA-256 xeshi yuboriladi, faylning o'zi emas. Fon oqimida chaqiring. */
object VT {

    /** (xabar, xavfli deb topgan antiviruslar soni) */
    fun lookup(key: String, sha256: String): Pair<String, Int> {
        val c = URL("https://www.virustotal.com/api/v3/files/$sha256").openConnection() as HttpURLConnection
        return try {
            c.setRequestProperty("x-apikey", key)
            c.connectTimeout = 10000
            c.readTimeout = 15000
            when (c.responseCode) {
                200 -> {
                    val body = c.inputStream.bufferedReader().readText()
                    val st = org.json.JSONObject(body)
                        .getJSONObject("data")
                        .getJSONObject("attributes")
                        .getJSONObject("last_analysis_stats")
                    val mal = st.optInt("malicious")
                    val sus = st.optInt("suspicious")
                    val ok = st.optInt("harmless") + st.optInt("undetected")
                    Pair("$mal ta antivirus xavfli, $sus ta shubhali deb topdi ($ok ta toza)", mal)
                }
                404 -> Pair("Bazada topilmadi (yangi yoki noyob fayl, ehtiyot bo'ling)", 0)
                401, 403 -> Pair("API kaliti noto'g'ri", 0)
                429 -> Pair("So'rovlar limiti tugadi, keyinroq urinib ko'ring", 0)
                else -> Pair("Xatolik: ${c.responseCode}", 0)
            }
        } catch (e: Exception) {
            Pair("Internetga ulanib bo'lmadi", 0)
        } finally {
            c.disconnect()
        }
    }
}
