// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Color-scale contract of the landscape heatmaps:
 * when anchorColorScaleOnFeasible is set, the 2D color range must be anchored on
 * the feasible cells (z >= 0) so constraint-violating cells (negative fObjW from
 * the legacy penalty) clamp to the bottom color instead of destroying the color
 * resolution of the feasible band. Delta grids and the 3D surface keep the raw
 * min/max scale.
 */
class PlotlySpecBuilderColorScaleTest {

    private val localized = PlotlySpecBuilder.LocalizedStrings(objective = "obj", heatmapCpu = "title")

    private fun traces(json: String) =
        JsonParser.parseString(json).asJsonObject["data"].asJsonArray

    private fun mixedGrid(anchor: Boolean) = SurfaceGrid(
        x = listOf(0.0, 1.0),
        y = listOf(0.0, 1.0),
        z = listOf(
            listOf(0.20, 0.24),
            listOf(-2.39, 0.22)
        ),
        anchorColorScaleOnFeasible = anchor
    )

    private fun buildJson(grid: SurfaceGrid, useHeatmap: Boolean, showContours: Boolean): String =
        PlotlySpecBuilder.buildJson(
            grid = grid,
            axisXTitle = "x",
            axisYTitle = "y",
            axisZTitle = "z",
            useHeatmap = useHeatmap,
            localized = localized,
            showContours = showContours
        )

    @Test
    fun heatmap_branch_anchors_color_range_on_feasible_cells() {
        val trace = traces(buildJson(mixedGrid(anchor = true), useHeatmap = true, showContours = false))[0].asJsonObject
        assertEquals(0.20, trace["zmin"].asDouble, 1e-9)
        assertEquals(0.24, trace["zmax"].asDouble, 1e-9)
    }

    @Test
    fun contour_branch_uses_feasible_range_for_levels() {
        val trace = traces(buildJson(mixedGrid(anchor = true), useHeatmap = true, showContours = true))[0].asJsonObject
        val contours = trace["contours"].asJsonObject
        assertEquals(0.20, contours["start"].asDouble, 1e-9)
        assertEquals(0.24, contours["end"].asDouble, 1e-9)
    }

    @Test
    fun anchor_disabled_keeps_raw_min_max_for_delta_grids() {
        val trace = traces(buildJson(mixedGrid(anchor = false), useHeatmap = true, showContours = false))[0].asJsonObject
        assertFalse(trace.has("zmin"))
        assertFalse(trace.has("zmax"))
    }

    @Test
    fun anchor_without_violators_keeps_plain_min_max() {
        val grid = SurfaceGrid(
            x = listOf(0.0, 1.0),
            y = listOf(0.0, 1.0),
            z = listOf(listOf(0.10, 0.20), listOf(0.30, 0.40)),
            anchorColorScaleOnFeasible = true
        )
        val trace = traces(buildJson(grid, useHeatmap = true, showContours = false))[0].asJsonObject
        assertEquals(0.10, trace["zmin"].asDouble, 1e-9)
        assertEquals(0.40, trace["zmax"].asDouble, 1e-9)
    }

    @Test
    fun anchor_with_all_cells_infeasible_falls_back_to_full_range() {
        val grid = SurfaceGrid(
            x = listOf(0.0, 1.0),
            y = listOf(0.0, 1.0),
            z = listOf(listOf(-1.0, -2.0), listOf(-3.0, -4.0)),
            anchorColorScaleOnFeasible = true
        )
        val trace = traces(buildJson(grid, useHeatmap = true, showContours = false))[0].asJsonObject
        assertEquals(-4.0, trace["zmin"].asDouble, 1e-9)
        assertEquals(-1.0, trace["zmax"].asDouble, 1e-9)
    }

    @Test
    fun surface_3d_branch_keeps_raw_scale_even_when_anchored() {
        val trace = traces(buildJson(mixedGrid(anchor = true), useHeatmap = false, showContours = false))[0].asJsonObject
        assertFalse(trace.has("zmin"))
        assertFalse(trace.has("zmax"))
    }
}
