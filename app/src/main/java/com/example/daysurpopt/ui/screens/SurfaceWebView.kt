// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.example.daysurpopt.BuildConfig
import com.example.daysurpopt.utils.AppDebugLog
import com.example.daysurpopt.R
import com.example.daysurpopt.domain.CurvePoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SurfaceGrid(
    val x: List<Double>,
    val y: List<Double>,
    val z: List<List<Double?>>,
    val anchorColorScaleOnFeasible: Boolean = false
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlotlyWebView(
    modifier: Modifier,
    specJson: String,
    cameraView: String? = null,
    cameraTrigger: Int = 0,
    isPerspective: Boolean = true,
    onCurveChanged: ((curveId: String, points: List<CurvePoint>) -> Unit)? = null,
    onPointSelected: ((traceName: String, pointIndex: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val onCurveChangedState by rememberUpdatedState(onCurveChanged)
    val onPointSelectedState by rememberUpdatedState(onPointSelected)
    val gson = remember { Gson() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    class ChartsJsBridge {
        @JavascriptInterface
        fun log(message: String) {
            if (BuildConfig.DEBUG) {
                AppDebugLog.add("JS", message)
            }
        }
    }

    class CurveJsBridge {
        @JavascriptInterface
        fun curveChanged(curveId: String, pointsJson: String) {
            val callback = onCurveChangedState ?: return
            try {
                val type = object : TypeToken<List<CurvePoint>>() {}.type
                val points: List<CurvePoint> = gson.fromJson(pointsJson, type)
                mainHandler.post { callback(curveId, points) }
            } catch (_: Exception) {
            }
        }
    }

    class PointJsBridge {
        @JavascriptInterface
        fun pointSelected(traceName: String, pointIndex: Int) {
            val callback = onPointSelectedState ?: return
            mainHandler.post { callback(traceName, pointIndex) }
        }
    }

    val fallbackText = stringResource(R.string.charts_plotly_failed)
    val htmlStrings = PlotlyHtmlProvider.LocalizedStrings(
        webviewActive = stringResource(R.string.charts_webview_active),
        receivedData = stringResource(R.string.charts_received_data),
        fallback2d = stringResource(R.string.charts_fallback_2d)
    )
    val html = remember(fallbackText, htmlStrings) { PlotlyHtmlProvider.getHtml(fallbackText, htmlStrings) }

    val currentSpecJson by rememberUpdatedState(specJson)
    var lastRenderedSpec by remember { mutableStateOf<String?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(isPageReady) {
        if (isPageReady) {
            val proj = if (isPerspective) "'perspective'" else "'orthographic'"
            // Use setProjection to just set the type without touching position
            webViewRef?.evaluateJavascript("if(window.setProjection) window.setProjection($proj);", null)
            
            if (cameraView != null) {
                 webViewRef?.evaluateJavascript("if(window.setCameraView) window.setCameraView('$cameraView');", null)
            }
        }
    }

    LaunchedEffect(cameraTrigger) {
        if (isPageReady && cameraView != null) {
            webViewRef?.evaluateJavascript("if(window.setCameraView) window.setCameraView('$cameraView');", null)
        }
    }

    LaunchedEffect(isPerspective) {
        if (isPageReady) {
            val proj = if (isPerspective) "'perspective'" else "'orthographic'"
            webViewRef?.evaluateJavascript("if(window.setProjection) window.setProjection($proj);", null)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            val wv = WebView(context).apply {
                if (BuildConfig.DEBUG) {
                    AppDebugLog.add("WebView", "Create WebView")
                }

                setBackgroundColor(android.graphics.Color.BLACK)

                setOnTouchListener { v, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    false
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                addJavascriptInterface(ChartsJsBridge(), "AndroidLog")
                addJavascriptInterface(CurveJsBridge(), "AndroidCurve")
                addJavascriptInterface(PointJsBridge(), "AndroidPoint")

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val msg = "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()} @ ${consoleMessage?.sourceId()}"
                        if (BuildConfig.DEBUG) {
                            Log.d("WebViewConsole", msg)
                            AppDebugLog.add("Console", msg)
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (BuildConfig.DEBUG) {
                            AppDebugLog.add("WebView", "onPageStarted url=$url")
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (BuildConfig.DEBUG) {
                            AppDebugLog.add("WebView", "onPageFinished url=$url")
                        }
                        isPageReady = true
                        val spec = currentSpecJson
                        if (lastRenderedSpec != spec) {
                            lastRenderedSpec = spec
                            view?.evaluateJavascript("if(window.renderPlot) window.renderPlot($spec, true);", null)
                        }
                    }
                }

                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                if (BuildConfig.DEBUG) {
                    AppDebugLog.add("WebView", "loadDataWithBaseURL done")
                }
            }
            webViewRef = wv
            wv
        },
        update = { webView ->
            if (!isPageReady) return@AndroidView
            val spec = currentSpecJson
            if (lastRenderedSpec != spec) {
                lastRenderedSpec = spec
                webView.evaluateJavascript("if(window.renderPlot) window.renderPlot($spec, false);", null)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SurfaceWebView(
    modifier: Modifier,
    grid: SurfaceGrid,
    axisXTitle: String,
    axisYTitle: String,
    axisZTitle: String,
    useHeatmap: Boolean = false,
    cameraView: String? = null,
    cameraTrigger: Int = 0,
    showContours: Boolean = true,
    isPerspective: Boolean = true,
    localized: PlotlySpecBuilder.LocalizedStrings? = null,
    extraMarkers: List<Map<String, Any>> = emptyList()
) {
    val context = LocalContext.current

    // Log data stats for debugging
    LaunchedEffect(grid) {
        if (BuildConfig.DEBUG) {
            val allZ = grid.z.flatten().filterNotNull().filter { !it.isNaN() }
            val minZ = allZ.minOrNull()
            val maxZ = allZ.maxOrNull()
            AppDebugLog.add("Charts", "Data Stats: MinZ=$minZ MaxZ=$maxZ Count=${allZ.size}")
        }
    }

    val fallbackText = stringResource(R.string.charts_plotly_failed)
    val htmlStrings = PlotlyHtmlProvider.LocalizedStrings(
        webviewActive = stringResource(R.string.charts_webview_active),
        receivedData = stringResource(R.string.charts_received_data),
        fallback2d = stringResource(R.string.charts_fallback_2d)
    )
    val html = remember(fallbackText, htmlStrings) { PlotlyHtmlProvider.getHtml(fallbackText, htmlStrings) }

    val specStrings = localized ?: PlotlySpecBuilder.LocalizedStrings(
        objective = stringResource(R.string.charts_objective),
        heatmapCpu = stringResource(R.string.charts_2d_heatmap_cpu)
    )
    val specJson = remember(grid, axisXTitle, axisYTitle, axisZTitle, useHeatmap, specStrings, showContours, extraMarkers) {
        // Note: isPerspective is handled via JS update to avoid full re-render and camera reset
        PlotlySpecBuilder.buildJson(
            grid = grid,
            axisXTitle = axisXTitle,
            axisYTitle = axisYTitle,
            axisZTitle = axisZTitle,
            useHeatmap = useHeatmap,
            localized = specStrings,
            showContours = showContours,
            isPerspective = true, // Always build with default, JS handles toggle
            extraMarkers = extraMarkers,
            disableMinMax = extraMarkers.isNotEmpty()
        )
    }

    PlotlyWebView(
        modifier = modifier,
        specJson = specJson,
        cameraView = cameraView,
        cameraTrigger = cameraTrigger,
        isPerspective = isPerspective,
        onCurveChanged = null
    )
}
