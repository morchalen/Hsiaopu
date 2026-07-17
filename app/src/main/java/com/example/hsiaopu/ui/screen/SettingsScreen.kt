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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val themeSettings by viewModel.themeSettings.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    // Shizuku state
    val shizukuAvailable = remember { mutableStateOf(ShizukuHelper.isAvailable()) }
    var wifiInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        shizukuAvailable.value = ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission()
    }

    // About dialog
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // Compact header
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── AI Provider ──
            SettingsCard(title = stringResource(R.string.settings_ai_provider)) {
                val providers by viewModel.providers.collectAsState()
                Text(stringResource(R.string.settings_provider), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        FilterChip(
                            selected = settings.providerId == provider.id,
                            onClick = {
                                viewModel.updateProviderId(provider.id)
                                viewModel.updateApiEndpoint(provider.defaultEndpoint)
                                viewModel.updateModelName(provider.defaultModel)
                            },
                            label = { Text(provider.name, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                SettingsTextField(stringResource(R.string.settings_api_key), settings.apiKey) { viewModel.updateApiKey(it) }
                SettingsTextField(stringResource(R.string.settings_api_endpoint), settings.apiEndpoint) { viewModel.updateApiEndpoint(it) }
                SettingsTextField(stringResource(R.string.settings_model), settings.modelName) { viewModel.updateModelName(it) }
                SettingsTextField(stringResource(R.string.settings_system_prompt), settings.systemPrompt, minLines = 2) { viewModel.updateSystemPrompt(it) }
            }

            // ── Parameters ──
            SettingsCard(title = stringResource(R.string.settings_parameters)) {
                Text("${stringResource(R.string.settings_temperature)}: ${String.format("%.1f", settings.temperature)}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.temperature.toFloat(),
                    onValueChange = { viewModel.updateTemperature(it.toDouble()) },
                    valueRange = 0f..2f, steps = 19,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
                Text("${stringResource(R.string.settings_max_tokens)}: ${settings.maxTokens}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                    valueRange = 256f..8192f, steps = 30,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                )
            }

            // ── Token Usage ──
            if (uiState.tokenStats.totalTokens > 0) {
                SettingsCard(title = stringResource(R.string.settings_token_usage)) {
                    val stats = uiState.tokenStats
                    SettingsRow(stringResource(R.string.settings_prompt_tokens), "${stats.promptTokens}")
                    SettingsRow(stringResource(R.string.settings_completion_tokens), "${stats.completionTokens}")
                    SettingsRow(stringResource(R.string.settings_total_tokens), "${stats.totalTokens}")
                    SettingsRow(stringResource(R.string.settings_est_cost), "$${String.format("%.4f", stats.estimatedCost)}")
                }
            }

            // ── Appearance ──
            SettingsCard(title = stringResource(R.string.settings_appearance)) {
                // Theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = themeSettings.isDarkTheme == "system",
                            onClick = { viewModel.updateDarkTheme("system") },
                            label = { Text(stringResource(R.string.settings_theme_system), style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = { Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = themeSettings.isDarkTheme != "system",
                            onClick = {
                                val isDark = themeSettings.isDarkTheme == "dark"
                                viewModel.updateDarkTheme(if (isDark) "light" else "dark")
                            },
                            label = {
                                Text(
                                    if (themeSettings.isDarkTheme == "dark") stringResource(R.string.settings_theme_dark)
                                    else stringResource(R.string.settings_theme_light),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (themeSettings.isDarkTheme == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null, modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Font Size
                Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = themeSettings.fontScale.toFloat(),
                        onValueChange = { viewModel.updateFontScale(it.toInt()) },
                        valueRange = 0f..4f, steps = 3,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("XS", "S", "M", "L", "XL").forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }


            }

            // ── Shizuku ──
            // SettingsCard(title = stringResource(R.string.settings_shizuku)) {
            //     Row(
            //         modifier = Modifier.fillMaxWidth(),
            //         horizontalArrangement = Arrangement.SpaceBetween,
            //         verticalAlignment = Alignment.CenterVertically
            //     ) {
            //         Text("Status", style = MaterialTheme.typography.bodyMedium)
            //         Surface(
            //             color = if (shizukuAvailable.value) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
            //             shape = RoundedCornerShape(8.dp)
            //         ) {
            //             Text(
            //                 text = if (shizukuAvailable.value) stringResource(R.string.shizuku_connected)
            //                        else stringResource(R.string.shizuku_unavailable),
            //                 modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            //                 color = if (shizukuAvailable.value) SuccessGreen else ErrorRed,
            //                 style = MaterialTheme.typography.labelMedium
            //             )
            //         }
            //     }
            //     Spacer(modifier = Modifier.height(10.dp))
            //     Button(
            //         onClick = { wifiInfo = ShizukuHelper.getWifiInfo() },
            //         enabled = shizukuAvailable.value,
            //         modifier = Modifier.fillMaxWidth(),
            //         shape = RoundedCornerShape(12.dp),
            //         colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            //     ) {
            //         Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
            //         Spacer(modifier = Modifier.width(8.dp))
            //         Text(stringResource(R.string.get_wifi_info))
            //     }
            //     if (wifiInfo.isNotEmpty()) {
            //         Spacer(modifier = Modifier.height(10.dp))
            //         Surface(
            //             color = MaterialTheme.colorScheme.surfaceVariant,
            //             shape = RoundedCornerShape(8.dp),
            //             modifier = Modifier.fillMaxWidth()
            //         ) {
            //             Column(modifier = Modifier.padding(12.dp)) {
            //                 wifiInfo.forEach { (key, value) ->
            //                     Text(
            //                         "$key: $value",
            //                         fontFamily = FontFamily.Monospace,
            //                         fontSize = MaterialTheme.typography.bodySmall.fontSize,
            //                         color = MaterialTheme.colorScheme.onSurface
            //                     )
            //                 }
            //             }
            //         }
            //     }
            // }

            // ── About ──
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Reusable Settings Components ──

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

@Composable
private fun SettingsTextField(label: String, value: String, minLines: Int = 1, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
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

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Medium)
    }
}