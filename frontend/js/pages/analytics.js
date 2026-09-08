/* ============================================================================
   ANALYTICS & REPORTING
   ============================================================================ */
let analyticsState = { reportType: "sales", dateRange: "30", marketplace: "All", category: "All", warehouseId: "" };
let analyticsReport = null;

function analyticsFilters(state = analyticsState) {
  return { type: state.reportType, range: state.dateRange, marketplace: state.marketplace,
    category: state.category, warehouseId: state.warehouseId || undefined };
}
function reportTitle(type) {
  return ({ sales: "Sales Report", revenue: "Revenue Report", product: "Product Report",
    inventory: "Inventory Report", marketplace: "Marketplace Report" })[type] || "Report";
}
function reportLabel(row) { return row.name || row.label || "Uncategorized"; }
function reportValue(row) { return Number(row.revenue ?? row.value ?? 0); }
function reportTrend(report) {
  return (report.trend || []).map(x => ({ label: x.label, value: reportValue(x) }));
}
function analyticsEmpty(message = "No data is available for these filters.") {
  return `<div class="empty-state"><strong>${message}</strong><p class="text-muted">Try changing the date range or filters.</p></div>`;
}
function analyticsError(error) {
  return `<div class="empty-state"><strong>Unable to load this report.</strong><p class="text-muted">${error.message || "Please try again."}</p></div>`;
}

function renderAnalytics() {
  $("#content").innerHTML = `
    <div class="page-head"><div><div class="page-eyebrow">Reports</div><h1 class="page-title">Analytics &amp; Reporting</h1>
      <p class="page-sub">Deep-dive reports across sales, revenue, products, inventory, and marketplaces.</p></div>
      <div class="page-actions"><button class="btn" id="export-csv-btn">⬇ Export CSV</button><button class="btn" id="export-pdf-btn">⬇ Export PDF</button></div></div>
    <div class="tabs">
      ${["sales", "revenue", "product", "inventory", "marketplace"].map(t => `<button class="tab-btn ${analyticsState.reportType === t ? "active" : ""}" data-report="${t}">${t[0].toUpperCase() + t.slice(1)}${t === "product" ? "s" : t === "marketplace" ? "s" : ""}</button>`).join("")}
    </div>
    <div class="card table-toolbar" style="margin-bottom:20px;"><div class="toolbar-filters">
      <select class="select-input" id="af-range"><option value="7">Last 7 days</option><option value="30">Last 30 days</option><option value="90">Last 90 days</option></select>
      <select class="select-input" id="af-marketplace"><option>All</option>${MARKETPLACES_LIST.map(m => `<option>${m}</option>`).join("")}</select>
      <select class="select-input" id="af-category"><option>All</option>${CATEGORIES.map(c => `<option>${c}</option>`).join("")}</select>
      <select class="select-input" id="af-warehouse"><option value="">All warehouses</option>${WAREHOUSES.map(w => `<option value="${w.id}">${w.name}</option>`).join("")}</select>
    </div><button class="btn btn-blue" id="af-generate">Generate report</button></div>
    <div id="report-body"><div class="loading-state">Loading report…</div></div>
    <p class="mt-16"><a class="link-action" id="report-detail-link" href="report-detail.html">Open this report as a shareable page →</a></p>`;
  $("#af-range").value = analyticsState.dateRange; $("#af-marketplace").value = analyticsState.marketplace;
  $("#af-category").value = analyticsState.category; $("#af-warehouse").value = analyticsState.warehouseId;
  $$("[data-report]").forEach(b => b.addEventListener("click", () => { analyticsState.reportType = b.dataset.report; loadAnalyticsReport(); }));
  ["range", "marketplace", "category", "warehouseId"].forEach(key => {
    const id = key === "range" ? "af-range" : key === "marketplace" ? "af-marketplace" : key === "category" ? "af-category" : "af-warehouse";
    $(`#${id}`).addEventListener("change", e => { analyticsState[key] = e.target.value; syncReportDetailLink(); });
  });
  $("#af-generate").addEventListener("click", loadAnalyticsReport);
  $("#export-csv-btn").addEventListener("click", () => exportAnalytics("csv"));
  $("#export-pdf-btn").addEventListener("click", () => exportAnalytics("pdf"));
  syncReportDetailLink(); loadAnalyticsReport();
}
function syncReportDetailLink() {
  const s = analyticsState;
  $("#report-detail-link").href = `report-detail.html?type=${s.reportType}&range=${s.dateRange}&marketplace=${encodeURIComponent(s.marketplace)}&category=${encodeURIComponent(s.category)}${s.warehouseId ? `&warehouseId=${encodeURIComponent(s.warehouseId)}` : ""}`;
}
async function loadAnalyticsReport() {
  const body = $("#report-body"); if (!body) return;
  body.innerHTML = `<div class="loading-state">Loading report…</div>`;
  try { analyticsReport = await api.analytics.report(analyticsFilters()); drawReportBody(analyticsReport); }
  catch (error) { analyticsReport = null; body.innerHTML = analyticsError(error); }
}
function drawReportBody(report) {
  const body = $("#report-body");
  if (!report || (!report.summary && !(report.categories || []).length && !(report.marketplaces || []).length && !(report.trend || []).length)) {
    body.innerHTML = analyticsEmpty(); return;
  }
  const categories = (report.categories || []).map(x => ({ label: reportLabel(x), value: reportValue(x) }));
  const marketplaces = (report.marketplaces || []).map(x => ({ label: reportLabel(x), value: reportValue(x) }));
  const trend = reportTrend(report), s = report.summary || {};
  body.innerHTML = `<div class="card mb-24"><div class="stats-grid">
    <div><span class="text-muted">Revenue</span><strong>${money(Number(s.revenue || 0))}</strong></div>
    <div><span class="text-muted">Orders</span><strong>${Number(s.orders || 0).toLocaleString()}</strong></div>
    <div><span class="text-muted">Units</span><strong>${Number(s.units || 0).toLocaleString()}</strong></div>
    <div><span class="text-muted">Average order value</span><strong>${money(Number(s.averageOrderValue || 0))}</strong></div>
  </div></div>
  <div class="two-col"><div class="card chart-wrap"><div class="card-head"><h3>Revenue by Category</h3></div>${categories.length ? barChartSVG(categories, { height: 230 }) : analyticsEmpty()}</div>
  <div class="card chart-wrap"><div class="card-head"><h3>Revenue by Marketplace</h3></div>${marketplaces.length ? barChartSVG(marketplaces, { height: 230 }) : analyticsEmpty()}</div></div>
  <div class="card mt-24"><div class="card-head"><h3>Sales Trend</h3></div>${trend.length ? lineChartSVG(trend, { height: 220 }) : analyticsEmpty()}</div>
  <div class="card mt-24"><div class="card-head"><h3>${reportTitle(analyticsState.reportType)}</h3></div><p class="text-muted" style="font-size:13px;line-height:1.6;">${trend.length ? `This report contains ${trend.length} periods of live data for the selected filters.` : "No trend data was returned for the selected filters."}</p></div>`;
  window._lastReportData = report;
}
async function exportAnalytics(fmt) {
  if (!analyticsReport) { toast("Generate a report before exporting.", "error"); return; }
  try {
    if (fmt === "csv") downloadFile(`${analyticsState.reportType}-report.csv`, await api.analytics.reportCsv(analyticsFilters()));
    else {
      const lines = [`Generated: ${fmtDateTime(new Date())}`, `Filters: range=${analyticsState.dateRange}d, marketplace=${analyticsState.marketplace}, category=${analyticsState.category}`, "", `Revenue: ${money(Number((analyticsReport.summary || {}).revenue || 0))}`];
      downloadFile(`${analyticsState.reportType}-report.pdf`, buildSimplePDF(reportTitle(analyticsState.reportType), lines), "application/pdf");
    }
    toast(`Report exported as ${fmt.toUpperCase()}.`, "success");
  } catch (error) { toast(error.message || "Export failed.", "error"); }
}

function renderReportDetailPage() {
  const state = { reportType: getQueryParam("type") || "sales", dateRange: getQueryParam("range") || "30",
    marketplace: getQueryParam("marketplace") || "All", category: getQueryParam("category") || "All", warehouseId: getQueryParam("warehouseId") || "" };
  openPage({ title: reportTitle(state.reportType), eyebrow: "Reports", subtitle: `Range: last ${state.dateRange} days · Marketplace: ${state.marketplace} · Category: ${state.category}`,
    backHref: "reports.html", backLabel: "← Back to Analytics & Reporting", hideFooter: true, wide: true,
    bodyHtml: `<div id="report-detail-body"><div class="loading-state">Loading report…</div></div>` });
  api.analytics.report(analyticsFilters(state)).then(report => {
    const target = $("#report-detail-body"); if (!target) return;
    if (!report || (!report.summary && !(report.categories || []).length && !(report.trend || []).length)) { target.innerHTML = analyticsEmpty(); return; }
    const categories = (report.categories || []).map(x => ({ label: reportLabel(x), value: reportValue(x) }));
    const marketplaces = (report.marketplaces || []).map(x => ({ label: reportLabel(x), value: reportValue(x) }));
    const trend = reportTrend(report);
    const s = report.summary || {};
    target.innerHTML = `<div class="card mb-16" style="box-shadow:none;"><div class="stats-grid"><div><span class="text-muted">Revenue</span><strong>${money(Number(s.revenue || 0))}</strong></div><div><span class="text-muted">Orders</span><strong>${Number(s.orders || 0).toLocaleString()}</strong></div><div><span class="text-muted">Units</span><strong>${Number(s.units || 0).toLocaleString()}</strong></div><div><span class="text-muted">Average order value</span><strong>${money(Number(s.averageOrderValue || 0))}</strong></div></div></div><div class="two-col"><div class="card chart-wrap" style="box-shadow:none;"><div class="card-head"><h3>Revenue by Category</h3></div>${categories.length ? barChartSVG(categories, { height: 220 }) : analyticsEmpty()}</div><div class="card chart-wrap" style="box-shadow:none;"><div class="card-head"><h3>Revenue by Marketplace</h3></div>${marketplaces.length ? barChartSVG(marketplaces, { height: 220 }) : analyticsEmpty()}</div></div><div class="card mt-16" style="box-shadow:none;"><div class="card-head"><h3>Sales Trend</h3></div>${trend.length ? lineChartSVG(trend, { height: 200 }) : analyticsEmpty()}</div>`;
  }).catch(error => { const target = $("#report-detail-body"); if (target) target.innerHTML = analyticsError(error); });
}
function initAnalyticsReports() { renderAnalytics(); }
function initAnalyticsReportDetail() { renderReportDetailPage(); }