package com.andrerinas.openheadunit.cluster

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Presentation window for the Instrument Cluster (Secondary Display) on Changan and other multi-screen vehicles.
 *
 * Supports:
 * 1. Mirror Mode: Whole-screen mirror with customizable Rect/Crop matrix.
 * 2. Nav Mode: Navigation cards (Turn, ETA, Song) on clean background.
 * 3. Native Car Screen: Smoothly hidden via setMapVisible(false) allowing native cluster UI to show.
 */
class ClusterPresentation(
    context: Context,
    display: Display,
    private val onMirrorSurfaceAvailable: ((Surface?) -> Unit)? = null,
) : Presentation(context, display) {

    private val projectionSize: Point = Point().also {
        @Suppress("DEPRECATION")
        display.getRealSize(it)
    }

    private var mirrorTextureView: TextureView? = null
    private var turnCardView: ClusterTurnCardView? = null
    private var etaCardView: ClusterEtaCardView? = null
    private var songView: ClusterSongView? = null
    private var rootView: FrameLayout? = null

    var mirrorSurface: Surface? = null
        private set

    private var mirrorMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootView = root

        // Mirror TextureView
        val textureView = TextureView(context)
        mirrorTextureView = textureView
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                AppLog.i("Cluster: mirror SurfaceTexture available ${width}x${height}")
                surfaceTexture.setDefaultBufferSize(projectionSize.x, projectionSize.y)
                refreshCrop()
                val surf = Surface(surfaceTexture)
                mirrorSurface = surf
                onMirrorSurfaceAvailable?.invoke(surf)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                refreshCrop()
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                AppLog.i("Cluster: mirror SurfaceTexture destroyed")
                onMirrorSurfaceAvailable?.invoke(null)
                mirrorSurface?.release()
                mirrorSurface = null
                return true
            }
        }
        root.addView(textureView, FrameLayout.LayoutParams(-1, -1))

        // Cards
        turnCardView = ClusterTurnCardView(context)
        etaCardView = ClusterEtaCardView(context)
        songView = ClusterSongView(context)

        root.addView(turnCardView, FrameLayout.LayoutParams(-1, -1))
        root.addView(etaCardView, FrameLayout.LayoutParams(-1, -1))
        root.addView(songView, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)

        mirrorMode = ClusterPersistence.loadClusterMirrorMode(context)
        setMode(mirrorMode)
    }

    fun setMapVisible(visible: Boolean) {
        window?.attributes = window?.attributes?.apply {
            alpha = if (visible) 1f else 0f
        }
        rootView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun setMode(mirror: Boolean) {
        mirrorMode = mirror
        ClusterPersistence.saveClusterMirrorMode(context, mirror)
        mirrorTextureView?.visibility = if (mirror) View.VISIBLE else View.GONE
        if (mirror) {
            refreshCrop()
        }
    }

    fun refreshCrop() {
        val textureView = mirrorTextureView ?: return
        val baseW = if (projectionSize.x > 0) projectionSize.x else 1920
        val baseH = if (projectionSize.y > 0) projectionSize.y else 720

        if (!mirrorMode) {
            textureView.setTransform(Matrix())
            textureView.invalidate()
            return
        }

        val cropLeftPx = ClusterPersistence.loadClusterMirrorLeftCropPx(context)
        val availW = (baseW - cropLeftPx).coerceAtLeast(1)

        val xPct = ClusterPersistence.loadClusterCropLeftPercent(context)
        val yPct = ClusterPersistence.loadClusterCropTopPercent(context)
        val wPct = ClusterPersistence.loadClusterCropWidthPercent(context)
        val hPct = ClusterPersistence.loadClusterCropHeightPercent(context)

        val w = (wPct * baseW / 100).coerceAtLeast(1)
        val h = (hPct * baseH / 100).coerceAtLeast(1)
        val x = xPct * baseW / 100
        val y = yPct * baseH / 100

        val scale = minOf(w.toFloat() / availW, h.toFloat() / baseH)
        val drawnW = availW * scale
        val drawnH = baseH * scale
        val picLeft = x + (w - drawnW) / 2f
        val picTop = y + (h - drawnH) / 2f
        val offsetX = picLeft - cropLeftPx * scale
        val offsetY = picTop

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(offsetX, offsetY)
        }
        textureView.setTransform(matrix)

        val clipLeft = if (picLeft > 0f) picLeft.toInt() else 0
        val clipRight = if (textureView.width > 0) textureView.width else baseW
        val clipBottom = if (textureView.height > 0) textureView.height else baseH
        textureView.setClipBounds(Rect(clipLeft, 0, clipRight, clipBottom))

        textureView.invalidate()
        textureView.postInvalidateOnAnimation()
    }

    fun updateTurnGuidance(guidance: ClusterTurnGuidance?) {
        turnCardView?.setGuidance(guidance)
        etaCardView?.setGuidance(guidance)
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
