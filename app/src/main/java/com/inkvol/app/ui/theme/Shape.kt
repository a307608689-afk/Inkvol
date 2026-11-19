package com.inkvol.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val InkvolShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

object InkvolRadius {
    val card = RoundedCornerShape(20.dp)

    /** 封面顶部圆角，卡片与详情页共用，避免每次重组新建 Shape。 */
    val cardTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** 细进度条圆角，书架列表会按项复用。 */
    val bar = RoundedCornerShape(4.dp)
    val cover = RoundedCornerShape(14.dp)

    /** 书夹整卡 shape：点击反馈、目标边框、拖拽阴影共用。 */
    val collection = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
    val group = RoundedCornerShape(24.dp)
}
