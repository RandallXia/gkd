package li.songe.gkd.executor

interface ExecutorCallback {
    fun onSuccess(result: ExecutorResult) {}
    fun onError(error: Exception) {}
    fun onBatchSuccess(results: List<ExecutorResult>) {}
}