package com.sscstudystudio.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingTimerService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_DOT"
        const val EXTRA_SECONDS = "EXTRA_SECONDS"
        const val EXTRA_SUBJECT = "EXTRA_SUBJECT"
        const val EXTRA_TARGET = "EXTRA_TARGET"
        private const val CH_ID = "ssc_floating_dot_channel"
    }

    private lateinit var wm: WindowManager
    private var rootView: LinearLayout? = null
    private var dotView: View? = null
    private var timerCard: LinearLayout? = null
    private var timeText: TextView? = null
    private var targetText: TextView? = null
    private var expanded = true

    private var endTimeMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val leftSec = ((endTimeMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            timeText?.text = formatSec(leftSec)
            if (leftSec > 0L) {
                handler.postDelayed(this, 500L)
            } else {
                timeText?.text = "DONE!"
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val secs = intent?.getIntExtra(EXTRA_SECONDS, 3600) ?: 3600
        val sub = intent?.getStringExtra(EXTRA_SUBJECT) ?: "SSC FOCUS"
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: "Study Target"
        endTimeMs = System.currentTimeMillis() + secs * 1000L

        if (rootView == null) {
            createOverlay(sub, target)
        } else {
            targetText?.text = "$sub • $target"
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun createOverlay(subject: String, target: String) {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 1. Small Glowing Transparent Dot (always visible — tap to hide/show timer!)
        val dot = TextView(this).apply {
            text = "●"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#22D3EE"))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B3060B18"))
                setStroke(dp(2), Color.parseColor("#22D3EE"))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }

        // 2. Compact Rounded Dark Glass Timer Capsule (toggles on dot click)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(8), dp(6))
            val bg = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#E6070B18"))
                setStroke(dp(1), Color.parseColor("#8022D3EE"))
            }
            background = bg
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = dp(6)
            layoutParams = lp
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvTime = TextView(this).apply {
            text = "60:00"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val cleanTgt = if (target.length > 22) target.take(21) + "…" else target
        val tvTarget = TextView(this).apply {
            text = "${subject.uppercase()} • $cleanTgt"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FDE68A"))
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(8), dp(2), dp(4), dp(2))
            setOnClickListener { stopSelf() }
        }

        infoCol.addView(tvTime)
        infoCol.addView(tvTarget)
        card.addView(infoCol)
        card.addView(closeBtn)

        container.addView(dot)
        container.addView(card)

        // Drag + Tap-to-Toggle Transparent Dot / Full Timer
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        val dragListener = View.OnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = ev.rawX
                    touchY = ev.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - touchX).toInt()
                    val dy = (ev.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = initX + dx
                    params.y = initY + dy
                    wm.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        expanded = !expanded
                        card.visibility = if (expanded) View.VISIBLE else View.GONE
                        dot.alpha = if (expanded) 1.0f else 0.45f
                    }
                    true
                }
                else -> false
            }
        }
        dot.setOnTouchListener(dragListener)
        infoCol.setOnTouchListener(dragListener)

        rootView = container
        dotView = dot
        timerCard = card
        timeText = tvTime
        targetText = tvTarget
        wm.addView(container, params)
    }

    private fun formatSec(total: Long): String {
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH_ID, "SSC Floating Timer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CH_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notif = builder
            .setContentTitle("SSC Study Studio Timer Active")
            .setContentText("Tap floating dot to hide/show timer")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
        startForeground(101, notif)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        rootView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        rootView = null
        super.onDestroy()
    }
}
