package com.inkvol.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.RemoteViews
import com.inkvol.app.MainActivity
import com.inkvol.app.R
import com.inkvol.app.data.local.InkvolDatabase
import kotlin.math.roundToInt

/**
 * M4-C 继续阅读 Widget。
 * - 读取最近一条本地阅读统计；
 * - 显示封面 / 书名 / 进度；
 * - 点击直接打开对应书。
 */
class ContinueReadingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ContinueReadingWidget::class.java),
            )
            ids.forEach { id -> render(context, manager, id) }
        }

        private fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val db = InkvolDatabase(context)
            val entry = runCatching { db.queryReadingHistory(limit = 1) }.getOrNull()?.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_continue_reading)
            val minOptions = manager.getAppWidgetOptions(appWidgetId)
            val targetWidth = (minOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) * 1).coerceAtLeast(120)
            val targetHeight = (minOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) * 1).coerceAtLeast(180)

            if (entry == null) {
                views.setViewVisibility(R.id.widget_cover, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_cover_fallback, android.view.View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_cover_fallback,
                    context.getString(R.string.widget_empty_reading),
                )
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_continue_reading))
                views.setTextViewText(R.id.widget_progress, "")
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    launchAppPendingIntent(context, null),
                )
            } else {
                val book = entry.book
                val cover = book.coverPath?.let { path -> decodeCover(path, targetWidth, targetHeight) }
                if (cover != null) {
                    views.setViewVisibility(R.id.widget_cover, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_cover_fallback, android.view.View.INVISIBLE)
                    views.setImageViewBitmap(R.id.widget_cover, cover)
                } else {
                    views.setViewVisibility(R.id.widget_cover, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_cover_fallback, android.view.View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_cover_fallback,
                        book.title.take(1).ifBlank { "书" },
                    )
                }
                views.setTextViewText(R.id.widget_title, book.title)
                views.setTextViewText(
                    R.id.widget_progress,
                    "进度 ${(entry.book.percent * 100).toInt().coerceIn(0, 100)}% · " +
                        "第 ${(entry.book.percent * book.pageCount).roundToInt().coerceIn(0, book.pageCount)} 页",
                )
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    launchAppPendingIntent(context, book.id),
                )
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun launchAppPendingIntent(context: Context, bookId: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_READER
                putExtra(MainActivity.EXTRA_OPEN_READER_BOOK_ID, bookId.orEmpty())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            return PendingIntent.getActivity(
                context,
                bookId.orEmpty().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun decodeCover(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth &&
                bounds.outHeight / (sample * 2) >= targetHeight
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeFile(path, options)
        }
    }
}
