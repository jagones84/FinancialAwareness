// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

data class ChartUiState(
    val grid: SurfaceGrid? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
