package com.inkvol.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.data.model.NovelReadingPrefs
import com.inkvol.app.data.repository.LibraryRepository
import com.inkvol.app.data.settings.SettingsKeys
import com.inkvol.app.ui.novel.NovelFontRegistry
import com.inkvol.app.ui.novel.NovelOpenSession
import com.inkvol.app.ui.novel.NovelPageSnapshot
import com.inkvol.app.ui.novel.NOVEL_SPREAD_GUTTER
import com.inkvol.app.ui.novel.cutoutSafeDp
import com.inkvol.app.ui.novel.NovelTextPager
import com.inkvol.app.ui.novel.novelTheme
import com.inkvol.app.ui.reader.PageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * 一镜到底的宿主持有者。
 * 放在 ViewModel 里，保证旋转/分屏尺寸变化时 session 不丢。
 */
class ReaderOpenViewModel : ViewModel() {
    val controller = ReaderOpenController()

    private var preloadJob: Job? = null

    fun startOpen(
        book: BookRecord,
        repository: LibraryRepository,
        sourceRect: ReaderOpenRect?,
        rootWidthPx: Int,
        rootHeightPx: Int,
    ) {
        preloadJob?.cancel()
        controller.beginOpen(
            bookId = book.id,
            coverPath = book.coverPath,
            sourceRect = sourceRect,
            rootWidthPx = rootWidthPx.toFloat(),
            rootHeightPx = rootHeightPx.toFloat(),
        )
        preloadJob = viewModelScope.launch {
            val preload = launch { preload(book, repository, rootWidthPx, rootHeightPx) }
            // openSource/大压缩包可能慢；不能让动画卡在 Preparing。
            delay(160)
            controller.beginOpening()
            preload.join()
        }
    }

    fun startNovelOpen(
        book: BookRecord,
        sourceRect: ReaderOpenRect?,
        rootWidthPx: Int,
        rootHeightPx: Int,
        repository: LibraryRepository,
        settingsRepository: com.inkvol.app.data.settings.SettingsRepository,
        textMeasurer: TextMeasurer,
        density: Density,
        cutoutLeftPx: Int = 0,
        cutoutTopPx: Int = 0,
        cutoutRightPx: Int = 0,
        cutoutBottomPx: Int = 0,
        locateChapter: Int = -1,
        locateChar: Int = -1,
        locateLen: Int = 0,
    ) {
        preloadJob?.cancel()
        controller.beginOpen(
            bookId = book.id,
            coverPath = book.coverPath,
            sourceRect = sourceRect,
            rootWidthPx = rootWidthPx.toFloat(),
            rootHeightPx = rootHeightPx.toFloat(),
            isNovel = true,
        )
        preloadJob = viewModelScope.launch {
            // 封面解码很快，先用封面让动画立刻起飞；正文快照只作为返回页备用。
            val cover = withContext(Dispatchers.IO) {
                decodeCoverBitmap(book.coverPath, rootWidthPx, rootHeightPx)
            }
            controller.attachNovelCoverOnly(book.id, cover)
            preloadNovel(
                book,
                repository,
                settingsRepository,
                rootWidthPx,
                rootHeightPx,
                textMeasurer,
                density,
                cover,
                cutoutLeftPx,
                cutoutTopPx,
                cutoutRightPx,
                cutoutBottomPx,
                locateChapter,
                locateChar,
                locateLen,
            )
        }
    }

    private suspend fun preloadNovel(
        book: BookRecord,
        repository: LibraryRepository,
        settingsRepository: com.inkvol.app.data.settings.SettingsRepository,
        rootWidthPx: Int,
        rootHeightPx: Int,
        textMeasurer: TextMeasurer,
        density: Density,
        cover: ImageBitmap?,
        cutoutLeftPx: Int,
        cutoutTopPx: Int,
        cutoutRightPx: Int,
        cutoutBottomPx: Int,
        locateChapter: Int = -1,
        locateChar: Int = -1,
        locateLen: Int = 0,
    ) {
        try {
            val snapshot = withContext(Dispatchers.IO) { settingsRepository.snapshotPreferences() }
            val perBook = settingsRepository.stringOf(snapshot, SettingsKeys.novelBookKey(book.id), "")
            val global = settingsRepository.stringOf(snapshot, SettingsKeys.NOVEL_GLOBAL_PREFS, "")
            // M6.4-D 与 Reader 读取同源：单独记忆开 → 本书快照；关 → 全局跟随（缺则兜底本书快照/默认）。
            val raw = if (
                settingsRepository.booleanOf(snapshot, SettingsKeys.novelBookSoloKey(book.id), false) &&
                perBook.isNotBlank()
            ) {
                perBook
            } else {
                global.ifBlank { perBook }
            }
            val prefs = if (raw.isBlank()) {
                NovelReadingPrefs()
            } else {
                runCatching { Json.decodeFromString(NovelReadingPrefs.serializer(), raw) }
                    .getOrDefault(NovelReadingPrefs())
            }
            val doublePageMode = settingsRepository.stringOf(
                snapshot,
                SettingsKeys.NOVEL_DOUBLE_PAGE,
                SettingsKeys.DOUBLE_PAGE_AUTO,
            )
            val rememberedCutoutDp = settingsRepository
                .stringOf(snapshot, SettingsKeys.NOVEL_CUTOUT_REMEMBER, "")
                .toFloatOrNull() ?: 0f
            controller.setNovelBackgroundColor(book.id, novelBackgroundColor(prefs.theme))

            val chapters = repository.novelChapters(book.id)
            val progress = repository.novelProgress(book.id)
            // M7 搜索定位：locateChapter 命中时覆盖进度推导——打开该章并定位到命中偏移
            val hasLocate = locateChapter in 0..chapters.lastIndex && locateChar >= 0
            val chapterIndex = if (hasLocate) {
                locateChapter
            } else {
                progress?.chapterIndex
                    ?.coerceIn(0, (chapters.lastIndex).coerceAtLeast(0)) ?: 0
            }
            val pendingRestoreOffset = if (hasLocate) {
                locateChar
            } else {
                progress?.charOffset ?: -1
            }
            val chapter = chapters.getOrNull(chapterIndex)
            val chapterText = chapter?.let { repository.readNovelChapter(book.id, it) }.orEmpty()

            val fonts = NovelFontRegistry.fonts(settingsRepository)
            val selectedFont = fonts.firstOrNull { it.id == prefs.fontId }
            val fontFamily: FontFamily? = when (prefs.fontId) {
                "serif" -> FontFamily.Serif
                "sans" -> FontFamily.SansSerif
                "mono" -> FontFamily.Monospace
                else -> NovelFontRegistry.fontFamily(selectedFont)
            }

            // 挖孔避让：与 NovelReaderScreen 共用同一派生规则（基准与挖孔 insets 取大者），
            // 含 OEM 横屏兜底（insets 全 0 时用记忆值补偿），保证分页预算与 Reader 完全同源。
            val landscape = rootWidthPx > rootHeightPx
            val rawLeftDp = with(density) { cutoutLeftPx.toDp() }
            val rawRightDp = with(density) { cutoutRightPx.toDp() }
            val rawTopDp = with(density) { cutoutTopPx.toDp() }
            val rawBottomDp = with(density) { cutoutBottomPx.toDp() }
            val rawMaxDp = rawLeftDp.coerceAtLeast(rawRightDp)
                .coerceAtLeast(rawTopDp).coerceAtLeast(rawBottomDp)
            val startPadDp: Dp
            val endPadDp: Dp
            if (rawMaxDp.value <= 0f && landscape && rememberedCutoutDp > 0f) {
                val compensation = rememberedCutoutDp.dp + 6.dp
                startPadDp = prefs.pagePadding.dp.coerceAtLeast(compensation)
                endPadDp = prefs.pagePadding.dp.coerceAtLeast(compensation)
            } else {
                startPadDp = cutoutSafeDp(prefs.pagePadding.dp, rawLeftDp)
                endPadDp = cutoutSafeDp(prefs.pagePadding.dp, rawRightDp)
            }
            val topPadDp = cutoutSafeDp(24.dp, rawTopDp)
            // 与 NovelReaderScreen 同款横竖屏分支：竖屏 32 兜底手势条（M6.3-E-H 03/05），
            // 横屏手势条在侧边，基准与 top 对齐（M6.3-E-H 06）——分页预算与 Reader 完全同源。
            val bottomPadDp = if (landscape) {
                cutoutSafeDp(24.dp, rawBottomDp)
            } else {
                cutoutSafeDp(32.dp, rawBottomDp)
            }

            // 双页档判定与 NovelReaderScreen 的 useDouble 同一规则（横向翻页 + 始终 or 横屏）。
            val useDouble = prefs.pagingMode != SettingsKeys.NOVEL_PAGE_VERTICAL &&
                doublePageMode != SettingsKeys.DOUBLE_PAGE_OFF &&
                (doublePageMode == SettingsKeys.DOUBLE_PAGE_ALWAYS || landscape)
            val textWidthPx = with(density) {
                (rootWidthPx - (startPadDp + endPadDp).toPx()).roundToInt()
            }.coerceAtLeast(1)
            val textHeightPx = with(density) {
                (rootHeightPx - (topPadDp + bottomPadDp).toPx()).roundToInt()
            }.coerceAtLeast(1)
            val gutterPx = with(density) { NOVEL_SPREAD_GUTTER.toPx().roundToInt() }
            val halfPageWidthPx = ((textWidthPx - gutterPx) / 2).coerceAtLeast(1)
            val paginateWidthPx = if (useDouble) halfPageWidthPx else textWidthPx
            val maxPages = if (hasLocate || progress != null) Int.MAX_VALUE else if (useDouble) 2 else 1
            val anchorChar = if (hasLocate) locateChar else progress?.charOffset ?: -1
            // 章首（anchorChar == 0）/ 无进度时不截断：`stopAfterCharOffset` 的停页条件是
            // 「当前页已覆盖该偏移」，而 0 落在 EPUB/MOBI 的「第X章 标题」块里 → 排完标题块
            // 立刻停页，首屏分页只剩「仅章节名」的 1 页（快照余量平分还会把它摆到屏幕中间）；
            // 随后整章分页给第 1 页补上正文 → 视觉上就是「先跳第X章、再跳正文」。
            // TXT 没有独立标题块、0 落在正文段内，所以不触发。
            val stopAfter = anchorChar.takeIf { it > 0 } ?: -1
            // 章首不截断时也只排首屏：`maxPages` 的停页发生在 flushPage 之后，页是完整的；
            // 而 stopAfterCharOffset 会在块中间停（首屏只剩「章节名」那一页不完整的内容）。
            val pageLimit = if (stopAfter > 0) maxPages else if (useDouble) 2 else 1
            val pagination = withContext(Dispatchers.Default) {
                NovelTextPager.paginate(
                    chapterText = chapterText,
                    prefs = prefs,
                    fontFamily = fontFamily,
                    widthPx = paginateWidthPx,
                    heightPx = textHeightPx,
                    density = density,
                    textMeasurer = textMeasurer,
                    maxPages = pageLimit,
                    stopAfterCharOffset = stopAfter,
                )
            }
            val page = if (anchorChar >= 0) {
                pagination.pages.firstOrNull { p ->
                    p.charStart <= anchorChar && anchorChar < p.charEnd
                } ?: pagination.pages.firstOrNull()
            } else {
                pagination.pages.firstOrNull()
            }
            // 双页快照：取含进度偏移（或首个）spread，左右两半渲染进同一张位图，
            // 保证一镜到底铺满瞬间与正文双栏一致（无单栏闪变）。
            val spreadChunks = pagination.pages.chunked(2)
            val targetSpread = if (useDouble) {
                if (progress != null) {
                    spreadChunks.firstOrNull { s ->
                        val start = s.first().charStart
                        val end = s.last().charEnd
                        start <= progress.charOffset && progress.charOffset < end
                    } ?: spreadChunks.firstOrNull()
                } else {
                    spreadChunks.firstOrNull()
                }
            } else {
                null
            }
            val bitmap = withContext(Dispatchers.Default) {
                if (targetSpread != null) {
                    NovelPageSnapshot.render(
                        page = targetSpread.getOrNull(0),
                        prefs = prefs,
                        theme = novelTheme(prefs.theme),
                        fontFamily = fontFamily,
                        widthPx = rootWidthPx,
                        heightPx = rootHeightPx,
                        density = density,
                        textMeasurer = textMeasurer,
                        startPadPx = with(density) { startPadDp.toPx() },
                        endPadPx = with(density) { endPadDp.toPx() },
                        topPadPx = with(density) { topPadDp.toPx() },
                        bottomPadPx = with(density) { bottomPadDp.toPx() },
                        secondPage = targetSpread.getOrNull(1),
                        halfWidthPx = halfPageWidthPx,
                        gutterPx = with(density) { NOVEL_SPREAD_GUTTER.toPx() },
                    )
                } else {
                    NovelPageSnapshot.render(
                        page = page,
                        prefs = prefs,
                        theme = novelTheme(prefs.theme),
                        fontFamily = fontFamily,
                        widthPx = rootWidthPx,
                        heightPx = rootHeightPx,
                        density = density,
                        textMeasurer = textMeasurer,
                        startPadPx = with(density) { startPadDp.toPx() },
                        endPadPx = with(density) { endPadDp.toPx() },
                        topPadPx = with(density) { topPadDp.toPx() },
                        bottomPadPx = with(density) { bottomPadDp.toPx() },
                    )
                }
            }
            val session = NovelOpenSession(
                book = book,
                chapters = chapters,
                chapterIndex = chapterIndex,
                chapterText = chapterText,
                prefs = prefs,
                fonts = fonts,
                pendingRestoreOffset = pendingRestoreOffset,
                firstPagination = pagination,
                firstPageBitmap = bitmap,
                paginationWidthPx = paginateWidthPx,
                locateChar = if (hasLocate) locateChar else -1,
                locateLen = locateLen,
            )
            controller.attachNovelSession(session, cover)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w("InkvolOpen", "novel preload failed book=${book.id}", error)
            controller.markContentFailed()
        }
    }

    private fun novelBackgroundColor(theme: String): Int = when (theme) {
        "sepia" -> 0xFFF7EFE1.toInt()
        "green" -> 0xFFEAF3E5.toInt()
        "dark" -> 0xFF121212.toInt()
        else -> 0xFFFDFDFD.toInt()
    }

    fun cancelPreparing() {
        preloadJob?.cancel()
        controller.cancelPreparing()
    }

    private suspend fun preload(
        book: BookRecord,
        repository: LibraryRepository,
        rootWidthPx: Int,
        rootHeightPx: Int,
    ) {
        var loader: PageLoader? = null
        var ownedByController = false
        try {
            val startPage = repository.progressPage(book.id)
                .coerceIn(0, (book.pageCount - 1).coerceAtLeast(0))
            val source = repository.openSource(book)
            loader = PageLoader(
                source = source,
                targetWidth = rootWidthPx.coerceAtLeast(1),
                targetHeight = rootHeightPx.coerceAtLeast(1),
            )
            val anchorPage = anchorPage(
                pageCount = source.pageCount,
                startPage = startPage,
                doublePage = rootWidthPx > rootHeightPx,
                rtl = true,
            )
            val bitmap = loader.load(anchorPage)
            val coverBitmap = decodeCoverBitmap(
                path = book.coverPath,
                targetWidth = rootWidthPx,
                targetHeight = rootHeightPx,
            )
            val session = ReaderOpenSession(
                book = book,
                source = source,
                loader = loader,
                startPage = startPage,
                firstPage = bitmap?.asImageBitmap(),
                coverBitmap = coverBitmap,
            )
            ownedByController = true
            if (!controller.attachSession(session)) {
                session.close()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w("InkvolOpen", "preload failed book=${book.id}", error)
            controller.markContentFailed()
        } finally {
            if (!ownedByController) {
                loader?.close()
            }
        }
    }

    private fun decodeCoverBitmap(
        path: String?,
        targetWidth: Int,
        targetHeight: Int,
    ): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val width = targetWidth.coerceAtLeast(1)
        val height = targetHeight.coerceAtLeast(1)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width &&
            bounds.outHeight / (sample * 2) >= height
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }

    /**
     * 双页模式下，封面交接必须使用 Reader 目标半屏上的那一页。
     * RTL 默认 true；若用户改成 LTR，Reader 上报实际矩形后会平滑重定向。
     */
    private fun anchorPage(
        pageCount: Int,
        startPage: Int,
        doublePage: Boolean,
        rtl: Boolean,
    ): Int {
        val last = (pageCount - 1).coerceAtLeast(0)
        val page = startPage.coerceIn(0, last)
        if (!doublePage) return page

        // buildSpreads 的规则：page 0 单独，之后两页一组。
        val spreadStart = if (page == 0) 0 else (((page - 1) / 2) * 2 + 1)
        val spread = if (spreadStart == 0) {
            listOf(0)
        } else if (spreadStart + 1 <= last) {
            listOf(spreadStart, spreadStart + 1)
        } else {
            listOf(spreadStart)
        }
        return if (rtl) spread.last() else spread.first()
    }
}
