/* ============================================================================
   BUSINESS INTELLIGENCE
   ============================================================================ */
let biOverview = null;

function biError(error) {
  return `<div class="empty-state"><strong>Unable to load business intelligence.</strong><p class="text-muted">${error.message || "Please try again."}</p></div>`;
}
function biEmpty(message = "No business intelligence data is available yet.") {
  return `<div class="empty-state"><strong>${message}</strong><p class="text-muted">Insights will appear here when live data is generated.</p></div>`;
}
function forecastRows(data) {
  return (data || []).map(f => ({ label: f.periodLabel || f.label, value: Number(f.forecastedValue ?? f.value ?? 0) }));
}
function renderBI() {
  $("#content").innerHTML = `<div class="page-head"><div><div class="page-eyebrow">AI-powered</div><h1 class="page-title">Business Intelligence</h1><p class="page-sub">Automated insights, forecasting, and recommendations generated from your live business data.</p></div></div><div id="bi-body"><div class="loading-state">Loading business intelligence…</div></div>`;
  api.bi.overview().then(data => { biOverview = data || {}; drawBiBody(); }).catch(error => { $("#bi-body").innerHTML = biError(error); });
}
function drawBiBody() {
  const body = $("#bi-body"), allInsights = biOverview.insights || [], insights = allInsights.filter(i => i.type !== "RECOMMENDATION");
  const recommendations = biOverview.recommendations || [], forecast = forecastRows(biOverview.forecast);
  body.innerHTML = `<div class="grid grid-2" id="bi-insights"></div><div class="two-col mt-24"><div class="card chart-wrap"><div class="card-head"><h3>Revenue Forecast — next 6 weeks</h3><span class="card-tag">Live forecast</span></div><div id="forecast-chart">${forecast.length ? lineChartSVG(forecast, { height: 230 }) : biEmpty("No forecast is available.")}</div><a class="btn btn-sm mt-8" href="forecasts.html">View forecast detail</a></div><div class="card"><div class="card-head"><h3>Recommendations</h3></div><div class="activity-list" id="bi-recommendations"></div></div></div><div class="card mt-24"><div class="card-head"><h3>Performance Insights</h3></div>${insights.length ? `<div class="insight-card"><div class="insight-icon">◈</div><p><strong>${insights[0].title}</strong>${insights[0].summary || insights[0].detail || ""}</p></div>` : biEmpty()}</div>`;
  drawBiInsights(insights); drawBiRecommendations(recommendations);
}
function drawBiInsights(insights = []) {
  const target = $("#bi-insights"); if (!target) return;
  target.innerHTML = insights.length ? insights.slice(0, 4).map(i => `<a class="insight-card insight-clickable" href="trends.html?id=${encodeURIComponent(i.id)}" style="text-decoration:none;color:inherit;"><div class="insight-icon">◈</div><p><strong>${i.title}</strong>${i.summary || i.detail || ""}</p></a>`).join("") : biEmpty();
}
function drawBiRecommendations(recommendations = []) {
  const target = $("#bi-recommendations"); if (!target) return;
  target.innerHTML = recommendations.length ? recommendations.map(r => `<div class="activity-row"><div class="activity-dot"></div><div style="flex:1;"><p>${r.summary || r.title || r.detail || ""}</p></div><button class="btn btn-sm btn-ghost" data-dismiss-recommendation="${r.id}">Dismiss</button></div>`).join("") : `<p class="text-muted" style="font-size:13px;">No open recommendations — all caught up.</p>`;
  $$("[data-dismiss-recommendation]").forEach(button => button.addEventListener("click", () => dismissRecommendation(button.dataset.dismissRecommendation)));
}
async function dismissRecommendation(id) {
  try {
    await api.bi.dismissRecommendation(id);
    biOverview.recommendations = (biOverview.recommendations || []).filter(r => String(r.id) !== String(id));
    drawBiRecommendations(biOverview.recommendations);
    toast("Recommendation dismissed.", "success");
  } catch (error) { toast(error.message || "Unable to dismiss recommendation.", "error"); }
}

function renderTrendDetailPage(id) {
  openPage({ title: "Business insight", eyebrow: "AI-powered", backHref: "index.html", hideFooter: true, bodyHtml: `<div id="bi-detail"><div class="loading-state">Loading insight…</div></div>` });
  api.bi.overview().then(data => {
    const i = (data.insights || []).find(item => String(item.id) === String(id)), target = $("#bi-detail");
    if (!target) return;
    target.innerHTML = i ? `<div style="font-size:30px;">◈</div><h2>${i.title}</h2><p style="font-size:13.5px;color:var(--navy-700);line-height:1.6;margin-top:10px;">${i.detail || i.summary || "No further detail is available."}</p>` : biEmpty("Insight not found.");
  }).catch(error => { const target = $("#bi-detail"); if (target) target.innerHTML = biError(error); });
}
function renderForecastDetailPage() {
  openPage({ title: "Revenue forecast — next 6 weeks", eyebrow: "AI-powered", backHref: "index.html", hideFooter: true, wide: true, bodyHtml: `<div id="bi-forecast-detail"><div class="loading-state">Loading forecast…</div></div>` });
  api.bi.overview().then(data => {
    const forecast = forecastRows(data.forecast), target = $("#bi-forecast-detail");
    if (!target) return;
    target.innerHTML = forecast.length ? `${lineChartSVG(forecast, { height: 220 })}<div class="table-wrap mt-16" style="border:none;"><table class="data-table"><thead><tr><th>Week</th><th>Forecasted Revenue</th></tr></thead><tbody>${forecast.map(f => `<tr><td>${f.label}</td><td class="cell-strong">${money(f.value)}</td></tr>`).join("")}</tbody></table></div>` : biEmpty("No forecast is available.");
  }).catch(error => { const target = $("#bi-forecast-detail"); if (target) target.innerHTML = biError(error); });
}
function initBiIndex() { renderBI(); }
function initBiTrend() { renderTrendDetailPage(getQueryParam("id")); }
function initBiForecast() { renderForecastDetailPage(); }