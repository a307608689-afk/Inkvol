@file:Suppress("MatchingDeclarationName")

package com.inkvol.pagecurl.page

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import com.inkvol.pagecurl.utils.rotate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI

internal data class DragConfig(
    val edge: Animatable<Edge, AnimationVector4D>,
    val start: Edge,
    val end: Edge,
    val isEnabled: () -> Boolean,
    val isDragSucceed: (Offset, Offset) -> Boolean,
    val onChange: () -> Unit,
)

internal suspend fun PointerInputScope.detectCurlGestures(
    scope: CoroutineScope,
    newEdgeCreator: NewEdgeCreator,
    getConfig: (Offset, Offset) -> DragConfig?,
) {
    // Use velocity tracker to support flings
    val velocityTracker = VelocityTracker()

    var config: DragConfig? = null
    var startOffset: Offset = Offset.Zero

    // vendor 修改：拖动目标合并。
    // onDrag 只把最新手指位置丢进 CONFLATED 通道（旧值被覆盖），由下方单个常驻协程消费并
    // 瞬时置值——每帧最多一次 snapTo/一次重组。原实现每个 move 起一个协程，慢速拖动时
    // 几十上百个协程在主线程排队抢 Animatable 锁，输入事件被挤后 → 拖到一半停住即"卡死"。
    val dragTargets = Channel<Offset>(Channel.CONFLATED)

    coroutineScope {
    launch {
        for (pos in dragTargets) {
            val cfg = config ?: continue
            if (!cfg.isEnabled()) continue
            cfg.edge.snapTo(newEdgeCreator.createNew(size, startOffset, pos))
        }
    }

    detectCustomDragGestures(
        onDragStart = { start, end ->
            startOffset = start
            config = getConfig(start, end)
            config != null
        },
        onDragEnd = { endOffset, complete ->
            // vendor 修改：先断开 pump 并清掉残留目标，再做收尾动画，
            // 避免残留的 snapTo 把页面又拽回旧位置、打断收尾动画
            val cfg = config
            config = null
            while (dragTargets.tryReceive().isSuccess) {
                // drain
            }
            cfg?.apply {
                val velocity = velocityTracker.calculateVelocity()
                val decay = splineBasedDecay<Offset>(this@detectCurlGestures)
                val flingEndOffset = decay.calculateTargetValue(
                    Offset.VectorConverter,
                    endOffset,
                    Offset(velocity.x, velocity.y)
                ).let {
                    Offset(
                        it.x.coerceIn(0f, size.width.toFloat() - 1),
                        it.y.coerceIn(0f, size.height.toFloat() - 1)
                    )
                }

                scope.launch {
                    if (complete && isDragSucceed(startOffset, flingEndOffset)) {
                        try {
                            edge.animateTo(end)
                        } finally {
                            onChange()
                            edge.snapTo(start)
                        }
                    } else {
                        try {
                            edge.animateTo(start)
                        } finally {
                            edge.snapTo(start)
                        }
                    }
                }
            }
        },
        onDrag = { change, _ ->
            val cfg = config
            if (cfg != null && !cfg.isEnabled()) {
                // vendor 修改：原实现 throw CancellationException() 会取消整个 pointerInput
                // 协程（awaitEachGesture 不捕获恢复，协程 key 稳定不重启）→ 库拖拽永久失效。
                // 改为静默放弃本手势：edge 回弹起点、清 config 跳过后续 onDrag/onDragEnd。
                config = null
                scope.launch { cfg.edge.animateTo(cfg.start) }
            } else if (cfg != null) {
                velocityTracker.addPosition(System.currentTimeMillis(), change.position)
                // vendor 修改：只投递最新手指位置（CONFLATED 通道覆盖旧值），由单个协程消费
                // 并瞬时置值——每帧最多一次 snapTo。原实现每个 move 起一个动画协程，慢速拖动
                // 时几十上百个协程在主线程排队抢锁，输入事件被挤后 → 拖到一半停住即"卡死"。
                dragTargets.trySend(change.position)
            }
        }
    )
    }
}

internal suspend fun PointerInputScope.detectCustomDragGestures(
    onDragStart: (Offset, Offset) -> Boolean,
    onDragEnd: (Offset, Boolean) -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var drag: PointerInputChange?
        var overSlop = Offset.Zero
        do {
            drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                overSlop = over
            }
        } while (drag != null && !drag.isConsumed)
        if (drag != null) {
            if (!onDragStart.invoke(down.position, drag.position)) {
                return@awaitEachGesture
            }
            onDrag(drag, overSlop)
            val completed = drag(drag.id) {
                drag = it
                onDrag(it, it.positionChange())
                it.consume()
            }
            onDragEnd(drag?.position ?: down.position, completed)
        }
    }
}

internal abstract class NewEdgeCreator {

    abstract fun createNew(size: IntSize, startOffset: Offset, currentOffset: Offset): Edge

    protected fun createVectors(size: IntSize, startOffset: Offset, currentOffset: Offset): Pair<Offset, Offset> {
        val vector = Offset(size.width.toFloat(), startOffset.y) - currentOffset
        val rotatedVector = vector.rotate(PI.toFloat() / 2)
        return vector to rotatedVector
    }

    class Default : NewEdgeCreator() {
        override fun createNew(size: IntSize, startOffset: Offset, currentOffset: Offset): Edge {
            val vectors = createVectors(size, startOffset, currentOffset)
            return Edge(currentOffset - vectors.second, currentOffset + vectors.second)
        }
    }

    class PageEdge : NewEdgeCreator() {
        override fun createNew(size: IntSize, startOffset: Offset, currentOffset: Offset): Edge {
            val (vector, rotatedVector) = createVectors(size, startOffset, currentOffset)
            return Edge(currentOffset - rotatedVector + vector / 2f, currentOffset + rotatedVector + vector / 2f)
        }
    }
}
