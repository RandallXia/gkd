package li.songe.gkd.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import li.songe.gkd.shizuku.safeLongTap
import li.songe.gkd.shizuku.safeTap
import java.util.Random

@Serializable
data class GkdAction(
    val selector: String,
    val fastQuery: Boolean = false,
    val action: String? = null,
    val position: RawSubscription.Position? = null,
)

@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
    val shizuku: Boolean = false,
    val position: Pair<Float, Float>? = null,
)

sealed class ActionPerformer(val action: String) {
    abstract fun perform(
        context: AccessibilityService,
        node: AccessibilityNodeInfo,
        position: RawSubscription.Position?,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action, result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
        }
    }

    data object ClickCenter : ActionPerformer("clickCenter") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val p = position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            return ActionResult(
                action = action,
                // TODO 在分屏/小窗模式下会点击到应用界面外部导致误触其他应用
                result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
                    val result = safeTap(x, y)
                    if (result != null) {
                        return ActionResult(action, result, true, position = x to y)
                    }
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, ViewConfiguration.getTapTimeout().toLong()
                        )
                    )
                    context.dispatchGesture(gestureDescription.build(), null, null)
                    true
                } else {
                    false
                }, position = x to y
            )
        }
    }

    data object Click : ActionPerformer("click") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            if (node.isClickable) {
                val result = ClickNode.perform(context, node, position)
                if (result.result) {
                    return result
                }
            }
            return ClickCenter.perform(context, node, position)
        }
    }

    data object LongClickNode : ActionPerformer("longClickNode") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            )
        }
    }

    data object LongClickCenter : ActionPerformer("longClickCenter") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val p = position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            // 500 https://cs.android.com/android/platform/superproject/+/android-8.1.0_r81:frameworks/base/core/java/android/view/ViewConfiguration.java;l=65
            // 400 https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/view/ViewConfiguration.java;drc=8b948e548b782592ae280a3cd9a91798afe6df9d;l=82
            // 某些系统的 ViewConfiguration.getLongPressTimeout() 返回 300 , 这将导致触发普通的 click 事件
            val longClickDuration = 500L
            return ActionResult(
                action = action,
                result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
                    val result = safeLongTap(x, y, longClickDuration)
                    if (result != null) {
                        return ActionResult(action, result, true, position = x to y)
                    }
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, longClickDuration
                        )
                    )
                    // TODO 传入处理 callback
                    context.dispatchGesture(gestureDescription.build(), null, null)
                    true
                } else {
                    false
                },
                position = x to y
            )
        }
    }

    data object LongClick : ActionPerformer("longClick") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            if (node.isLongClickable) {
                val result = LongClickNode.perform(context, node, position)
                if (result.result) {
                    return result
                }
            }
            return LongClickCenter.perform(context, node, position)
        }
    }

    data object Back : ActionPerformer("back") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            )
        }
    }

    data object None : ActionPerformer("none") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = true
            )
        }
    }

    data object ScrollUp : ActionPerformer("scrollUp") {
        override fun perform(
            context: AccessibilityService,
            node: AccessibilityNodeInfo,
            position: RawSubscription.Position?,
        ): ActionResult {
            val random = Random()
            val screenWidth = ScreenUtils.getScreenWidth()
            val screenHeight = ScreenUtils.getScreenHeight()
            
            // 起点和终点位置添加随机性
            val startX = screenWidth * (0.25f + random.nextFloat() * 0.1f)
            val startY = screenHeight * (0.75f + random.nextFloat() * 0.1f)
            
            // 确定滑动方向（轻微向左或向右，但保持一致方向）
            // 生成一个-1到1之间的值，决定整体滑动方向
            val directionBias = random.nextFloat() * 0.3f // 0到0.3之间的偏移
            
            // 终点X坐标在起点基础上添加方向偏移
            val endX = startX + screenWidth * directionBias
            val endY = screenHeight * (0.2f + random.nextFloat() * 0.1f)
            
            // 创建路径
            val path = Path()
            path.moveTo(startX, startY)
            
            // 使用二次贝塞尔曲线创建平滑的路径
            // 控制点在路径中间，但X坐标有轻微偏移，创造自然的弧度
            val controlX = (startX + endX) / 2 + screenWidth * directionBias * 0.5f * (random.nextFloat() - 0.5f)
            val controlY = (startY + endY) / 2
            
            // 添加贝塞尔曲线
            path.quadTo(controlX, controlY, endX, endY)
            
            // 创建手势，设置持续时间在200-300ms之间，模拟真实滑动速度
            val duration = 200L + random.nextInt(100)
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            val gesture = builder.build()
            
            return ActionResult(
                action = action, result = context.dispatchGesture(
                    gesture, object : AccessibilityService.GestureResultCallback() {

                    }, null
                )
            )
        }
    }

    companion object {
        private val allSubObjects by lazy {
            arrayOf(
                ClickNode,
                ClickCenter,
                Click,
                LongClickNode,
                LongClickCenter,
                LongClick,
                Back,
                None,
                ScrollUp
            )
        }

        fun getAction(action: String?): ActionPerformer {
            return allSubObjects.find { it.action == action } ?: Click
        }
    }
}
