// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentSettingsTest {

    @Test
    fun defaultModel_isQwen37Plus() {
        assertEquals("qwen/qwen3.7-plus", AgentSettings().model)
    }
}
