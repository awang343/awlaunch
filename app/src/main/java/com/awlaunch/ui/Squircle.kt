package com.awlaunch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Continuous rounded rectangle: straight edges with a single cubic-Bézier per corner.
 *
 * @param cornerFraction  Corner zone length as a fraction of the shorter side
 *                        (0.5 = corners meet, no straight edge; iOS-like ≈ 0.3).
 * @param controlBias     Bézier control offset as a fraction of the corner zone.
 *                        0.55 ≈ circular arc; smaller → tighter, squircle-ier corner.
 */
class SquircleShape(
    private val cornerFraction: Float = 0.22f,
    private val controlBias: Float = 0.45f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val r = min(w, h) * cornerFraction.coerceIn(0f, 0.5f)
        val c = r * controlBias.coerceIn(0f, 1f)

        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f)
            cubicTo(w - r + c, 0f, w, r - c, w, r)
            lineTo(w, h - r)
            cubicTo(w, h - r + c, w - r + c, h, w - r, h)
            lineTo(r, h)
            cubicTo(r - c, h, 0f, h - r + c, 0f, h - r)
            lineTo(0f, r)
            cubicTo(0f, r - c, r - c, 0f, r, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Render an app icon into a square bitmap that fills the corners, so a squircle
 * clip in Compose looks right (instead of a circle inside transparent corners).
 *
 * Adaptive icons: draw foreground + background layers directly, expanded past the
 * canvas bounds by the standard 18/72 ratio so the visible safe zone fills the
 * canvas — bypassing the system circular mask in AdaptiveIconDrawable.draw().
 *
 * Legacy icons: paint a dark background, draw the icon inset so it doesn't crowd
 * the corners.
 */
fun renderSquircleIconBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val adaptive = findAdaptive(drawable)
    if (adaptive != null) {
        val expand = (sizePx * 18f / 72f).toInt()
        val left = -expand
        val top = -expand
        val right = sizePx + expand
        val bottom = sizePx + expand

        val bg = adaptive.background
        if (bg != null) {
            bg.setBounds(left, top, right, bottom)
            bg.draw(canvas)
        } else {
            val p = Paint().apply { color = 0xFF1F2230.toInt() }
            canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), p)
        }
        adaptive.foreground?.apply {
            setBounds(left, top, right, bottom)
            draw(canvas)
        }
    } else {
        // Legacy icon: draw at full canvas size so it fills the squircle.
        // Most legacy icons have their own circular/transparent margin, which is fine —
        // the squircle clip just trims any opaque corners.
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
    }
    return bmp
}

private fun findAdaptive(drawable: Drawable): AdaptiveIconDrawable? {
    if (drawable is AdaptiveIconDrawable) return drawable
    if (drawable is InsetDrawable) return drawable.drawable?.let(::findAdaptive)
    if (drawable is LayerDrawable) {
        for (i in 0 until drawable.numberOfLayers) {
            drawable.getDrawable(i)?.let { child ->
                findAdaptive(child)?.let { return it }
            }
        }
    }
    return null
}
