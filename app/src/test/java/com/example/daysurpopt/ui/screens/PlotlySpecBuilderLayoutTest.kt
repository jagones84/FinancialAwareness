// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Layout contract of the Plotly multi-line builder:
 *  - DEFAULT must stay the full-screen style used by the Pareto chart (no legend key,
 *    rotated ticks, wide margins);
 *  - the COMPACT variant (layoutOverrides + xTickAngle) must put the legend INSIDE the
 *    plot (horizontal, top-right), shrink the margins and free the x axis (straight
 *    ticks, explicit 0..100 range) — fixes the Goal Solver locus chart being crushed
 *    sideways just to make room for an outside legend.
 */
class PlotlySpecBuilderLayoutTest {

    private fun layout(json: String) =
        JsonParser.parseString(json).asJsonObject["layout"].asJsonObject

    private fun trace() = PlotlySpecBuilder.LineTraceSpec(
        name = "t", x = listOf(0.0), y = listOf(1.0), color = "#000000", pointColor = "#000000"
    )

    @Test
    fun default_layout_is_unchanged_fullscreen_style() {
        val layout = layout(
            PlotlySpecBuilder.buildMultiLineJson(
                traces = listOf(trace()),
                title = "",
                axisXTitle = "x",
                axisYTitle = "y",
                xRange = null,
                yRange = null,
                fixedRange = false,
                meta = null
            )
        )
        assertFalse(layout.has("legend"))
        assertEquals(-45, layout["xaxis"].asJsonObject["tickangle"].asInt)
        assertEquals(70, layout["margin"].asJsonObject["l"].asInt)
        assertEquals(110, layout["margin"].asJsonObject["b"].asInt)
    }

    @Test
    fun compact_overrides_place_the_legend_inside_and_free_the_x_axis() {
        val layout = layout(
            PlotlySpecBuilder.buildMultiLineJson(
                traces = listOf(trace()),
                title = "",
                axisXTitle = "x",
                axisYTitle = "y",
                xRange = 0.0 to 100.0,
                yRange = null,
                fixedRange = true,
                meta = mapOf("staticPlot" to true),
                layoutOverrides = mapOf(
                    "legend" to mapOf(
                        "orientation" to "h",
                        "x" to 1.0, "xanchor" to "right",
                        "y" to 1.0, "yanchor" to "top",
                        "font" to mapOf("size" to 10, "color" to "#FFFFFF"),
                        "bgcolor" to "rgba(0,0,0,0)"
                    ),
                    "margin" to mapOf("l" to 44, "r" to 8, "t" to 8, "b" to 30)
                ),
                xTickAngle = 0
            )
        )
        val legend = layout["legend"].asJsonObject
        assertEquals("h", legend["orientation"].asString)
        assertEquals("right", legend["xanchor"].asString)
        assertEquals("top", legend["yanchor"].asString)
        val margin = layout["margin"].asJsonObject
        assertEquals(44, margin["l"].asInt)
        assertEquals(8, margin["r"].asInt)
        assertEquals(8, margin["t"].asInt)
        assertEquals(30, margin["b"].asInt)
        assertEquals(0, layout["xaxis"].asJsonObject["tickangle"].asInt)
        assertEquals(0.0, layout["xaxis"].asJsonObject["range"].asJsonArray[0].asDouble, 1e-9)
        assertEquals(100.0, layout["xaxis"].asJsonObject["range"].asJsonArray[1].asDouble, 1e-9)
        assertEquals(true, layout["meta"].asJsonObject["staticPlot"].asBoolean)
    }
}
