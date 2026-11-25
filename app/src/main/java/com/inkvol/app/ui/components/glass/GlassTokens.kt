package com.inkvol.app.ui.components.glass

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported

/**
 * 液态玻璃底部 chrome 的统一几何。
 *
 * 参照 TiebaPura 的满宽玻璃底栏比例换算而来：整条栏左右各留 24dp、离屏底 30dp、
 * 高度 74dp，内部按 5 枚彼此独立的胶囊等分（而不是共享一条玻璃轨道）。
 */
object GlassDimens {
    /** 底栏高度（无文字，仅图标居中） */
    val BarHeight: Dp = 56.dp

    /** 底栏左右外边距 */
    val BarHorizontalMargin: Dp = 24.dp

    /** 底栏距手势条上缘的距离（宿主再叠加 navigationBars 安全区，总底距 ≈ 30dp，贴底观感） */
    val BarBottomMargin: Dp = 6.dp

    /** 底栏胶囊之间的间隙 */
    val CapsuleGap: Dp = 8.dp

    /** 单枚胶囊的最小触控宽度 */
    val CapsuleMinWidth: Dp = 64.dp

    /** 选中态额外扩张的宽度 */
    val SelectedExtraWidth: Dp = 12.dp

    /** 分页胶囊高度（压薄一档，减轻"两层厚玻璃"的观感） */
    val PageBarHeight: Dp = 40.dp

    /** 分页胶囊之间的间隙 */
    val PageCapsuleGap: Dp = 10.dp

    /** 分页层与底栏之间的间隙（物理值已含玻璃高光外扩约 6dp 的补偿，观感 ≈16） */
    val PageBarGap: Dp = 22.dp

    /** 列表底部呼吸空间，避免最后一行贴着玻璃边缘 */
    val ContentBreathing: Dp = 12.dp

    /**
     * 手机底部三颗玻璃钮（RailFab）的视觉间距补偿：三颗的折射边缘宽度随背景内容
     * 略有出入，实测按此值左移中间那颗（刷新）后两处空隙视觉等距（真机校准 -2.6dp）
     */
    val FabSpacingCompensation: Dp = -2.6.dp

    /** 列表底部额外滚动余量：内容不足一屏时也能上滑触发顶栏隐显、看全被遮内容 */
    val ListBottomExtraScroll: Dp = 240.dp

    /** 胶囊内图标尺寸 */
    val IconSize: Dp = 22.dp

    /** 底栏 / 分页条的模糊半径（12dp：背景细节更淡，文字对比更干净） */
    val StrongBlur: Dp = 12.dp

    /** 小型悬浮钮的模糊半径 */
    val LightBlur: Dp = 8.dp

    /** 平板侧栏宽度：导航只剩图标，页码用简写数字，收窄一档 */
    val RailWidth: Dp = 52.dp

    /** 侧栏离屏幕右缘的间隙，避免完全贴边 */
    val RailMargin: Dp = 14.dp

    /** 旧版 5 图标满配侧栏高度，侧栏改版后不再使用 */
    val RailHeightFull: Dp = 560.dp

    /** 旧版 5 图标仅导航高度，侧栏改版后不再使用 */
    val RailHeightCompact: Dp = 272.dp

    /** 导航单项高度（仅图标，无文字） */
    val RailItemHeight: Dp = 48.dp

    /** 侧栏内竖排圆钮尺寸 */
    val RailFabSize: Dp = 44.dp

    /** 侧栏导航条上下内边距（导航高度 = 项数 × RailItemHeight + 2 × 此值） */
    val RailNavPadding: Dp = 8.dp

    /** 侧栏顶部动作槽内圆钮间距 */
    val RailActionGap: Dp = 10.dp

    /** 侧栏外框圆角 = 半宽 60/2，完整胶囊（上下半圆，宽度收窄后同步） */
    val RailCorner: Dp = 30.dp

    /** 底栏整条的最大宽度：平板上避免五等分被拉得过散 */
    val BarMaxWidth: Dp = 560.dp

    /** 底栏外框圆角 = 半高 56/2，完整胶囊 */
    val BarCorner: Dp = 28.dp

    /** 条内内容整体内收：首尾项离玻璃两端各让出此值（底栏与页数栏共用；16 = 两轮内收合计） */
    val BarInnerPaddingX: Dp = 16.dp
}

/** 玻璃材质分级：全档（模糊 + 折射 + 着色）/ 轻档（仅模糊 + 着色）/ 关闭（自绘降级） */
enum class GlassTier { Full, Lite, None }

/**
 * 平台能力分档。
 *
 * 真模糊依赖 RenderEffect（Android 12 / API 31+），折射与色散依赖 RuntimeShader
 * （Android 13 / API 33+）。库内部对这些能力是静默降级（`updateEffects()` 直接返回），
 * 但为了不在低版本白跑效果链，这里先自己判一次。
 */
object GlassCapabilities {
    val blurSupported: Boolean by lazy {
        runCatching { isRenderEffectSupported() }.getOrDefault(false)
    }

    val refractionSupported: Boolean by lazy {
        runCatching { isRuntimeShaderSupported() }.getOrDefault(false)
    }
}

/** 动效参数（集中定义，避免各处硬编码时长） */
object GlassMotion {
    const val HIDE_MS = 220
    const val SHOW_MS = 260
    const val SELECT_MS = 260

    // ---- 覆盖层（弹窗类）进出场 ----
    // 书架/设置页的底部面板：进 220 / 出 170
    const val OVERLAY_ENTER_MS = 220
    const val OVERLAY_EXIT_MS = 170

    // 阅读器内三块面板快一档（沉浸场景连续操作，不打断阅读节奏）
    const val READER_ENTER_MS = 220
    const val READER_EXIT_MS = 160

    /**
     * 阅读器面板位移用「自身高度比例」而不是固定 dp：
     * 面板高度随内容变化（短则半屏、长则近满屏），固定 40dp 在高面板上只占 6%，
     * 看起来只是"黏着飘了一下"；按比例才有明确"升起来"的行程。
     */
    const val READER_PANEL_RISE_FRAC = 0.20f
    const val READER_PANEL_FALL_FRAC = 0.12f

    /** 阅读器面板轻微缩放（进场 0.96 → 1，退场回 0.96），补"体量生成"的观感 */
    const val READER_PANEL_SCALE = 0.96f

    /** 底部面板进场起始位移（只用 40dp，不做整页滑入：背景不压暗，模态感要弱） */
    const val PANEL_RISE_DP = 40f

    /** 底部面板退场位移（比进场短，退得快） */
    const val PANEL_FALL_DP = 24f

    /** 居中卡进/退场缩放 */
    const val CARD_SCALE_FROM = 0.94f
    const val CARD_SCALE_TO = 0.96f

    /** 下拉菜单：从锚点角放大 + 轻微下坠收口 */
    const val MENU_SCALE_FROM = 0.94f
    const val MENU_SCALE_TO = 0.97f
    const val MENU_LIFT_DP = 8f

    /** chrome 让位（覆盖层打开时底栏/侧栏退场） */
    const val CHROME_YIELD_MS = 160
    const val CHROME_YIELD_DROP_DP = 12f

    /** 停止滚动多久之后把底栏叫回来 */
    const val RESTORE_DELAY_MS = 700L

    /** 滚动停止多久之后恢复透镜（滚动中降档） */
    const val LENS_SETTLE_MS = 200L

    /** 隐藏时整体缩小的比例 */
    const val HIDDEN_SCALE = 0.96f

    /** 隐藏时的透明度 */
    const val HIDDEN_ALPHA = 0.6f

    /** 按压回弹比例 */
    const val PRESS_SCALE = 0.94f

    /** 上滑/下滑累计多少距离才切换显隐，避免惯性抖动 */
    val SCROLL_TRIGGER: Dp = 18.dp
}

/**
 * 底部 chrome 对内容的占位信息。
 *
 * 宿主通过 [LocalGlassChrome] 下发，各页列表直接用它算 `contentPadding.bottom`，
 * 不再逐页硬编码 80.dp。
 */
data class GlassChromeInsets(
    val bottomBarHeight: Dp = GlassDimens.BarHeight,
    /** 已包含手势条安全区的底部间距 */
    val bottomGap: Dp = GlassDimens.BarBottomMargin,
    val pageBarHeight: Dp = GlassDimens.PageBarHeight,
    val pageBarGap: Dp = GlassDimens.PageBarGap,
    /** 当前页面是否会出现分页胶囊 */
    val hasPageBar: Boolean = false,
    /** 底部 chrome 是否参与显示（二级页面为 false） */
    val chromeVisible: Boolean = true,
    /** 侧栏形态：chrome 件挂在屏幕右缘而非底部（平板 / 手机横屏） */
    val railMode: Boolean = false,
    /** 沉浸侧栏：内容区右侧需要让出的宽度；由宿主按形态下发（有侧栏 66 / 无侧栏 0） */
    val railEndInset: Dp = 0.dp,
) {
    /** 不含分页层时，列表需要的底部留白 */
    val contentBottom: Dp
        get() = if (!chromeVisible) {
            bottomGap
        } else {
            bottomBarHeight + bottomGap + GlassDimens.ContentBreathing
        }

    /** 含分页层时，列表需要的底部留白 */
    val contentBottomWithPageBar: Dp
        get() = if (!chromeVisible) 0.dp else contentBottom + pageBarHeight + pageBarGap

    /** 按当前页面是否分页，给出实际留白 */
    val contentBottomForPage: Dp
        get() = if (hasPageBar) contentBottomWithPageBar else contentBottom

    /** 页数栏底边距屏幕底的距离（悬浮钮用它与之底部齐平） */
    val pageBarBottom: Dp
        get() = if (!chromeVisible) {
            bottomGap + pageBarGap
        } else {
            bottomBarHeight + bottomGap + pageBarGap
        }

}

/** 玻璃边缘的两种装饰风格：亮高光（默认）或细黑描边 */

/** 玻璃边缘的两种装饰风格：亮高光（默认）或细黑描边 */
enum class GlassEdge { Highlight, Outline }

/** 无宿主包裹时的兜底（例如预览、单页调试） */
val LocalGlassChrome = staticCompositionLocalOf { GlassChromeInsets() }

/** 平板侧栏内的页数信息（由各顶层页提供；无分页的页面不提供） */
data class RailPageInfo(
    val page: Int,
    val loading: Boolean,
    val hasMore: Boolean,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onJump: (Int) -> Unit,
)

/** 平板侧栏内的悬浮动作（由各顶层页提供） */
data class RailFabActions(
    val onRefresh: () -> Unit,
    val onToTop: () -> Unit,
    val onDownloads: () -> Unit,
)

/**
 * 侧栏数据槽位：页面写入、侧栏读取。
 * CompositionLocal 只能自上而下传播，而侧栏在宿主层、页面在 NavHost 内，
 * 所以"页面向上递数据"必须走可变槽位而不是 CompositionLocal。
 */
class RailSlot<T> {
    var value: T? by mutableStateOf(null)
}

/** 侧栏页数段槽位：由当前顶层页写入 */
val LocalRailPageSlot = staticCompositionLocalOf { RailSlot<RailPageInfo>() }

/** 侧栏悬浮钮段槽位：由当前顶层页写入 */
val LocalRailFabSlot = staticCompositionLocalOf { RailSlot<RailFabActions>() }

/**
 * 系统「移除动画」开关。关闭时所有底部 chrome 的位移直接切换，
 * 避免无障碍设置被无视（同时也让低端机上省掉一整条动画链）。
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}

/**
 * 共享的玻璃采集层。页面里的悬浮玻璃（分页胶囊）直接复用它，
 * 不要各自 `rememberLayerBackdrop`，否则同一块屏幕会被录制多份。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

/**
 * 底部 chrome 的"收起进度"：0 = 完全显示，1 = 完全收起。
 * 由宿主统一驱动动画，页面侧的悬浮层读它做同相位位移，保证两层层同步不打滑。
 */
val LocalGlassChromeProgress = compositionLocalOf { 0f }
