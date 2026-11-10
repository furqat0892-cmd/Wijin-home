package uz.qoriqchi.pro

import android.app.Activity
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.TextView

/** Tanlangan ilovalarni ochishdan oldin telefon ekran qulfini (PIN, barmoq izi) so'rash. */
object AppLock {
    private fun prefs(c: Context) = c.getSharedPreferences("qp", Context.MODE_PRIVATE)

    /** Hozir qulfi ochilgan ilovalar: ilovadan chiqilsa yoki ekran o'chsa, yana qulflanadi. */
    val unlocked: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

    fun enabled(c: Context) = prefs(c).getBoolean("al_on", false)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("al_on", on).apply()
        if (on) start(c) else c.stopService(Intent(c, AppLockService::class.java))
    }

    fun pkgs(c: Context): Set<String> = HashSet(prefs(c).getStringSet("al_pkgs", emptySet()) ?: emptySet())

    fun setLocked(c: Context, pkg: String, on: Boolean) {
        val s = HashSet(pkgs(c))
        if (on) s.add(pkg) else s.remove(pkg)
        prefs(c).edit().putStringSet("al_pkgs", s).apply()
    }

    @Suppress("DEPRECATION")
    fun hasUsage(c: Context): Boolean = try {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    fun hasOverlay(c: Context): Boolean = try {
        Settings.canDrawOverlays(c)
    } catch (e: Exception) {
        false
    }

    fun ready(c: Context) = hasUsage(c) && hasOverlay(c)

    fun start(c: Context) {
        if (!enabled(c) || !ready(c)) return
        try {
            c.startForegroundService(Intent(c, AppLockService::class.java))
        } catch (e: Exception) {
            // tizim ruxsat bermasa, ilova ochilganda qayta uriniladi
        }
    }
}

class AppLockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastFg: String? = null
    private var screenReceiver: BroadcastReceiver? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                poll()
            } catch (e: Exception) {
                // keyingi urinishda davom etadi
            }
            handler.postDelayed(this, 600)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        val n = Notification.Builder(this, Notifier.CH_GUARD)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(tr(this, "Ilova qulfi yoqilgan", "Блокировка приложений включена", "App lock is on"))
            .setContentText(
                tr(
                    this,
                    "Tanlangan ilovalar ochilishdan oldin ekran qulfini so'raydi.",
                    "Выбранные приложения запрашивают блокировку экрана при открытии.",
                    "Selected apps ask for the screen lock before opening."
                )
            )
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, n)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                AppLock.unlocked.clear()
                lastFg = null
            }
        }
        registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF))
        screenReceiver = r
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // allaqachon o'chirilgan
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun poll() {
        if (!AppLock.enabled(this) || !AppLock.ready(this)) return
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 4000, now)
        val e = UsageEvents.Event()
        var fg: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) fg = e.packageName
        }
        if (fg == null || fg == lastFg || fg == packageName) return
        val prev = lastFg
        lastFg = fg
        if (prev != null) AppLock.unlocked.remove(prev)
        if (fg in AppLock.pkgs(this) && fg !in AppLock.unlocked) {
            val i = Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("pkg", fg)
            startActivity(i)
        }
    }
}

class LockActivity : Activity() {

    private var target = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra("pkg") ?: ""
        window.setBackgroundDrawable(ColorDrawable(0xFF0F172A.toInt()))
        setContentView(TextView(this).apply {
            text = "🔒\n" + tr(this@LockActivity, "Qo'riqchi Pro", "Qo'riqchi Pro", "Qo'riqchi Pro")
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        val kg = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!kg.isDeviceSecure) {
            // Telefonda ekran qulfi yo'q: himoya qila olmaymiz, ilovani o'tkazib yuboramiz
            AppLock.unlocked.add(target)
            finish()
            return
        }
        val i = kg.createConfirmDeviceCredentialIntent(
            tr(this, "Ilova qulflangan", "Приложение заблокировано", "App is locked"),
            tr(this, "Ochish uchun ekran qulfini kiriting", "Введите блокировку экрана, чтобы открыть", "Enter your screen lock to open")
        )
        if (i == null) {
            AppLock.unlocked.add(target)
            finish()
        } else {
            startActivityForResult(i, 7)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && resultCode == RESULT_OK) {
            AppLock.unlocked.add(target)
            finish()
        } else {
            goHome()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
