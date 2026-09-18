// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.daysurpopt.R
import com.example.daysurpopt.utils.AppDebugLog
import com.example.daysurpopt.data.AgentSettingsRepository
import com.example.daysurpopt.data.ChatRepository
import com.example.daysurpopt.data.OpenRouterClient
import com.example.daysurpopt.data.OpenRouterApi
import com.example.daysurpopt.data.chatCompletionText
import com.example.daysurpopt.data.PrivacyConsentRepository
import com.example.daysurpopt.domain.*
import com.example.daysurpopt.agent.PromptConstructor
import com.example.daysurpopt.agent.AgentToolExecutor
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for managing the AI Agent's state, chat history, and interactions.
 * Handles OpenRouter API calls and executes local simulation tools requested by the agent.
 */
class AgentViewModel(application: Application) : AndroidViewModel(application) {
    
    // State
    /** Current agent settings (API key, model). */
    var settings = mutableStateOf(AgentSettings())
        private set
    
    /** The currently active chat session. */
    var currentSession = mutableStateOf<ChatSession?>(null)
        private set
        
    /** List of all saved chat sessions. */
    var chatHistory = mutableStateListOf<ChatSession>()
        private set

    /** Loading state for API requests. */
    var isLoading = mutableStateOf(false)
        private set

    // Service
    private var openRouterApi: OpenRouterApi? = null

    init {
        loadSettings()
        loadHistory()
        initializeApi()
    }

    private fun loadSettings() {
        settings.value = AgentSettingsRepository.loadSettings(getApplication())
    }

    /**
     * Updates the agent settings and re-initializes the API client.
     * @param newSettings The new settings to apply.
     */
    fun updateSettings(newSettings: AgentSettings) {
        settings.value = newSettings
        AgentSettingsRepository.saveSettings(getApplication(), newSettings)
        initializeApi()
    }

    private fun loadHistory() {
        chatHistory.clear()
        chatHistory.addAll(ChatRepository.loadSessions(getApplication()))
    }

    private fun initializeApi() {
        if (settings.value.apiKey.isNotBlank()) {
            openRouterApi = OpenRouterClient.create()
        }
    }

    /**
     * Creates a new empty chat session and sets it as active.
     */
    fun createNewSession() {
        val session = ChatRepository.createSession(getApplication())
        chatHistory.add(0, session)
        currentSession.value = session
    }

    /**
     * Deletes a chat session by ID.
     * @param sessionId The ID of the session to delete.
     */
    fun deleteSession(sessionId: String) {
        ChatRepository.deleteSession(getApplication(), sessionId)
        chatHistory.removeIf { it.id == sessionId }
        if (currentSession.value?.id == sessionId) {
            currentSession.value = null
        }
    }

    /**
     * Marks an AI message as user-reported and hides it from the UI and future context windows.
     * @param messageId The message ID to report.
     */
    fun reportMessage(messageId: String) {
        val session = currentSession.value ?: return
        val updatedMessages = session.messages.map { msg ->
            if (msg.id == messageId && msg.role == "assistant") {
                msg.copy(isUserReported = true, isHidden = true)
            } else {
                msg
            }
        }
        val updatedSession = session.copy(messages = updatedMessages, lastModified = System.currentTimeMillis())
        currentSession.value = updatedSession
        ChatRepository.updateSession(getApplication(), updatedSession)
        loadHistory()
    }

    /**
     * Selects a chat session to be active.
     * @param session The session to select.
     */
    fun selectSession(session: ChatSession) {
        currentSession.value = session
    }

    /**
     * Sends a user message to the AI agent.
     * Appends the message to the session, calls the API, handles tool usage (simulations),
     * and appends the assistant's response.
     *
     * @param content The user's message text.
     * @param currentInputs Current financial inputs for context.
     * @param specificExpenses Current specific expenses for context.
     * @param surplusData Current surplus data for context.
     */
    fun sendMessage(
        content: String,
        currentInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        userGaConfig: GAConfigUI? = null,
        comparisonContext: String? = null
    ) {
        AppDebugLog.add("Agent", "sendMessage: $content")
        if (!PrivacyConsentRepository.isGranted(getApplication())) {
            val session = currentSession.value ?: run {
                createNewSession()
                currentSession.value!!
            }
            val systemMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "system",
                content = getApplication<Application>().getString(R.string.privacy_consent_required_ai),
                timestamp = System.currentTimeMillis()
            )
            val updatedSession = session.copy(messages = session.messages + systemMessage, lastModified = System.currentTimeMillis())
            currentSession.value = updatedSession
            ChatRepository.updateSession(getApplication(), updatedSession)
            loadHistory()
            return
        }

        val session = currentSession.value ?: run {
            createNewSession()
            currentSession.value!!
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = content,
            timestamp = System.currentTimeMillis()
        )

        val updatedMessages = session.messages + userMessage
        val updatedSession = session.copy(messages = updatedMessages)
        currentSession.value = updatedSession
        ChatRepository.updateSession(getApplication(), updatedSession)

        viewModelScope.launch {
            isLoading.value = true
            try {
                // Initial recursive call
                processAgentTurn(updatedMessages, updatedSession, currentInputs, specificExpenses, surplusData, 0, userGaConfig, comparisonContext)
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "system",
                    content = "Error: ${e.message}",
                    timestamp = System.currentTimeMillis()
                )
                 val finalMessages = updatedSession.messages + errorMessage
                 val finalSession = updatedSession.copy(messages = finalMessages)
                 currentSession.value = finalSession
                 ChatRepository.updateSession(getApplication(), finalSession)
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun generateResponse(
        messages: List<ChatMessage>, 
        inputs: FinancialInput, 
        specificExpenses: List<SpecificExpense>, 
        surplusData: SurplusInput,
        systemOverride: String? = null
    ): String {
        AppDebugLog.add("Agent", "generateResponse: systemOverride=${systemOverride != null}")
        val api = openRouterApi ?: return "Please configure API Key in settings."
        
        val systemPrompt = systemOverride ?: PromptConstructor.constructSystemPrompt(inputs, specificExpenses, surplusData)
        
        val apiMessages = mutableListOf<OpenRouterMessage>()
        apiMessages.add(OpenRouterMessage("system", Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT.trim() + "\n\n" + systemPrompt))
        val contextMessages = messages
            .filterNot { it.isHidden || it.isUserReported }
            .takeLast(10)
        apiMessages.addAll(contextMessages.map { OpenRouterMessage(it.role, it.content) })

        val request = OpenRouterRequest(
            model = settings.value.model,
            messages = apiMessages
        )

        return api.chatCompletionText(
            authorization = "Bearer ${settings.value.apiKey}",
            request = request
        )
    }

    private suspend fun processAgentTurn(
        currentMessages: List<ChatMessage>,
        session: ChatSession,
        inputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        depth: Int,
        userGaConfig: GAConfigUI? = null,
        comparisonContext: String? = null,
        executedCommands: Set<String> = emptySet()
    ) {
        if (depth > 5) { // Prevent infinite loops
             val stopMessage = ChatMessage(
                 id = UUID.randomUUID().toString(),
                 role = "system",
                 content = "Tool usage limit reached (5 steps). Stopping analysis.",
                 timestamp = System.currentTimeMillis()
             )
             val finalMessages = currentMessages + stopMessage
             val finalSession = session.copy(messages = finalMessages)
             currentSession.value = finalSession
             ChatRepository.updateSession(getApplication(), finalSession)
             return
        }

        val responseContent = generateResponse(currentMessages, inputs, specificExpenses, surplusData)
        AppDebugLog.add("Agent", "Response: ${responseContent.take(50)}...")
        
        // Add agent response to history immediately
        val agentMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = responseContent,
            timestamp = System.currentTimeMillis()
        )
        val messagesWithAgent = currentMessages + agentMessage
        val sessionWithAgent = session.copy(messages = messagesWithAgent)
        currentSession.value = sessionWithAgent
        ChatRepository.updateSession(getApplication(), sessionWithAgent)

        // Check for tools
        val toolResult = AgentToolExecutor.checkForToolUse(
            response = responseContent,
            baseInputs = inputs,
            specificExpenses = specificExpenses,
            surplusData = surplusData,
            userGaConfig = userGaConfig,
            comparisonContext = comparisonContext,
            alreadyExecutedCommands = executedCommands,
            llmRequest = { prompt ->
                generateResponse(
                    messages = listOf(ChatMessage("", "user", "Execute analysis.", 0)),
                    inputs = inputs,
                    specificExpenses = specificExpenses,
                    surplusData = surplusData,
                    systemOverride = prompt
                )
            }
        )
        
        if (toolResult != null) {
            // Add tool result to history
            val toolMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "system", // or "tool" if API supports it, using system for now
                content = toolResult,
                timestamp = System.currentTimeMillis()
            )
            val messagesWithTool = messagesWithAgent + toolMessage
            val sessionWithTool = sessionWithAgent.copy(messages = messagesWithTool)
            currentSession.value = sessionWithTool
            ChatRepository.updateSession(getApplication(), sessionWithTool)
            
            // Recurse: Agent sees tool result and decides next step, remembering which
            // tools already ran in this turn so the same heavy tool is never executed twice.
            val commandNames = AgentToolExecutor.extractAllCommandNames(responseContent)
            processAgentTurn(
                messagesWithTool, sessionWithTool, inputs, specificExpenses, surplusData, depth + 1,
                userGaConfig, comparisonContext,
                executedCommands = executedCommands + commandNames
            )
        } else {
            // No tool used, turn ends
        }
    }
}