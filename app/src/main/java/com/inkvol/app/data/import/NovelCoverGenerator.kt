package com.inkvol.app.data.import

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/** M6.1-A：TXT 没有首图，用排版封面兜底。 */
object NovelCoverGenerator {

    fun save(context: android.content.Context, fingerprint: String, title: String, chapterCount: Int): String? {
        return runCatching {
            val width = 512
            val height = 768
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.rgb(246, 238, 225),
                Color.rgb(217, 202, 180),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            paint.shader = null
            paint.color = Color.argb(52, 0, 0, 0)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            canvas.drawRect(RectF(28f, 28f, width - 28f, height - 28f), paint)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(58, 44, 33)
                textSize = 54f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val layout = StaticLayout.Builder
                .obtain(title, 0, title.length, titlePaint, width - 128)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1.1f)
                .build()
            canvas.withTranslation(64f, 180f) { layout.draw(this) }

            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 58, 44, 33)
                textSize = 30f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val meta = "TXT · $chapterCount 章"
            val metaWidth = metaPaint.measureText(meta)
            canvas.drawText(meta, (width - metaWidth) / 2f, height - 88f, metaPaint)

            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val out = File(dir, "$fingerprint.jpg")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            out.absolutePath
        }.getOrNull()
    }

    private inline fun Canvas.withTranslation(x: Float, y: Float, block: Canvas.() -> Unit) {
        save()
        translate(x, y)
        block()
        restore()
    }
}
