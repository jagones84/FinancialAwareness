// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

object PlotlyHtmlProvider {
    data class LocalizedStrings(
        val webviewActive: String,
        val receivedData: String,
        val fallback2d: String
    )

    fun getHtml(fallbackText: String, localized: LocalizedStrings): String {
        return """
            <!doctype html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <script src="plotly-2.30.0.min.js"></script>
                <style>
                  html, body { margin:0; padding:0; background:#000000; width:100%; height:100%; overscroll-behavior:none; }
                  #plot { width:100vw; height:100vh; position:absolute; top:0; left:0; touch-action:none; }
                  #status { 
                      position:absolute; top:0; left:0; width:100%; z-index:9999; 
                      color:white; background:rgba(0,0,0,0.5); font-family:monospace; font-size:10px;
                      text-align:center; padding:2px;
                  }
                </style>
              </head>
              <body>
                <div id="status">${localized.webviewActive}</div>
                <div id="plot"></div>
                <script>
                  (function() {
                    function statusText(text, color) {
                      var el = document.getElementById('status');
                      if(el) {
                          el.innerText = text;
                          if(color) el.style.background = color;
                      }
                      try { if (window.AndroidLog && window.AndroidLog.log) window.AndroidLog.log(text); } catch (e) {}
                    }

                    setTimeout(function() {
                        if (document.getElementById('status').innerText.indexOf('Waiting') !== -1) {
                             statusText('Timeout: JS did not start or Plotly missing?', 'black');
                        }
                    }, 5000);

                    var __webglOk = (function() {
                      try {
                        var canvas = document.createElement('canvas');
                        if (!window.WebGLRenderingContext) return false;
                        var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
                        return !!gl;
                      } catch (e) {
                        return false;
                      }
                    })();

                    function plotSize() {
                      var plot = document.getElementById('plot');
                      return { w: plot.clientWidth || 0, h: plot.clientHeight || 0 };
                    }

                    window.onerror = function(msg, url, line) {
                      var base = document.getElementById('status').innerText || '';
                      statusText(base + '\nError: ' + msg + '\nLine: ' + line);
                    };

                    window.renderPlot = function(spec, isInitial) {
                      window.__pendingSpec = spec;
                      window.__renderTry = 0;
                      statusText("${localized.receivedData}", "orange");
                      tryRender(isInitial);
                    };

                    window.setProjection = function(projectionType) {
                      if (!window.Plotly || !document.getElementById('plot')) return;
                      var projType = projectionType || 'perspective';
                      Plotly.relayout('plot', { 'scene.camera.projection.type': projType });
                    };

                    window.setCameraView = function(viewType) {
                      if (!window.Plotly || !document.getElementById('plot')) return;
                      
                      var camera;
                      switch(viewType) {
                        case 'XY': camera = { eye: {x: 0, y: 0, z: 2.5}, up: {x: 0, y: 1, z: 0} }; break;
                        case 'XZ': camera = { eye: {x: 0, y: 2.5, z: 0}, up: {x: 0, y: 0, z: 1} }; break;
                        case 'YZ': camera = { eye: {x: 2.5, y: 0, z: 0}, up: {x: 0, y: 0, z: 1} }; break;
                        default: camera = { eye: {x: 1.8, y: 1.8, z: 1.2}, up: {x: 0, y: 0, z: 1} }; break;
                      }
                      
                      Plotly.relayout('plot', { 
                          'scene.camera.eye': camera.eye,
                          'scene.camera.up': camera.up
                      });
                    };

                    function heatmapFallbackSpec(spec) {
                      try {
                        var d0 = spec && spec.data && spec.data[0] ? spec.data[0] : null;
                        var x = d0 && d0.x ? d0.x : [];
                        var y = d0 && d0.y ? d0.y : [];
                        var z = d0 && d0.z ? d0.z : [];

                        var baseLayout = spec && spec.layout ? spec.layout : {};
                        var scene = baseLayout.scene || {};
                        
                        var layout = {
                          paper_bgcolor: baseLayout.paper_bgcolor || "#000000",
                          plot_bgcolor: baseLayout.plot_bgcolor || "#000000",
                          uirevision: baseLayout.uirevision || "constant",
                          font: { color: "#FFFFFF" },
                          xaxis: { title: (scene.xaxis && scene.xaxis.title) || "X", color: "#FFFFFF" },
                          yaxis: { title: (scene.yaxis && scene.yaxis.title) || "Y", color: "#FFFFFF" },
                          title: (baseLayout.title && baseLayout.title.text ? baseLayout.title.text : "${localized.fallback2d}") + " (Z: " + ((scene.zaxis && scene.zaxis.title) || "fObj") + ")"
                        };

                        var trace = {
                          type: 'heatmap',
                          x: x,
                          y: y,
                          z: z,
                          colorscale: 'Viridis',
                          showscale: true
                        };

                        return { data: [trace], layout: layout };
                      } catch(e) {
                        return null;
                      }
                    }

                    function tryRender(isInitial) {
                       if (!window.Plotly) {
                          if (window.__renderTry++ < 20) {
                              setTimeout(function() { tryRender(isInitial); }, 200);
                          } else {
                              statusText('Plotly lib not loaded!', 'red');
                          }
                          return;
                       }
                       
                       var spec = window.__pendingSpec;
                       var s = plotSize();

                       if (!isInitial) {
                         try {
                           if (spec && spec.layout) {
                               if (spec.layout.scene && spec.layout.scene.camera) {
                                   delete spec.layout.scene.camera;
                               }
                               if (spec.layout.dragmode) {
                                   delete spec.layout.dragmode;
                               }
                           }
                         } catch(e) {}
                       }
                       
                       if (!__webglOk && spec.data[0].type === 'surface') {
                            statusText("WebGL missing/disabled. Using Heatmap Fallback...", "orange");
                            var fallback = heatmapFallbackSpec(spec);
                            if(fallback) spec = fallback;
                            else statusText("WebGL missing & Fallback failed", "red");
                       }

                       function getMeta(spec) {
                         try { return (spec && spec.layout && spec.layout.meta) ? spec.layout.meta : {}; } catch(e) { return {}; }
                       }

                       function setupDraggablePoints(spec) {
                         var gd = document.getElementById('plot');
                         if (!gd) return;

                         if (window.__dragCleanup) {
                           try { window.__dragCleanup(); } catch(e) {}
                           window.__dragCleanup = null;
                         }

                         var meta = getMeta(spec);
                         if (!meta || !meta.draggablePoints) return;

                         var curveId = meta.curveId || 'curve';
                         var traceIndex = (meta.traceIndex !== undefined) ? meta.traceIndex : 0;
                         var lockXOrder = !!meta.lockXOrder;
                         var pickRadiusPx = (meta.pickRadiusPx !== undefined) ? meta.pickRadiusPx : 14;

                         var xMin = (meta.xMin !== undefined) ? meta.xMin : null;
                         var xMax = (meta.xMax !== undefined) ? meta.xMax : null;
                         var yMin = (meta.yMin !== undefined) ? meta.yMin : null;
                         var yMax = (meta.yMax !== undefined) ? meta.yMax : null;

                         var dragging = false;
                         var dragIndex = -1;
                         var lastMoveTs = 0;

                         function getArrays() {
                           var t = (gd.data && gd.data[traceIndex]) ? gd.data[traceIndex] : (spec.data ? spec.data[traceIndex] : null);
                           var xs = (t && t.x) ? t.x.slice() : [];
                           var ys = (t && t.y) ? t.y.slice() : [];
                           return { xs: xs, ys: ys };
                         }

                         function clamp(v, lo, hi) {
                           if (lo !== null && v < lo) return lo;
                           if (hi !== null && v > hi) return hi;
                           return v;
                         }

                         function pixelToData(px, py) {
                           var xa = gd._fullLayout && gd._fullLayout.xaxis ? gd._fullLayout.xaxis : null;
                           var ya = gd._fullLayout && gd._fullLayout.yaxis ? gd._fullLayout.yaxis : null;
                           if (!xa || !ya) return null;
                           var xPlot = px - xa._offset;
                           var yPlot = py - ya._offset;
                           var xVal = xa.p2c ? xa.p2c(xPlot) : (xa.p2l ? xa.p2l(xPlot) : null);
                           var yVal = ya.p2c ? ya.p2c(yPlot) : (ya.p2l ? ya.p2l(yPlot) : null);
                           if (xVal === null || yVal === null) return null;
                           return { x: xVal, y: yVal };
                         }

                         function dataToPixel(x, y) {
                           var xa = gd._fullLayout && gd._fullLayout.xaxis ? gd._fullLayout.xaxis : null;
                           var ya = gd._fullLayout && gd._fullLayout.yaxis ? gd._fullLayout.yaxis : null;
                           if (!xa || !ya) return null;
                           var xPlot = xa.c2p ? xa.c2p(x) : (xa.l2p ? xa.l2p(x) : null);
                           var yPlot = ya.c2p ? ya.c2p(y) : (ya.l2p ? ya.l2p(y) : null);
                           if (xPlot === null || yPlot === null) return null;
                           return { px: xPlot + xa._offset, py: yPlot + ya._offset };
                         }

                         function findNearestPoint(px, py) {
                           var arr = getArrays();
                           var xs = arr.xs, ys = arr.ys;
                           var best = { idx: -1, d2: Infinity };
                           for (var i = 0; i < xs.length; i++) {
                             var p = dataToPixel(xs[i], ys[i]);
                             if (!p) continue;
                             var dx = p.px - px;
                             var dy = p.py - py;
                             var d2 = dx*dx + dy*dy;
                             if (d2 < best.d2) best = { idx: i, d2: d2 };
                           }
                           if (best.idx >= 0 && best.d2 <= pickRadiusPx*pickRadiusPx) return best.idx;
                           return -1;
                         }

                         function emitCurve(xs, ys) {
                           try {
                             var pts = [];
                             for (var i = 0; i < xs.length; i++) pts.push({ x: xs[i], y: ys[i] });
                             if (window.AndroidCurve && window.AndroidCurve.curveChanged) {
                               window.AndroidCurve.curveChanged(curveId, JSON.stringify(pts));
                             }
                           } catch(e) {}
                         }

                         function eventXY(ev) {
                           try {
                             if (ev.touches && ev.touches[0]) return { x: ev.touches[0].clientX, y: ev.touches[0].clientY };
                             if (ev.changedTouches && ev.changedTouches[0]) return { x: ev.changedTouches[0].clientX, y: ev.changedTouches[0].clientY };
                           } catch(e) {}
                           return { x: ev.clientX, y: ev.clientY };
                         }

                         function onDown(ev) {
                           var rect = gd.getBoundingClientRect();
                           var xy = eventXY(ev);
                           var px = xy.x - rect.left;
                           var py = xy.y - rect.top;
                           var idx = findNearestPoint(px, py);
                           if (idx < 0) return;
                           dragging = true;
                           dragIndex = idx;
                           try { ev.preventDefault(); ev.stopPropagation(); } catch(e) {}
                         }

                         function onMove(ev) {
                           if (!dragging || dragIndex < 0) return;
                           var now = Date.now();
                           if (now - lastMoveTs < 16) return;
                           lastMoveTs = now;

                           var rect = gd.getBoundingClientRect();
                           var xy = eventXY(ev);
                           var px = xy.x - rect.left;
                           var py = xy.y - rect.top;
                           var v = pixelToData(px, py);
                           if (!v) return;

                           var arr = getArrays();
                           var xs = arr.xs, ys = arr.ys;

                           var x = v.x;
                           var y = v.y;

                           if (lockXOrder) {
                             var prevX = (dragIndex > 0) ? xs[dragIndex - 1] : null;
                             var nextX = (dragIndex < xs.length - 1) ? xs[dragIndex + 1] : null;
                             var eps = 1e-6;
                             var xLo = xMin;
                             var xHi = xMax;
                             if (prevX !== null) xLo = Math.max(xLo !== null ? xLo : prevX + eps, prevX + eps);
                             if (nextX !== null) xHi = Math.min(xHi !== null ? xHi : nextX - eps, nextX - eps);
                             x = clamp(x, xLo, xHi);
                           }

                           x = clamp(x, xMin, xMax);
                           y = clamp(y, yMin, yMax);

                           xs[dragIndex] = x;
                           ys[dragIndex] = y;

                           try {
                             Plotly.restyle(gd, { x: [xs], y: [ys] }, [traceIndex]);
                           } catch(e) {}

                           try { ev.preventDefault(); ev.stopPropagation(); } catch(e) {}
                         }

                         function onUp(ev) {
                           if (!dragging) return;
                           dragging = false;
                           var arr = getArrays();
                           emitCurve(arr.xs, arr.ys);
                           dragIndex = -1;
                           try { ev.preventDefault(); ev.stopPropagation(); } catch(e) {}
                         }

                         gd.addEventListener('pointerdown', onDown, { passive: false });
                         window.addEventListener('pointermove', onMove, { passive: false });
                         window.addEventListener('pointerup', onUp, { passive: false });
                         gd.addEventListener('mousedown', onDown, { passive: false });
                         window.addEventListener('mousemove', onMove, { passive: false });
                         window.addEventListener('mouseup', onUp, { passive: false });
                         gd.addEventListener('touchstart', onDown, { passive: false });
                         window.addEventListener('touchmove', onMove, { passive: false });
                         window.addEventListener('touchend', onUp, { passive: false });
                         window.__dragCleanup = function() {
                           try { gd.removeEventListener('pointerdown', onDown); } catch(e) {}
                           try { window.removeEventListener('pointermove', onMove); } catch(e) {}
                           try { window.removeEventListener('pointerup', onUp); } catch(e) {}
                           try { gd.removeEventListener('mousedown', onDown); } catch(e) {}
                           try { window.removeEventListener('mousemove', onMove); } catch(e) {}
                           try { window.removeEventListener('mouseup', onUp); } catch(e) {}
                           try { gd.removeEventListener('touchstart', onDown); } catch(e) {}
                           try { window.removeEventListener('touchmove', onMove); } catch(e) {}
                           try { window.removeEventListener('touchend', onUp); } catch(e) {}
                         };
                       }

                       function setupPointSelection(spec) {
                         var gd = document.getElementById('plot');
                         if (!gd) return;

                         if (window.__clickCleanup) {
                           try { window.__clickCleanup(); } catch(e) {}
                           window.__clickCleanup = null;
                         }

                         var meta = getMeta(spec);
                         if (!meta || !meta.pointSelection) return;

                         function onClick(ev) {
                           try {
                             var point = ev && ev.points && ev.points[0] ? ev.points[0] : null;
                             if (!point) return;
                             var traceName = (point.data && point.data.name) ? point.data.name : '';
                             var pointIndex = (point.pointIndex !== undefined) ? point.pointIndex : -1;
                             if (window.AndroidPoint && window.AndroidPoint.pointSelected) {
                               window.AndroidPoint.pointSelected(traceName, pointIndex);
                             }
                           } catch(e) {}
                         }

                         gd.on('plotly_click', onClick);
                         window.__clickCleanup = function() {
                           try { gd.removeAllListeners('plotly_click'); } catch(e) {}
                         };
                       }

                       try {
                         var meta = getMeta(spec);
                         var config = {
                             responsive: true,
                             scrollZoom: (meta.scrollZoom !== undefined) ? meta.scrollZoom : true,
                             displaylogo: false,
                             modeBarButtonsToRemove: ['select2d', 'lasso2d']
                         };
                         if (meta.displayModeBar === false) config.displayModeBar = false;
                         if (meta.doubleClick === false) config.doubleClick = false;
                         if (meta.staticPlot === true) config.staticPlot = true;

                         // Pin the plot to the CURRENT div size: the viewport height can be
                         // wrong while the WebView is still settling (e.g. inside a dialog),
                         // which clipped the bottom (x axis labels/title) with no re-render.
                         if (s.w > 0 && s.h > 0) {
                             if (!spec.layout) spec.layout = {};
                             spec.layout.width = s.w;
                             spec.layout.height = s.h;
                         }

                         var promise;
                         if (isInitial || config.staticPlot === true) {
                             promise = Plotly.newPlot('plot', spec.data, spec.layout, config);
                         } else {
                             promise = Plotly.react('plot', spec.data, spec.layout, config);
                         }

                         promise.then(function() {
                            statusText('[JS] OK ' + s.w + 'x' + s.h, 'green');
                            window.__renderDone = true;
                            window.__lastPlotW = s.w;
                            window.__lastPlotH = s.h;
                            setupDraggablePoints(spec);
                            setupPointSelection(spec);
                            setTimeout(function(){
                                var el = document.getElementById('status');
                                if(el && el.style.background === 'green') {
                                    el.style.display = 'none';
                                }
                            }, 2000);
                         }).catch(function(e) {
                            statusText('Plotly Error: ' + e, 'red');
                         });
                       } catch (e) {
                          statusText('Render Exception: ' + e, 'red');
                       }
                    }

                    // Re-render when the container settles or changes size (dialog layout,
                    // rotation): the first render can happen before the WebView is laid out.
                    if (window.ResizeObserver) {
                        var __plotRo = new ResizeObserver(function() {
                            var el = document.getElementById('plot');
                            if (!el || !window.__pendingSpec || !window.Plotly || !window.__renderDone) return;
                            var s = plotSize();
                            if (s.w < 10 || s.h < 10) return;
                            if (Math.abs(s.w - (window.__lastPlotW || 0)) > 2 || Math.abs(s.h - (window.__lastPlotH || 0)) > 2) {
                                window.__lastPlotW = s.w;
                                window.__lastPlotH = s.h;
                                tryRender(false);
                            }
                        });
                        __plotRo.observe(document.getElementById('plot'));
                    }
                  })();
                </script>
              </body>
            </html>
        """.trimIndent()
    }
}
