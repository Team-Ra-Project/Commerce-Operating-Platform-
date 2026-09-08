/* ============================================================================
   ORDER MANAGEMENT — Phase 8 (real backend)
   Buttons/actions: View Order, Confirm Order, Mark as Packed, Generate
   Shipping Label / Book Courier, Track Shipment, Export CSV. Backed by
   /api/orders/* — no mock data on this module's own pages. ORDERS/RETURNS
   in data.js remain only for other still-mocked modules (Returns & Shipping
   is Phase 9, not implemented yet — the "Initiate return" flow that used
   to live here is intentionally not wired to real orders and has been
   removed from this page rather than left pointing at disconnected mock
   data; it belongs with Phase 9).
   index.html = order queue. orders/detail.html?id= is a real page.
   ============================================================================ */
let orderFilters = { search: "", marketplace: "All", status: "All" };
let orderCache = [];
const ORDER_STAGE_ENUM = ["NEW", "CONFIRMED", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"];
function orderStatusLabel(status) {
  return { NEW: "New", CONFIRMED: "Confirmed", PACKED: "Packed", SHIPPED: "Shipped",
    OUT_FOR_DELIVERY: "Out for Delivery", DELIVERED: "Delivered", CANCELLED: "Cancelled" }[status] || status;
}
function paymentStatusLabel(status) {
  return { PAID: "Paid", PENDING: "Pending", REFUNDED: "Refunded", FAILED: "Failed" }[status] || status;
}

/* -------------------------------- INDEX -------------------------------- */
function renderOrders() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Fulfillment</div>
        <h1 class="page-title">Order Management</h1>
        <p class="page-sub" id="orders-summary">Loading orders…</p>
      </div>
      <div class="page-actions">
        <button class="btn" id="order-export">⬇ Export CSV</button>
      </div>
    </div>

    <div class="card">
      <div class="table-toolbar">
        <input type="text" class="text-input" id="order-search" placeholder="Search order number or customer…" style="min-width:240px;">
        <div class="toolbar-filters">
          <select class="select-input" id="order-marketplace-filter">
            <option>All</option>${MARKETPLACES_LIST.map(m => `<option>${m}</option>`).join("")}
          </select>
          <select class="select-input" id="order-status-filter">
            <option value="All">All</option>${ORDER_STAGE_ENUM.map(s => `<option value="${s}">${orderStatusLabel(s)}</option>`).join("")}
          </select>
        </div>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr>
            <th>Order #</th><th>Customer</th><th>Items</th><th>Marketplace</th><th>Amount</th><th>Payment</th><th>Status</th><th></th>
          </tr></thead>
          <tbody id="order-table-body"><tr><td colspan="8"><div class="empty-state">Loading…</div></td></tr></tbody>
        </table>
      </div>
      <div class="pagination"><span id="order-count"></span><span>Sorted by most recent</span></div>
    </div>
  `;
  const q = getQueryParam("q");
  if (q) { orderFilters.search = q.toLowerCase(); $("#order-search").value = q; }

  $("#order-export").addEventListener("click", () => {
    const csv = toCSV(orderCache.map(o => ({
      orderNumber: o.orderNumber, customer: o.customerName, marketplace: o.marketplaceName,
      amount: o.totalAmount, payment: o.paymentStatus, status: o.status, date: fmtDate(new Date(o.placedAt))
    })), ["orderNumber", "customer", "marketplace", "amount", "payment", "status", "date"]);
    downloadFile("orders-export.csv", csv);
    toast("Orders exported.", "success");
  });
  $("#order-search").addEventListener("input", e => { orderFilters.search = e.target.value.toLowerCase(); drawOrderTable(); });
  $("#order-marketplace-filter").addEventListener("change", e => { orderFilters.marketplace = e.target.value; drawOrderTable(); });
  $("#order-status-filter").addEventListener("change", e => { orderFilters.status = e.target.value; drawOrderTable(); });

  loadOrders();
}

async function loadOrders() {
  try {
    orderCache = await api.orders.list();
    $("#orders-summary").textContent = `${orderCache.length} orders tracked across all connected marketplaces.`;
    drawOrderTable();
  } catch (e) {
    $("#order-table-body").innerHTML = `<tr><td colspan="8"><div class="empty-state"><strong>Couldn't load orders.</strong><br>${escapeHtml(e.message)}</div></td></tr>`;
  }
}

function drawOrderTable() {
  const rows = orderCache.filter(o => {
    const matchSearch = !orderFilters.search
      || o.orderNumber.toLowerCase().includes(orderFilters.search)
      || o.customerName.toLowerCase().includes(orderFilters.search);
    const matchMp = orderFilters.marketplace === "All" || o.marketplaceName === orderFilters.marketplace.toUpperCase();
    const matchStatus = orderFilters.status === "All" || o.status === orderFilters.status;
    return matchSearch && matchMp && matchStatus;
  });
  $("#order-count").textContent = `${rows.length} orders`;
  $("#order-table-body").innerHTML = rows.length ? rows.map(o => `
    <tr>
      <td class="cell-strong">${escapeHtml(o.orderNumber)}</td>
      <td>${escapeHtml(o.customerName)}</td>
      <td>${o.itemCount} item${o.itemCount === 1 ? "" : "s"}</td>
      <td>${escapeHtml(o.marketplaceName)}</td>
      <td class="cell-strong">${moneyDec(o.totalAmount)}</td>
      <td>${statusBadge(paymentStatusLabel(o.paymentStatus))}</td>
      <td>${statusBadge(orderStatusLabel(o.status))}</td>
      <td><a class="btn btn-sm" href="detail.html?id=${o.id}">View</a></td>
    </tr>
  `).join("") : `<tr><td colspan="8"><div class="empty-state"><div class="e-ico">🧾</div><strong>No orders found</strong>Try adjusting your filters.</div></td></tr>`;
}

function nextOrderAction(status) {
  const map = {
    NEW: { label: "Confirm order", fn: "confirmOrderAction" },
    CONFIRMED: { label: "Mark as packed", fn: "markPackedAction" },
    PACKED: { label: "Generate shipping label", fn: "openShipModal" },
    SHIPPED: { label: "Refresh tracking", fn: "trackShipmentAction" },
    OUT_FOR_DELIVERY: { label: "Refresh tracking", fn: "trackShipmentAction" },
    DELIVERED: null,
    CANCELLED: null
  };
  return map[status];
}

/* --------------------------------- DETAIL -------------------------------- */
async function renderOrderDetailPage(id) {
  openPage({
    title: "Loading…",
    eyebrow: "Fulfillment",
    subtitle: "Loading…",
    backHref: "index.html",
    wide: true,
    hideFooter: true,
    bodyHtml: `<div id="order-detail-root"><div class="empty-state">Loading…</div></div>`
  });
  try {
    await drawOrderDetail(id);
  } catch (e) {
    $("#order-detail-root").innerHTML = `<div class="empty-state"><strong>Couldn't load this order.</strong><br>${escapeHtml(e.message)}</div>`;
  }
}

async function drawOrderDetail(id) {
  const o = await api.orders.get(id);
  const action = nextOrderAction(o.status);

  $(".page-title").textContent = `Order ${o.orderNumber}`;
  const sub = $(".page-sub");
  if (sub) sub.textContent = `${o.marketplaceName} · placed ${fmtDate(new Date(o.placedAt))}`;

  $("#order-detail-root").innerHTML = `
    <div class="stat-strip" style="margin-top:0;padding-top:0;border-top:none;">
      <div><strong>${escapeHtml(o.customerName)}</strong><span>Customer</span></div>
      <div><strong>${moneyDec(o.totalAmount)}</strong><span>Order value</span></div>
      <div><strong>${escapeHtml(o.marketplaceName)}</strong><span>Marketplace</span></div>
    </div>

    <h4 style="font-size:13px;margin-top:20px;margin-bottom:10px;">Items</h4>
    ${o.items.map(i => `
      <div class="settings-row"><div><div class="settings-row-title">${escapeHtml(i.productName)}</div><div class="settings-row-sub">${escapeHtml(i.sku)} · Qty ${i.quantity}</div></div><span class="cell-strong">${moneyDec(i.lineTotal)}</span></div>
    `).join("")}

    ${o.shippingAddress ? `<div class="settings-row"><div><div class="settings-row-title">Shipping address</div></div><span class="text-muted" style="font-size:12.5px;max-width:260px;text-align:right;">${escapeHtml(o.shippingAddress)}</span></div>` : ""}
    <div class="settings-row"><div><div class="settings-row-title">Payment</div></div>${statusBadge(paymentStatusLabel(o.paymentStatus))}</div>
    ${o.trackingNumber ? `<div class="settings-row"><div><div class="settings-row-title">Tracking</div><div class="settings-row-sub">${escapeHtml(o.courierName || "")}</div></div><span class="cell-strong">${escapeHtml(o.trackingNumber)}</span></div>` : ""}

    <h4 style="font-size:13px;margin-top:20px;margin-bottom:10px;">Order status</h4>
    <div class="order-track">
      ${ORDER_STAGE_ENUM.map((s, i) => `<div class="order-track-step ${ORDER_STAGE_ENUM.indexOf(o.status) >= i ? "done" : ""}"><span class="dot"></span><span class="lbl">${orderStatusLabel(s)}</span></div>`).join("")}
    </div>

    <h4 style="font-size:13px;margin-top:20px;margin-bottom:10px;">Status history</h4>
    ${o.history.map(h => `<div class="settings-row"><div class="settings-row-title">${orderStatusLabel(h.status)}</div><span class="text-muted" style="font-size:12px;">${fmtDateTime(new Date(h.changedAt))}</span></div>`).join("")}

    <div class="modal-foot" style="padding-left:0;padding-right:0;">
      ${action ? `<button class="btn btn-blue" id="order-next-action">${action.label}</button>` : ""}
    </div>
  `;

  if (action) {
    $("#order-next-action").addEventListener("click", () => window[action.fn](o.id));
  }
}

async function confirmOrderAction(id) {
  try {
    await api.orders.confirm(id);
    toast("Order confirmed. Inventory reserved.", "success");
    await drawOrderDetail(id);
    renderSidebar();
  } catch (e) {
    toast(e.message || "Couldn't confirm this order.", "error");
  }
}

async function markPackedAction(id) {
  try {
    await api.orders.pack(id);
    toast("Order marked as packed.", "success");
    await drawOrderDetail(id);
  } catch (e) {
    toast(e.message || "Couldn't mark this order as packed.", "error");
  }
}

function openShipModal(id) {
  openModal({
    title: "Generate shipping label",
    confirmLabel: "Generate label",
    bodyHtml: `
      <div class="form-field full"><label>Courier</label>
        <select name="courierName">${COURIERS.map(c => `<option>${c}</option>`).join("")}</select>
      </div>
      <div class="form-field full"><label>Tracking number (optional)</label><input name="trackingNumber" placeholder="Leave blank to auto-generate"></div>
    `,
    onSubmit: async (data) => {
      try {
        const o = await api.orders.ship(id, { courierName: data.courierName, trackingNumber: data.trackingNumber || null });
        toast(`Shipping label generated — tracking ${o.trackingNumber} via ${o.courierName}.`, "success");
        await drawOrderDetail(id);
      } catch (e) {
        toast(e.message || "Couldn't generate shipping label.", "error");
      }
    }
  });
}

async function trackShipmentAction(id) {
  try {
    const o = await api.orders.track(id);
    toast(`Tracking refreshed — now ${orderStatusLabel(o.status)}.`, "success");
    await drawOrderDetail(id);
  } catch (e) {
    toast(e.message || "Couldn't refresh tracking.", "error");
  }
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initOrdersIndex() { renderOrders(); }
function initOrderDetail() { renderOrderDetailPage(Number(getQueryParam("id"))); }
