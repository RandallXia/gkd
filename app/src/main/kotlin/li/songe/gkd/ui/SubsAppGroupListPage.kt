package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.UpsertRuleGroupPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.data.GkdAction
import li.songe.gkd.db.DbSet
import li.songe.gkd.executor.ExecutorCallback
import li.songe.gkd.executor.ExecutorResult
import li.songe.gkd.service.A11yService
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.BatchActionButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.animateListItem
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.local.LocalMainViewModel
import li.songe.gkd.ui.local.LocalNavController
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LIST_PLACEHOLDER_KEY
import li.songe.gkd.util.copyText
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toJson5String
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsAppGroupListPage(
    subsItemId: Long,
    appId: String,
    @Suppress("unused") focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current
    val vm = viewModel<SubsAppGroupListVm>()
    val subs = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val app by vm.subsAppFlow.collectAsState()

    val groupToCategoryMap = subs?.groupToCategoryMap ?: emptyMap()

    val editable = subsItemId < 0
    val isSelectedMode = vm.isSelectedModeFlow.collectAsState().value
    val selectedDataSet = vm.selectedDataSetFlow.collectAsState().value
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode) {
            vm.selectedDataSetFlow.value = emptySet()
        }
    }
    LaunchedEffect(key1 = selectedDataSet.isEmpty()) {
        if (selectedDataSet.isEmpty()) {
            vm.isSelectedModeFlow.value = false
        }
    }
    BackHandler(isSelectedMode) {
        vm.isSelectedModeFlow.value = false
    }
    val (scrollBehavior, listState) = useListScrollState(app.groups.isEmpty())
    if (focusGroupKey != null) {
        LaunchedEffect(null) {
            if (vm.focusGroupFlow?.value != null) {
                val i = app.groups.indexOfFirst { it.key == focusGroupKey }
                if (i >= 0) {
                    listState.scrollToItem(i)
                }
            }
        }
    }
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = throttle {
                if (isSelectedMode) {
                    vm.isSelectedModeFlow.value = false
                } else {
                    navController.popBackStack()
                }
            }) {
                BackCloseIcon(backOrClose = !isSelectedMode)
            }
        }, title = {
            if (isSelectedMode) {
                Text(
                    text = if (selectedDataSet.isNotEmpty()) selectedDataSet.size.toString() else "",
                )
            } else {
                TowLineText(
                    title = subs?.name ?: subsItemId.toString(),
                    subtitle = appId,
                    showApp = true,
                )
            }
        }, actions = {
            var expanded by remember { mutableStateOf(false) }
            AnimatedContent(
                targetState = isSelectedMode,
                transitionSpec = { getUpDownTransform() },
                contentAlignment = Alignment.TopEnd,
            ) {
                if (it) {
                    Row {
                        IconButton(onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
                            val copyGroups = app.groups.filter { g ->
                                selectedDataSet.any { it.groupKey == g.key }
                            }
                            val str = toJson5String(app.copy(groups = copyGroups))
                            copyText(str)
                        })) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                            )
                        }
                        BatchActionButtonGroup(vm, selectedDataSet)
                        if (editable) {
                            IconButton(onClick = throttle(vm.viewModelScope.launchAsFn {
                                subs!!
                                mainVm.dialogFlow.waitResult(
                                    title = "删除规则组",
                                    text = "删除当前所选规则组?",
                                    error = true,
                                )
                                val keys = selectedDataSet.mapNotNull { it.groupKey }
                                vm.isSelectedModeFlow.value = false
                                if (keys.size == app.groups.size) {
                                    updateSubscription(
                                        subs.copy(
                                            apps = subs.apps.filter { a -> a.id != appId }
                                        )
                                    )
                                    DbSet.subsConfigDao.deleteAppConfig(subsItemId, appId)
                                } else {
                                    updateSubscription(
                                        subs.copy(
                                            apps = subs.apps.toMutableList().apply {
                                                set(
                                                    indexOfFirst { it.id == appId },
                                                    app.copy(groups = app.groups.filterNot { g ->
                                                        keys.contains(
                                                            g.key
                                                        )
                                                    })
                                                )
                                            }
                                        )
                                    )
                                    DbSet.subsConfigDao.batchDeleteAppGroupConfig(
                                        subsItemId,
                                        appId,
                                        keys
                                    )
                                }
                                toast("删除成功")
                            })) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                )
                            }
                        }
                        IconButton(onClick = {
                            expanded = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
            if (isSelectedMode) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(text = "全选")
                            },
                            onClick = {
                                expanded = false
                                vm.selectedDataSetFlow.value = app.groups.map {
                                    it.toGroupState(
                                        subsId = subsItemId,
                                        appId = appId,
                                    )
                                }.toSet()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = "反选")
                            },
                            onClick = {
                                expanded = false
                                val newSelectedIds = app.groups.map {
                                    it.toGroupState(
                                        subsId = subsItemId,
                                        appId = appId,
                                    )
                                }.toSet() - selectedDataSet
                                vm.selectedDataSetFlow.value = newSelectedIds
                            }
                        )
                    }
                }
            }
        })
    }, floatingActionButton = {
        if (editable) {
            AnimationFloatingActionButton(
                visible = !isSelectedMode,
                onClick = throttle {
                    mainVm.navigatePage(
                        UpsertRuleGroupPageDestination(
                            subsId = subsItemId,
                            groupKey = null,
                            appId = appId
                        )
                    )
                },
                content = {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                    )
                }
            )
        }
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
        ) {
            items(app.groups, { it.key }) { group ->
                val category = groupToCategoryMap[group]
                val subsConfig = subsConfigs.find { it.groupKey == group.key }
                val categoryConfig = categoryConfigs.find {
                    it.categoryKey == category?.key
                }
                RuleGroupCard(
                    modifier = Modifier.animateListItem(this),
                    subs = subs!!,
                    appId = appId,
                    group = group,
                    category = category,
                    subsConfig = subsConfig,
                    categoryConfig = categoryConfig,
                    showBottom = group !== app.groups.last(),
                    focusGroupFlow = vm.focusGroupFlow,
                    isSelectedMode = isSelectedMode,
                    isSelected = selectedDataSet.any { it.groupKey == group.key },
                    onLongClick = {
                        if (app.groups.size > 1) {
                            vm.isSelectedModeFlow.value = true
                            vm.selectedDataSetFlow.value = setOf(
                                group.toGroupState(subsItemId, appId)
                            )
                        }
                    },
                    onSelectedChange = {
                        vm.selectedDataSetFlow.value = selectedDataSet.switchItem(
                            group.toGroupState(
                                subsItemId,
                                appId,
                            )
                        )
                    },
                    onExecute = { globalGroup ->
                        // 获取 A11yService 实例
                        val a11yService = A11yService.weakInstance.get()
                        if (a11yService != null && globalGroup.rules.isNotEmpty()) {
                            // 创建所有规则的 GkdAction 列表
                            // 在构建 actions 时直接处理延迟
                            val actions = globalGroup.rules.mapNotNull { rule ->
                                val selector = rule.matches?.firstOrNull()
                                selector?.let {
                                    // 如果规则有延迟，先处理延迟
                                    if ((rule.actionDelay ?: 0) > 0) {
                                        appScope.launch {
                                            delay(rule.actionDelay ?: 0)
                                            val action = GkdAction(
                                                selector = it,
                                                action = rule.action ?: "click",
                                                fastQuery = true
                                            )
                                            a11yService.executeRule(action)
                                        }
                                        null // 返回 null，不加入批量执行列表
                                    } else {
                                        // 无延迟的规则直接返回
                                        GkdAction(
                                            selector = it,
                                            action = rule.action ?: "click",
                                            fastQuery = true
                                        )
                                    }
                                }
                            }

                            if (actions.isNotEmpty()) {
                                // 批量执行所有规则
                                a11yService.executeRules(actions, object : ExecutorCallback {
                                    override fun onBatchSuccess(results: List<ExecutorResult>) {
                                        appScope.launch(Dispatchers.Main) {
                                            val successCount = results.count { it.success }
                                            toast("执行完成: $successCount/${results.size} 条规则成功")
                                        }
                                    }

                                    override fun onError(error: Exception) {
                                        appScope.launch(Dispatchers.Main) {
                                            toast("执行失败: ${error.message}")
                                        }
                                    }
                                })
                            } else {
                                toast("无法执行：所有规则选择器均为空")
                            }
                        } else {
                            toast("无法执行：无障碍服务未运行或规则为空")
                        }
                    }
                )
            }
            item(LIST_PLACEHOLDER_KEY) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (app.groups.isEmpty()) {
                    EmptyText(text = "暂无规则")
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}
