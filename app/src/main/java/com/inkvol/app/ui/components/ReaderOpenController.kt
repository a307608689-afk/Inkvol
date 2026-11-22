package com.inkvol.app.ui.components

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.inkvol.app.data.model.BookRecord
import com.inkvol.app.ui.novel.NovelOpenSession
import com.inkvol.app.ui.reader.PageLoader

/** 一镜到底状态机的阶段；progress 由宿主按 phase target 驱动。 */
enum class ReaderOpenPhase {
    Idle,

    /** 预打开书源/解码首页；封面仍停在书架卡上。 */
    Preparing,

    /** 封面飞行 + 背景渐黑 + 封面/内容交叉。 */
    Opening,

    /** overlay 已移除，Reader 完全接管。 */
    Visible,

    Closing,
}

/** 根坐标下的矩形，供宿主 overlay 与书架卡/阅读器目标共用。 */
data class ReaderOpenRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun lerpTo(other: ReaderOpenRect, fraction: Float): ReaderOpenRect {
        val t = fraction.coerceIn(0f, 1f)
        return ReaderOpenRect(
            left = left + (other.left - left) * t,
            top = top + (other.top - top) * t,
            width = width + (other.width - width) * t,
            height = height + (other.height - height) * t,
        )
    }
}

/**
 * 打开动画的预打开会话：书源 / PageLoader / 首页 bitmap 由宿主提前创建，
 * Reader 直接复用，避免重复打开书源或重复解码首页。
 */
class ReaderOpenSession(
    val book: BookRecord,
    val source: com.inkvol.app.data.source.BookContentSource,
    val loader: PageLoader,
    val startPage: Int,
    val firstPage: ImageBitmap?,
    val coverBitmap: ImageBitmap? = null,
) : AutoCloseable {
    val firstPageAspect: Float?
        get() = firstPage?.let { it.width.toFloat() / it.height.toFloat() }

    override fun close() {
        loader.close()
    }
}

/** 书架卡可在 Closing/旋转后回报当前卡片位置，保证封面飞回最新位置。 */
val LocalReaderOpenController = staticCompositionLocalOf<ReaderOpenController?> { null }

/**
 * 阅读器屏（漫画 / 小说）当前是否还有实例挂着（引用计数）。
 *
 * 一镜到底返回的垫层需要判断「真实阅读器还在不在」：路由 pop 后 NavHost 卸载组件
 * 与 `currentRoute` 状态更新不同步（后者滞后 2~3 帧，见 §9.18），用路由判断会让垫层
 * 与阅读器生命周期错位——垫层早撤则真实阅读器正文/顶栏透出叠在书架上（「踩顶栏 /
 * 退出堆叠」），晚撤则书架被底色盖住。这里改用组件生命周期做唯一判据。
 */
object ReaderSurfaceAlive {
    private var count = 0

    var alive by mutableStateOf(false)
        private set

    fun enter() {
        count++
        alive = true
    }

    fun exit() {
        count = (count - 1).coerceAtLeast(0)
        alive = count > 0
    }
}

/**
 * 一镜到底状态机的唯一真源。
 * 由 ReaderOpenViewModel 持有，避免旋转重建丢 session。
 */
class ReaderOpenController {
    private var rootWidthPx = 0f
    private var rootHeightPx = 0f

    /** Closing 起始时刻：书架卡片回报 sourceRect 的瞬态窗口门限用（§9.16）。 */
    private var closingStartedAt = 0L

    var phase by mutableStateOf(ReaderOpenPhase.Idle)
        private set

    var bookId by mutableStateOf<String?>(null)
        private set

    var coverPath by mutableStateOf<String?>(null)
        private set

    /** 预解码封面；overlay 动画期间不再让 Coil 按 target size 反复请求。 */
    var coverBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    var sourceRect by mutableStateOf<ReaderOpenRect?>(null)
        private set

    var targetRect by mutableStateOf<ReaderOpenRect?>(null)
        private set

    /** true = targetRect 来自 Reader 实际布局；false = 导航前的预测矩形。 */
    var targetFromReader by mutableStateOf(false)
        private set

    var coverAspect by mutableStateOf<Float?>(null)
        private set

    /** 用于目标矩形的第一页/封面比例；首页解码后优先使用首页比例。 */
    var contentAspect by mutableStateOf<Float?>(null)
        private set

    /** 预解码的第一页；overlay 用它和封面做同层交叉。 */
    var contentBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * 小说正文首屏快照（打开动画的「终态内容」）。
     * 与 [contentBitmap] 分开存：打开阶段先用封面起飞（不等排版），快照到达后由 overlay
     * 在容器内做「封面 → 正文」交叉；返回阶段仍用 contentBitmap（updateNovelContent 刷新为当前页）。
     */
    var pageBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * 打开阶段最终呈现的内容层：小说优先正文页快照（交接帧与 Reader 首帧同构），
     * 快照未就绪时回落到占位内容（封面）；漫画没有 pageBitmap，恒为首页。
     * overlay 在它之上用 [openCoverBitmap] 做「封面 → 正文」交叉。
     */
    val finalContentBitmap: ImageBitmap? get() = pageBitmap ?: contentBitmap

    /**
     * 打开阶段的交叉封面层：只有小说、且正文页快照已就绪时才有值
     * （漫画打开阶段不画封面；小说快照未就绪时 [finalContentBitmap] 本身就是封面，无需叠加）。
     */
    val openCoverBitmap: ImageBitmap? get() =
        if (isNovel && pageBitmap != null) coverBitmap else null

    /** 飞行容器填充色：漫画黑底 / 小说正文底色。 */
    val flightContainerColor: Int
        get() = if (isNovel) novelBackgroundColor else 0xFF000000.toInt()

    /** 飞行目标是否铺满全屏（小说正文铺满；漫画按页比例 letterbox）。 */
    val flightFullScreen: Boolean get() = isNovel

    var session by mutableStateOf<ReaderOpenSession?>(null)
        private set

    var novelSession by mutableStateOf<NovelOpenSession?>(null)
        private set

    var contentFailed by mutableStateOf(false)
        private set

    var readerReady by mutableStateOf(false)
        private set

    /** Reader 是否处于纵向滚动；overlay 首页需要按 Reader 实际缩放方式绘制。 */
    var readerVertical by mutableStateOf(false)
        private set

    var reason by mutableStateOf<String?>(null)
        private set

    /**
     * 阅读期间旋转（或窗口尺寸变化）过再返回：sourceRect 是打开方向下记录的坐标，
     * 当前布局下不可信（横屏返回时竖屏坐标可能整体在屏幕外）。
     * beginClose 时由宿主传入当前根尺寸与打开时对比置位。
     */
    var sourceRectStale by mutableStateOf(false)
        private set

    /**
     * stale 的 Closing 起飞闸：容器保持全屏（盖住书架以新方向重建的过程），
     * 等书架回报首个稳定坐标（updateSourceRect 释放）或宿主超时
     * （releaseClosingHold）后再从全屏缩回正确卡片。
     */
    var closingHold by mutableStateOf(false)
        private set

    /** true = 本次打开的是 TXT 小说（封面飞行，无 PageLoader）。 */
    var isNovel by mutableStateOf(false)
        private set

    /** 小说打开时飞行容器填充的正文背景色（ARGB）。 */
    var novelBackgroundColor by mutableStateOf(0xFFFDFDFD.toInt())
        private set

    /** phase 对应的目标进度；Preparing 不起飞。 */
    val targetProgress: Float
        get() = when (phase) {
            ReaderOpenPhase.Preparing -> 0f
            ReaderOpenPhase.Opening -> 1f
            ReaderOpenPhase.Visible -> 1f
            ReaderOpenPhase.Closing -> 0f
            ReaderOpenPhase.Idle -> 0f
        }

    fun beginOpen(
        bookId: String,
        coverPath: String?,
        sourceRect: ReaderOpenRect?,
        rootWidthPx: Float,
        rootHeightPx: Float,
        isNovel: Boolean = false,
    ) {
        // phase 守卫：与点击入口的 Idle 守卫对齐。非 Idle 时 beginOpen 会无条件
        // closeSession() 清掉活会话（ReaderContent 短暂卸载、返回键落空），且把
        // 进行中的流转整体清零——连续快速开关时正是卡死 Visible 的源头之一。
        if (phase != ReaderOpenPhase.Idle) {
            log("beginOpen rejected phase=$phase book=$bookId")
            return
        }
        this.bookId = bookId
        this.coverPath = coverPath
        this.coverBitmap = null
        this.sourceRect = sourceRect
        this.coverAspect = null
        this.contentAspect = null
        this.isNovel = isNovel
        this.novelBackgroundColor = 0xFFFDFDFD.toInt()
        this.rootWidthPx = rootWidthPx
        this.rootHeightPx = rootHeightPx
        targetRect = predictTargetRect(sourceRect, null, rootWidthPx, rootHeightPx)
        targetFromReader = false
        sourceRectStale = false
        closingHold = false
        closeSession()
        contentFailed = false
        readerReady = false
        readerVertical = false
        reason = "Open"
        phase = ReaderOpenPhase.Preparing
        log("beginOpen source=$sourceRect target=$targetRect")
    }

    /** 预加载完成/超时后开始起飞。 */
    fun beginOpening() {
        if (phase != ReaderOpenPhase.Preparing) return
        reason = "Open"
        phase = ReaderOpenPhase.Opening
        log("beginOpening")
    }

    fun cancelPreparing() {
        if (phase != ReaderOpenPhase.Preparing) return
        closeSession()
        contentFailed = false
        readerReady = false
        reason = null
        phase = ReaderOpenPhase.Idle
        log("cancelPreparing")
    }

    fun setCoverAspect(bookId: String, aspect: Float?) {
        if (this.bookId != bookId) return
        coverAspect = aspect
        if (!targetFromReader) {
            targetRect = predictTargetRect(sourceRect, contentAspect ?: aspect, rootWidthPx, rootHeightPx)
        }
        log("coverAspect=$aspect target=$targetRect")
    }

    fun setTargetRect(bookId: String, rect: ReaderOpenRect?) {
        if (this.bookId != bookId) return
        targetRect = rect
        targetFromReader = true
        log("actual target=$rect ready=$readerReady")
    }

    /**
     * 书架卡片回报当前位置（Closing 期间）。
     * settled=false 表示宿主网格/列表还在恢复（数据生长、逐帧重申），瞬态布局
     * 坐标不可信，直接忽略——替代旧的 250ms 时间门限（§9.16）：恢复落位由页面侧
     * 稳定信号判定，恢复时长不再与动画时长竞争。旋转场景（sourceRectStale）下
     * 首条 settled 回报同时解除起飞闸，容器改道飞向新布局下的正确位置。
     */
    fun updateSourceRect(bookId: String, rect: ReaderOpenRect?, settled: Boolean = true) {
        if (this.bookId != bookId) return
        if (phase != ReaderOpenPhase.Closing) return
        if (!settled) {
            log("closing sourceRect ignored (not settled) $rect")
            return
        }
        sourceRect = rect
        if (closingHold) {
            closingHold = false
        }
        log("closing sourceRect=$rect")
    }

    fun setReaderVertical(bookId: String, vertical: Boolean) {
        if (this.bookId != bookId) return
        readerVertical = vertical
        log("readerVertical=$vertical")
    }

    fun setReaderReady(bookId: String) {
        if (this.bookId != bookId) return
        readerReady = true
        log("readerReady target=$targetRect")
    }

    fun setNovelBackgroundColor(bookId: String, color: Int) {
        if (this.bookId != bookId) return
        novelBackgroundColor = color
        log("novelBackgroundColor=#${Integer.toHexString(color)}")
    }

    /** 封面先行：正文快照可能较慢，先用封面起飞避免空屏等待。 */
    fun attachNovelCoverOnly(bookId: String, cover: ImageBitmap?): Boolean {
        if (this.bookId != bookId) return false
        if (phase != ReaderOpenPhase.Preparing && phase != ReaderOpenPhase.Opening) return false
        coverBitmap = cover
        coverAspect = cover?.let { it.width.toFloat() / it.height.toFloat() }
        contentBitmap = cover
        contentAspect = coverAspect
        contentFailed = false
        readerReady = false
        if (phase == ReaderOpenPhase.Preparing) beginOpening()
        log("novel cover attached")
        return true
    }

    /** 小说正文页与会话就绪后挂接；不覆盖已用于起飞的封面内容。 */
    fun attachNovelSession(session: NovelOpenSession, cover: ImageBitmap?): Boolean {
        if (bookId != session.book.id) return false
        if (phase != ReaderOpenPhase.Preparing && phase != ReaderOpenPhase.Opening) return false
        novelSession = session
        // 正文首屏快照单独留存：打开动画的「终态内容」，供封面 → 正文交叉使用
        pageBitmap = session.firstPageBitmap
        coverBitmap = cover ?: coverBitmap
        if (contentBitmap == null) {
            contentBitmap = session.firstPageBitmap
            contentAspect = session.firstPageBitmap.width.toFloat() / session.firstPageBitmap.height.toFloat()
        }
        contentFailed = false
        readerReady = false
        if (phase == ReaderOpenPhase.Preparing) beginOpening()
        log("novel session attached chapter=${session.chapterIndex}")
        return true
    }

    /** 返回前更新为用户实际停留的正文页，而不是打开时的首页。 */
    fun updateNovelContent(bookId: String, bitmap: ImageBitmap) {
        if (this.bookId != bookId || !isNovel) return
        contentBitmap = bitmap
        contentAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    }

    /** 预加载失败：Preparing 直接取消；Opening 反向关闭。 */
    fun markContentFailed() {
        if (phase != ReaderOpenPhase.Preparing && phase != ReaderOpenPhase.Opening) return
        contentFailed = true
        log("contentFailed")
    }

    /**
     * 宿主预加载完成后挂接会话。
     * 只允许 Preparing/Opening；取消/旧任务/已完成都拒绝。
     */
    fun attachSession(session: ReaderOpenSession): Boolean {
        if (bookId != session.book.id) {
            session.close()
            return false
        }
        if (phase != ReaderOpenPhase.Preparing && phase != ReaderOpenPhase.Opening) {
            session.close()
            return false
        }

        this.session = session
        coverBitmap = session.coverBitmap
        contentBitmap = session.firstPage
        contentFailed = false
        contentAspect = session.firstPageAspect ?: coverAspect
        // 首页 bitmap 已就绪时，handoff 可以在打开途中开始；
        // 否则仍等 Reader 内部 load 首页后 setReaderReady。
        readerReady = session.firstPage != null
        if (!targetFromReader) {
            targetRect = predictTargetRect(
                sourceRect = sourceRect,
                coverAspect = contentAspect,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
            )
        }
        if (phase == ReaderOpenPhase.Preparing) {
            beginOpening()
        }
        log("session attached start=${session.startPage} first=${session.firstPage != null}")
        return true
    }

    fun closeSession() {
        session?.close()
        session = null
        novelSession = null
        coverBitmap = null
        contentBitmap = null
        pageBitmap = null
    }

    fun beginClose(
        reason: String,
        currentRootWidthPx: Float? = null,
        currentRootHeightPx: Float? = null,
    ) {
        if (phase == ReaderOpenPhase.Idle) return
        this.reason = reason
        phase = ReaderOpenPhase.Closing
        closingStartedAt = System.currentTimeMillis()
        // 根尺寸与打开时不同 = 阅读期间旋转（或分屏/窗口尺寸变化）过：sourceRect
        // 是旧方向的坐标，当前布局下不可信。置 stale 并拉起起飞闸——书架正在以
        // 新方向重建，等首个稳定回报（updateSourceRect 释放）再反向，宿主超时
        // releaseClosingHold 兜底（旧坐标飞完，宁可错不卡死）。
        val rotated = currentRootWidthPx != null && currentRootHeightPx != null &&
            (currentRootWidthPx != rootWidthPx || currentRootHeightPx != rootHeightPx)
        sourceRectStale = rotated
        closingHold = rotated
        log("beginClose rotated=$rotated")
    }

    /** 宿主超时兜底：书架始终没回报（目标卡不在视口、恢复异常）时放行按旧坐标起飞。 */
    fun releaseClosingHold() {
        if (phase != ReaderOpenPhase.Closing || !closingHold) return
        closingHold = false
        log("closing hold released by timeout")
    }

    fun markVisible() {
        if (phase != ReaderOpenPhase.Opening) return
        reason = "Ready"
        phase = ReaderOpenPhase.Visible
        log("visible")
    }

    fun markIdle() {
        if (phase != ReaderOpenPhase.Closing) return
        phase = ReaderOpenPhase.Idle
        reason = null
        closingHold = false
        sourceRectStale = false
        closeSession()
        log("idle")
    }

    /**
     * 自愈复位：路由已回非 reader 页而 phase 卡在非 Idle（静默 pop、导航丢失等
     * 异常路径）时由宿主调用。不做动画，直接归零——否则点击入口的 Idle 守卫会把
     * 后续打开全部静默丢弃，只能重启恢复。
     */
    fun forceIdle() {
        if (phase == ReaderOpenPhase.Idle) return
        log("forceIdle from=$phase")
        closeSession()
        contentFailed = false
        readerReady = false
        readerVertical = false
        isNovel = false
        novelBackgroundColor = 0xFFFDFDFD.toInt()
        closingHold = false
        sourceRectStale = false
        reason = null
        phase = ReaderOpenPhase.Idle
    }

    private fun predictTargetRect(
        sourceRect: ReaderOpenRect?,
        coverAspect: Float?,
        rootWidthPx: Float,
        rootHeightPx: Float,
    ): ReaderOpenRect {
        // 小说正文铺满全屏，目标矩形从一开始就是根矩形，避免飞行中途改道。
        if (flightFullScreen) {
            return ReaderOpenRect(0f, 0f, rootWidthPx, rootHeightPx)
        }
        val aspect = coverAspect
            ?: sourceRect?.takeIf { it.height > 0f }?.let { it.width / it.height }
            ?: 0.72f
        val doublePage = rootWidthPx > rootHeightPx
        if (!doublePage) {
            val width = minOf(rootWidthPx, rootHeightPx * aspect)
            val height = width / aspect
            return ReaderOpenRect(
                left = (rootWidthPx - width) / 2f,
                top = (rootHeightPx - height) / 2f,
                width = width,
                height = height,
            )
        }
        val spreadAspect = aspect * 2f
        val spreadWidth = minOf(rootWidthPx, rootHeightPx * spreadAspect)
        val spreadHeight = spreadWidth / spreadAspect
        val spreadLeft = (rootWidthPx - spreadWidth) / 2f
        val halfWidth = spreadWidth / 2f
        return ReaderOpenRect(
            left = spreadLeft + halfWidth,
            top = (rootHeightPx - spreadHeight) / 2f,
            width = halfWidth,
            height = spreadHeight,
        )
    }

    private fun log(message: String) {
        Log.d(
            "InkvolOpen",
            "phase=$phase book=$bookId reason=$reason ready=$readerReady target=$targetRect $message",
        )
    }
}
