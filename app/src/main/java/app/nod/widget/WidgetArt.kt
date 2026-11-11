package app.nod.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import app.nod.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Widgets can't use custom fonts or draw arcs, so the expressive bits (the progress ring, the
 * logo and the big Space Grotesk numbers) are rendered to bitmaps here.
 */
object WidgetArt {
    const val LIME = 0xFFC8F55A.toInt()
    const val INK = 0xFF0E0F11.toInt()
    const val INK2 = 0xFF16181B.toInt()
    const val TRACK = 0xFF2A2E33.toInt()
    const val WHITE = 0xFFF1F1EC.toInt()
    const val MUTED = 0xFF9097A0.toInt()

    private fun font(context: Context, res: Int, weight: Int): Typeface {
        val base = runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else base
    }

    /** Variable fonts need the weight axis set on the paint; the typeface weight alone isn't enough. */
    private fun Paint.weight(w: Int) = apply { fontVariationSettings = "'wght' $w" }

    /** The Nod mark: ring with a gap, traveller dot, crescent moon. */
    fun drawMark(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, progress: Float = 1f) {
        val u = size / 108f
        val r = 23f * u
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val stroke = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = 4.6f * u; strokeCap = Paint.Cap.ROUND }
        val gapCenter = -40f
        val gap = 34f
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), gapCenter + gap / 2, (360f - gap) * progress, false, stroke)
        val a = Math.toRadians(gapCenter.toDouble())
        canvas.drawCircle(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), 4.4f * u, p)
        val mx = cx - 1f * u
        val my = cy + 1f * u
        val outer = Path().apply { addCircle(mx, my, 12.5f * u, Path.Direction.CW) }
        val inner = Path().apply { addCircle(mx + 7.2f * u * cos(a).toFloat(), my + 7.2f * u * sin(a).toFloat(), 10.6f * u, Path.Direction.CW) }
        outer.op(inner, Path.Op.DIFFERENCE)
        canvas.drawPath(outer, p)
    }

    fun mark(sizePx: Int, color: Int = LIME): Bitmap = createBitmap(sizePx, sizePx).also {
        drawMark(Canvas(it), sizePx / 2f, sizePx / 2f, sizePx * 1.35f, color)
    }

    /** A big number with a smaller unit, baseline-aligned, e.g. "12.4 km". */
    fun hero(context: Context, value: String, unit: String, heightPx: Int, color: Int = WHITE, unitColor: Int = MUTED): Bitmap {
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context, R.font.space_grotesk, 500)
            textSize = heightPx * 0.95f
            this.color = color
            letterSpacing = -0.04f
        }.weight(560)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context, R.font.space_grotesk, 500)
            textSize = heightPx * 0.38f
            this.color = unitColor
        }.weight(500)
        val gap = heightPx * 0.12f
        val w = big.measureText(value) + gap + small.measureText(unit)
        val bmp = createBitmap(w.toInt().coerceAtLeast(1) + 2, heightPx)
        val c = Canvas(bmp)
        val baseline = heightPx * 0.86f
        c.drawText(value, 0f, baseline, big)
        c.drawText(unit, big.measureText(value) + gap, baseline, small)
        return bmp
    }

    /**
     * The ring widget face. With [progress] null it shows the idle mark; otherwise a progress arc
     * that closes as you approach, with the traveller dot riding its head.
     */
    fun ring(
        context: Context,
        sizePx: Int,
        progress: Float?,
        value: String?,
        unit: String?,
        caption: String?,
        /** Opaque on the widget's ink: RGB_565 halves the bytes sent to the launcher on every update. */
        opaque: Boolean = false,
    ): Bitmap {
        val bmp = createBitmap(sizePx, sizePx, if (opaque) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (opaque) c.drawColor(INK)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        if (progress == null) {
            drawMark(c, cx, cy - sizePx * 0.06f, sizePx * 1.15f, LIME)
            caption?.let {
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = font(context, R.font.manrope, 700)
                    textSize = sizePx * 0.085f
                    color = WHITE
                    textAlign = Paint.Align.CENTER
                }.weight(700)
                c.drawText(it, cx, sizePx * 0.9f, t)
            }
            return bmp
        }
        val stroke = sizePx * 0.075f
        val r = sizePx / 2f - stroke * 1.4f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = TRACK; strokeCap = Paint.Cap.ROUND }
        val arc = Paint(track).apply { color = LIME }
        c.drawArc(rect, 0f, 360f, false, track)
        val sweep = 360f * progress.coerceIn(0.02f, 1f)
        c.drawArc(rect, -90f, sweep, false, arc)
        val head = Math.toRadians((-90f + sweep).toDouble())
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        c.drawCircle(cx + r * cos(head).toFloat(), cy + r * sin(head).toFloat(), stroke * 0.32f, dot)

        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context, R.font.space_grotesk, 500)
            textSize = sizePx * if ((value?.length ?: 0) > 3) 0.22f else 0.27f
            color = WHITE
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.04f
        }.weight(560)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context, R.font.manrope, 700)
            textSize = sizePx * 0.075f
            color = MUTED
            textAlign = Paint.Align.CENTER
        }.weight(700)
        c.drawText(value ?: "—", cx, cy + sizePx * 0.06f, big)
        unit?.let { c.drawText(it, cx, cy + sizePx * 0.17f, small) }
        caption?.let {
            val cap = Paint(small).apply { color = LIME; textSize = sizePx * 0.065f }
            c.drawText(ellipsize(it, cap, r * 1.3f), cx, cy - sizePx * 0.16f, cap)
        }
        return bmp
    }

    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
