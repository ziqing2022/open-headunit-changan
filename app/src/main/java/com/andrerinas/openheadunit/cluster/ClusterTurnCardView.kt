package com.andrerinas.openheadunit.cluster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.R

/**
 * Instruction card drawn on top of the dashboard map.
 * Visual language follows a dark liquid-glass capsule with a hairline stroke,
 * the maneuver glyph in a soft chip on the left, distance and road stacked on the right.
 */
internal class ClusterTurnCardView(context: Context) : View(context) {
    private var guidance: ClusterTurnGuidance? = null
    private var xPx = ClusterPersistence.DEFAULT_CLUSTER_TURN_CARD_X_PX
    private var yPx = ClusterPersistence.DEFAULT_CLUSTER_TURN_CARD_Y_PX
    private var scalePercent = ClusterPersistence.CLUSTER_CARD_SCALE_DEFAULT
    private var backgroundAlpha = 50
    private var currentTheme: ClusterTheme = ClusterPersistence.loadClusterTheme(context)
    private var accent = currentTheme.primaryAccent
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(2f, 0f, 1f, Color.argb(140, 0, 0, 0))
    }
    private val distancePaint = GlassCardStyle.labelPaint(0f, 255, true)
    private val roadPaint = GlassCardStyle.labelPaint(0f, 200, false)
    private val rect = RectF()
    private val textBackstop = RectF()
    private var glyph: Drawable? = null
    private var glyphTag: Int = -1

    private var lastDistance = ""
    private var lastDistanceSize = 0f
    private var lastRoad = ""
    private var lastRoadSize = 0f
    private var wDistance = 0f
    private var wRoad = 0f

    init {
        xPx = ClusterPersistence.loadCardX(context, "turn", ClusterPersistence.DEFAULT_CLUSTER_TURN_CARD_X_PX)
        yPx = ClusterPersistence.loadCardY(context, "turn", ClusterPersistence.DEFAULT_CLUSTER_TURN_CARD_Y_PX)
        scalePercent = ClusterPersistence.loadCardScalePercent(context, "turn")
        backgroundAlpha = ClusterPersistence.loadCardAlpha(context, "turn", 50)
    }

    fun setLayout(xPx: Int, yPx: Int, scalePercent: Int) {
        this.xPx = xPx.coerceIn(0, 1920)
        this.yPx = yPx.coerceIn(0, 720)
        this.scalePercent = scalePercent
        invalidate()
    }

    fun notifyPositionChanged() {
        xPx = ClusterPersistence.loadCardX(context, "turn", ClusterPersistence.DEFAULT_CLUSTER_TURN_CARD_X_PX)
        yPx = ClusterPersistence.loadCardY(context, "turn", ClusterPersistence.DEFAULT_CLUSTER_TURN_CARD_Y_PX)
        scalePercent = ClusterPersistence.loadCardScalePercent(context, "turn")
        backgroundAlpha = ClusterPersistence.loadCardAlpha(context, "turn", 50)
        setTheme(ClusterPersistence.loadClusterTheme(context))
        invalidate()
    }

    fun setTheme(theme: ClusterTheme) {
        currentTheme = theme
        accent = theme.primaryAccent
        badgePaint.color = accent
        glyph = null
        glyphTag = -1
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
        val visible = ClusterPersistence.loadCardVisible(context, "turn", true)
        val wanted = if (visible) next else null
        if (guidance == wanted) {
            visibility = if (wanted == null) GONE else VISIBLE
            return
        }
        guidance = wanted
        val wasVisible = visibility == VISIBLE
        visibility = if (wanted == null) GONE else VISIBLE
        if (visibility == VISIBLE && !wasVisible) bringToFront()
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
        val next = guidance ?: return
        val panelW = width.toFloat()
        val panelH = height.toFloat()

        val heightFraction = 0.28f * (scalePercent / 100f)
        val h = (panelH * heightFraction).coerceAtLeast(72f).coerceAtMost(panelH)
        val radius = h * 0.30f
        val chip = h * 0.76f
        val chipLeft = h * 0.12f
        val textLeft = chipLeft + chip + h * 0.14f
        val distanceText = distanceLabel(next.distanceMeters)
        val roadText = roadLabel(next)
        distancePaint.textSize = h * 0.30f
        roadPaint.textSize = h * 0.17f

        if (distanceText != lastDistance || distancePaint.textSize != lastDistanceSize) {
            lastDistance = distanceText
            lastDistanceSize = distancePaint.textSize
            wDistance = distancePaint.measureText(distanceText)
        }
        if (roadText.isNotEmpty() && (roadText != lastRoad || roadPaint.textSize != lastRoadSize)) {
            lastRoad = roadText
            lastRoadSize = roadPaint.textSize
            wRoad = roadPaint.measureText(roadText)
        }
        val textW = maxOf(
            wDistance,
            if (roadText.isNotEmpty()) wRoad else 0f,
        )

        val w = (textLeft + textW + h * 0.10f).coerceAtMost(panelW)
        val left = xPx.toFloat().coerceIn(0f, (panelW - w).coerceAtLeast(0f))
        val top = yPx.toFloat().coerceIn(0f, (panelH - h).coerceAtLeast(0f))

        rect.set(left, top, left + w, top + h)
        GlassCardStyle.drawCapsule(canvas, rect, radius, backgroundFill())

        val chipTop = top + (h - chip) / 2f
        rect.set(chipLeft + left, chipTop, chipLeft + left + chip, chipTop + chip)
        GlassCardStyle.drawChip(canvas, rect, chip * 0.26f)

        val exit = next.roundaboutExit.takeIf { it in 1..9 }
        drawGlyph(canvas, next, chipLeft + left, chipTop, chip, exit)

        textBackstop.set(left, top, left + w, top + h)
        GlassCardStyle.drawTextBackstop(canvas, textBackstop, radius)

        val textX = left + textLeft
        canvas.drawText(distanceText, textX, top + h * 0.44f, distancePaint)
        if (roadText.isNotEmpty()) {
            canvas.drawText(roadText, textX, top + h * 0.72f, roadPaint)
        }
    }

    private fun drawGlyph(canvas: Canvas, next: ClusterTurnGuidance, left: Float, top: Float, side: Float, exit: Int?) {
        if (next.icon == 0) return
        val resId = glyphRes(next.icon)
        if (resId != glyphTag) {
            glyph = ContextCompat.getDrawable(context, resId)?.mutate()?.apply { setTint(accent) }
            glyphTag = resId
        }
        val inset = side * 0.10f
        glyph?.setBounds(
            (left + inset).toInt(), (top + inset).toInt(),
            (left + side - inset).toInt(), (top + side - inset).toInt(),
        )
        glyph?.draw(canvas)
        if (exit != null) {
            val d = side * 0.42f
            val cx = left + side - d / 2f
            val cy = top + side - d / 2f
            canvas.drawCircle(cx, cy, d / 2f, badgePaint)
            badgeTextPaint.textSize = d * 0.62f
            val textY = cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            canvas.drawText(exit.toString(), cx, textY, badgeTextPaint)
        }
    }

    private fun glyphRes(icon: Int): Int = when (icon) {
        2 -> R.drawable.ic_turn_card_turn_left
        3 -> R.drawable.ic_turn_card_turn_right
        4 -> R.drawable.ic_turn_card_turn_slight_left
        5 -> R.drawable.ic_turn_card_turn_slight_right
        6 -> R.drawable.ic_turn_card_turn_sharp_left
        7 -> R.drawable.ic_turn_card_turn_sharp_right
        8 -> R.drawable.ic_turn_card_u_turn_left
        19 -> R.drawable.ic_turn_card_u_turn_right
        11, 12, 17, 18 -> R.drawable.ic_turn_card_roundabout_right
        15 -> R.drawable.ic_turn_card_sports_score
        else -> R.drawable.ic_turn_card_straight
    }

    private fun distanceLabel(meters: Int): String = when {
        meters <= 20 -> context.getString(R.string.turn_card_now)
        meters < 1000 -> context.getString(R.string.turn_card_distance_m, meters)
        else -> context.getString(R.string.turn_card_distance_km, meters / 100 / 10f)
    }

    private fun roadLabel(next: ClusterTurnGuidance): String =
        if (next.roundaboutExit in 1..9) {
            context.getString(R.string.turn_card_exit, next.roundaboutExit)
        } else if (next.road.isNotEmpty()) {
            next.road
        } else {
            next.actionText
        }
}
