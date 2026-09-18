// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import com.example.daysurpopt.domain.SpecificExpense
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scheduled-expenses inputs must be LIVE: every real change from the form must
 * invalidate stale analysis and re-simulate, while no-op edits (e.g. typing the
 * same value again, or non-numeric text) must not destroy the current analysis.
 */
class ScheduledExpensesFlowTest {

    private fun expense(age: Int = 40, amount: Double = 10000.0, offset: Double = 0.0) =
        SpecificExpense(age = age, amount = amount, utilityOffset = offset)

    @Test
    fun expenses_lists_differ_detects_any_field_change() {
        val base = listOf(expense(), expense(age = 50, amount = 5000.0, offset = 0.1))

        assertTrue(expensesListsDiffer(base, listOf(expense(age = 41), expense(age = 50, amount = 5000.0, offset = 0.1))))
        assertTrue(expensesListsDiffer(base, listOf(expense(), expense(age = 50, amount = 5001.0, offset = 0.1))))
        assertTrue(expensesListsDiffer(base, listOf(expense(), expense(age = 50, amount = 5000.0, offset = 0.2))))
        assertTrue(expensesListsDiffer(base, listOf(expense(age = 50, amount = 5000.0, offset = 0.1), expense())))
        assertTrue(expensesListsDiffer(base, listOf(expense())))
        assertTrue(expensesListsDiffer(base, emptyList()))
    }

    @Test
    fun expenses_lists_differ_false_for_identical_lists_and_noop_edits() {
        val base = listOf(expense(), expense(age = 50, amount = 5000.0, offset = 0.1))

        assertFalse(expensesListsDiffer(base, base))
        assertFalse(
            "Rebuilding the same values (e.g. re-typing the same number) is a no-op",
            expensesListsDiffer(base, listOf(expense(), expense(age = 50, amount = 5000.0, offset = 0.1)))
        )
        assertFalse("Empty vs empty is a no-op", expensesListsDiffer(emptyList(), emptyList()))
    }
}
