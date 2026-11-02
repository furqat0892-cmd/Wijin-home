package uz.qoriqchi.pro

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Real vaqt himoyasi: yangi o'rnatilgan ilovalar va yuklab olingan APK fayllarni kuzatadi. */

data class Event(val time: Long, val title: String, val detail: String, val score: Int)

/** VirusTotal natijasini hisobga olib, ballni yangilaydi. */
fun applyVt(r: Report, key: String, sha: String): Report {
    val (msg, mal) = VT.lookup(key, sha)
    val extra = when {
        mal >= 3 -> 60
        mal >= 1 -> 30
        else -> 0
    }
    val findings = if (extra > 0) r.findings + Finding(extra, "VirusTotal antiviruslari xavf topdi") else r.findings
    return r.copy(score = minOf(100, r.score + extra), findings = findings, vt = msg)
}

/** Yuklab olingan fayllarni kuzatish uchun fayl ruxsati bormi. */
fun hasFilesAccess(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

/** Foydalanuvchi "ishonaman" deb belgilagan ilovalar: ular ogohlantirilmaydi. */
object Trust {
    private fun prefs(c: Context) = c.getSharedPreferences("qp", Context.MODE_PRIVATE)

    fun all(c: Context): Set<String> = HashSet(prefs(c).getStringSet("trust", emptySet()) ?: emptySet())

    fun has(c: Context, pkg: String) = pkg in all(c)

    fun add(c: Context, pkg: String) {
        val s = HashSet(all(c))
        s.add(pkg)
        prefs(c).edit().putStringSet("trust", s).apply()
    }
}

object Store {
    private const val KEY = "events"

    private fun prefs(c: Context) = c.getSharedPreferences("qp", Context.MODE_PRIVATE)

    @Synchronized
    fun add(c: Context, title: String, detail: String, score: Int) {
        val old = try {
            JSONArray(prefs(c).getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = JSONArray()
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        o.put("a", title)
        o.put("b", detail)
        o.put("s", score)
        out.put(o)
        var i = 0
        while (i < old.length() && out.length() < 30) {
            out.put(old.getJSONObject(i))
            i++
        }
        prefs(c).edit().putString(KEY, out.toString()).apply()
    }

    @Synchronized
    fun list(c: Context): List<Event> {
        val res = ArrayList<Event>()
        try {
            val arr = JSONArray(prefs(c).getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                res.add(Event(o.optLong("t"), o.optString("a"), o.optString("b"), o.optInt("s")))
            }
        } catch (e: Exception) {
            // bo'sh ro'yxat qaytadi
        }
        return res
    }

    @Synchronized
    fun clear(c: Context) {
        prefs(c).edit().remove(KEY).apply()
    }
}

object Notifier {
    const val CH_ALERT = "qp_alert"
    const val CH_GUARD = "qp_guard"
    private const val PI = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_GUARD, "Himoya holati", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Xavf ogohlantirishlari", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun guardNotification(c: Context): Notification {
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PI)
        return Notification.Builder(c, CH_GUARD)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Qo'riqchi Pro faol")
            .setContentText("Yangi ilova va yuklangan APK fayllar kuzatilmoqda")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    fun alert(c: Context, r: Report, pkg: String?, filePath: String?) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val id = (pkg ?: filePath ?: r.label).hashCode()
        val top = r.findings
            .filter { it.points > 0 }
            .sortedByDescending { it.points }
            .take(3)
            .joinToString("\n") { "• " + it.text }
        val openIntent = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(c, id, openIntent, PI)

        val b = Notification.Builder(c, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${r.level.title}: ${r.label} (${r.score}/100)")
            .setContentText(top.lines().firstOrNull() ?: "")
            .setStyle(Notification.BigTextStyle().bigText(top))
            .setContentIntent(open)
            .setAutoCancel(true)

        if (pkg != null) {
            val delIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val del = PendingIntent.getActivity(c, id + 1, delIntent, PI)
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(c, android.R.drawable.ic_menu_delete),
                    "Ilovani o'chirish",
                    del
                ).build()
            )
        }
        if (filePath != null) {
            val i = Intent(c, ActionReceiver::class.java)
                .setAction(ActionReceiver.DELETE_FILE)
                .putExtra("path", filePath)
                .putExtra("nid", id)
            val del = PendingIntent.getBroadcast(c, id + 2, i, PI)
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(c, android.R.drawable.ic_menu_delete),
                    "Faylni o'chirish",
                    del
                ).build()
            )
        }
        nm.notify(id, b.build())
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != DELETE_FILE) return
        val p = i.getStringExtra("path")
        val ok = try {
            p != null && File(p).delete()
        } catch (e: Exception) {
            false
        }
        c.getSystemService(NotificationManager::class.java).cancel(i.getIntExtra("nid", 0))
        Store.add(
            c,
            if (ok) "Xavfli fayl o'chirildi" else "Faylni o'chirib bo'lmadi",
            p ?: "",
            0
        )
    }

    companion object {
        const val DELETE_FILE = "uz.qoriqchi.pro.DELETE_FILE"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val on = c.getSharedPreferences("qp", Context.MODE_PRIVATE).getBoolean("guard", false)
        if (on) {
            try {
                c.startForegroundService(Intent(c, GuardService::class.java))
            } catch (e: Exception) {
                // tizim ruxsat bermasa, ilova ochilganda yoqiladi
            }
        }
    }
}

class GuardService : Service() {

    private val exec: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private var receiver: BroadcastReceiver? = null
    private val sizes = HashMap<String, Long>()
    private val done = HashSet<String>()
    private var seeded = false

    private val prefs get() = getSharedPreferences("qp", Context.MODE_PRIVATE)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        val n = Notifier.guardNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        registerPackageReceiver()
        exec.scheduleWithFixedDelay(
            Runnable {
                try {
                    pollDownloads()
                } catch (e: Exception) {
                    // keyingi urinishda davom etadi
                }
            },
            3, 4, TimeUnit.SECONDS
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try {
            receiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // allaqachon o'chirilgan
        }
        exec.shutdownNow()
        super.onDestroy()
    }

    // ---------- Yangi o'rnatilgan ilovalar ----------

    private fun registerPackageReceiver() {
        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val pkg = i.data?.schemeSpecificPart ?: return
                if (i.action == Intent.ACTION_PACKAGE_ADDED &&
                    i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                ) return
                if (pkg == packageName) return
                exec.execute { scanPackage(pkg) }
            }
        }
        receiver = r
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(r, f, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(r, f)
        }
    }

    private fun scanPackage(pkg: String) {
        try {
            if (Trust.has(this, pkg)) return
            var rep = Scanner.analyzePackage(this, pkg) ?: return
            val key = prefs.getString("vt", "") ?: ""
            val path = rep.apkPath
            if (path != null) {
                rep = DeepScan.apply(rep, path)
                if (key.isNotBlank()) rep = applyVt(rep, key, Scanner.sha256File(File(path)))
            }
            record(rep, pkg, null)
        } catch (e: Exception) {
            // bitta ilovadagi xato butun himoyani to'xtatmasin
        }
    }

    // ---------- Yuklab olingan APK fayllar ----------

    private fun findApks(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(root, "Download"),
            File(root, "Telegram/Telegram Documents"),
            File(root, "Documents")
        )
        val out = ArrayList<File>()
        fun walk(d: File, depth: Int) {
            val list = d.listFiles() ?: return
            for (x in list) {
                if (x.isDirectory) {
                    if (depth < 2) walk(x, depth + 1)
                } else if (x.name.endsWith(".apk", ignoreCase = true)) {
                    out.add(x)
                }
            }
        }
        for (d in dirs) walk(d, 0)
        return out
    }

    private fun pollDownloads() {
        if (!hasFilesAccess(this)) return
        val apks = findApks()
        if (!seeded) {
            // ilova yoqilguncha mavjud fayllar uchun ogohlantirish chiqarmaymiz
            apks.forEach { done.add(it.absolutePath) }
            seeded = true
            return
        }
        for (f in apks) {
            val p = f.absolutePath
            if (p in done) continue
            val size = f.length()
            val prev = sizes[p]
            sizes[p] = size
            // fayl to'liq yuklanganini (hajmi o'zgarmay qolganini) kutamiz
            if (size > 0 && prev == size) {
                done.add(p)
                sizes.remove(p)
                scanFile(f)
            }
        }
    }

    private fun scanFile(f: File) {
        try {
            val sha = Scanner.sha256File(f)
            var rep = Scanner.scanArchive(this, f, sha)
            if (rep == null) {
                Store.add(this, "APK o'qib bo'lmadi", f.name, 0)
                return
            }
            rep = DeepScan.apply(rep, f.absolutePath)
            val key = prefs.getString("vt", "") ?: ""
            if (key.isNotBlank()) rep = applyVt(rep, key, sha)
            record(rep, null, f.absolutePath)
        } catch (e: Exception) {
            // keyingi faylga o'tamiz
        }
    }

    private fun record(rep: Report, pkg: String?, path: String?) {
        if (rep.score >= 25) {
            Notifier.alert(this, rep, pkg, path)
            val why = rep.findings.filter { it.points > 0 }.maxByOrNull { it.points }?.text ?: ""
            Store.add(this, "${rep.level.title}: ${rep.label}", why, rep.score)
        } else {
            Store.add(this, "Tekshirildi: ${rep.label}", "Xavfli belgilar topilmadi", rep.score)
        }
    }
}
