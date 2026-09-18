// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.data

import android.content.Context
import androidx.core.content.edit
import com.example.daysurpopt.domain.*
import com.google.gson.Gson

object LanguageRepository {
    private const val PREFS_NAME = "LangPrefs"
    private const val KEY_LANG = "Language"

    fun saveLanguage(context: Context, lang: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LANG, lang) }
    }

    fun loadLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, "en") ?: "en"
    }
}

object PrivacyConsentRepository {
    private const val PREFS_NAME = "PrivacyConsentPrefs"
    private const val KEY_STATUS = "ConsentStatus"

    private const val STATUS_UNKNOWN = 0
    private const val STATUS_GRANTED = 1
    private const val STATUS_DENIED = 2

    fun hasDecision(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_STATUS)
    }

    fun isGranted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_STATUS, STATUS_UNKNOWN) == STATUS_GRANTED
    }

    fun isDenied(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_STATUS, STATUS_UNKNOWN) == STATUS_DENIED
    }

    fun setGranted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_STATUS, STATUS_GRANTED) }
    }

    fun setDenied(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_STATUS, STATUS_DENIED) }
    }
}

object QuickStartRepository {
    private const val PREFS_NAME = "QuickStartPrefs"
    private const val KEY_SEEN = "QuickStartSeen"

    fun hasSeenQuickStart(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SEEN, false)
    }

    fun markQuickStartSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SEEN, true) }
    }
}

object FinancialDataRepository {
    private const val PREFS_NAME = "FinancialPrefs"
    private const val KEY_INPUTS = "FinancialInputs"
    private val gson = Gson()

    fun saveInputs(context: Context, inputs: FinancialInput) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(inputs)
        prefs.edit { putString(KEY_INPUTS, json) }
    }

    fun loadInputs(context: Context): FinancialInput {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_INPUTS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, FinancialInput::class.java)
            } catch (_: Exception) {
                FinancialInput()
            }
        } else {
            FinancialInput()
        }
    }
}

object SurplusDataRepository {
    private const val PREFS_NAME = "SurplusPrefs"
    private const val KEY_INPUTS = "SurplusInputs"
    private val gson = Gson()

    fun saveInputs(context: Context, inputs: SurplusInput) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(inputs)
        prefs.edit { putString(KEY_INPUTS, json) }
    }

    fun loadInputs(context: Context): SurplusInput {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_INPUTS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, SurplusInput::class.java)
            } catch (_: Exception) {
                SurplusInput()
            }
        } else {
            SurplusInput()
        }
    }
}

object GaConfigRepository {
    private const val PREFS_NAME = "GaConfigPrefs"
    private const val KEY_CONFIG = "GaConfig"
    private val gson = Gson()

    fun saveConfig(context: Context, config: GAConfigUI) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(config)
        prefs.edit { putString(KEY_CONFIG, json) }
    }

    fun loadConfig(context: Context): GAConfigUI {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, GAConfigUI::class.java)
            } catch (_: Exception) {
                GAConfigUI()
            }
        } else {
            GAConfigUI()
        }
    }
}

object ProfileRepository {
    private const val PREFS_NAME = "AppProfiles"
    private val gson = Gson()

    fun saveProfile(context: Context, name: String, profile: FullProfile) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(profile)
        prefs.edit { putString(name, json) }
    }

    fun loadProfile(context: Context, name: String): FullProfile? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(name, null)
        return if (json != null) {
            try {
                gson.fromJson(json, FullProfile::class.java)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    fun getProfileNames(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.keys.sorted()
    }

    fun deleteProfile(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(name) }
    }
}

object SpecificExpensesRepository {
    private const val PREFS_NAME = "SpecificExpensesPrefs"
    private const val KEY_EXPENSES = "SpecificExpenses"
    private val gson = Gson()

    fun saveExpenses(context: Context, expenses: List<SpecificExpense>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(expenses)
        prefs.edit { putString(KEY_EXPENSES, json) }
    }

    fun loadExpenses(context: Context): List<SpecificExpense> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_EXPENSES, null)
        val type = object : com.google.gson.reflect.TypeToken<List<SpecificExpense>>() {}.type
        return if (json != null) {
            try {
                gson.fromJson<List<SpecificExpense>>(json, type)?.take(10)?.let {
                    if (it.size < 10) {
                        it + List(10 - it.size) { SpecificExpense() }
                    } else {
                        it
                    }
                } ?: List(10) { SpecificExpense() }
            } catch (_: Exception) {
                List(10) { SpecificExpense() }
            }
        } else {
            List(10) { SpecificExpense() }
        }
    }
}
