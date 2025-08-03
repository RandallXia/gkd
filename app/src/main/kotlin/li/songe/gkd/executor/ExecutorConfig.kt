package li.songe.gkd.executor

import kotlinx.serialization.Serializable
import li.songe.gkd.data.NodeInfo
import li.songe.gkd.data.RawSubscription

@Serializable
data class ExecutorConfig(
    val selector: String,
    val action: String = "click",
    val fastQuery: Boolean = false,
    val position: RawSubscription.Position? = null,
    val delayAfterExecution: Long? = null, // 执行后延迟时间（毫秒）
    val maxRetries: Int = 1, // 最大重试次数
    val retryDelay: Long = 1000L // 重试间隔（毫秒）
)

data class ExecutorResult(
    val selector: String,
    val action: String,
    val success: Boolean,
    val message: String,
    val nodeInfo: NodeInfo? = null
)