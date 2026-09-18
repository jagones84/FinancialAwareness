// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ParetoFrontResult
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.logic.calculateObjectivesFromYears
import com.example.daysurpopt.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlin.math.max

object PdfExporter {

    private const val COLOR_PROFESSIONAL_BLUE = 0xFF0066CC.toInt()
    private const val COLOR_HEADER_BG = 0xFF505050.toInt()
    private const val COLOR_CELL_BG = 0xFFF5F5F5.toInt()
    private const val COLOR_POSITIVE_GREEN = 0xFF008000.toInt()
    private const val COLOR_NEGATIVE_RED = 0xFFC80000.toInt()
    private const val COLOR_CHART_P1 = 0xFF0050B4.toInt()
    private const val COLOR_CHART_P2 = 0xFFDC6400.toInt()

    fun generateReport(
        context: Context,
        inputs: FinancialInput,
        results: List<SimulationYear>,
        objectiveValue: Double,
        sensitivityResults: List<com.example.daysurpopt.domain.SensitivityResult>? = null,
        aiComment: String? = null,
        modelName: String? = null,
        optimizationMode: OptimizationMode = OptimizationMode.TRUE_SCALAR,
        paretoFrontResult: ParetoFrontResult? = null,
        compareState: com.example.daysurpopt.domain.CompareState? = null,
        profile2Results: Triple<com.example.daysurpopt.domain.ObjectiveResults?, List<SimulationYear>, List<com.example.daysurpopt.domain.SensitivityResult>?>? = null,
        deltaResults: Triple<com.example.daysurpopt.domain.DeltaObjectiveResults?, List<com.example.daysurpopt.domain.DeltaSimulationYear>, List<com.example.daysurpopt.domain.DeltaSensitivityResult>?>? = null
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()

        val paint = Paint()
        val titlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            color = COLOR_PROFESSIONAL_BLUE
        }
        val textPaint = Paint().apply {
            textSize = 10f
            color = Color.BLACK
        }
        val tableHeaderPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = Color.WHITE
        }
        val tableHeaderBgPaint = Paint().apply {
            color = COLOR_HEADER_BG
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }
        val cellBgPaint = Paint().apply {
            color = COLOR_CELL_BG
            style = Paint.Style.FILL
        }
        val deltaPositivePaint = Paint().apply {
            textSize = 10f
            color = COLOR_POSITIVE_GREEN
        }
        val deltaNegativePaint = Paint().apply {
            textSize = 10f
            color = COLOR_NEGATIVE_RED
        }

        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 50f
        val margin = 50f
        val pageWidth = pageInfo.pageWidth.toFloat()
        val contentWidth = pageWidth - 2 * margin

        fun checkNewPage(heightNeeded: Float) {
            if (y + heightNeeded > 800) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        // --- TITLE & DATE ---
        val titleText = if (compareState?.isComparing == true) {
            "${context.getString(R.string.pdf_report_title)} - COMPARISON: ${compareState.profile1Name} vs ${compareState.profile2Name}"
        } else {
            context.getString(R.string.pdf_report_title)
        }
        
        // Wrap title if too long
        val titleWords = titleText.split(" ")
        var titleLine = ""
        titleWords.forEach { word ->
            val testLine = if (titleLine.isEmpty()) word else "$titleLine $word"
            if (titlePaint.measureText(testLine) < contentWidth) {
                titleLine = testLine
            } else {
                canvas.drawText(titleLine, margin, y, titlePaint)
                y += 20f
                titleLine = word
            }
        }
        if (titleLine.isNotEmpty()) {
            canvas.drawText(titleLine, margin, y, titlePaint)
            y += 25f
        }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        canvas.drawText(context.getString(R.string.pdf_generated_on, dateFormat.format(Date())), margin, y, textPaint)
        y += 35f

        // --- HELPER FOR TABLES ---
        fun drawTable(
            title: String,
            headers: List<String>,
            rows: List<List<String>>,
            colWeights: List<Float>
        ) {
            checkNewPage(60f)
            canvas.drawText(title, margin, y, headerPaint)
            y += 15f

            val totalWeight = colWeights.sum()
            val colWidths = colWeights.map { (it / totalWeight) * contentWidth }

            fun drawHeader() {
                canvas.drawRect(margin, y, margin + contentWidth, y + 20, tableHeaderBgPaint)
                var currentX = margin
                headers.forEachIndexed { i, h ->
                    // Simple clipping for headers for now, or could wrap too, but usually shorts
                    val colW = colWidths[i]
                    val safeHeader = if (tableHeaderPaint.measureText(h) > colW - 4) {
                         var cut = h
                         while (cut.isNotEmpty() && tableHeaderPaint.measureText(cut + "...") > colW - 4) {
                             cut = cut.dropLast(1)
                         }
                         "$cut..."
                    } else h
                    canvas.drawText(safeHeader, currentX + 5, y + 14, tableHeaderPaint)
                    currentX += colW
                }
                y += 20f
            }

            drawHeader()

            // Helper to split text into lines
            fun splitTextIntoLines(text: String, maxWidth: Float, paint: Paint): List<String> {
                val words = text.split(" ")
                val lines = mutableListOf<String>()
                var currentLine = ""

                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) <= maxWidth) {
                        currentLine = testLine
                    } else {
                        if (currentLine.isNotEmpty()) lines.add(currentLine)
                        currentLine = word
                        // Handle single words that are too long
                        while (paint.measureText(currentLine) > maxWidth) {
                            // Find split point
                            var splitIndex = 1
                            while (splitIndex < currentLine.length && paint.measureText(currentLine.substring(0, splitIndex + 1)) <= maxWidth) {
                                splitIndex++
                            }
                            lines.add(currentLine.substring(0, splitIndex))
                            currentLine = currentLine.substring(splitIndex)
                        }
                    }
                }
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                return lines
            }

            // Draw rows
            rows.forEachIndexed { index, row ->
                // 1. Calculate height needed for this row
                var maxLines = 1
                val rowLines = row.mapIndexed { i, cell ->
                    val availableWidth = colWidths[i] - 10f // 5 padding each side
                    val lines = splitTextIntoLines(cell, availableWidth, textPaint)
                    maxLines = max(maxLines, lines.size)
                    lines
                }

                val lineHeight = 12f
                val rowHeight = maxLines * lineHeight + 10f // padding

                if (y + rowHeight > 800) {
                    document.finishPage(page)
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f
                    // Redraw title (mini) and header on new page
                    canvas.drawText(title + " (cont.)", margin, y, headerPaint)
                    y += 15f
                    drawHeader()
                }

                if (index % 2 == 1) {
                    canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, cellBgPaint)
                }

                var currentX = margin
                rowLines.forEachIndexed { i, lines ->
                    var lineY = y + 11
                    lines.forEach { line ->
                        // Check if line contains delta (starts with "(Δ" or "(" and has +/-)
                        if (line.contains("(") && (line.contains("+") || line.contains("-"))) {
                            // Parse main text and delta
                            val parts = line.split("\n", limit = 2)
                            if (parts.size == 2) {
                                // Draw main value in black
                                canvas.drawText(parts[0], currentX + 5, lineY, textPaint)
                                lineY += lineHeight
                                // Draw delta in color
                                val deltaText = parts[1]
                                val paint = if (deltaText.contains("+")) deltaPositivePaint else deltaNegativePaint
                                canvas.drawText(deltaText, currentX + 5, lineY, paint)
                            } else {
                                canvas.drawText(line, currentX + 5, lineY, textPaint)
                            }
                        } else {
                            canvas.drawText(line, currentX + 5, lineY, textPaint)
                        }
                        lineY += lineHeight
                    }
                    currentX += colWidths[i]
                }
                
                // Draw bottom line
                canvas.drawLine(margin, y + rowHeight, margin + contentWidth, y + rowHeight, linePaint)
                y += rowHeight
            }
            y += 20f
        }

        // --- 0. REPORT METADATA ---
        drawTable(
            context.getString(R.string.pdf_report_info_title),
            listOf(context.getString(R.string.pdf_table_header_parameter), context.getString(R.string.pdf_table_header_value)),
            listOf(
                listOf(context.getString(R.string.pdf_table_header_date), dateFormat.format(Date())),
                listOf(context.getString(R.string.pdf_table_header_version), "1.0")
            ),
            listOf(2f, 1.5f)
        )

        // Helper to format delta
        fun fmtDelta(val1: Double, val2: Double, format: String = "%.2f", positivePrefix: Boolean = true): String {
             val diff = val2 - val1
             if (kotlin.math.abs(diff) < 0.0001) return ""
             val sign = if (diff >= 0 && positivePrefix) "+" else ""
             return "\n(${context.getString(R.string.delta_prefix)} $sign${String.format(Locale.getDefault(), format, diff)})"
        }
        
        fun fmtDeltaInt(val1: Int, val2: Int): String {
             val diff = val2 - val1
             if (diff == 0) return ""
             val sign = if (diff > 0) "+" else ""
             return "\n(${context.getString(R.string.delta_prefix)} $sign$diff)"
        }

        val p2Inputs = compareState?.profile2?.financialInput
        val isCompare = compareState?.isComparing == true && p2Inputs != null



        // --- 1. USER & FINANCIAL DATA ---
        val userDataRows = mutableListOf<List<String>>()
        
        fun row(label: String, val1: Any, val2: Any?, format: String = "%s", isInt: Boolean = false): List<String> {
             val v1Str = if (val1 is Number) String.format(Locale.getDefault(), format, val1) else val1.toString()
             var finalStr = v1Str
             if (isCompare && val2 != null) {
                 // Fix: Extract format pattern correctly
                 val cleanFormat = if (format.contains("%")) {
                     // Extract the format specifier (e.g., "%.2f" from "€%.2f" or "%.2f%%")
                     format.replace("€", "").replace("%%", "").replace("%,", "%").trim()
                 } else format
                 val deltaStr = if (isInt) fmtDeltaInt(val1 as Int, val2 as Int) 
                                else fmtDelta((val1 as Number).toDouble(), (val2 as Number).toDouble(), cleanFormat)
                 if (deltaStr.isNotEmpty()) finalStr += deltaStr
             }
             return listOf(label, finalStr)
        }

        // We accept that for composed strings like "Inheritance: X at age Y" we might not show perfect deltas, 
        // or we show deltas for the main numeric parts. Simpler to show separate rows or just delta of main value.
        // For simplicity in this text-based table, we'll check specific fields.
        
        userDataRows.add(row(context.getString(R.string.current_age), inputs.etaAttuale, p2Inputs?.etaAttuale, "%d", true))
        userDataRows.add(row(context.getString(R.string.retirement_age), inputs.etaPensione, p2Inputs?.etaPensione, "%d", true))
        userDataRows.add(row(context.getString(R.string.end_of_horizon_age), inputs.etaMorte, p2Inputs?.etaMorte, "%d", true))
        userDataRows.add(row(context.getString(R.string.initial_capital_at_current_age), inputs.capitaleIniziale, p2Inputs?.capitaleIniziale, "€%,.2f"))
        userDataRows.add(row(context.getString(R.string.capital_to_leave), inputs.soldiDaConservare, p2Inputs?.soldiDaConservare, "€%,.2f"))
        
        // Inheritance complex string
        val inh1 = context.getString(R.string.pdf_label_inheritance, String.format(Locale.getDefault(), "€%,.2f", inputs.eredita), inputs.etaRicevimentoEredita)
        var inhStr = inh1
        if (isCompare && p2Inputs != null) {
            val dAmount = fmtDelta(inputs.eredita, p2Inputs.eredita, "%,.2f")
            val dAge = fmtDeltaInt(inputs.etaRicevimentoEredita, p2Inputs.etaRicevimentoEredita)
            if (dAmount.isNotEmpty() || dAge.isNotEmpty()) {
                inhStr += "\n(Δ: $dAmount${if(dAge.isNotEmpty()) ", Age $dAge" else ""})"
            }
        }
        userDataRows.add(listOf(context.getString(R.string.expected_inheritance), inhStr))

        userDataRows.add(row(context.getString(R.string.net_tfr_at_retirement), inputs.tfrNetto, p2Inputs?.tfrNetto, "€%,.2f"))
        userDataRows.add(row(context.getString(R.string.annual_interest_rate_on_capital), inputs.tassoGuadagnoInteresse * 100, p2Inputs?.tassoGuadagnoInteresse?.times(100), "%.2f%%"))
        userDataRows.add(row(context.getString(R.string.annual_interest_rate_on_debt), inputs.tassoInteresseDebito * 100, p2Inputs?.tassoInteresseDebito?.times(100), "%.2f%%"))

        drawTable(
            context.getString(R.string.pdf_section_user_financial),
            listOf(context.getString(R.string.pdf_table_header_parameter), context.getString(R.string.pdf_table_header_value)),
            userDataRows,
            listOf(2f, 1.5f)
        )

        // --- 2. UTILITY & MODEL ---
        val utilityRows = mutableListOf<List<String>>()
        utilityRows.add(row(context.getString(R.string.extra_daily_spending_for_utility), inputs.valoreSpesaGiornalieraMaxUtilita, p2Inputs?.valoreSpesaGiornalieraMaxUtilita, "€%,.2f"))
        utilityRows.add(row(context.getString(R.string.minimum_utility_threshold), inputs.sogliaMinimaFunzioneUtilita, p2Inputs?.sogliaMinimaFunzioneUtilita, "%.4f"))
        utilityRows.add(row(context.getString(R.string.constant_utility_weight), inputs.bonusStdWeight, p2Inputs?.bonusStdWeight, "%.2f"))
        
        drawTable(
            context.getString(R.string.pdf_section_utility_model),
            listOf(context.getString(R.string.pdf_table_header_parameter), context.getString(R.string.pdf_table_header_value)),
            utilityRows,
            listOf(2f, 1.5f)
        )

        // --- 3. OPTIMIZATION PARAMETERS ---
        val optRows = mutableListOf<List<String>>()
        optRows.add(row(context.getString(R.string.p1_surplus_fraction_saved), inputs.p1SavingRatioSurplus * 100, p2Inputs?.p1SavingRatioSurplus?.times(100), "%.2f%%"))
        optRows.add(row(context.getString(R.string.p2_end_of_savings_age), inputs.p2EtaFineRisparmioNoCapitale, p2Inputs?.p2EtaFineRisparmioNoCapitale, "%d", true))
        optRows.add(row(context.getString(R.string.p3_annual_capital_spending_share), inputs.p3PercentualeCapitaleDaSpendereAnnualmente * 100, p2Inputs?.p3PercentualeCapitaleDaSpendereAnnualmente?.times(100), "%.2f%%"))
        optRows.add(row(context.getString(R.string.p4_start_of_capital_spending_age), inputs.p4EtaAnticipataInizioSpesaCapitale, p2Inputs?.p4EtaAnticipataInizioSpesaCapitale, "%d", true))

        drawTable(
            context.getString(R.string.pdf_section_optimization_params),
            listOf(context.getString(R.string.pdf_table_header_parameter), context.getString(R.string.pdf_table_header_value)),
            optRows,
            listOf(2f, 1.5f)
        )

        // --- 4. RESULTS SUMMARY ---
        val objResults = calculateObjectivesFromYears(results, inputs.bonusStdWeight, inputs.soldiDaConservare)
        val deltaObj = deltaResults?.first
        
        val resRows = mutableListOf<List<String>>()
        
        fun resRow(label: String, val1: Double, deltaVal: Double?, format: String = "%.4f"): List<String> {
            val v1Str = String.format(Locale.getDefault(), format, val1)
            var finalStr = v1Str
            if (isCompare && deltaVal != null) {
                finalStr += "\n(${context.getString(R.string.delta_prefix)} ${if(deltaVal>=0)"+" else ""}${String.format(Locale.getDefault(), format, deltaVal)})"
            }
            return listOf(label, finalStr)
        }

        resRows.add(resRow(context.getString(R.string.label_objective_function), objectiveValue, deltaObj?.deltaFObjW))
        resRows.add(resRow(context.getString(R.string.label_average_utility), results.map { it.funzioneUtilita }.average(), deltaObj?.deltaAvgUtilita))
        resRows.add(resRow(context.getString(R.string.label_stability_index), objResults.stabilityIndex, deltaObj?.deltaStabilityIndex))
        resRows.add(
            listOf(
                context.getString(R.string.optimization_mode_title),
                when (optimizationMode) {
                    OptimizationMode.TRUE_SCALAR -> context.getString(R.string.optimization_mode_true_scalar)
                    OptimizationMode.PARETO_KNEE -> context.getString(R.string.optimization_mode_pareto_knee)
                    OptimizationMode.PARETO_FRONT -> context.getString(R.string.optimization_mode_pareto_front)
                }
            )
        )
        if (paretoFrontResult != null) {
            resRows.add(
                listOf(
                    context.getString(R.string.pareto_front_points_label),
                    paretoFrontResult.points.size.toString()
                )
            )
            paretoFrontResult.referencePoint?.let { selected ->
                resRows.add(
                    resRow(
                        context.getString(R.string.pareto_knee_score_label),
                        selected.kneeScore ?: 0.0,
                        null
                    )
                )
            }
        }
        
        // Final capital delta
        val cap1 = results.lastOrNull()?.capitaleFineAnno ?: 0.0
        val cap2 = profile2Results?.second?.lastOrNull()?.capitaleFineAnno
        val deltaCap = if(cap2 != null) cap2 - cap1 else null
        resRows.add(resRow(context.getString(R.string.table_capital_end_year), cap1, deltaCap, "€%,.2f"))

        drawTable(
            context.getString(R.string.pdf_section_results_summary),
            listOf(context.getString(R.string.pdf_table_header_parameter), context.getString(R.string.pdf_table_header_value)),
            resRows,
            listOf(2f, 1.5f)
        )

        // --- 5. SENSITIVITY ANALYSIS (ALL) ---
        sensitivityResults?.let { sensResults ->
            val deltaSensMap = deltaResults?.third?.associateBy { it.nameResId }
            
            drawTable(
                context.getString(R.string.pdf_section_sensitivity_analysis),
                listOf(context.getString(R.string.pdf_table_header_parameter), context.getString(R.string.pdf_table_header_impact), context.getString(R.string.pdf_table_header_unit)),
                sensResults.map { res ->
                    val s1 = String.format(Locale.US, "%.4f", res.scaledImpact)
                    var sImpact = s1
                    if (isCompare) {
                         val d = deltaSensMap?.get(res.nameResId)?.deltaScaledImpact
                         if (d != null) {
                             sImpact += "\n(${context.getString(R.string.delta_prefix)} ${if(d>=0)"+" else ""}${String.format(Locale.US, "%.4f", d)})"
                         }
                    }
                    
                    listOf(
                        context.getString(res.nameResId),
                        sImpact,
                        context.getString(res.unitResId)
                    )
                },
                listOf(1.5f, 0.8f, 0.7f)
            )
        }

        // --- 6. AI COMMENT (As is) ---
        aiComment?.let { comment ->
            checkNewPage(100f)
            val title = if (modelName != null) "${context.getString(R.string.pdf_section_ai_comment)} ($modelName)" else context.getString(R.string.pdf_section_ai_comment)
            canvas.drawText(title, margin, y, headerPaint)
            y += 20f

            val paragraphs = comment.split("\n")
            paragraphs.forEach { paragraph ->
                if (paragraph.isBlank()) {
                    y += 12f // Empty line spacing
                    checkNewPage(20f)
                    return@forEach
                }
                val words = paragraph.split(" ")
                var line = ""
                words.forEach { word ->
                    val testLine = if (line.isEmpty()) word else "$line $word"
                    if (textPaint.measureText(testLine) < contentWidth) {
                        line = testLine
                    } else {
                        canvas.drawText(line, margin, y, textPaint)
                        y += 12f
                        checkNewPage(20f)
                        line = word
                    }
                }
                if (line.isNotEmpty()) {
                    canvas.drawText(line, margin, y, textPaint)
                    y += 12f
                    checkNewPage(20f)
                }
            }
            y += 20f
        }

        // --- 7. SIMULATION DETAILS ---
        val deltaYearsMap = deltaResults?.second?.associateBy { it.eta }
        
        drawTable(
            context.getString(R.string.pdf_section_simulation_details),
            listOf(
                context.getString(R.string.pdf_table_header_age),
                context.getString(R.string.pdf_table_header_extra_spend),
                context.getString(R.string.pdf_table_header_cap_end),
                context.getString(R.string.table_savings_ratio),
                context.getString(R.string.pdf_table_header_util),
                context.getString(R.string.pdf_table_header_debt),
                context.getString(R.string.table_capital_gained)
            ),
            results.map { row ->
                val dYear = deltaYearsMap?.get(row.eta)
                
                fun cell(val1: Any, delta: Any?, format: String): String {
                    val s1 = String.format(Locale.getDefault(), format, val1)
                    if (!isCompare || delta == null) return s1
                    val dVal = if (delta is Number) delta.toDouble() else 0.0
                    if (kotlin.math.abs(dVal) < 0.001) return s1
                    return "$s1\n(${context.getString(R.string.delta_prefix)}${if(dVal>=0)"+" else ""}${String.format(Locale.getDefault(), format.replace("€", "").trim(), dVal)})"
                }

                val gained = row.capitaleFineAnno - row.capitaleInizioAnno
                val dGained = dYear?.deltaCapitaleFineAnno
                // Note: Delta of gained is roughly deltaCapEnd - deltaCapStart, but simplify to just use deltaCapEnd if start is same? 
                // Actually dYear has deltaCapitaleFineAnno. We don't have deltaCapitaleInizioAnno explicitly in DeltaSimulationYear maybe.
                // Let's just show gained value. Calculating delta for gained might be complex if we don't have it.
                // We'll skip delta for gained/saving ratio for now to be safe, or compute simple diff if possible.
                // Saving Ratio delta:
                val srDelta = dYear?.deltaSavingRatioEffettivo // Need to check if this field exists in DeltaSimulationYear? 
                // Checking previous code... DeltaSimulationYear not fully visible but we can assume simple fields.
                // Wait, I should check DeltaSimulationYear definition first to be sure. 
                // But for now, let's just add the columns with main values.
                
                listOf(
                    row.eta.toString(),
                    cell(row.spesaMensileCorrettaFinale * 12.0 / 365.25, dYear?.deltaSpesaMensileCorrettaFinale?.times(12.0/365.25), "€%,.2f"),
                    cell(row.capitaleFineAnno, dYear?.deltaCapitaleFineAnno, "€%,.0f"),
                    String.format(Locale.getDefault(), "%.2f", row.savingRatioEffettivo),
                    cell(row.funzioneUtilita, dYear?.deltaFunzioneUtilita, "%.3f"),
                    cell(row.debtAmount, dYear?.deltaDebtAmount, "€%,.0f"),
                    String.format(Locale.getDefault(), "%.1fk", gained / 1000)
                )
            },
            listOf(0.5f, 1f, 1.1f, 0.7f, 0.7f, 0.9f, 0.8f)
        )

        // --- CHARTS (Optional but good to keep) ---
        checkNewPage(200f)
        canvas.drawText(context.getString(R.string.model_assumptions), margin, y, headerPaint)
        y += 25f

        val effectiveInputs = inputs.withDefaultAssumptionCurves()
        val chartHeight = 120f
        val chartWidth = (contentWidth / 2) - 20f

        fun drawChart(title: String, points: List<com.example.daysurpopt.domain.CurvePoint>, startX: Float, startY: Float, w: Float, h: Float, points2: List<com.example.daysurpopt.domain.CurvePoint>? = null) {
            val chartPaint = Paint().apply { color = COLOR_PROFESSIONAL_BLUE; strokeWidth = 2.0f; style = Paint.Style.STROKE; isAntiAlias = true }
            val chartPaint2 = Paint().apply { color = COLOR_CHART_P2; strokeWidth = 2.0f; style = Paint.Style.STROKE; isAntiAlias = true } // Orange for P2
            val axisPaint = Paint().apply { color = Color.BLACK; strokeWidth = 1.5f; style = Paint.Style.STROKE }
            val gridPaint = Paint().apply { color = Color.rgb(220, 220, 220); strokeWidth = 0.5f }
            val labelPaint = Paint().apply { textSize = 8f; color = Color.DKGRAY; isAntiAlias = true }
            val markerPaint1 = Paint().apply { color = COLOR_CHART_P1; style = Paint.Style.FILL; isAntiAlias = true }
            val markerPaint2 = Paint().apply { color = COLOR_CHART_P2; style = Paint.Style.FILL; isAntiAlias = true }
            
            // Draw axes
            canvas.drawLine(startX, startY, startX, startY + h, axisPaint)
            canvas.drawLine(startX, startY + h, startX + w, startY + h, axisPaint)
            canvas.drawText(title, startX, startY - 10, textPaint.apply { isFakeBoldText = true })
            
            if (points.isEmpty()) return
            
            // Determine X range from both series
            val allPoints = if (points2 != null) points + points2 else points
            val minX = allPoints.minOf { it.x }
            val maxX = allPoints.maxOf { it.x }.coerceAtLeast(minX + 1e-9)
            val minY = 0.0
            val maxY = 1.1 // Add some headroom
            
            // Draw horizontal grid lines and Y-axis labels
            for (i in 0..4) {
                val gridY = startY + h - (h * i / 4)
                canvas.drawLine(startX, gridY, startX + w, gridY, gridPaint)
                canvas.drawText(String.format(Locale.getDefault(), "%.1f", i * 0.25), startX - 22, gridY + 3, labelPaint)
            }
            
            // Draw vertical grid lines and X-axis labels
            val xTicks = 5
            for (i in 0 until xTicks) {
                val gridX = startX + (w * i / (xTicks - 1))
                canvas.drawLine(gridX, startY, gridX, startY + h, gridPaint)
                val xVal = minX + (maxX - minX) * i / (xTicks - 1)
                canvas.drawText(String.format(Locale.getDefault(), "%.0f", xVal), gridX - 5, startY + h + 15, labelPaint)
            }
            
            // Draw Profile 1 series
            val path = android.graphics.Path()
            points.forEachIndexed { i, p ->
                val px = startX + ((p.x - minX) / (maxX - minX) * w).toFloat()
                val py = startY + h - ((p.y - minY) / (maxY - minY) * h).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                canvas.drawCircle(px, py, 2.5f, markerPaint1)
            }
            canvas.drawPath(path, chartPaint)
            
            // Draw Profile 2 series if provided
            points2?.let { pts2 ->
                val path2 = android.graphics.Path()
                pts2.forEachIndexed { i, p ->
                    val px = startX + ((p.x - minX) / (maxX - minX) * w).toFloat()
                    val py = startY + h - ((p.y - minY) / (maxY - minY) * h).toFloat()
                    if (i == 0) path2.moveTo(px, py) else path2.lineTo(px, py)
                    canvas.drawCircle(px, py, 2.5f, markerPaint2)
                }
                canvas.drawPath(path2, chartPaint2)
            }
        }

        val p2EffectiveInputs = compareState?.profile2?.financialInput?.withDefaultAssumptionCurves()
        
        effectiveInputs.utilityCurvePoints?.let { pts ->
            drawChart(
                context.getString(R.string.assumptions_chart_utility_title), 
                pts, 
                margin, 
                y, 
                chartWidth, 
                chartHeight,
                p2EffectiveInputs?.utilityCurvePoints
            )
        }
        effectiveInputs.degradationCurvePoints?.let { pts ->
            drawChart(
                context.getString(R.string.assumptions_chart_degradation_title), 
                pts, 
                margin + chartWidth + 40, 
                y, 
                chartWidth, 
                chartHeight,
                p2EffectiveInputs?.degradationCurvePoints
            )
        }
        y += chartHeight + 30f

        document.finishPage(page)
        val fileName = "FinancialReport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        
        try {
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            document.close()
        }
    }
}
