package app.synctheme.mobile.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** Картинки для «Попробовать на примере» — рисуются на месте, в приложении их не хранить. */
object SamplePhotos {
    private const val W = 1080
    private const val H = 2340

    fun draw(sea: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun hills(vararg pts: Float, color: Int) {
            p.shader = null
            p.color = color
            c.drawPath(Path().apply {
                moveTo(0f, H * pts[0])
                quadTo(W * pts[1], H * pts[2], W.toFloat(), H * pts[3])
                lineTo(W.toFloat(), H.toFloat()); lineTo(0f, H.toFloat()); close()
            }, p)
        }
        if (sea) {
            p.shader = LinearGradient(0f, 0f, 0f, H * 0.56f, intArrayOf(0xFF9ED8F0.toInt(), 0xFFE8F4F6.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W.toFloat(), H * 0.56f, p)
            p.shader = LinearGradient(0f, H * 0.56f, 0f, H.toFloat(), intArrayOf(0xFF2A8FB0.toInt(), 0xFF0F4C6B.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, H * 0.56f, W.toFloat(), H.toFloat(), p)
            p.shader = null
            p.color = 0xFFFFF6D6.toInt()
            c.drawCircle(W * 0.7f, H * 0.3f, W * 0.11f, p)
            hills(0.85f, 0.5f, 0.78f, 0.9f, color = 0xFFEED9AE.toInt())
            // Парусник — «объект» снимка.
            p.color = 0xFFE85D3A.toInt()
            c.drawPath(Path().apply { moveTo(W * 0.24f, H * 0.53f); lineTo(W * 0.46f, H * 0.53f); lineTo(W * 0.35f, H * 0.36f); close() }, p)
            p.color = 0xFF3A2A20.toInt()
            c.drawRect(W * 0.345f, H * 0.36f, W * 0.355f, H * 0.53f, p)
            c.drawRect(W * 0.22f, H * 0.53f, W * 0.48f, H * 0.55f, p)
        } else {
            p.shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(0xFF1B2A4A.toInt(), 0xFF7A4B7E.toInt(), 0xFFF29E6D.toInt(), 0xFFF7C99A.toInt()), floatArrayOf(0f, 0.45f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            p.shader = null
            p.color = 0xFFFFE2B0.toInt()
            c.drawCircle(W * 0.66f, H * 0.64f, W * 0.11f, p)
            hills(0.72f, 0.35f, 0.62f, 0.75f, color = 0xFF4A2F4F.toInt())
            hills(0.82f, 0.5f, 0.74f, 0.86f, color = 0xFF221A2E.toInt())
            // Дерево — «объект» снимка.
            p.color = 0xFF15101C.toInt()
            c.drawRect(W * 0.24f, H * 0.6f, W * 0.27f, H * 0.8f, p)
            c.drawCircle(W * 0.255f, H * 0.56f, W * 0.13f, p)
            c.drawCircle(W * 0.19f, H * 0.6f, W * 0.093f, p)
            c.drawCircle(W * 0.32f, H * 0.6f, W * 0.1f, p)
        }
        return bmp
    }
}
