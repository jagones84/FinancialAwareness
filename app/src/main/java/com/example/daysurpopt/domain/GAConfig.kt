// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

data class GAConfigUI(
    val popSize: String = "150",
    val generations: String = "60",
    val pc: String = "0.7",
    val pm: String = "0.08",
    val minRange: String = "0.0;30;0.0;30",
    val maxRange: String = "1.0;82;3.5;82",
    val maximize: String = "1"
)

data class GAConfig(
    val popSize: Int,
    val generations: Int,
    val pc: Double,
    val pm: Double,
    val min: ParamsCandidate,
    val max: ParamsCandidate,
    val maximize: Boolean
)
