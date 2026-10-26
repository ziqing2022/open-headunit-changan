package com.andrerinas.openheadunit.cluster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Music info card drawn on the instrument cluster:
 * Displays Song Title, Artist, and Album Artwork.
 */
internal class ClusterSongView(context: Context) : View(context) {
    private val rect = RectF()
    private val artRect = RectF()
    private val textPaint = GlassCardStyle.labelPaint(0f, 235, true)
    private val artistPaint = GlassCardStyle.labelPaint(0f, 190, false)
    private val artworkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
    private var song: ClusterSong? = null
    private var artwork: Bitmap? = null
    private var posX = Float.NaN
    private var posY = Float.NaN
    private var overlayScale = Float.NaN
    private var cardAlpha = 50
    private var currentTheme: ClusterTheme = ClusterPersistence.loadClusterTheme(context)

    init {
        cardAlpha = ClusterPersistence.loadCardAlpha(context, "song", 50)
    }

    fun setSong(next: ClusterSong?) {
        val visible = ClusterPersistence.loadCardVisible(context, "song", true)
        val wanted = if (visible && next != null && (next.title.isNotEmpty() || next.artist.isNotEmpty())) next else null
        if (song == wanted && artwork === wanted?.artwork) {
            visibility = if (wanted == null) GONE else VISIBLE
            return
        }
        song = wanted
        artwork = wanted?.artwork
        visibility = if (wanted == null) GONE else VISIBLE
        invalidate()
    }

    fun notifyPositionChanged() {
        posX = Float.NaN
        posY = Float.NaN
        overlayScale = Float.NaN
        cardAlpha = ClusterPersistence.loadCardAlpha(context, "song", 50)
        setTheme(ClusterPersistence.loadClusterTheme(context))
        invalidate()
    }

    fun setTheme(theme: ClusterTheme) {
        currentTheme = theme
        artistPaint.color = Color.argb(
            210,
            Color.red(theme.secondaryAccent),
            Color.green(theme.secondaryAccent),
            Color.blue(theme.secondaryAccent)
        )
        invalidate()
    }

    fun setBackgroundAlpha(percent: Int) {
        val next = percent.coerceIn(0, 100)
        if (next == cardAlpha) return
        cardAlpha = next
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1),
            MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val next = song ?: return
        if (next.title.isEmpty() && next.artist.isEmpty()) return

        if (posX.isNaN()) {
            posX = ClusterPersistence.loadCardX(context, "song", ClusterPersistence.DEFAULT_CLUSTER_SONG_CARD_X_PX).toFloat()
            posY = ClusterPersistence.loadCardY(context, "song", ClusterPersistence.DEFAULT_CLUSTER_SONG_CARD_Y_PX).toFloat()
        }
        if (overlayScale.isNaN()) {
            overlayScale = ClusterPersistence.cardScale(context, "song")
        }

        val unit = minOf(width, height).coerceAtLeast(1).toFloat() * overlayScale
        val lineH = unit * 0.075f
        textPaint.textSize = lineH * 0.62f
        artistPaint.textSize = lineH * 0.50f

        val padH = lineH * 0.72f
        val padV = lineH * 0.34f

        val art = artwork?.takeIf { !it.isRecycled }
        val artSize = if (art != null) lineH * 2.5f else 0f
        val gap = if (art != null) lineH * 0.3f else 0f

        val titleText = next.title.takeIf { it.isNotEmpty() } ?: "—"
        val artistText = next.artist.takeIf { it.isNotEmpty() } ?: ""

        val wTitle = textPaint.measureText(titleText)
        val wArtist = if (artistText.isNotEmpty()) artistPaint.measureText(artistText) else 0f
        val textW = maxOf(wTitle, wArtist).coerceAtLeast(lineH * 2f)

        val w = artSize + gap + textW + padH * 2f
        val h = maxOf(artSize, lineH * 2.2f) + padV * 2f

        val left = posX.coerceIn(0f, (width - w).coerceAtLeast(0f))
        val top = posY.coerceIn(0f, (height - h).coerceAtLeast(0f))
        rect.set(left, top, left + w, top + h)

        val cardFill = Color.argb(
            (255 * cardAlpha.coerceIn(0, 100) / 100f).toInt().coerceIn(0, 255),
            0, 0, 0,
        )
        val radius = h * 0.25f
        GlassCardStyle.drawCapsule(canvas, rect, radius, cardFill)
        GlassCardStyle.drawTextBackstop(canvas, rect, radius)

        val contentLeft = rect.left + padH
        val contentTop = rect.top + padV

        var curX = contentLeft
        if (art != null) {
            val artTop = contentTop + (h - padV * 2f - artSize) / 2f
            artRect.set(curX, artTop, curX + artSize, artTop + artSize)
            canvas.drawBitmap(art, null, artRect, artworkPaint)
            curX += artSize + gap
        }

        val textTop = contentTop + lineH * 0.70f
        canvas.drawText(titleText, curX, textTop, textPaint)
        if (artistText.isNotEmpty()) {
            canvas.drawText(artistText, curX, textTop + lineH * 0.85f, artistPaint)
        }
    }

    companion object {
        const val DEFAULT_CLUSTER_SONG_CARD_X_PX = 1300
        const val DEFAULT_CLUSTER_SONG_CARD_PX = DEFAULT_CLUSTER_SONG_CARD_X_PX
    }
}
