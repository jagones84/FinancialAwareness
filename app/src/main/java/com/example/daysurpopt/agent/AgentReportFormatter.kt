// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.logic.computeStabilityScore

object AgentReportFormatter {

    fun computeStabilityIndex(avgUtilita: Double, stdDev: Double): Double {
        return computeStabilityScore(avgUtilita, stdDev)
    }
}
