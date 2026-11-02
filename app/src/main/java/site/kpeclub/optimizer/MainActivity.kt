package site.kpeclub.optimizer

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private lateinit var am: ActivityManager
    private lateinit var ramText: TextView
    private lateinit var ramBar: ProgressBar
    private lateinit var listBox: LinearLayout
    private lateinit var notifBtn: Button
    private val prefs by lazy { getSharedPreferences("kpe", Context.MODE_PRIVATE) }
    private var apps: List<AppItem> = emptyList()

    private val cBg = Color.parseColor("#0D0D0D")
    private val cCard = Color.parseColor("#16181F")
    private val cBlue = Color.parseColor("#2563EB")
    private val cTeal = Color.parseColor("#14B8A6")
    private val cText = Color.parseColor("#E5E7EB")
    private val cMuted = Color.parseColor("#9CA3AF")
    private val mb = 1024L * 1024L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        setContentView(buildUi())
        refreshRam()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (::ramText.isInitialized) refreshRam()
        if (::notifBtn.isInitialized) updateNotifBtn()
    }

    // ---------- UI ----------

    private fun card(): GradientDrawable = GradientDrawable().apply {
        setColor(cCard)
        cornerRadius = dp(16).toFloat()
    }

    private fun gradient(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cBlue, cTeal)).apply {
            cornerRadius = dp(14).toFloat()
        }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 15f
        background = gradient()
        minHeight = 0
        minimumHeight = dp(44)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { onClick() }
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
            setBackgroundColor(cBg)
        }

        root.addView(TextView(this).apply {
            text = "KPE Optimizer"
            setTextColor(cText)
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Stop background apps. Free RAM."
            setTextColor(cMuted)
            textSize = 14f
            setPadding(0, dp(4), 0, dp(16))
        })

        // RAM card
        val ramCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card()
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        ramText = TextView(this).apply {
            setTextColor(cText)
            textSize = 16f
        }
        ramBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(cTeal)
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2D36"))
        }
        ramCard.addView(ramText)
        ramCard.addView(ramBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(12)).apply { topMargin = dp(10) })
        root.addView(ramCard)

        // Boost
        root.addView(button("⚡  Boost now") { boost() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        // Notification blocker
        notifBtn = button("") { toggleNotif() }
        root.addView(notifBtn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        root.addView(TextView(this).apply {
            text = "Blocks notifications of your apps. Calls and alarms are never blocked."
            setTextColor(cMuted)
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        })
        updateNotifBtn()

        // Tools
        val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tools.addView(toolButton("Battery optimization") {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        tools.addView(toolButton("Running services (Developer options)") {
            open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                "Turn on Developer options first: Settings > About phone > tap Build number 7 times.")
        })
        tools.addView(toolButton("Free up storage") {
            open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        })
        root.addView(tools)

        root.addView(TextView(this).apply {
            text = "Your apps"
            setTextColor(cText)
            textSize = 18f
            setPadding(0, dp(20), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Tap Stop to close an app. Tap the app name to open its settings (Force stop)."
            setTextColor(cMuted)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        return ScrollView(this).apply {
            setBackgroundColor(cBg)
            addView(root)
        }
    }

    private fun toolButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(cText)
        textSize = 14f
        minHeight = 0
        minimumHeight = dp(44)
        background = card()
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }

    private fun renderApps() {
        listBox.removeAllViews()
        if (apps.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "No apps found"
                setTextColor(cMuted)
                textSize = 14f
            })
        }
        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = card()
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            row.addView(ImageView(this).apply { setImageDrawable(app.icon) },
                LinearLayout.LayoutParams(dp(40), dp(40)))
            row.addView(TextView(this).apply {
                text = app.label
                setTextColor(cText)
                textSize = 15f
                setPadding(dp(12), 0, dp(8), 0)
                setOnClickListener { openAppInfo(app.pkg) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Stop"
                isAllCaps = false
                setTextColor(Color.WHITE)
                textSize = 13f
                minHeight = 0
                minimumHeight = dp(36)
                minWidth = dp(64)
                background = gradient()
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setOnClickListener {
                    am.killBackgroundProcesses(app.pkg)
                    toast("Stopped ${app.label}")
                    postDelayed({ refreshRam() }, 600)
                }
            })
            listBox.addView(row)
        }
    }

    // ---------- Logic ----------

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.applicationInfo }
                .filter { it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .distinctBy { it.packageName }
                .map { AppItem(it.loadLabel(pm).toString(), it.packageName, it.loadIcon(pm)) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                apps = items
                renderApps()
            }
        }.start()
    }

    private fun availMem(): Long {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem
    }

    private fun refreshRam() {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val used = mi.totalMem - mi.availMem
        val pct = (used * 100 / mi.totalMem).toInt()
        ramText.text = "RAM: ${used / mb} / ${mi.totalMem / mb} MB  ($pct%)"
        ramBar.progress = pct
    }

    private fun boost() {
        val before = availMem()
        apps.forEach { am.killBackgroundProcesses(it.pkg) }
        ramText.postDelayed({
            val freed = ((availMem() - before) / mb).coerceAtLeast(0)
            refreshRam()
            toast("Done. Freed about $freed MB")
        }, 1000)
    }

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun updateNotifBtn() {
        val on = listenerEnabled() && prefs.getBoolean("block", false)
        notifBtn.text = if (on) "🔕  Notifications blocked: ON" else "🔔  Block app notifications: OFF"
    }

    private fun toggleNotif() {
        if (!listenerEnabled()) {
            prefs.edit().putBoolean("block", true).apply()
            toast("Turn on access for KPE Optimizer")
            open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            prefs.edit().putBoolean("block", !prefs.getBoolean("block", false)).apply()
            updateNotifBtn()
        }
    }

    private fun openAppInfo(pkg: String) {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }

    private fun open(intent: Intent, failMsg: String = "Not available on this phone") {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(failMsg)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
