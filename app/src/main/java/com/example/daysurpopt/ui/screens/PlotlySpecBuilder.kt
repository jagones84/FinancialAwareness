// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer

object PlotlySpecBuilder {
    private val gson = GsonBuilder()
        .registerTypeAdapter(Double::class.java, JsonSerializer<Double> { src, _, _ ->
            if (src.isNaN() || src.isInfinite()) JsonNull.INSTANCE else JsonPrimitive(src)
        })
        .create()

    data class LocalizedStrings(
        val objective: String,
        val heatmapCpu: String,
        val saveImage: String = "Save Image",
        val resetScale: String = "Reset Scale"
    )

    fun buildLineJson(
        x: List<Double>,
        y: List<Double>,
        title: String,
        axisXTitle: String,
        axisYTitle: String,
        traceName: String
    ): String {
        return buildLineJson(
            x = x,
            y = y,
            title = title,
            axisXTitle = axisXTitle,
            axisYTitle = axisYTitle,
            traceName = traceName,
            xRange = null,
            yRange = null,
            fixedRange = false,
            meta = null
        )
    }

    fun buildLineJson(
        x: List<Double>,
        y: List<Double>,
        title: String,
        axisXTitle: String,
        axisYTitle: String,
        traceName: String,
        xRange: Pair<Double, Double>?,
        yRange: Pair<Double, Double>?,
        fixedRange: Boolean,
        meta: Map<String, Any>?
    ): String {
        val trace = mapOf(
            "type" to "scatter",
            "mode" to "lines+markers",
            "name" to traceName,
            "x" to x,
            "y" to y,
            "line" to mapOf("color" to "#00E5FF", "width" to 2),
            "marker" to mapOf("size" to 4, "color" to "#FFD600")
        )

        val xaxis = mutableMapOf<String, Any>(
            "title" to axisXTitle,
            "gridcolor" to "#444444",
            "gridwidth" to 1,
            "color" to "#FFFFFF",
            "zeroline" to true,
            "zerolinecolor" to "#FFFFFF",
            "zerolinewidth" to 2,
            "nticks" to 20,
            "tickmode" to "auto",
            "showticklabels" to true,
            "tickangle" to -45,
            "automargin" to true,
            "ticks" to "outside",
            "fixedrange" to fixedRange
        )
        if (xRange != null) {
            xaxis["range"] = listOf(xRange.first, xRange.second)
        }

        val yaxis = mutableMapOf<String, Any>(
            "title" to axisYTitle,
            "gridcolor" to "#444444",
            "gridwidth" to 1,
            "color" to "#FFFFFF",
            "zeroline" to true,
            "zerolinecolor" to "#FFFFFF",
            "zerolinewidth" to 2,
            "nticks" to 20,
            "tickmode" to "auto",
            "showticklabels" to true,
            "automargin" to true,
            "rangemode" to "tozero",
            "fixedrange" to fixedRange
        )
        if (yRange != null) {
            yaxis["range"] = listOf(yRange.first, yRange.second)
        }

        val layout = mapOf(
            "paper_bgcolor" to "#000000",
            "plot_bgcolor" to "#000000",
            "uirevision" to "constant",
            "font" to mapOf("color" to "#FFFFFF"),
            "margin" to mapOf("l" to 70, "r" to 30, "t" to 60, "b" to 110),
            "xaxis" to xaxis,
            "yaxis" to yaxis,
            "title" to mapOf("text" to title, "y" to 0.95),
            "autosize" to true,
            "dragmode" to (if (fixedRange) false else "zoom"),
            "meta" to meta
        )

        val json = gson.toJson(mapOf("data" to listOf(trace), "layout" to layout))
        Log.d("PlotlySpecBuilder", "Generated Plotly LINE JSON length: ${json.length}")
        return json
    }

    fun buildMultiLineJson(
        traces: List<LineTraceSpec>,
        title: String,
        axisXTitle: String,
        axisYTitle: String,
        xRange: Pair<Double, Double>?,
        yRange: Pair<Double, Double>?,
        fixedRange: Boolean,
        meta: Map<String, Any>?,
        layoutOverrides: Map<String, Any> = emptyMap(),
        xTickAngle: Int = -45
    ): String {
        val dataTraces = traces.map { spec ->
            mapOf(
                "type" to "scatter",
                "mode" to "lines+markers",
                "name" to spec.name,
                "x" to spec.x,
                "y" to spec.y,
                "line" to mapOf("color" to spec.color, "width" to 2),
                "marker" to mapOf("size" to spec.pointSize, "color" to spec.pointColor)
            )
        }

        val xaxis = mutableMapOf<String, Any>(
            "title" to axisXTitle,
            "gridcolor" to "#444444",
            "gridwidth" to 1,
            "color" to "#FFFFFF",
            "zeroline" to true,
            "zerolinecolor" to "#FFFFFF",
            "zerolinewidth" to 2,
            "nticks" to 20,
            "tickmode" to "auto",
            "showticklabels" to true,
            "tickangle" to xTickAngle,
            "automargin" to true,
            "ticks" to "outside",
            "fixedrange" to fixedRange
        )
        if (xRange != null) {
            xaxis["range"] = listOf(xRange.first, xRange.second)
        }

        val yaxis = mutableMapOf<String, Any>(
            "title" to axisYTitle,
            "gridcolor" to "#444444",
            "gridwidth" to 1,
            "color" to "#FFFFFF",
            "zeroline" to true,
            "zerolinecolor" to "#FFFFFF",
            "zerolinewidth" to 2,
            "nticks" to 20,
            "tickmode" to "auto",
            "showticklabels" to true,
            "automargin" to true,
            "rangemode" to "tozero",
            "fixedrange" to fixedRange
        )
        if (yRange != null) {
            yaxis["range"] = listOf(yRange.first, yRange.second)
        }

        val layout = mapOf(
            "paper_bgcolor" to "#000000",
            "plot_bgcolor" to "#000000",
            "uirevision" to "constant",
            "font" to mapOf("color" to "#FFFFFF"),
            "margin" to mapOf("l" to 70, "r" to 30, "t" to 60, "b" to 110),
            "xaxis" to xaxis,
            "yaxis" to yaxis,
            "title" to mapOf("text" to title, "y" to 0.95),
            "autosize" to true,
            "dragmode" to (if (fixedRange) false else "zoom"),
            "meta" to meta
        ).plus(layoutOverrides)

        return gson.toJson(mapOf("data" to dataTraces, "layout" to layout))
    }

    data class LineTraceSpec(
        val name: String,
        val x: List<Double>,
        val y: List<Double>,
        val color: String,
        val pointColor: String,
        val pointSize: Int = 4
    )

    fun buildJson(
        grid: SurfaceGrid,
        axisXTitle: String,
        axisYTitle: String,
        axisZTitle: String,
        useHeatmap: Boolean,
        localized: LocalizedStrings,
        showContours: Boolean = true,
        isPerspective: Boolean = true,
        extraMarkers: List<Map<String, Any>> = emptyList(),
        disableMinMax: Boolean = false
    ): String {
        val traces = mutableListOf<Map<String, Any>>()
        var layout: Map<String, Any> = mapOf()

        // Helper to find min/max
        fun getMinMaxMarkers(x: List<Double>, y: List<Double>, z: List<List<Double?>>, is3d: Boolean): List<Map<String, Any>> {
            var minVal = Double.POSITIVE_INFINITY
            var maxVal = Double.NEGATIVE_INFINITY
            var minIdx = Pair(0, 0)
            var maxIdx = Pair(0, 0)
            var found = false

            for (iy in z.indices) {
                for (ix in z[iy].indices) {
                    val v = z[iy][ix]
                    if (v == null || !v.isFinite()) continue
                    
                    if (v < minVal) {
                        minVal = v
                        minIdx = Pair(iy, ix)
                        found = true
                    }
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = Pair(iy, ix)
                        found = true
                    }
                }
            }

            if (!found) return emptyList()

            val markers = mutableListOf<Map<String, Any>>()
            
            // Max Marker
            val maxMarker = mutableMapOf<String, Any>(
                "type" to if (is3d) "scatter3d" else "scatter",
                "mode" to "markers",
                "name" to "Max",
                "x" to listOf(x[maxIdx.second]),
                "y" to listOf(y[maxIdx.first]),
                "marker" to mapOf("size" to 4, "color" to "red", "symbol" to "circle"),
                "showlegend" to false
            )
            if (is3d) maxMarker["z"] = listOf(maxVal)
            markers.add(maxMarker)

            // Min Marker
            val minMarker = mutableMapOf<String, Any>(
                "type" to if (is3d) "scatter3d" else "scatter",
                "mode" to "markers",
                "name" to "Min",
                "x" to listOf(x[minIdx.second]),
                "y" to listOf(y[minIdx.first]),
                "marker" to mapOf("size" to 4, "color" to "blue", "symbol" to "circle"),
                "showlegend" to false
            )
            if (is3d) minMarker["z"] = listOf(minVal)
            markers.add(minMarker)

            return markers
        }

        fun getZMinMax(z: List<List<Double?>>): Pair<Double, Double> {
            var minVal = Double.POSITIVE_INFINITY
            var maxVal = Double.NEGATIVE_INFINITY
            for (row in z) {
                for (v in row) {
                    if (v == null || v.isNaN() || v.isInfinite()) continue
                    if (v < minVal) minVal = v
                    if (v > maxVal) maxVal = v
                }
            }
            if (!minVal.isFinite() || !maxVal.isFinite()) return 0.0 to 1.0
            if (minVal == maxVal) return minVal to (minVal + 1.0)
            return minVal to maxVal
        }

        // Anchors the color range on the feasible cells (z >= 0) so constraint-violating
        // cells (negative fObjW from the legacy penalty) clamp to the bottom color
        // instead of destroying the color resolution of the feasible band.
        fun getFeasibleAnchoredRange(z: List<List<Double?>>): Pair<Double, Double> {
            var minVal = Double.POSITIVE_INFINITY
            var maxVal = Double.NEGATIVE_INFINITY
            for (row in z) {
                for (v in row) {
                    if (v == null || v.isNaN() || v.isInfinite() || v < 0.0) continue
                    if (v < minVal) minVal = v
                    if (v > maxVal) maxVal = v
                }
            }
            if (!minVal.isFinite() || !maxVal.isFinite()) return getZMinMax(z)
            if (minVal == maxVal) return minVal to (minVal + 1.0)
            return minVal to maxVal
        }

        fun contourStep(minVal: Double, maxVal: Double, levels: Int): Double {
            val range = maxVal - minVal
            if (!range.isFinite() || range <= 0.0) return 1.0
            return range / levels.toDouble()
        }

        if (useHeatmap) {
            val (zMin, zMax) = if (grid.anchorColorScaleOnFeasible) getFeasibleAnchoredRange(grid.z) else getZMinMax(grid.z)
            val levels = 60
            
            if (showContours) {
                // Contour plot with lines and labels
                traces.add(mapOf(
                    "type" to "contour",
                    "name" to localized.objective,
                    "x" to grid.x,
                    "y" to grid.y,
                    "z" to grid.z,
                    "colorscale" to "Viridis",
                    "showscale" to true,
                    "colorbar" to mapOf("len" to 0.5, "y" to 0.75, "tickfont" to mapOf("color" to "#FFFFFF")),
                    "ncontours" to levels,
                    "autocontour" to true,
                    "contours" to mapOf(
                        "coloring" to "heatmap",
                        "showlines" to true,
                        "labelfont" to mapOf("size" to 10, "color" to "black"),
                        "showlabels" to true,
                        "line" to mapOf("width" to 0.5, "color" to "black"),
                        "start" to zMin,
                        "end" to zMax,
                        "size" to contourStep(zMin, zMax, levels)
                    )
                ))
            } else {
                // Pure Heatmap (no lines, no labels)
                val heatmapTrace = mutableMapOf<String, Any>(
                    "type" to "heatmap",
                    "name" to localized.objective,
                    "x" to grid.x,
                    "y" to grid.y,
                    "z" to grid.z,
                    "colorscale" to "Viridis",
                    "showscale" to true,
                    "colorbar" to mapOf("len" to 0.5, "y" to 0.75, "tickfont" to mapOf("color" to "#FFFFFF"))
                )
                if (grid.anchorColorScaleOnFeasible) {
                    heatmapTrace["zmin"] = zMin
                    heatmapTrace["zmax"] = zMax
                }
                traces.add(heatmapTrace)
            }
            // Add automatic Min/Max markers
            if (!disableMinMax) {
                traces.addAll(getMinMaxMarkers(grid.x, grid.y, grid.z, false))
            }
            
            // Add manually provided extra markers (e.g. Optimal Points)
            // Ensure they are 2D scatter
            extraMarkers.forEach { m ->
                val marker = m.toMutableMap()
                marker["type"] = "scatter" 
                marker["mode"] = "markers" // Force markers mode
                traces.add(marker)
            }
            
            layout = mapOf(
                "paper_bgcolor" to "#000000",
                "plot_bgcolor" to "#000000",
                "uirevision" to "constant",
                "font" to mapOf("color" to "#FFFFFF"),
                "margin" to mapOf("l" to 60, "r" to 30, "t" to 60, "b" to 80),
                "xaxis" to mapOf(
                    "title" to axisXTitle,
                    "showgrid" to false,
                    "zeroline" to false,
                    "color" to "#FFFFFF",
                    "nticks" to 20,
                    "automargin" to true
                ),
                "yaxis" to mapOf(
                    "title" to axisYTitle,
                    "showgrid" to false,
                    "zeroline" to false,
                    "color" to "#FFFFFF",
                    "nticks" to 20,
                    "automargin" to true
                ),
                "title" to mapOf("text" to localized.heatmapCpu, "y" to 0.95),
                "dragmode" to false,
                "meta" to mapOf("staticPlot" to true)
            )
        } else {
            val (zMin, zMax) = getZMinMax(grid.z)
            val levels = 20
            
            val contoursMap = if (showContours) {
                mapOf(
                    "x" to mapOf("show" to false),
                    "y" to mapOf("show" to false),
                    "z" to mapOf(
                        "show" to true,
                        "usecolormap" to false,
                        "color" to "black",
                        "width" to 1,
                        "project" to mapOf("x" to false, "y" to false, "z" to false),
                        "start" to zMin,
                        "end" to zMax,
                        "size" to contourStep(zMin, zMax, levels),
                        "showlabels" to true,
                        "labelfont" to mapOf("size" to 10, "color" to "black")
                    )
                )
            } else {
                mapOf(
                    "x" to mapOf("show" to false),
                    "y" to mapOf("show" to false),
                    "z" to mapOf("show" to false)
                )
            }

            traces.add(mapOf(
                "type" to "surface",
                "name" to localized.objective,
                "x" to grid.x,
                "y" to grid.y,
                "z" to grid.z,
                "opacity" to 0.95,
                "colorscale" to "Viridis",
                "showscale" to true,
                "contours" to contoursMap
            ))
            
            // Add automatic Min/Max markers
            if (!disableMinMax) {
                traces.addAll(getMinMaxMarkers(grid.x, grid.y, grid.z, true))
            }
            
            // Add manually provided extra markers
            // Ensure they are 3D scatter
            extraMarkers.forEach { m ->
                val marker = m.toMutableMap()
                marker["type"] = "scatter3d"
                marker["mode"] = "markers"
                traces.add(marker)
            }
            
            layout = mapOf(
                "paper_bgcolor" to "#000000",
                "plot_bgcolor" to "#000000",
                "uirevision" to "constant",
                "font" to mapOf("color" to "#FFFFFF"),
                "margin" to mapOf("l" to 0, "r" to 0, "t" to 0, "b" to 0),
                "dragmode" to "orbit",
                "scene" to mapOf(
                    "xaxis" to mapOf("title" to axisXTitle, "showgrid" to false, "zeroline" to false, "color" to "#FFFFFF", "nticks" to 15),
                    "yaxis" to mapOf("title" to axisYTitle, "showgrid" to false, "zeroline" to false, "color" to "#FFFFFF", "nticks" to 15),
                    "zaxis" to mapOf("title" to axisZTitle, "showgrid" to false, "zeroline" to false, "color" to "#FFFFFF", "nticks" to 15),
                    "aspectmode" to "manual",
                    "aspectratio" to mapOf("x" to 1, "y" to 1, "z" to 0.7),
                    "camera" to mapOf(
                        "eye" to mapOf("x" to 1.8, "y" to 1.8, "z" to 1.2),
                        "projection" to mapOf("type" to if (isPerspective) "perspective" else "orthographic")
                    )
                ),
                "annotations" to listOf(
                    mapOf(
                        "text" to "Obj = Avg * ((1 - w) + w * StabilityScore)<br>Stability Score = Avg / (Avg + StdDev)",
                        "showarrow" to false,
                        "xref" to "paper",
                        "yref" to "paper",
                        "x" to 0.02,
                        "y" to 0.02,
                        "xanchor" to "left",
                        "yanchor" to "bottom",
                        "font" to mapOf("color" to "#FFFFFF", "size" to 10),
                        "bgcolor" to "rgba(0,0,0,0.5)"
                    )
                ),
                "showlegend" to true,
                "legend" to mapOf("orientation" to "h", "x" to 0.0, "y" to 1.0, "font" to mapOf("color" to "#FFFFFF"))
            )
        }

        val json = gson.toJson(mapOf("data" to traces, "layout" to layout))
        Log.d("PlotlySpecBuilder", "Generated Plotly JSON length: ${json.length}")
        return json
    }
}
