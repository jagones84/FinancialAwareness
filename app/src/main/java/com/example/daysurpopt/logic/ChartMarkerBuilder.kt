// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMarkerSnapshot
import com.example.daysurpopt.ui.theme.ChartP1Hex

private const val TrueScalarMarkerHex = "#008000"
private const val ParetoCompromiseMarkerHex = "#8E24AA"
private const val ParetoReferenceMarkerHex = "#FF8C00"

object ChartMarkerBuilder {

    fun buildP1P2Markers(
        inputs: FinancialInput,
        currentObjective: Double,
        lastTrueScalar: OptimizationMarkerSnapshot?,
        lastParetoCompromise: OptimizationMarkerSnapshot?,
        lastParetoReference: OptimizationMarkerSnapshot?,
        currentLabel: String,
        trueScalarLabel: String,
        paretoCompromiseLabel: String,
        paretoReferenceLabel: String
    ): List<Map<String, Any>> {
        val markers = mutableListOf<Map<String, Any>>(
            mapOf(
                "x" to listOf(inputs.p1SavingRatioSurplus),
                "y" to listOf(inputs.p2EtaFineRisparmioNoCapitale),
                "z" to listOf(currentObjective),
                "name" to currentLabel,
                "color" to ChartP1Hex
            )
        )

        lastTrueScalar?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p1),
                    "y" to listOf(it.params.p2),
                    "z" to listOf(it.objectiveValue),
                    "name" to trueScalarLabel,
                    "color" to TrueScalarMarkerHex
                )
            )
        }

        lastParetoCompromise?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p1),
                    "y" to listOf(it.params.p2),
                    "z" to listOf(it.objectiveValue),
                    "name" to paretoCompromiseLabel,
                    "color" to ParetoCompromiseMarkerHex
                )
            )
        }

        lastParetoReference?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p1),
                    "y" to listOf(it.params.p2),
                    "z" to listOf(it.objectiveValue),
                    "name" to paretoReferenceLabel,
                    "color" to ParetoReferenceMarkerHex
                )
            )
        }

        return markers
    }

    fun buildP3P4Markers(
        inputs: FinancialInput,
        currentObjective: Double,
        lastTrueScalar: OptimizationMarkerSnapshot?,
        lastParetoCompromise: OptimizationMarkerSnapshot?,
        lastParetoReference: OptimizationMarkerSnapshot?,
        currentLabel: String,
        trueScalarLabel: String,
        paretoCompromiseLabel: String,
        paretoReferenceLabel: String
    ): List<Map<String, Any>> {
        val markers = mutableListOf<Map<String, Any>>(
            mapOf(
                "x" to listOf(inputs.p3PercentualeCapitaleDaSpendereAnnualmente),
                "y" to listOf(inputs.p4EtaAnticipataInizioSpesaCapitale),
                "z" to listOf(currentObjective),
                "name" to currentLabel,
                "color" to ChartP1Hex
            )
        )

        lastTrueScalar?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p3),
                    "y" to listOf(it.params.p4),
                    "z" to listOf(it.objectiveValue),
                    "name" to trueScalarLabel,
                    "color" to TrueScalarMarkerHex
                )
            )
        }

        lastParetoCompromise?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p3),
                    "y" to listOf(it.params.p4),
                    "z" to listOf(it.objectiveValue),
                    "name" to paretoCompromiseLabel,
                    "color" to ParetoCompromiseMarkerHex
                )
            )
        }

        lastParetoReference?.let {
            markers.add(
                mapOf(
                    "x" to listOf(it.params.p3),
                    "y" to listOf(it.params.p4),
                    "z" to listOf(it.objectiveValue),
                    "name" to paretoReferenceLabel,
                    "color" to ParetoReferenceMarkerHex
                )
            )
        }

        return markers
    }
}
