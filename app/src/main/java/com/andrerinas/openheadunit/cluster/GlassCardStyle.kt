package com.andrerinas.openheadunit.cluster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Shared "liquid glass" renderer for instrument cluster overlay cards.
 *
 * Provides a translucent, frosted glass effect with:
 * 1. Translucent tinted fill.
 * 2. Vertical sheen gradient inside the top edge.
 * 3. Soft drop shadow (optimized light shadow with small fixed offset).
 * 4. Bright hairline top edge + dim bottom edge (glossy bevel).
 */
internal object GlassCardStyle {
    private const val FILL_R = 118
    private const val FILL_G = 140
    private const val FILL_B = 162
    private const val FILL_ALPHA = 58

    private const val SHEEN_ALPHA_TOP = 72
    private const val SHEEN_ALPHA_MID = 22

    // Light shadow parameters
    private const val SHADOW_DY_PX = 2f
    private const val SHADOW_FILL_ALPHA = 26
    private const val SHADOW_BLUR = 5f
    private const val SHADOW_LAYER_ALPHA = 64

    private val topEdge = Color.argb(90, 255, 255, 255)
    private val bottomEdge = Color.argb(28, 0, 0, 0)
    private val outline = Color.argb(40, 255, 255, 255)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(SHADOW_FILL_ALPHA, 0, 0, 0)
        setShadowLayer(SHADOW_BLUR, 0f, SHADOW_DY_PX, Color.argb(SHADOW_LAYER_ALPHA, 0, 0, 0))
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(FILL_ALPHA, FILL_R, FILL_G, FILL_B)
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val chipFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 255, 255, 255)
    }

    fun drawCapsule(canvas: Canvas, rect: RectF, radius: Float, fillOverride: Int? = null) {
        canvas.saveLayer(rect.left - radius, rect.top - radius, rect.right + radius, rect.bottom + radius, null)
        val shadowRect = RectF(rect)
        shadowRect.offset(0f, SHADOW_DY_PX)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        canvas.restore()

        if (fillOverride == null) {
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        } else {
            val custom = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillOverride }
            canvas.drawRoundRect(rect, radius, radius, custom)
        }

        sheenPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.55f,
            Color.argb(SHEEN_ALPHA_TOP, 255, 255, 255),
            Color.argb(SHEEN_ALPHA_MID, 255, 255, 255),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, sheenPaint)
        sheenPaint.shader = null

        edgePaint.color = topEdge
        canvas.drawLine(rect.left + radius, rect.top + 0.75f, rect.right - radius, rect.top + 0.75f, edgePaint)
        edgePaint.color = bottomEdge
        canvas.drawLine(rect.left + radius, rect.bottom - 0.75f, rect.right - radius, rect.bottom - 0.75f, edgePaint)
        edgePaint.color = outline
        canvas.drawRoundRect(rect, radius, radius, edgePaint)
    }

    fun drawChip(canvas: Canvas, rect: RectF, radius: Float, highlight: Boolean = false) {
        if (highlight) {
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 10, 132, 255) }
            canvas.drawRoundRect(rect, radius, radius, accent)
            val accentStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 10, 132, 255); style = Paint.Style.STROKE; strokeWidth = 2f
            }
            canvas.drawRoundRect(rect, radius, radius, accentStroke)
        } else {
            canvas.drawRoundRect(rect, radius, radius, chipFillPaint)
            val chipEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f
            }
            canvas.drawRoundRect(rect, radius, radius, chipEdge)
        }
    }

    fun drawTextBackstop(canvas: Canvas, rect: RectF, radius: Float) {
        val back = Paint(Paint.ANTI_ALIAS_FLAG)
        back.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.argb(40, 0, 0, 0), Color.argb(22, 0, 0, 0),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, back)
        back.shader = null
    }

    fun labelPaint(textSize: Float, alpha: Int = 255, medium: Boolean = true): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 255, 255, 255)
            this.textSize = textSize
            typeface = Typeface.create(
                if (medium) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL,
            )
            setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0))
        }
}
