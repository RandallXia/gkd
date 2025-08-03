package li.songe.gkd.executor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.data.ActionPerformer
import li.songe.gkd.data.ActionResult
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
     * @param config 执行配置
     * @param callback 执行回调
     */
    fun executeRule(
        config: ExecutorConfig,
        callback: ExecutorCallback? = null
    ) {
        scope.launch {
            try {
                val result = performRule(config)
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
     * @param configs 执行配置列表
     * @param callback 执行回调
     */
    fun executeRules(
        configs: List<ExecutorConfig>,
        callback: ExecutorCallback? = null
    ) {
        scope.launch {
            val results = mutableListOf<ExecutorResult>()

            for (config in configs) {
                try {
                    val result = performRule(config)
                    results.add(result)

                    // 如果配置了执行后延迟，则等待
                    config.delayAfterExecution?.let {
                        delay(it)
                    }
                } catch (e: Exception) {
                    // 创建失败的结果
                    val failedResult = ExecutorResult(
                        selector = config.selector,
                        action = config.action,
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
            }
        }
    }

    private suspend fun performRule(config: ExecutorConfig): ExecutorResult =
        withContext(Dispatchers.IO) {
            try {
                // 获取当前活动窗口
                val rootNode = accessibilityService.rootInActiveWindow
                    ?: throw IllegalStateException("无法获取当前活动窗口")

                // 解析选择器
                val selector = Selector.parseOrNull(config.selector)
                    ?: throw IllegalArgumentException("无效的选择器: ${config.selector}")

                // 查找目标节点 - 修正 MatchOption 的构造
                val matchOption = MatchOption(
                    fastQuery = config.fastQuery
                )

                // 使用正确的方法查找节点
                val targetNode =
                    a11yContext.transform.querySelector(rootNode, selector, matchOption)
                        ?: throw IllegalStateException("未找到匹配的节点")

                // 执行动作
                val actionResult = performAction(targetNode, config)

                // 创建节点信息 - 使用 info2nodeList 获取正确的 NodeInfo
                val nodeInfoList = info2nodeList(targetNode)
                val nodeInfo = nodeInfoList.firstOrNull()

                ExecutorResult(
                    selector = config.selector,
                    action = config.action,
                    success = actionResult.result,
                    message = if (actionResult.result) "执行成功" else "执行失败",
                    nodeInfo = nodeInfo
                )
            } catch (e: Exception) {
                ExecutorResult(
                    selector = config.selector,
                    action = config.action,
                    success = false,
                    message = "执行异常: ${e.message}"
                )
            }
        }

    private fun performAction(
        node: AccessibilityNodeInfo,
        config: ExecutorConfig
    ): ActionResult {
        // 复用现有的 ActionPerformer 实现
        val performer = ActionPerformer.getAction(config.action)

        return performer.perform(accessibilityService, node, config.position)
    }
}