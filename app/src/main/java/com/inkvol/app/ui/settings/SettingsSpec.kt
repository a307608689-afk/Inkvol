package com.inkvol.app.ui.settings

/**
 * M4-S 声明式设置模型：
 * 设置页不再逐行硬编码 UI，而是先声明 SettingSpec，再统一渲染。
 * 这里只描述“可展示的设置项”，不包含 Dialog / Popup。
 */
sealed interface SettingSpec {
    val id: String
    val title: String
    val subtitle: String?
}

/**
 * 开关项。点击即切换；[enabled] 为 false 时整行不可点。
 */
data class ToggleSpec(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    val value: Boolean,
    val enabled: Boolean = true,
    val onToggle: (Boolean) -> Unit,
) : SettingSpec

data class SettingChoice(
    val value: String,
    val label: String,
)

/**
 * 单选项。选项数 > 2 时必须由设置页打开页内玻璃选择面板；
 * 两个选项的小枚举可以继续循环，避免为是/否增加一次点击。
 */
data class ChoiceSpec(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    val value: String,
    val options: List<SettingChoice>,
    val enabled: Boolean = true,
    /**
     * 主题色色卡模式：选项 value 即 ThemeAccent.id，面板渲染为色卡网格，
     * 点选立即生效且面板不关闭（便于连续试色），行首显示当前色圆点。
     */
    val accentPicker: Boolean = false,
    val onSelect: (String) -> Unit,
) : SettingSpec {
    val currentLabel: String
        get() = options.firstOrNull { it.value == value }?.label ?: value

    val usesSheet: Boolean
        get() = options.size > 2

    fun nextValue(): String {
        val index = options.indexOfFirst { it.value == value }
        return options[(index + 1).coerceAtLeast(0) % options.size].value
    }
}

/** 只读信息项，用于版本 / 诊断 / 统计摘要。 */
data class InfoSpec(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    val value: String,
) : SettingSpec

/** 尚未接入功能的说明项，替代旧的 Toast 占位。 */
data class NoteSpec(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    val message: String,
) : SettingSpec

/** 单次动作项，用于导出 / 恢复 / 清理等显式操作。 */
data class ActionSpec(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    val value: String = "执行",
    val enabled: Boolean = true,
    val onAction: () -> Unit,
) : SettingSpec

data class SettingsGroupSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val specs: List<SettingSpec>,
)
