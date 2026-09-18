// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.example.daysurpopt.BuildConfig
import com.example.daysurpopt.R
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.daysurpopt.domain.AgentSettings
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.ui.AgentViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.tooling.preview.Preview
import com.example.daysurpopt.domain.ChatMessage
import com.example.daysurpopt.ui.theme.ConsapevolezzaFinanziariaTheme

/**
 * Main screen for the AI Agent feature.
 * Provides a chat interface, settings dialog, and a navigation drawer for chat history.
 *
 * @param viewModel The ViewModel managing agent state.
 * @param inputs Current financial inputs for simulation context.
 * @param specificExpenses List of specific expenses for simulation context.
 * @param surplusData Surplus data for simulation context.
 * @param onBack Callback to navigate back to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel = viewModel(),
    inputs: FinancialInput,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput,
    gaConfig: com.example.daysurpopt.domain.GAConfigUI? = null,
    comparisonContext: String? = null,
    onBack: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Check if API key is set
    LaunchedEffect(viewModel.settings.value.apiKey) {
        if (viewModel.settings.value.apiKey.isBlank()) {
            showSettings = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.agent_chat_history),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                LazyColumn(
                    modifier = Modifier.weight(1f) // Ensure it takes available space but leaves room for bottom button
                ) {
                    items(viewModel.chatHistory) { session ->
                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(
                                        text = session.messages.firstOrNull()?.content?.take(30) ?: stringResource(R.string.agent_new_chat),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(session.lastModified)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            selected = viewModel.currentSession.value?.id == session.id,
                            onClick = {
                                viewModel.selectSession(session)
                                scope.launch {
                                    drawerState.close()
                                }
                            },
                            badge = {
                                IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.privacy_policy)) },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    selected = false,
                    onClick = {
                        val url = BuildConfig.PRIVACY_POLICY_URL
                        if (url.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.agent_privacy_not_configured), Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    }
                )
                // Spacer(modifier = Modifier.weight(1f)) // Removed weight here, handled by LazyColumn
                Button(
                    onClick = {
                        viewModel.createNewSession()
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.agent_new_chat))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.agent_title)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.agent_menu))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.agent_settings))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (viewModel.currentSession.value == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.agent_start_message))
                    }
                } else {
                    ChatView(
                        viewModel = viewModel,
                        inputs = inputs,
                        specificExpenses = specificExpenses,
                        surplusData = surplusData
                    )
                }
            }
        }
    }

    if (showSettings) {
        AgentSettingsDialog(
            currentSettings = viewModel.settings.value,
            onDismiss = { showSettings = false },
            onSave = { newSettings ->
                viewModel.updateSettings(newSettings)
                showSettings = false
            }
        )
    }
}

/**
 * Composable that renders the chat interface (message list and input field).
 */
@Composable
fun ChatView(
    viewModel: AgentViewModel,
    inputs: FinancialInput,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput,
    gaConfig: com.example.daysurpopt.domain.GAConfigUI? = null,
    comparisonContext: String? = null
) {
    val session = viewModel.currentSession.value ?: return
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(session.messages.size) {
        if (session.messages.isNotEmpty()) {
            listState.animateScrollToItem(session.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AiDisclaimerBanner(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(session.messages) { message ->
                MessageBubble(
                    message = message,
                    onReport = { viewModel.reportMessage(message.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (viewModel.isLoading.value) {
                item {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
                }
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                .heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            val suggestions = listOf(
                "Simulate Wage +1%" to "Run simulation changing the wage of +1% tell me percentage sensitivity of fobj and avg_utility",
                "Optimize Plan" to "Run optimization to find the best parameters.",
                "Full Analysis" to "Generate a detailed multi-agent analysis report.",
                "Stress Test" to "Run a stress test assuming interest rates drop by 1%."
            )
            items(suggestions) { (label, prompt) ->
                AssistChip(
                    onClick = { inputText = prompt },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.agent_ask_placeholder)) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText, inputs, specificExpenses, surplusData, gaConfig, comparisonContext)
                        inputText = ""
                    }
                },
                enabled = !viewModel.isLoading.value
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * Displays a single chat message bubble.
 * Supports clicking to copy text to clipboard.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    showThinking: Boolean = true,
    onReport: () -> Unit
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showReportConfirm by remember { mutableStateOf(false) }

    Surface(
        color = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !message.isHidden) {
                clipboardManager.setText(AnnotatedString(message.content))
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isUser) stringResource(R.string.agent_you) else stringResource(R.string.agent_ai),
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            if (message.isHidden) {
                Text(
                    text = stringResource(R.string.ai_message_hidden),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val content = message.content
                // More robust extraction of thinking block
                val thinkStartTag = "<think>"
                val thinkEndTag = "</think>"
                val thinkStartIndex = content.indexOf(thinkStartTag, ignoreCase = true)
                val thinkEndIndex = content.indexOf(thinkEndTag, ignoreCase = true)
                
                if (thinkStartIndex != -1 && thinkEndIndex != -1 && thinkEndIndex > thinkStartIndex) {
                    val thinking = content.substring(thinkStartIndex + thinkStartTag.length, thinkEndIndex).trim()
                    val response = content.substring(thinkEndIndex + thinkEndTag.length).trim()
                    
                    if (thinking.isNotBlank()) {
                        var isThinkingExpanded by remember { mutableStateOf(false) }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.medium
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isThinkingExpanded = !isThinkingExpanded }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info, // Or a brain icon if available
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Thinking Process",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isThinkingExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isThinkingExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            
                            if (isThinkingExpanded) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Text(
                                    text = thinking,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = androidx.compose.ui.unit.TextUnit.Unspecified
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = response,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (!isUser && !message.isHidden) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = {
                            showReportConfirm = true
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(R.string.report_content_desc),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    if (showReportConfirm) {
        AlertDialog(
            onDismissRequest = { showReportConfirm = false },
            title = { Text(stringResource(R.string.report_confirm_title)) },
            text = { Text(stringResource(R.string.report_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        val subject = context.getString(R.string.report_email_subject)
                        val body = context.getString(R.string.report_email_body, message.content.take(500))
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.report_content)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                        }

                        onReport()
                        showReportConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.report_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun AiDisclaimerBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.ai_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AiDisclaimerBannerPreview() {
    ConsapevolezzaFinanziariaTheme {
        AiDisclaimerBanner(modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun MessageBubblePreview() {
    ConsapevolezzaFinanziariaTheme {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            MessageBubble(
                message = ChatMessage(id = "1", role = "assistant", content = "Test answer", timestamp = 0L),
                onReport = {}
            )
            Spacer(modifier = Modifier.height(12.dp))
            MessageBubble(
                message = ChatMessage(id = "2", role = "assistant", content = "Hidden answer", timestamp = 0L, isHidden = true, isUserReported = true),
                onReport = {}
            )
        }
    }
}

/**
 * Dialog for configuring AI agent settings (API Key, Model).
 */
@Composable
fun AgentSettingsDialog(
    currentSettings: AgentSettings,
    onDismiss: () -> Unit,
    onSave: (AgentSettings) -> Unit
) {
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var model by remember { mutableStateOf(currentSettings.model) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.agent_settings_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.agent_api_key_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                val apiKeyUrl = buildAnnotatedString {
                    append(stringResource(R.string.agent_get_key_at))
                    pushStringAnnotation(tag = "URL", annotation = "https://openrouter.ai/settings/keys")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append("openrouter.ai/settings/keys")
                    }
                    pop()
                }

                ClickableText(
                    text = apiKeyUrl,
                    modifier = Modifier.padding(top = 4.dp),
                    onClick = { offset ->
                        apiKeyUrl.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.agent_model_id_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Show Thinking Process", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = currentSettings.showThinking,
                        onCheckedChange = { onSave(currentSettings.copy(showThinking = it, apiKey = apiKey, model = model)) }
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val annotatedString = buildAnnotatedString {
                    append(stringResource(R.string.agent_see_models_at))
                    pushStringAnnotation(tag = "URL", annotation = "https://openrouter.ai/models")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                        append("openrouter.ai/models")
                    }
                    pop()
                }

                ClickableText(
                    text = annotatedString,
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(currentSettings.copy(apiKey = apiKey, model = model)) }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
