// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

/**
 * Configuration settings for the AI Agent.
 * @property apiKey OpenRouter API Key.
 * @property model OpenRouter Model ID (default: x-ai/grok-4.1-fast).
 */
data class AgentSettings(
    val apiKey: String = "",
    val model: String = "qwen/qwen3.7-plus",
    val showThinking: Boolean = true
)

/**
 * Represents a single chat message in a conversation.
 * @property id Unique identifier.
 * @property role Sender role ("user", "assistant", "system").
 * @property content Message text.
 * @property timestamp Time of message creation.
 */
data class ChatMessage(
    val id: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long,
    val isUserReported: Boolean = false,
    val isHidden: Boolean = false
)

/**
 * Represents a chat session containing a history of messages.
 * @property id Unique session identifier.
 * @property messages List of messages in the session.
 * @property lastModified Timestamp of the last update.
 */
data class ChatSession(
    val id: String,
    val messages: List<ChatMessage>,
    val lastModified: Long
)

// OpenRouter API models
/**
 * Request payload for OpenRouter chat completion API.
 */
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = false
)

/**
 * Message object formatted for OpenRouter API.
 */
data class OpenRouterMessage(
    val role: String? = null,
    val content: String? = null
)

/**
 * Response payload from OpenRouter chat completion API.
 */
data class OpenRouterResponse(
    val id: String? = null,
    val choices: List<OpenRouterChoice>? = null,
    val error: OpenRouterError? = null
)

/**
 * Error object within OpenRouter response.
 */
data class OpenRouterError(
    val message: String? = null,
    val code: Int? = null
)

/**
 * Choice object within OpenRouter response.
 */
data class OpenRouterChoice(
    val message: OpenRouterMessage? = null
)
