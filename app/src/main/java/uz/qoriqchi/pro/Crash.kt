package uz.qoriqchi.pro

import android.content.Context
import android.util.Log
import java.io.File

/** Ilova yiqilsa, xato matnini faylga yozadi; keyingi ochilishda ekranda ko'rsatiladi. */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                file(app).writeText(Log.getStackTraceString(e))
            } catch (x: Exception) {
                // yozib bo'lmasa, davom etamiz
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(c: Context): String? = try {
        val f = file(c)
        if (f.exists()) f.readText().take(6000) else null
    } catch (e: Exception) {
        null
    }

    fun clear(c: Context) {
        try {
            file(c).delete()
        } catch (e: Exception) {
            // e'tibor bermaymiz
        }
    }
}
