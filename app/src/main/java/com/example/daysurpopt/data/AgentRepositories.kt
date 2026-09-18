// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.data

import android.content.Context
import androidx.core.content.edit
import com.example.daysurpopt.domain.AgentSettings
import com.example.daysurpopt.domain.ChatMessage
import com.example.daysurpopt.domain.ChatSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Repository for persisting Agent settings (API Key, Model) using SharedPreferences.
 */
object AgentSettingsRepository {
    private const val PREFS_NAME = "AgentPrefs"
    private const val KEY_SETTINGS = "AgentSettings"
    private val gson = Gson()

    fun saveSettings(context: Context, settings: AgentSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(settings)
        prefs.edit { putString(KEY_SETTINGS, json) }
    }

    fun loadSettings(context: Context): AgentSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SETTINGS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, AgentSettings::class.java)
            } catch (_: Exception) {
                AgentSettings()
            }
        } else {
            AgentSettings()
        }
    }
}

/**
 * Repository for persisting Chat History (Sessions) using SharedPreferences.
 */
object ChatRepository {
    private const val PREFS_NAME = "ChatHistoryPrefs"
    private const val KEY_SESSIONS = "ChatSessions"
    private val gson = Gson()

    fun saveSessions(context: Context, sessions: List<ChatSession>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(sessions)
        prefs.edit { putString(KEY_SESSIONS, json) }
    }

    fun loadSessions(context: Context): List<ChatSession> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<ChatSession>>() {}.type
                gson.fromJson(json, type)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun createSession(context: Context): ChatSession {
        val newSession = ChatSession(
            id = UUID.randomUUID().toString(),
            messages = emptyList(),
            lastModified = System.currentTimeMillis()
        )
        val sessions = loadSessions(context).toMutableList()
        sessions.add(0, newSession) // Add to top
        saveSessions(context, sessions)
        return newSession
    }

    fun updateSession(context: Context, session: ChatSession) {
        val sessions = loadSessions(context).toMutableList()
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index != -1) {
            sessions[index] = session.copy(lastModified = System.currentTimeMillis())
            saveSessions(context, sessions)
        } else {
            // New session, add it
            sessions.add(0, session)
            saveSessions(context, sessions)
        }
    }
    
    fun deleteSession(context: Context, sessionId: String) {
        val sessions = loadSessions(context).toMutableList()
        sessions.removeAll { it.id == sessionId }
        saveSessions(context, sessions)
    }
}
