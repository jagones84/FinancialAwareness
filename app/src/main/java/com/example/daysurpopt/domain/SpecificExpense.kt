// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

data class SpecificExpense(
    val age: Int = 0,
    val amount: Double = 0.0,
    val utilityOffset: Double = 0.0
)

data class SpecificExpenseUI(
    val age: String,
    val amount: String,
    val utilityOffset: String
)
