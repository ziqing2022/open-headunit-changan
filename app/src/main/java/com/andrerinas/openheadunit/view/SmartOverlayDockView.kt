package com.andrerinas.openheadunit.view

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.andrerinas.openheadunit.cluster.ClusterPersistence
import com.andrerinas.openheadunit.cluster.ClusterTheme

/**
 * Smart Floating Capsule & Quick Controls Dock (灵动岛智能悬浮胶囊融合桌面).
 *
 * Provides a non-intrusive, liquid-glass floating capsule over the projection screen.
 * - Collapsed: Miniature pill displaying real-time vehicle speed and cluster mode.
 * - Expanded: Frosted quick control dock for one-touch switching of Cluster Projection,
 *   Cluster Themes, Audio/TTS Test, and Instant Return to Native Car Launcher.
 * - Automatically collapses after 4.5s idle to preserve clean projection visuals.
 */
class SmartOverlayDockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var onToggleClusterMode: (() -> Unit)? = null
    var onCycleScaleMode: (() -> ClusterPersistence.ClusterScaleMode)? = null
    var onCycleTheme: (() -> ClusterTheme)? = null
    var onTestVoice: (() -> Unit)? = null
    var onReturnCarHome: (() -> Unit)? = null
    var clusterPositionGetter: (() -> Int)? = null

    private var isExpanded = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { collapse() }

    private val capsuleLayout: LinearLayout
    private val speedTv: TextView
    private val statusTv: TextView
    private val chevronTv: TextView

    private val expandedLayout: LinearLayout
    private val themeChipTv: TextView
    private val modeChipTv: TextView
    private val scaleChipTv: TextView

    init {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(220L)
        }

        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density + 0.5f).toInt()

        // 1. Collapsed Capsule View
        capsuleLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(6f), dp(14f), dp(6f))

            val bg = GradientDrawable().apply {
                setColor(Color.argb(210, 15, 23, 42))
                cornerRadius = dp(19f).toFloat()
                setStroke(dp(1.2f), Color.argb(90, 255, 255, 255))
            }
            background = bg
        }

        speedTv = TextView(context).apply {
            text = "0 km/h"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val dotTv = TextView(context).apply {
            text = " · "
            setTextColor(Color.argb(120, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        statusTv = TextView(context).apply {
            text = "智能融合桌面"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        chevronTv = TextView(context).apply {
            text = " ▾"
            setTextColor(Color.argb(160, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        capsuleLayout.addView(speedTv)
        capsuleLayout.addView(dotTv)
        capsuleLayout.addView(statusTv)
        capsuleLayout.addView(chevronTv)

        capsuleLayout.setOnClickListener {
            toggleExpand()
        }

        // 2. Expanded Quick Controls Dock
        expandedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            visibility = View.GONE

            val bg = GradientDrawable().apply {
                setColor(Color.argb(235, 15, 23, 42))
                cornerRadius = dp(20f).toFloat()
                setStroke(dp(1.5f), Color.argb(100, 255, 255, 255))
            }
            background = bg
        }

        // Top Header of expanded dock
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), 0, dp(4f), dp(8f))
        }

        val headerTitle = TextView(context).apply {
            text = "长安 · 无边界智能融合桌面"
            setTextColor(Color.argb(200, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(context).apply {
            text = "▴ 收起"
            setTextColor(Color.parseColor("#4A90E2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
            setOnClickListener { collapse() }
        }

        headerLayout.addView(headerTitle)
        headerLayout.addView(closeBtn)
        expandedLayout.addView(headerLayout)

        // Action Chips Row
        val chipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun createChip(title: String, subtitle: String, onClick: () -> Unit): Pair<LinearLayout, TextView> {
            val chip = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
                val chipParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(4f), 0, dp(4f), 0)
                }
                layoutParams = chipParams

                val chipBg = GradientDrawable().apply {
                    setColor(Color.argb(50, 255, 255, 255))
                    cornerRadius = dp(12f).toFloat()
                    setStroke(dp(1f), Color.argb(60, 255, 255, 255))
                }
                background = chipBg

                setOnClickListener {
                    onClick()
                    resetInactivityTimer()
                }
            }

            val tTv = TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            val subTv = TextView(context).apply {
                text = subtitle
                setTextColor(Color.parseColor("#80D8FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(0, dp(2f), 0, 0)
            }

            chip.addView(tTv)
            chip.addView(subTv)
            return Pair(chip, subTv)
        }

        // Chip 1: Cluster Mode
        val (modeChip, modeSub) = createChip("仪表投屏", "原车") {
            onToggleClusterMode?.invoke()
            updateStateLabels()
        }
        modeChipTv = modeSub

        // Chip 2: Cluster Scale Mode
        val (scaleChip, scaleSub) = createChip("画面比例", "拉伸铺满") {
            val next = onCycleScaleMode?.invoke()
            if (next != null) {
                scaleChipTv.text = next.title
            }
            updateStateLabels()
        }
        scaleChipTv = scaleSub

        // Chip 3: Cluster Theme
        val (themeChip, themeSub) = createChip("仪表主题", "科技深蓝") {
            onCycleTheme?.invoke()
            updateStateLabels()
        }
        themeChipTv = themeSub

        // Chip 4: Audio/TTS Test
        val (ttsChip, _) = createChip("播报测试", "通道保活") {
            onTestVoice?.invoke()
        }

        // Chip 5: Return to Car Launcher
        val (homeChip, _) = createChip("原车桌面", "融合返回") {
            onReturnCarHome?.invoke()
        }

        chipsRow.addView(modeChip)
        chipsRow.addView(scaleChip)
        chipsRow.addView(themeChip)
        chipsRow.addView(ttsChip)
        chipsRow.addView(homeChip)
        expandedLayout.addView(chipsRow)

        // Add both to this root FrameLayout
        val capsuleParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(8f)
        }
        addView(capsuleLayout, capsuleParams)

        val expandedParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(8f)
        }
        addView(expandedLayout, expandedParams)

        updateStateLabels()
    }

    fun updateSpeed(speedKmh: Int) {
        speedTv.text = "$speedKmh km/h"
    }

    fun updateStateLabels() {
        val isMirror = ClusterPersistence.loadClusterMirrorMode(context)
        val currentTheme = ClusterPersistence.loadClusterTheme(context)
        val currentScaleMode = ClusterPersistence.loadClusterScaleMode(context)
        val pos = clusterPositionGetter?.invoke() ?: (if (isMirror) 0 else 1)
        val modeStr = when (pos) {
            0 -> "整机镜像"
            1 -> "极简卡片"
            else -> "原车仪表"
        }

        modeChipTv.text = modeStr
        scaleChipTv.text = currentScaleMode.title
        themeChipTv.text = currentTheme.displayName
        statusTv.text = "$modeStr · ${currentScaleMode.title} · ${currentTheme.displayName}"
        speedTv.setTextColor(currentTheme.speedColor)
    }

    private fun toggleExpand() {
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }

    fun expand() {
        if (isExpanded) return
        isExpanded = true
        updateStateLabels()
        capsuleLayout.visibility = View.GONE
        expandedLayout.visibility = View.VISIBLE
        resetInactivityTimer()
    }

    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        mainHandler.removeCallbacks(collapseRunnable)
        expandedLayout.visibility = View.GONE
        capsuleLayout.visibility = View.VISIBLE
        updateStateLabels()
    }

    private fun resetInactivityTimer() {
        mainHandler.removeCallbacks(collapseRunnable)
        mainHandler.postDelayed(collapseRunnable, 4500L)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(collapseRunnable)
    }
}
