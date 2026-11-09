package uz.qoriqchi.pro

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Har 24 soatda o'rnatilgan ilovalarni avtomatik tekshiradi. */
class DailyScanService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try {
                CrashLog.install(this)
                scanAll(this)
            } catch (e: Exception) {
                // keyingi kunga qoldiramiz
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 77

        fun schedule(c: Context) {
            try {
                val js = c.getSystemService(JobScheduler::class.java)
                if (js.getPendingJob(JOB_ID) != null) return
                val info = JobInfo.Builder(JOB_ID, ComponentName(c, DailyScanService::class.java))
                    .setPeriodic(24L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build()
                js.schedule(info)
            } catch (e: Exception) {
                // jadval o'rnatilmasa, qo'lda tekshirish ishlayveradi
            }
        }

        fun cancel(c: Context) {
            try {
                c.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            } catch (e: Exception) {
                // e'tibor bermaymiz
            }
        }

        fun scanAll(c: Context) {
            Notifier.channels(c)
            val trust = Trust.all(c)
            val list = Scanner.scanInstalled(c).filter { it.pkg !in trust }
            val risky = list.filter { it.score >= 25 }
            val p = c.getSharedPreferences("qp", Context.MODE_PRIVATE)
            val seen = HashSet(p.getStringSet("alerted", emptySet()) ?: emptySet())
            for (r in list) {
                if (r.score < 50) continue
                val k = "${r.pkg}#${r.score}"
                if (k in seen) continue
                seen.add(k)
                Notifier.alert(c, r, r.pkg, null)
            }
            p.edit()
                .putStringSet("alerted", seen)
                .putLong("lastScan", System.currentTimeMillis())
                .putInt("lastTotal", list.size)
                .putInt("lastRisky", risky.size)
                .putInt("lastDanger", list.count { it.score >= 50 })
                .putInt("lastSusp", list.count { it.score in 25..49 })
                .apply()
            try {
                PermWatch.check(c)
            } catch (e: Exception) {
                // ruxsat kuzatuvi xatosi kunlik tekshiruvni to'xtatmasin
            }
            Store.add(c, "Kunlik tekshiruv", "${list.size} ta ilova: ${risky.size} tasi e'tibor talab qiladi", 0)
        }
    }
}
