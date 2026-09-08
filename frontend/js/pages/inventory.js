/* ============================================================================
   INVENTORY MANAGEMENT — Phase 7 (real backend)
   Buttons/actions: Update Stock Count, Set Low-Stock Threshold, View by
   Warehouse, Export Inventory Report. Backed by /api/inventory/* — no mock
   data on this module's own pages. WAREHOUSES/PRODUCTS in data.js remain
   only for other still-mocked modules (dashboard widgets, competitors,
   etc.), which are outside this phase and are left untouched.
   index.html = grid. detail.html?variantId=&warehouseId= is a real page.
   ============================================================================ */
let inventoryWarehouseFilter = "All";
let invCache = { summary: { totalUnits: 0, lowStockCount: 0, outOfStockCount: 0, warehouseCount: 0 }, warehouses: [], rows: [] };

/* -------------------------------- INDEX -------------------------------- */
function renderInventory() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Stock control</div>
        <h1 class="page-title">Inventory Management</h1>
        <p class="page-sub">Monitor stock levels, warehouse capacity, and movement across your fulfillment network.</p>
      </div>
      <div class="page-actions">
        <select class="select-input" id="inv-warehouse-filter"><option>All</option></select>
        <button class="btn" id="inv-export">⬇ Export report</button>
        <button class="btn btn-blue" id="inv-update-stock">↻ Update stock</button>
      </div>
    </div>

    <div class="grid grid-4" id="inv-kpis"><div class="empty-state">Loading…</div></div>

    <div class="two-col mt-24">
      <div class="card">
        <div class="card-head"><h3>Low Stock &amp; Out of Stock Alerts</h3></div>
        <div class="table-wrap" style="border:none;">
          <table class="data-table">
            <thead><tr><th>Product</th><th>Stock</th><th>Threshold</th><th>Status</th><th></th></tr></thead>
            <tbody id="inv-alert-body"><tr><td colspan="5"><div class="empty-state">Loading…</div></td></tr></tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><h3>Warehouses</h3></div>
        <div class="rank-list" id="inv-warehouse-list"><div class="empty-state">Loading…</div></div>
      </div>
    </div>

    <div class="card mt-24">
      <div class="card-head"><h3 id="inv-table-title">Stock by Product</h3></div>
      <div class="table-wrap" style="border:none;">
        <table class="data-table">
          <thead><tr><th>Product</th><th>Warehouse</th><th>Stock</th><th>Reserved</th><th>Available</th><th>Threshold</th><th>Status</th><th></th></tr></thead>
          <tbody id="inv-product-body"><tr><td colspan="8"><div class="empty-state">Loading…</div></td></tr></tbody>
        </table>
      </div>
    </div>
  `;

  $("#inv-update-stock").addEventListener("click", () => openUpdateStockModal());
  $("#inv-export").addEventListener("click", async () => {
    try {
      const csv = await api.inventory.exportCsv(inventoryWarehouseFilter === "All" ? null : inventoryWarehouseFilter);
      downloadFile("inventory-report.csv", csv);
      toast("Inventory report exported.", "success");
    } catch (e) {
      toast(e.message || "Couldn't export inventory report.", "error");
    }
  });

  loadInventory();
}

async function loadInventory() {
  try {
    invCache = await api.inventory.list(inventoryWarehouseFilter === "All" ? null : inventoryWarehouseFilter);
  } catch (e) {
    $("#inv-kpis").innerHTML = `<div class="empty-state"><strong>Couldn't load inventory.</strong><br>${escapeHtml(e.message)}</div>`;
    return;
  }
  drawWarehouseFilter();
  drawKpis();
  drawInventoryTables();
}

function drawWarehouseFilter() {
  const select = $("#inv-warehouse-filter");
  if (!select) return;
  select.innerHTML = `<option value="All" ${inventoryWarehouseFilter === "All" ? "selected" : ""}>All warehouses</option>`
    + invCache.warehouses.map(w => `<option value="${w.id}" ${String(inventoryWarehouseFilter) === String(w.id) ? "selected" : ""}>${escapeHtml(w.name)}</option>`).join("");
  select.onchange = (e) => { inventoryWarehouseFilter = e.target.value === "All" ? "All" : Number(e.target.value); loadInventory(); };
}

function drawKpis() {
  const s = invCache.summary;
  $("#inv-kpis").innerHTML = [
    kpiCard("Total Units", s.totalUnits.toLocaleString(), "", "up", "📦"),
    kpiCard("Low Stock Alerts", s.lowStockCount, "", "down", "⚠️"),
    kpiCard("Out of Stock", s.outOfStockCount, "", "up", "⛔"),
    kpiCard("Warehouses Active", s.warehouseCount, "", "up", "🏭")
  ].join("");
}

function drawInventoryTables() {
  const rows = invCache.rows;
  const alerts = rows.filter(r => r.status === "Low Stock" || r.status === "Out of Stock").slice(0, 8);
  $("#inv-alert-body").innerHTML = alerts.length ? alerts.map(r => `
    <tr>
      <td><a href="detail.html?variantId=${r.variantId}&warehouseId=${r.warehouseId}" class="row-flex" style="color:inherit;"><div class="thumb">📦</div><span class="cell-strong">${escapeHtml(r.productName)}</span></a></td>
      <td>${r.stockQuantity} units</td>
      <td>${r.lowStockThreshold}</td>
      <td>${statusBadge(r.status)}</td>
      <td><button class="btn btn-sm" onclick="openUpdateStockModal(${r.variantId}, ${r.warehouseId})">Restock</button></td>
    </tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><div class="e-ico">✅</div><strong>All stock healthy</strong>No alerts right now.</div></td></tr>`;

  $("#inv-warehouse-list").innerHTML = invCache.warehouses.length ? invCache.warehouses.map(w => `
    <div>
      <div class="flex-between"><span style="font-size:13px;font-weight:600;color:var(--navy-700)">${escapeHtml(w.name)}</span></div>
      <div class="rank-bar-track"><div class="rank-bar-fill" style="width:${w.capacityPct}%"></div></div>
      <div style="font-size:11.5px;color:var(--navy-400);margin-top:4px;">${w.capacityPct}% capacity used${w.location ? " · " + escapeHtml(w.location) : ""}</div>
    </div>`).join("") : `<div class="empty-state">No warehouses yet.</div>`;

  const filterWarehouse = invCache.warehouses.find(w => String(w.id) === String(inventoryWarehouseFilter));
  $("#inv-table-title").textContent = "Stock by Product" + (filterWarehouse ? " — " + filterWarehouse.name : "");

  $("#inv-product-body").innerHTML = rows.length ? rows.slice(0, 50).map(r => `
    <tr>
      <td><a href="detail.html?variantId=${r.variantId}&warehouseId=${r.warehouseId}" class="row-flex" style="color:inherit;"><div class="thumb">📦</div><span class="cell-strong">${escapeHtml(r.productName)}</span></a></td>
      <td>${escapeHtml(r.warehouseName)}</td>
      <td>${r.stockQuantity}</td>
      <td>${r.reservedQuantity}</td>
      <td>${r.availableQuantity}</td>
      <td>${r.lowStockThreshold}</td>
      <td>${statusBadge(r.status)}</td>
      <td>
        <div class="action-group">
          <button class="btn btn-sm btn-ghost" onclick="openUpdateStockModal(${r.variantId}, ${r.warehouseId})">Update</button>
          <button class="btn btn-sm btn-ghost" onclick="openThresholdModal(${r.variantId})">Threshold</button>
        </div>
      </td>
    </tr>`).join("") : `<tr><td colspan="8"><div class="empty-state"><strong>No products found.</strong></div></td></tr>`;
}

function openUpdateStockModal(variantId = null, warehouseId = null) {
  if (!invCache.rows.length) { toast("No products to update yet — add products first.", "error"); return; }
  const options = invCache.rows.reduce((acc, r) => {
    if (!acc.some(x => x.variantId === r.variantId)) acc.push(r);
    return acc;
  }, []);
  openModal({
    title: "Update stock count",
    confirmLabel: "Update stock",
    bodyHtml: `
      <div class="form-grid">
        <div class="form-field full"><label>Product</label>
          <select name="variantId">${options.map(r => `<option value="${r.variantId}" ${r.variantId === variantId ? "selected" : ""}>${escapeHtml(r.productName)} (${escapeHtml(r.sku)})</option>`).join("")}</select>
        </div>
        <div class="form-field"><label>Warehouse</label>
          <select name="warehouseId">${invCache.warehouses.map(w => `<option value="${w.id}" ${w.id === warehouseId ? "selected" : ""}>${escapeHtml(w.name)}</option>`).join("")}</select>
        </div>
        <div class="form-field"><label>Quantity change</label><input name="qty" type="number" value="50" required></div>
        <div class="form-field full"><span class="text-muted" style="font-size:12px;">Positive = stock received. Negative = manual correction (e.g. damaged/miscounted stock).</span></div>
      </div>
    `,
    onSubmit: async (data) => {
      const qty = Number(data.qty) || 0;
      if (!qty) { toast("Enter a non-zero quantity.", "error"); return; }
      try {
        await api.inventory.stockCount({ variantId: Number(data.variantId), warehouseId: Number(data.warehouseId), quantityDelta: qty });
        toast(`${qty > 0 ? "+" : ""}${qty} units recorded.`, "success");
        await loadInventory();
        if ($("#inv-detail-root")) renderInventoryDetailPage(Number(data.variantId), Number(data.warehouseId));
        renderSidebar();
      } catch (e) {
        toast(e.message || "Couldn't update stock.", "error");
      }
    }
  });
}

function openThresholdModal(variantId) {
  const row = invCache.rows.find(r => r.variantId === variantId);
  if (!row) return;
  openModal({
    title: "Set low-stock threshold — " + row.productName,
    confirmLabel: "Save threshold",
    bodyHtml: `
      <div class="form-field full"><label>Alert when available stock falls to or below</label><input name="threshold" type="number" min="0" value="${row.lowStockThreshold}" required></div>
    `,
    onSubmit: async (data) => {
      const threshold = Number(data.threshold);
      if (Number.isNaN(threshold) || threshold < 0) { toast("Enter a valid threshold.", "error"); return; }
      try {
        await api.inventory.setThreshold({ variantId, lowStockThreshold: threshold });
        toast(`Low-stock threshold for ${row.productName} set to ${threshold}.`, "success");
        await loadInventory();
        if ($("#inv-detail-root")) renderInventoryDetailPage(variantId, row.warehouseId);
      } catch (e) {
        toast(e.message || "Couldn't set threshold.", "error");
      }
    }
  });
}

/* --------------------------------- DETAIL -------------------------------- */
async function renderInventoryDetailPage(variantId, warehouseId) {
  openPage({
    title: "Loading…",
    eyebrow: "Stock control",
    subtitle: "Loading…",
    backHref: "index.html",
    hideFooter: true,
    bodyHtml: `<div id="inv-detail-root"><div class="empty-state">Loading…</div></div>`
  });
  try {
    invCache = await api.inventory.list();
    const row = invCache.rows.find(r => r.variantId === variantId && r.warehouseId === warehouseId) || invCache.rows.find(r => r.variantId === variantId);
    if (!row) { $("#inv-detail-root").innerHTML = `<div class="empty-state"><strong>Product not found.</strong></div>`; return; }
    const movements = await api.inventory.movements(row.variantId, row.warehouseId).catch(() => []);

    $("#inv-detail-root").innerHTML = `
      <div class="stat-strip" style="margin-top:0;padding-top:0;border-top:none;">
        <div><strong>${row.stockQuantity}</strong><span>Units on hand</span></div>
        <div><strong>${row.availableQuantity}</strong><span>Available (unreserved)</span></div>
        <div><strong>${row.lowStockThreshold}</strong><span>Low-stock threshold</span></div>
      </div>
      <div class="settings-row"><div class="settings-row-title">Status</div>${statusBadge(row.status)}</div>
      <div class="settings-row"><div class="settings-row-title">Warehouse</div><span>${escapeHtml(row.warehouseName)}</span></div>
      <div class="settings-row"><div class="settings-row-title">Reserved</div><span>${row.reservedQuantity}</span></div>
      ${row.price != null ? `<div class="settings-row"><div class="settings-row-title">Price</div><span>${moneyDec(row.price)}</span></div>` : ""}

      <h4 style="font-size:13px;margin-top:20px;margin-bottom:10px;">Recent movement</h4>
      ${movements.length ? movements.map(m => `
        <div class="settings-row"><div><div class="settings-row-title">${m.movementType.replace(/_/g, " ")}</div></div><span class="cell-strong">${m.quantityDelta > 0 ? "+" : ""}${m.quantityDelta}</span></div>
      `).join("") : `<div class="empty-state">No movement recorded yet.</div>`}

      <div class="modal-foot" style="padding-left:0;padding-right:0;">
        <button class="btn" onclick="openThresholdModal(${row.variantId})">Set threshold</button>
        <button class="btn btn-blue" onclick="openUpdateStockModal(${row.variantId}, ${row.warehouseId})">Update stock</button>
      </div>
    `;
    $(".page-title").textContent = row.productName;
    const sub = $(".page-sub");
    if (sub) sub.textContent = `${row.sku} · ${row.warehouseName}`;
  } catch (e) {
    $("#inv-detail-root").innerHTML = `<div class="empty-state"><strong>Couldn't load this item.</strong><br>${escapeHtml(e.message)}</div>`;
  }
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initInventoryIndex() { renderInventory(); }
function initInventoryDetail() {
  const variantId = Number(getQueryParam("variantId"));
  const warehouseId = Number(getQueryParam("warehouseId"));
  renderInventoryDetailPage(variantId, warehouseId);
}
