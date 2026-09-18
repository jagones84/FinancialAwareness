// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentReportFormatterTest {

    @Test
    fun computeStabilityIndex_matchesSimulationLogicScaling() {
        assertEquals(0.8, AgentReportFormatter.computeStabilityIndex(0.50, 0.125), 1e-9)
    }
}
