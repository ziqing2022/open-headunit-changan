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
    private var clockTextView: TextView? = null
    private var turnCardView: ClusterTurnCardView? = null
    private var etaCardView: ClusterEtaCardView? = null
    private var songView: ClusterSongView? = null
    private var rootView: FrameLayout? = null

    private var mirrorMode = true
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val clockRunnable = object : Runnable {
        override fun run() {
            clockTextView?.text = clockFormat.format(Date())
            mainHandler.postDelayed(this, 1000L)
        }
    }

    inner class ClusterMirrorView(context: Context) : View(context) {
        var frameBitmap: Bitmap? = null
        val drawMatrix = Matrix()
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bm = frameBitmap ?: return
            if (bm.isRecycled) return
            canvas.drawBitmap(bm, drawMatrix, paint)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

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
        val clockTv = TextView(context).apply {
            text = clockFormat.format(Date())
            textSize = 58f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        clockTextView = clockTv

        val subTv = TextView(context).apply {
            text = "等待手机导航启动…"
            textSize = 18f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
        }
        standby.addView(titleTv)
        standby.addView(clockTv)
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

        mirrorMode = ClusterPersistence.loadClusterMirrorMode(context)
        setMode(mirrorMode)
        mainHandler.post(clockRunnable)
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(clockRunnable)
    }

    fun setMapVisible(visible: Boolean) {
        window?.attributes = window?.attributes?.apply {
            alpha = if (visible) 1f else 0f
        }
        rootView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setMode(mirror: Boolean) {
        mirrorMode = mirror
        ClusterPersistence.saveClusterMirrorMode(context, mirror)
        mirrorView?.visibility = if (mirror) View.VISIBLE else View.GONE
        if (mirror) {
            refreshCrop()
            standbyLayout?.visibility = View.GONE
        } else {
            val hasActiveTurn = turnCardView?.visibility == View.VISIBLE
            standbyLayout?.visibility = if (hasActiveTurn) View.GONE else View.VISIBLE
        }
    }

    fun updateMirrorFrame(bitmap: Bitmap?) {
        if (!mirrorMode) return
        val mv = mirrorView ?: return
        mv.frameBitmap = bitmap
        refreshCrop()
        mv.invalidate()
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

        val cropLeftPx = ClusterPersistence.loadClusterMirrorLeftCropPx(context)
        val availW = (bmW - cropLeftPx).coerceAtLeast(1)

        val xPct = ClusterPersistence.loadClusterCropLeftPercent(context)
        val yPct = ClusterPersistence.loadClusterCropTopPercent(context)
        val wPct = ClusterPersistence.loadClusterCropWidthPercent(context)
        val hPct = ClusterPersistence.loadClusterCropHeightPercent(context)

        val w = (wPct * baseW / 100).coerceAtLeast(1)
        val h = (hPct * baseH / 100).coerceAtLeast(1)
        val x = xPct * baseW / 100
        val y = yPct * baseH / 100

        val scale = minOf(w.toFloat() / availW, h.toFloat() / bmH)
        val drawnW = availW * scale
        val drawnH = bmH * scale
        val picLeft = x + (w - drawnW) / 2f
        val picTop = y + (h - drawnH) / 2f
        val offsetX = picLeft - cropLeftPx * scale
        val offsetY = picTop

        mv.drawMatrix.apply {
            reset()
            setScale(scale, scale)
            postTranslate(offsetX, offsetY)
        }
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

    fun notifySettingsChanged() {
        turnCardView?.notifyPositionChanged()
        etaCardView?.notifyPositionChanged()
        songView?.notifyPositionChanged()
        refreshCrop()
    }

    companion object {
        fun findDisplay(context: Context): Display? {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
            dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.let { return it }
            dm.displays
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.let { return it }
            return null
        }
    }
}
