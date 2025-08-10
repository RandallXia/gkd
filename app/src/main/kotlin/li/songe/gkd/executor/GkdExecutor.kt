package li.songe.gkd.executor

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.data.ActionPerformer
import li.songe.gkd.data.GkdAction
import li.songe.gkd.data.info2nodeList
import li.songe.gkd.service.A11yContext
import li.songe.selector.MatchOption
import li.songe.selector.Selector

class GkdExecutor(private val accessibilityService: AccessibilityService) {
    private val scope = CoroutineScope(Dispatchers.Default)

    // 复用 A11yContext 中的 transform
    private val a11yContext = A11yContext()

    /**
     * 执行单个规则
     * @param action GkdAction 执行配置
     * @param callback 执行回调
     */
    fun executeRule(
        action: GkdAction,
        callback: ExecutorCallback? = null
    ) {
        scope.launch {
            try {
                val result = performRule(action)
                withContext(Dispatchers.Main) {
                    callback?.onSuccess(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onError(e)
                }
            }
        }
    }

    /**
     * 批量执行规则
     * @param actions GkdAction 执行配置列表
     * @param callback 执行回调
     */
    fun executeRules(
        actions: List<GkdAction>,
        callback: ExecutorCallback? = null
    ) {
        scope.launch {
            val results = mutableListOf<ExecutorResult>()

            for (action in actions) {
                try {
                    val result = performRule(action)
                    results.add(result)
                } catch (e: Exception) {
                    // 创建失败的结果
                    val failedResult = ExecutorResult(
                        selector = action.selector,
                        action = action.action ?: "click",
                        success = false,
                        message = "执行失败: ${e.message}"
                    )
                    results.add(failedResult)
                }
            }

            // 创建批量执行的汇总结果
            val batchResult = ExecutorResult(
                selector = "batch",
                action = "batch",
                success = results.all { it.success },
                message = "批量执行完成，成功: ${results.count { it.success }}/${results.size}"
            )

            withContext(Dispatchers.Main) {
                callback?.onSuccess(batchResult)
                callback?.onBatchSuccess(results)
            }
        }
    }

    private suspend fun performRule(action: GkdAction): ExecutorResult =
        withContext(Dispatchers.IO) {
            try {
                // 使用 A11yService.execAction 方法执行动作
                val serviceVal = accessibilityService
                val selector = Selector.parseOrNull(action.selector)
                    ?: throw IllegalArgumentException("无效的选择器: ${action.selector}")
                
                val matchOption = MatchOption(
                    fastQuery = action.fastQuery
                )
                val cache = A11yContext(true)

                val rootNode = serviceVal.rootInActiveWindow
                    ?: throw IllegalStateException("无法获取当前活动窗口")

                val targetNode = cache.querySelfOrSelector(
                    rootNode,
                    selector,
                    matchOption
                ) ?: throw IllegalStateException("未找到匹配的节点")
                
                // 执行动作
                val performer =
                    ActionPerformer.getAction(action.action ?: ActionPerformer.None.action)
                val actionResult = performer.perform(serviceVal, targetNode, action.position)

                // 创建节点信息
                val nodeInfoList = info2nodeList(targetNode)
                val nodeInfo = nodeInfoList.firstOrNull()

                ExecutorResult(
                    selector = action.selector,
                    action = action.action ?: "click",
                    success = actionResult.result,
                    message = if (actionResult.result) "执行成功" else "执行失败",
                    nodeInfo = nodeInfo
                )
            } catch (e: Exception) {
                ExecutorResult(
                    selector = action.selector,
                    action = action.action ?: "click",
                    success = false,
                    message = "执行异常: ${e.message}"
                )
            }
        }
}