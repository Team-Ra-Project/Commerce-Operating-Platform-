/* ============================================================================
   DASHBOARD — Phase 4.
   Now backed by real endpoints (GET /api/dashboard/summary,
   GET /api/dashboard/recent-activity) instead of the PRODUCTS/ORDERS/
   CUSTOMERS mock arrays. Buttons/actions covered: Date Range Filter,
   Marketplace Filter, Refresh Dashboard, View All Orders, View All
   Products/Inventory, View Full Revenue Report, Recent Activity item
   click-through — all unchanged in behavior, just now driven by real data.
   ============================================================================ */
let dashboardData = null;
let dashboardActivity = null;
let dashboardLoadError = null;

function renderExecutiveDashboard() {
  if (dashboardLoadError) {
    $("#content").innerHTML = `
      <div class="page-head">
        <div>
          <div class="page-eyebrow">Overview</div>
          <h1 class="page-title">Executive Dashboard</h1>
        </div>
      </div>
      <div class="card">
        <div class="empty-state">
          <div class="e-ico">⚠️</div>
          <strong>Couldn't load the dashboard</strong>
          ${escapeHtml(dashboardLoadError)}
          <div class="mt-16"><button class="btn btn-blue" id="dash-retry">Try again</button></div>
        </div>
      </div>
    `;
    $("#dash-retry").addEventListener("click", loadDashboard);
    return;
  }

  if (!dashboardData) {
    $("#content").innerHTML = `
      <div class="page-head">
        <div>
          <div class="page-eyebrow">Overview</div>
          <h1 class="page-title">Executive Dashboard</h1>
          <p class="page-sub">Loading real-time data…</p>
        </div>
      </div>
      <div class="card"><div class="empty-state"><div class="spinner"></div>Loading dashboard…</div></div>
    `;
    return;
  }

  const d = dashboardData;
  const topProducts = d.topProducts || [];
  const maxSold = topProducts.length ? topProducts[0].quantitySold : 1;
  const marketplaces = d.marketplacePerformance || [];

  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Overview</div>
        <h1 class="page-title">Executive Dashboard</h1>
        <p class="page-sub">Real-time snapshot of revenue, orders, and marketplace performance across your business.</p>
      </div>
      <div class="page-actions">
        <select class="select-input" id="dash-range">
          <option value="7" ${dashboardFilters.range === "7" ? "selected" : ""}>Last 7 days</option>
          <option value="30" ${dashboardFilters.range === "30" ? "selected" : ""}>Last 30 days</option>
          <option value="90" ${dashboardFilters.range === "90" ? "selected" : ""}>Last 90 days</option>
        </select>
        <select class="select-input" id="dash-store">
          <option ${dashboardFilters.store === "All" ? "selected" : ""}>All</option>
          ${MARKETPLACES_LIST.map(m => `<option ${dashboardFilters.store === m ? "selected" : ""}>${m}</option>`).join("")}
        </select>
        <button class="btn" id="dash-refresh" title="Refresh dashboard">↻ Refresh</button>
        <button class="btn btn-blue" id="dash-export">⬇ Export report</button>
      </div>
    </div>

    <div class="grid grid-4">
      ${kpiCard("Total Revenue", money(d.totalRevenue.value), fmtDeltaPct(d.totalRevenue.deltaPct), d.totalRevenue.deltaDirection, "💰")}
      ${kpiCard("Total Orders", d.totalOrders.value.toLocaleString(), fmtDeltaPct(d.totalOrders.deltaPct), d.totalOrders.deltaDirection, "🧾", false, "orders")}
      ${kpiCard("Active Customers", d.activeCustomers.value.toLocaleString(), fmtDeltaPct(d.activeCustomers.deltaPct), d.activeCustomers.deltaDirection, "👥", false, "crm")}
      ${kpiCard("Products Listed", d.productsListed.toLocaleString(), "", "up", "🏷️", false, "products")}
    </div>

    <div class="grid grid-2 mt-24">
      ${kpiCard("Inventory Alerts", d.lowStockCount + " SKUs", "", "up", "📦", true, "inventory")}
      ${kpiCard("Marketplace Performance", d.avgMarketplaceSyncPct != null ? d.avgMarketplaceSyncPct + "% healthy" : "No marketplaces connected yet", "", "up", "🛒", true, "marketplace")}
    </div>

    <div class="two-col mt-24">
      <div class="card chart-wrap">
        <div class="card-head">
          <h3>Revenue — last 12 months</h3>
          <a href="#" class="link-action" data-nav="analytics">View full revenue report →</a>
        </div>
        ${lineChartSVG(d.revenueTrend, { height: 240 })}
      </div>
      <div class="card">
        <div class="card-head"><h3>Recent Activity</h3></div>
        <div class="activity-list" id="dash-activity">${renderActivityList()}</div>
      </div>
    </div>

    <div class="two-col mt-24">
      <div class="card chart-wrap">
        <div class="card-head">
          <h3>Sales Trend — this week</h3>
          <span class="card-tag">Units sold / day</span>
        </div>
        ${barChartSVG(d.salesTrendWeek, { height: 220 })}
      </div>
      <div class="card">
        <div class="card-head"><h3>Top Selling Products</h3><a href="#" class="link-action" data-nav="products">View all products →</a></div>
        ${topProducts.length ? `
          <div class="rank-list">
            ${topProducts.map((p, i) => `
              <div class="rank-row">
                <span class="rank-num">${i + 1}</span>
                <div class="rank-bar-wrap">
                  <div class="flex-between"><span style="font-size:13px;color:var(--navy-700);font-weight:600">${escapeHtml(p.name)}</span><span style="font-size:12px;color:var(--navy-400)">${p.quantitySold} sold</span></div>
                  <div class="rank-bar-track"><div class="rank-bar-fill" style="width:${(p.quantitySold / maxSold) * 100}%"></div></div>
                </div>
              </div>`).join("")}
          </div>` : `<div class="empty-state"><div class="e-ico">🛒</div><strong>No sales yet</strong>Top sellers will show up here once orders come in for this period.</div>`}
      </div>
    </div>

    <div class="card mt-24">
      <div class="card-head"><h3>Platform Performance by Marketplace</h3><a href="#" class="link-action" data-nav="marketplace">Manage integrations →</a></div>
      ${marketplaces.length ? `
        <div class="grid grid-4">
          ${marketplaces.map(m => `
            <div>
              <div class="flex-between" style="margin-bottom:6px;">
                <span style="font-size:12.5px;font-weight:700;color:var(--navy-700)">${titleCase(m.marketplace)}</span>
                <span style="font-size:12px;color:var(--navy-400)">${m.orderSyncPct}%</span>
              </div>
              <div class="rank-bar-track"><div class="rank-bar-fill" style="width:${m.orderSyncPct}%"></div></div>
            </div>
          `).join("")}
        </div>` : `<div class="empty-state"><div class="e-ico">🛒</div><strong>No marketplaces connected yet</strong>Connect a marketplace to see sync performance here.</div>`}
    </div>
  `;

  $("#dash-range").addEventListener("change", e => { dashboardFilters.range = e.target.value; loadDashboard(); });
  $("#dash-store").addEventListener("change", e => { dashboardFilters.store = e.target.value; loadDashboard(); });
  $("#dash-refresh").addEventListener("click", () => loadDashboard(true));
  $("#dash-export").addEventListener("click", () => {
    const csv = toCSV(
      [
        { metric: "Total Revenue", value: d.totalRevenue.value },
        { metric: "Total Orders", value: d.totalOrders.value },
        { metric: "Active Customers", value: d.activeCustomers.value },
        { metric: "Products Listed", value: d.productsListed },
        { metric: "Inventory Alerts", value: d.lowStockCount }
      ],
      ["metric", "value"]
    );
    downloadFile("executive-dashboard-summary.csv", csv);
    toast("Dashboard report exported.", "success");
  });
}

function renderActivityList() {
  if (!dashboardActivity) return `<div class="empty-state"><div class="e-ico">🔔</div><strong>Loading…</strong></div>`;
  if (dashboardActivity.length === 0) return `<div class="empty-state"><div class="e-ico">🔔</div><strong>No recent activity</strong>Activity from orders, inventory, and marketplaces will show up here.</div>`;
  const colorByCategory = { LOW_STOCK: "#B7791F", SYNC_ERROR: "#D6483D", ORDER_UPDATE: "#0E8F5F", RETURN_UPDATE: "#D6483D", CAMPAIGN: "#7C4FEE", AUTOMATION: "#7C4FEE", INTEGRATION: "#7C4FEE" };
  return dashboardActivity.map(a => `
    <div class="activity-row activity-clickable" data-nav-target="${a.linkModule || ""}">
      <div class="activity-dot" style="background:${colorByCategory[a.category] || "#66748F"}"></div>
      <div><p>${escapeHtml(a.title)}</p><span>${timeAgo(a.createdAt)}</span></div>
    </div>`).join("");
}

function fmtDeltaPct(pct) {
  if (pct == null) return "";
  const sign = pct >= 0 ? "+" : "";
  return `${sign}${pct}%`;
}

function titleCase(s) {
  return s ? s.charAt(0) + s.slice(1).toLowerCase() : s;
}

function timeAgo(isoString) {
  const then = new Date(isoString).getTime();
  const diffMs = Date.now() - then;
  const mins = Math.round(diffMs / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `${hrs} hr ago`;
  const days = Math.round(hrs / 24);
  if (days === 1) return "Yesterday";
  return `${days} days ago`;
}

async function loadDashboard(isManualRefresh = false) {
  dashboardLoadError = null;
  dashboardData = null;
  dashboardActivity = null;
  renderExecutiveDashboard();
  try {
    const [summary, activity] = await Promise.all([
      api.dashboard.summary(dashboardFilters.range, dashboardFilters.store),
      api.dashboard.recentActivity(10)
    ]);
    dashboardData = summary;
    dashboardActivity = activity;
    renderExecutiveDashboard();
    if (isManualRefresh) toast("Dashboard data refreshed.", "success");
  } catch (err) {
    dashboardLoadError = err.message === "Failed to fetch"
      ? "Could not reach the server. Check your connection and try again."
      : (err.message || "Something went wrong loading dashboard data.");
    renderExecutiveDashboard();
  }
}

function initDashboardPage() {
  loadDashboard();
}