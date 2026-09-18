// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.utils

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object AppDebugLog {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val maxLines = 800
    private val _lines = mutableStateListOf<String>()
    val lines: List<String> get() = _lines

    fun clear() {
        _lines.clear()
        add("App", "Log cleared")
    }

    fun add(tag: String, message: String) {
        val ts = timeFormat.format(Date())
        _lines.add("$ts [$tag] $message")
        val overflow = _lines.size - maxLines
        if (overflow > 0) {
            repeat(max(0, overflow)) { _lines.removeAt(0) }
        }
    }
}
