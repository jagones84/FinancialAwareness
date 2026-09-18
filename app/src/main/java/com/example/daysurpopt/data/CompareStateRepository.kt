// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.data

import android.content.Context
import androidx.core.content.edit
import com.example.daysurpopt.domain.CompareState

object CompareStateRepository {
    private const val PREFS_NAME = "compare_state_prefs"
    private const val KEY_IS_COMPARING = "is_comparing"
    private const val KEY_PROFILE_1 = "profile_1"
    private const val KEY_PROFILE_2 = "profile_2"

    fun saveState(context: Context, state: CompareState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_IS_COMPARING, state.isComparing)
            putString(KEY_PROFILE_1, state.profile1Name)
            putString(KEY_PROFILE_2, state.profile2Name)
        }
    }

    fun loadState(context: Context): CompareState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isComparing = prefs.getBoolean(KEY_IS_COMPARING, false)
        if (!isComparing) return null

        val p1 = prefs.getString(KEY_PROFILE_1, null)
        val p2 = prefs.getString(KEY_PROFILE_2, null)

        if (p1 == null || p2 == null) return null

        // Note: Actual profiles (FullProfile objects) must be loaded from ProfileRepository
        // This just returns the names/state metadata.
        return CompareState(
            isComparing = true,
            profile1Name = p1,
            profile2Name = p2
        )
    }

    fun clearState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            clear()
        }
    }
}
