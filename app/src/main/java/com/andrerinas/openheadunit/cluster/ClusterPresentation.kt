package com.andrerinas.openheadunit.cluster

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.andrerinas.openheadunit.utils.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Presentation window for the Instrument Cluster (Secondary Display) on Changan and other multi-screen vehicles.
 *
 * Supports:
 * 1. Mirror Mode: Whole-screen mirror with customizable Rect/Crop matrix.
 * 2. Nav Mode: Navigation cards (Turn, ETA, Song) on automotive dark background with standby HUD.
 * 3. Native Car Screen: Smoothly hidden via setMapVisible(false) allowing native cluster UI to show.
 */
class ClusterPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display) {

    private val projectionSize: Point = Point().also {
        @Suppress("DEPRECATION")
        display.getRealSize(it)
    }

    private var mirrorView: ClusterMirrorView? = null
    private var standbyLayout: LinearLayout? = null
    private var titleTextView: TextView? = null
    private var clockTextView: TextView? = null
    private var speedTextView: TextView? = null
    private var turnCardView: ClusterTurnCardView? = null
    private var etaCardView: ClusterEtaCardView? = null
    private var songView: ClusterSongView? = null
    private var rootView: FrameLayout? = null

    private var mirrorMode = true
    private var currentTheme = ClusterPersistence.loadClusterTheme(context)
    private var scaleMode = ClusterPersistence.loadClusterScaleMode(context)
    private var cropLeftPx = ClusterPersistence.loadClusterMirrorLeftCropPx(context)
    private var xPct = ClusterPersistence.loadClusterCropLeftPercent(context)
    private var yPct = ClusterPersistence.loadClusterCropTopPercent(context)
    private var wPct = ClusterPersistence.loadClusterCropWidthPercent(context)
    private var hPct = ClusterPersistence.loadClusterCropHeightPercent(context)
    private var lastBmW = -1
    private var lastBmH = -1
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val clockRunnable = object : Runnable {
        override fun run() {
            clockTextView?.text = clockFormat.format(Date())
            val loc = com.andrerinas.openheadunit.location.LocationHolder.currentLocation(context)
            if (loc != null && loc.hasSpeed()) {
                val kmh = (loc.speed * 3.6f + 0.5f).toInt()
                speedTextView?.text = "$kmh km/h"
                speedTextView?.visibility = View.VISIBLE
            } else {
                speedTextView?.text = "0 km/h"
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }

    inner class ClusterMirrorView(context: Context) : View(context) {
        var frameBitmap: Bitmap? = null
        val drawMatrix = Matrix()
        private var clipL = 0
        private var clipT = 0
        private var clipR = 0
        private var clipB = 0
        private var isClipEnabled = false
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        fun setClipArea(left: Int, top: Int, right: Int, bottom: Int) {
            clipL = left
            clipT = top
            clipR = right
            clipB = bottom
            isClipEnabled = (right > left && bottom > top)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bm = frameBitmap ?: return
            if (bm.isRecycled) return
            if (isClipEnabled) {
                canvas.save()
                canvas.clipRect(clipL, clipT, clipR, clipB)
                canvas.drawBitmap(bm, drawMatrix, paint)
                canvas.restore()
            } else {
                canvas.drawBitmap(bm, drawMatrix, paint)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(15, 22, 30))
        }
        rootView = root

        // 1. Mirror View (for Whole-Screen Mirror)
        val mv = ClusterMirrorView(context)
        mirrorView = mv
        root.addView(mv, FrameLayout.LayoutParams(-1, -1))

        // 2. Standby HUD Layout (Clock + Status when no navigation route active)
        val standby = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        standbyLayout = standby

        val titleTv = TextView(context).apply {
            text = "Android Auto 仪表投屏"
            textSize = 28f
            setTextColor(Color.parseColor("#4A90E2"))
            gravity = Gravity.CENTER
        }
        titleTextView = titleTv
        val clockTv = TextView(context).apply {
            text = clockFormat.format(Date())
            textSize = 58f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 4)
        }
        clockTextView = clockTv

        val speedTv = TextView(context).apply {
            text = "0 km/h"
            textSize = 32f
            setTextColor(Color.parseColor("#00E5FF"))
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        speedTextView = speedTv

        val subTv = TextView(context).apply {
            text = "等待手机导航启动 · 三指横滑切换仪表"
            textSize = 18f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
        }
        standby.addView(titleTv)
        standby.addView(clockTv)
        standby.addView(speedTv)
        standby.addView(subTv)

        val standbyParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        root.addView(standby, standbyParams)

        // 3. Navigation Cards
        turnCardView = ClusterTurnCardView(context).apply { visibility = View.GONE }
        etaCardView = ClusterEtaCardView(context).apply { visibility = View.GONE }
        songView = ClusterSongView(context).apply { visibility = View.GONE }

        root.addView(turnCardView, FrameLayout.LayoutParams(-1, -1))
        root.addView(etaCardView, FrameLayout.LayoutParams(-1, -1))
        root.addView(songView, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)

        applyTheme(currentTheme)

        mirrorMode = ClusterPersistence.loadClusterMirrorMode(context)
        setMode(mirrorMode)
        mainHandler.post(clockRunnable)
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(clockRunnable)
    }

    fun setMapVisible(visible: Boolean) {
        val w = window ?: return
        val alpha = if (visible) 1f else 0f
        if (w.attributes.alpha != alpha) {
            w.attributes = w.attributes.apply { this.alpha = alpha }
        }
        val target = if (visible) View.VISIBLE else View.GONE
        if (w.decorView.visibility != target) {
            w.decorView.visibility = target
        }
        rootView?.visibility = target
    }

    fun setMode(mirror: Boolean) {
        mirrorMode = mirror
        ClusterPersistence.saveClusterMirrorMode(context, mirror)
        mirrorView?.visibility = if (mirror) View.VISIBLE else View.GONE
        if (mirror) {
            refreshCrop()
            val hasFrame = mirrorView?.frameBitmap != null
            standbyLayout?.visibility = if (hasFrame) View.GONE else View.VISIBLE
        } else {
            val hasActiveTurn = turnCardView?.visibility == View.VISIBLE
            standbyLayout?.visibility = if (hasActiveTurn) View.GONE else View.VISIBLE
        }
    }

    fun updateMirrorFrame(bitmap: Bitmap?) {
        if (!mirrorMode) return
        val mv = mirrorView ?: return
        mv.frameBitmap = bitmap
        val bw = bitmap?.width ?: -1
        val bh = bitmap?.height ?: -1
        if (bw != lastBmW || bh != lastBmH) {
            lastBmW = bw
            lastBmH = bh
            refreshCrop()
        }
        mv.invalidate()
        if (bitmap != null && standbyLayout?.visibility == View.VISIBLE) {
            standbyLayout?.visibility = View.GONE
        }
    }

    fun refreshCrop() {
        val mv = mirrorView ?: return
        val baseW = if (projectionSize.x > 0) projectionSize.x else 1920
        val baseH = if (projectionSize.y > 0) projectionSize.y else 720

        if (!mirrorMode) {
            mv.drawMatrix.reset()
            return
        }

        val bm = mv.frameBitmap
        val bmW = bm?.width ?: baseW
        val bmH = bm?.height ?: baseH

        // Scaled to bmW coordinate space (cropLeftPx calibrated against baseW)
        val scaledCropLeft = (cropLeftPx.toFloat() * bmW / baseW).coerceAtLeast(0f)
        val availW = (bmW - scaledCropLeft).coerceAtLeast(1f)

        val w = (wPct * baseW / 100f).coerceAtLeast(1f)
        val h = (hPct * baseH / 100f).coerceAtLeast(1f)
        val x = xPct * baseW / 100f
        val y = yPct * baseH / 100f

        val scaleX: Float
        val scaleY: Float
        when (scaleMode) {
            ClusterPersistence.ClusterScaleMode.FIT -> {
                val s = minOf(w / availW, h / bmH.toFloat())
                scaleX = s
                scaleY = s
            }
            ClusterPersistence.ClusterScaleMode.COVER -> {
                val s = maxOf(w / availW, h / bmH.toFloat())
                scaleX = s
                scaleY = s
            }
            ClusterPersistence.ClusterScaleMode.FILL -> {
                scaleX = w / availW
                scaleY = h / bmH.toFloat()
            }
        }

        val drawnW = availW * scaleX
        val drawnH = bmH * scaleY
        val picLeft = x + (w - drawnW) / 2f
        val picTop = y + (h - drawnH) / 2f
        val offsetX = picLeft - scaledCropLeft * scaleX
        val offsetY = picTop

        mv.drawMatrix.apply {
            reset()
            setScale(scaleX, scaleY)
            postTranslate(offsetX, offsetY)
        }

        val clipLeft = if (picLeft > 0f) picLeft.toInt() else 0
        val clipRight = if (picLeft > 0f && (picLeft + drawnW) < baseW) (picLeft + drawnW).toInt() else if (mv.width > 0) mv.width else baseW
        val clipTop = if (picTop > 0f) picTop.toInt() else 0
        val clipBottom = if (mv.height > 0) mv.height else baseH
        mv.setClipArea(clipLeft, clipTop, clipRight, clipBottom)
    }

    fun applyScaleMode(mode: ClusterPersistence.ClusterScaleMode) {
        scaleMode = mode
        refreshCrop()
        mirrorView?.invalidate()
    }

    fun updateTurnGuidance(guidance: ClusterTurnGuidance?) {
        val hasRoute = guidance != null && (guidance.distanceMeters > 0 || guidance.remainingMeters != null || guidance.road.isNotEmpty())
        turnCardView?.setGuidance(if (hasRoute) guidance else null)
        etaCardView?.setGuidance(if (hasRoute) guidance else null)
        if (!mirrorMode) {
            standbyLayout?.visibility = if (hasRoute) View.GONE else View.VISIBLE
        }
    }

    fun updateSong(song: ClusterSong?) {
        songView?.setSong(song)
    }

    fun applyTheme(theme: ClusterTheme) {
        currentTheme = theme
        val gd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(theme.backgroundStart, theme.backgroundEnd)
        )
        rootView?.background = gd
        titleTextView?.setTextColor(theme.primaryAccent)
        speedTextView?.setTextColor(theme.speedColor)
        turnCardView?.setTheme(theme)
        etaCardView?.setTheme(theme)
        songView?.setTheme(theme)
    }

    fun notifySettingsChanged() {
        scaleMode = ClusterPersistence.loadClusterScaleMode(context)
        cropLeftPx = ClusterPersistence.loadClusterMirrorLeftCropPx(context)
        xPct = ClusterPersistence.loadClusterCropLeftPercent(context)
        yPct = ClusterPersistence.loadClusterCropTopPercent(context)
        wPct = ClusterPersistence.loadClusterCropWidthPercent(context)
        hPct = ClusterPersistence.loadClusterCropHeightPercent(context)
        applyTheme(ClusterPersistence.loadClusterTheme(context))
        turnCardView?.notifyPositionChanged()
        etaCardView?.notifyPositionChanged()
        songView?.notifyPositionChanged()
        refreshCrop()
    }

    companion object {
        fun describeDisplays(context: Context): String {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return "null"
            return dm.displays.joinToString { "${it.displayId}:${it.name}" }
        }

        fun findDisplay(context: Context): Display? {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
            val all = dm.displays
            AppLog.i("Cluster: available displays -> [${all.joinToString { "${it.displayId}:${it.name}" }}]")
            val presDisplays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            presDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.let {
                AppLog.i("Cluster: selected presentation display -> ${it.displayId}:${it.name}")
                return it
            }
            all.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.let {
                AppLog.i("Cluster: selected non-default display -> ${it.displayId}:${it.name}")
                return it
            }
            return null
        }
    }
}
