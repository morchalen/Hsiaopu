package com.example.hsiaopu.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hsiaopu.R
import com.example.hsiaopu.system.ShizukuHelper
import com.example.hsiaopu.ui.theme.*
import com.example.hsiaopu.viewmodel.ChatViewModel
import androidx.compose.foundation.isSystemInDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel) {//ChatViewModel是整个app的mvvm架构的vm部分
    val uiState by viewModel.uiState.collectAsState()//📡 “订阅” ViewModel 的uiState数据流，只要 uiState 有更新，界面就自动重绘。”
    val settings by viewModel.settings.collectAsState()//📡 “订阅” ViewModel 的settings数据流，只要 settings 有更新，界面就自动重绘。”
    val themeSettings by viewModel.themeSettings.collectAsState()//📡 “订阅” ViewModel 的themeSettings数据类，只要 themeSettings 有更新，界面就自动重绘。”
    var showAboutDialog by remember { mutableStateOf(false) }//创建一个实时监听的布尔变量初始化为false，remember就是重载的时候记住这个，然后mutableStateOf(false)就是初始化为false，showAboutDialog就是实时监听的布尔变量，只要showAboutDialog有更新，界面就自动重绘
    // mutableStateOf(xxx) = 初始化为 xxx，并且实时监听变化，变了就立刻重载（重组）
    // remember{xxx}就是记住里面的xxx，每次重载的时候都记住这个xxx，不然每次重载就再次初始化了；
    //重载的例子：旋转屏幕，分屏，深色模式切换，语言切换，字体，显示大小切换，折叠屏切换；
    // Shizuku 状态
    val shizukuAvailable = remember { mutableStateOf(ShizukuHelper.isAvailable()) }//实时监听状态，只要shizukuAvailable有更新，界面就自动重绘
    val systemDarkTheme = isSystemInDarkTheme()//isSystemInDarkTheme函数本身就是自动监听系统主题变化的，所以这里不需要mutableStateOf
   
    LaunchedEffect(Unit) {//初始化时启动一个协程
        shizukuAvailable.value = ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission()//判断Shizuku是否可用 且 是否有权限
    }

    // 关于 App 的详细信息对话框
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.about_detail_title)) },
            text = { Text(stringResource(R.string.about_detail_body)) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)  // 原来内层 Column 的 padding
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)  // 原来内层 Column 的间距
        ) {
            //第一行: ── AI 服务 ──
            SettingsCard(title = stringResource(R.string.settings_ai_provider)) {
                // val providers by viewModel.providers.collectAsState()
                // Text(stringResource(R.string.settings_provider), style = MaterialTheme.typography.bodyMedium)
                // Spacer(modifier = Modifier.height(6.dp))
                // Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                //     providers.forEach { provider ->
                //         FilterChip(
                //             selected = settings.providerId == provider.id,
                //             onClick = {
                //                 viewModel.updateProviderId(provider.id)
                //                 viewModel.updateApiEndpoint(provider.defaultEndpoint)
                //                 viewModel.updateModelName(provider.defaultModel)
                //             },
                //             label = { Text(provider.name, style = MaterialTheme.typography.labelMedium) },
                //             colors = FilterChipDefaults.filterChipColors(
                //                 selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                //                 selectedLabelColor = MaterialTheme.colorScheme.primary
                //             )
                //         )
                //     }
                // }
                Spacer(modifier = Modifier.height(10.dp))
                //apikey输入框，当输入框的值改变时调用onValueChange函数，参数it是输入框的值，调用这个{}里面的viewModel.updateApiKey函数，更新settings.apiKey的值
                SettingsTextField(stringResource(R.string.settings_api_key), settings.apiKey) { viewModel.updateApiKey(it) }
                //api地址输入框，当输入框的值改变时调用onValueChange函数，参数it是输入框的值，调用这个{}里面的viewModel.updateApiEndpoint函数，更新settings.apiEndpoint的值
                SettingsTextField(stringResource(R.string.settings_api_endpoint), settings.apiEndpoint) { viewModel.updateApiEndpoint(it) }
                val models by viewModel.models.collectAsState()//实时收集viewModel.models的变化，有更新就立刻重绘[返回的类型是List<String>]
                var expanded by remember { mutableStateOf(false) }//下拉菜单是否展开的状态，默认关闭
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(//下拉菜单容器
                            expanded = expanded,// 1️⃣ 是否展开
                            onExpandedChange = { expanded = !expanded }  // 2️⃣ 点击切换
                        ) {
                            TextField(//下拉菜单输入框
                                readOnly = true,// 只读，用户不能输入
                                value = settings.modelName,// 下拉菜单输入框的值，默认是settings.modelName
                                onValueChange = {},// 下拉菜单输入框的值改变时调用，这里为空
                                label = { Text(stringResource(R.string.settings_model), style = MaterialTheme.typography.bodySmall) },// 下拉菜单输入框的标签
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },// 下拉菜单输入框的尾随图标
                                colors = OutlinedTextFieldDefaults.colors(// 下拉菜单输入框的颜色
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(//下拉菜单
                                expanded = expanded,// 1️⃣ 是否展开
                                onDismissRequest = { expanded = false }// 点击关闭下拉菜单
                            ) {
                                models.forEach { model ->//遍历models列表，为每个模型创建一个下拉菜单的项
                                    DropdownMenuItem(//下拉菜单的项
                                        text = { Text(model, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            viewModel.updateModelName(model)//点击下拉菜单的项时，更新settings.modelName的值
                                            expanded = false//点击下拉菜单的项时，关闭下拉菜单
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(//刷新模型列表按钮
                        onClick = { viewModel.refreshModels() },//点击刷新模型列表按钮时，调用viewModel.refreshModels函数，刷新模型列表
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新模型列表", modifier = Modifier.size(20.dp))
                    }
                }
                SettingsTextField(stringResource(R.string.settings_system_prompt), settings.systemPrompt, minLines = 2) { viewModel.updateSystemPrompt(it) }
            }
            //第二行: ── 参数设置 ──
            SettingsCard(title = stringResource(R.string.settings_parameters)) {
                Text("${stringResource(R.string.settings_temperature)}: ${String.format("%.1f", settings.temperature)}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.temperature.toFloat(),
                    onValueChange = { viewModel.updateTemperature(it.toDouble()) },
                    valueRange = 0f..2f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
                Text("${stringResource(R.string.settings_max_tokens)}: ${settings.maxTokens}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(//最大令牌数滑块
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                    valueRange = 256f..8192f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
            //第三行: ── 外观设置 ──
            SettingsCard(title = stringResource(R.string.settings_appearance)) {
                // 主题设置：系统跟随 / 深色 / 浅色
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,  // 左右两端对齐
                    verticalAlignment = Alignment.CenterVertically     // 垂直居中
                ) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 系统主题选项
                        FilterChip(
                            //themeSettings是vm里面的一个类(vm里面又是委托给了SettingsDataStore.kt里面的ThemeSettings数据类)，里面有一个themeMode参数，用于判断是否深色模式
                            //DataStore这个属于mvvm里面的model里面的repository中间层，是来自于安卓本地的存储，区别于来源数据库和网络的第三方
                            selected = themeSettings.themeMode == "system",//如果是系统跟随主题，选中
                            onClick = { viewModel.updateThemeMode("system") },
                            label = { Text(stringResource(R.string.settings_theme_system), style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = { Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        //
                        FilterChip(
                            selected = themeSettings.themeMode != "system",//如果是浅色主题，选中
                            onClick = {
                                if(systemDarkTheme){//onclick里面不属于@Composable区域，所以这里需要调用这个提前定义的参数systemDarkTheme=isSystemInDarkTheme()
                                    viewModel.updateThemeMode("light")
                                }
                                else{
                                    viewModel.updateThemeMode("dark")
                                }
                            },
                            label = {//如果现在是浅色主题，显示浅色文本，否则显示深色文本
                                if(isSystemInDarkTheme()){//这里label和leadingIcon属于@Composable区域，所以不需要二次调用
                                    Text(stringResource(R.string.settings_theme_light), style = MaterialTheme.typography.labelMedium)
                                }
                                else{
                                    Text(stringResource(R.string.settings_theme_dark), style = MaterialTheme.typography.labelMedium)
                                }   
                            },
                            leadingIcon = {//如果现在是浅色主题，显示浅色图标，否则显示深色图标
                                if(isSystemInDarkTheme()){
                                    Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                else{
                                    Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                }

                            }

                        )

                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 字体大小设置
                Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧小字体预览
                    Text("A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 滑块：0-4 对应 XS-XL 五档
                    Slider(
                        value = themeSettings.fontScale.toFloat(),
                        onValueChange = { viewModel.updateFontScale(it.toInt()) },
                        valueRange = 0f..4f, steps = 3,  // 0,1,2,3,4 共五档
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    // 右侧大字体预览
                    Text("A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 字体档位标签：XS S M L XL
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("XS", "S", "M", "L", "XL").forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }

            //第四行: ── 关于 ──
            SettingsCard(title = stringResource(R.string.settings_about)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.about_version), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showAboutDialog = true }) {
                        Text(stringResource(R.string.about_detail_title), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            //第五行: 空行
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


// ── 可复用设置组件 ──

//卡片
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

//输入框
@Composable
private fun SettingsTextField(label: String, value: String, minLines: Int = 1, onValueChange: (String) -> Unit) {
    OutlinedTextField(//自带清晰边框的输入框
        value = value,//输入框的当前值
        onValueChange = onValueChange,//输入框的值改变时调用，实时更新settings.apiKey的值
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },//输入框的标签
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        singleLine = minLines == 1,
        minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

