package com.andrerinas.openheadunit.cluster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import com.andrerinas.openheadunit.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ETA card drawn at the bottom-left of the dashboard, displaying:
 * "21:40到达 · 11分钟 · 8.4公里".
 */
internal class ClusterEtaCardView(context: Context) : View(context) {
    private var guidance: ClusterTurnGuidance? = null

    private val arrivalPaint = GlassCardStyle.labelPaint(0f, 255, true)
    private val subPaint = GlassCardStyle.labelPaint(0f, 200, false)
    private val rect = RectF()

    private var posX = Float.NaN
    private var posY = Float.NaN
    private var overlayScale = Float.NaN
    private var backgroundAlpha = 50
    private var currentTheme: ClusterTheme = ClusterPersistence.loadClusterTheme(context)
    private var lastArrivalLine = ""
    private var lastArrivalSize = 0f
    private var lastSubLine = ""
    private var lastSubSize = 0f
    private var wArr = 0f
    private var wSub = 0f
    private val clockFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        backgroundAlpha = ClusterPersistence.loadCardAlpha(context, "eta", 50)
    }

    fun notifyPositionChanged() {
        posX = Float.NaN
        posY = Float.NaN
        overlayScale = Float.NaN
        backgroundAlpha = ClusterPersistence.loadCardAlpha(context, "eta", 50)
        setTheme(ClusterPersistence.loadClusterTheme(context))
        invalidate()
    }

    fun setTheme(theme: ClusterTheme) {
        currentTheme = theme
        subPaint.color = Color.argb(
            220,
            Color.red(theme.secondaryAccent),
            Color.green(theme.secondaryAccent),
            Color.blue(theme.secondaryAccent)
        )
        invalidate()
    }

    fun setBackgroundAlpha(percent: Int) {
        val next = percent.coerceIn(0, 100)
        if (next == backgroundAlpha) return
        backgroundAlpha = next
        invalidate()
    }

    private fun backgroundFill(): Int =
        Color.argb((255 * backgroundAlpha.coerceIn(0, 100) / 100f).toInt().coerceIn(0, 255), 0, 0, 0)

    fun setGuidance(next: ClusterTurnGuidance?) {
        guidance = next
        val visible = next != null && hasEta(next) && ClusterPersistence.loadCardVisible(context, "eta", true)
        val wasVisible = visibility == VISIBLE
        visibility = if (visible) VISIBLE else GONE
        if (visible && !wasVisible) bringToFront()
        invalidate()
    }

    private fun hasEta(next: ClusterTurnGuidance): Boolean =
        next.arrivalEpochSeconds != null || next.remainingSeconds != null || next.remainingMeters != null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1),
            MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val next = guidance ?: return
        val arrival = next.arrivalEpochSeconds
        val remainingSec = next.remainingSeconds
        val remainingM = next.remainingMeters
        if (arrival == null && remainingSec == null && remainingM == null) return

        if (overlayScale.isNaN()) {
            overlayScale = ClusterPersistence.cardScale(context, "eta")
        }
        val unit = minOf(width, height).coerceAtLeast(1) * overlayScale
        val padX = unit * 0.030f
        val padY = unit * 0.026f
        arrivalPaint.textSize = unit * 0.050f
        subPaint.textSize = unit * 0.034f

        val arrivalLine: String = if (arrival != null && arrival > 0) {
            context.getString(R.string.cluster_eta_arrival, clockTime(arrival))
        } else {
            context.getString(R.string.cluster_eta_on_the_way)
        }
        val subLine = buildString {
            if (remainingSec != null && remainingSec > 0) append(remainingLabel(remainingSec))
            if (remainingM != null && remainingM > 0) {
                if (isNotEmpty()) append(context.getString(R.string.cluster_eta_sep))
                append(distanceLabel(remainingM))
            }
        }

        if (arrivalLine != lastArrivalLine || arrivalPaint.textSize != lastArrivalSize) {
            lastArrivalLine = arrivalLine
            lastArrivalSize = arrivalPaint.textSize
            wArr = arrivalPaint.measureText(arrivalLine)
        }
        if (subLine.isNotEmpty() && (subLine != lastSubLine || subPaint.textSize != lastSubSize)) {
            lastSubLine = subLine
            lastSubSize = subPaint.textSize
            wSub = subPaint.measureText(subLine)
        }
        if (subLine.isEmpty()) wSub = 0f
        val cardW = maxOf(wArr, wSub) + padX * 2f
        val lineGap = unit * 0.012f
        val cardH = padY * 2f + arrivalPaint.textSize + lineGap + subPaint.textSize

        var left = posX
        var top = posY
        if (left.isNaN()) {
            left = ClusterPersistence.loadCardX(context, "eta", ClusterPersistence.DEFAULT_CLUSTER_ETA_CARD_X_PX).toFloat()
            top = ClusterPersistence.loadCardY(context, "eta", ClusterPersistence.DEFAULT_CLUSTER_ETA_CARD_Y_PX).toFloat()
            posX = left
            posY = top
        }

        val clampedLeft = left.coerceIn(0f, (width - cardW).coerceAtLeast(0f))
        val clampedTop = top.coerceIn(0f, (height - cardH).coerceAtLeast(0f))
        rect.set(clampedLeft, clampedTop, clampedLeft + cardW, clampedTop + cardH)

        val radius = cardH * 0.28f
        GlassCardStyle.drawCapsule(canvas, rect, radius, backgroundFill())
        GlassCardStyle.drawTextBackstop(canvas, rect, radius)

        val textX = clampedLeft + padX
        val arrivalY = clampedTop + padY + arrivalPaint.textSize
        canvas.drawText(arrivalLine, textX, arrivalY, arrivalPaint)

        if (subLine.isNotEmpty()) {
            val subY = arrivalY + lineGap + subPaint.textSize
            canvas.drawText(subLine, textX, subY, subPaint)
        }
    }

    private fun clockTime(epochSec: Long): String {
        val millis = if (epochSec < 100_000_000_000L) epochSec * 1000L else epochSec
        return clockFormatter.format(Date(millis))
    }

    private fun remainingLabel(seconds: Long): String {
        val totalMinutes = (seconds + 30) / 60
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 -> {
                if (m > 0) context.getString(R.string.cluster_eta_hours_minutes, h, m)
                else context.getString(R.string.cluster_eta_hours, h)
            }
            else -> context.getString(R.string.cluster_eta_minutes, m.coerceAtLeast(1))
        }
    }

    private fun distanceLabel(meters: Long): String = when {
        meters >= 1000 -> context.getString(R.string.cluster_eta_km, meters / 1000f)
        else -> context.getString(R.string.cluster_eta_m, meters)
    }
}
