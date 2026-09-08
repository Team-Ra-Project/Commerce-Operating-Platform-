/* ============================================================================
   RETURNS & SHIPPING MANAGEMENT — Phase 9 (real backend)
   Buttons/actions: Approve/Reject Return, Log Item Received, Approve
   Refund/Replacement, Close Case. Backed by /api/returns/* — no mock data
   on this module's own pages. RETURNS in data.js is no longer used here.
   ============================================================================ */
let returnCache = [];
const RETURN_STATUS_LABEL = {
  REQUESTED: "Requested",
  APPROVED_AWAITING_ITEM: "Approved – Awaiting Item",
  REJECTED: "Rejected",
  RECEIVED_INSPECTING: "Received – Inspecting",
  REFUNDED: "Refunded",
  REPLACED: "Replaced",
  CLOSED: "Closed"
};

/* -------------------------------- INDEX -------------------------------- */
function renderReturnsIndex() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Fulfillment</div>
        <h1 class="page-title">Returns &amp; Shipping</h1>
        <p class="page-sub" id="returns-summary">Loading return requests…</p>
      </div>
      <div class="page-actions">
        <a class="btn" href="../orders/index.html">🧾 Order Management</a>
        <button class="btn btn-blue" id="returns-log-new">+ Log return request</button>
      </div>
    </div>

    <div class="card">
      <div class="card-head"><h3>Return &amp; Exchange Requests</h3></div>
      <div class="table-wrap" style="border:none;">
        <table class="data-table">
          <thead><tr><th>Return #</th><th>Order</th><th>Customer</th><th>Reason</th><th>Status</th><th></th></tr></thead>
          <tbody id="returns-body"><tr><td colspan="6"><div class="empty-state">Loading…</div></td></tr></tbody>
        </table>
      </div>
    </div>
  `;
  $("#returns-log-new").addEventListener("click", openLogReturnModal);
  loadReturns();
}

async function loadReturns() {
  try {
    returnCache = await api.returns.list();
    $("#returns-summary").textContent = `${returnCache.length} return/exchange requests across all connected marketplaces.`;
    drawReturnsTable();
  } catch (e) {
    $("#returns-body").innerHTML = `<tr><td colspan="6"><div class="empty-state"><strong>Couldn't load returns.</strong><br>${escapeHtml(e.message)}</div></td></tr>`;
  }
}

function drawReturnsTable() {
  $("#returns-body").innerHTML = returnCache.length ? returnCache.map(r => `
    <tr>
      <td class="cell-strong">RET-${r.id}</td>
      <td><a class="link-action" href="../orders/detail.html?id=${r.orderId}">${escapeHtml(r.orderNumber)}</a></td>
      <td>${escapeHtml(r.customerName)}</td>
      <td class="text-muted" style="font-size:12.5px;max-width:220px;">${escapeHtml(r.reason)}</td>
      <td>${statusBadge(RETURN_STATUS_LABEL[r.status] || r.status)}</td>
      <td><a class="btn btn-sm" href="detail.html?id=${r.id}">Manage</a></td>
    </tr>
  `).join("") : `<tr><td colspan="6"><div class="empty-state"><div class="e-ico">↩️</div><strong>No return requests</strong>Returns will appear here once customers request them.</div></td></tr>`;
}

async function openLogReturnModal() {
  let orders = [];
  try { orders = await api.orders.list(); } catch (e) { /* fall through to empty list */ }
  if (!orders.length) { toast("No orders to log a return against yet.", "error"); return; }
  openModal({
    title: "Log a return request",
    confirmLabel: "Log request",
    bodyHtml: `
      <div class="form-field full"><label>Order</label>
        <select name="orderId">${orders.map(o => `<option value="${o.id}">${escapeHtml(o.orderNumber)} — ${escapeHtml(o.customerName)}</option>`).join("")}</select>
      </div>
      <div class="form-field full"><label>Reason</label><textarea name="reason" placeholder="Why the customer wants to return this order" required></textarea></div>
    `,
    onSubmit: async (data) => {
      if (!data.reason.trim()) { toast("Enter a reason.", "error"); return; }
      try {
        const r = await api.returns.create({ orderId: Number(data.orderId), reason: data.reason.trim() });
        toast(`Return request RET-${r.id} logged.`, "success");
        await loadReturns();
      } catch (e) {
        toast(e.message || "Couldn't log this return request.", "error");
      }
    }
  });
}

/* --------------------------------- DETAIL -------------------------------- */
async function renderReturnDetailPage(id) {
  openPage({
    title: "Loading…",
    eyebrow: "Fulfillment",
    subtitle: "Loading…",
    backHref: "index.html",
    hideFooter: true,
    bodyHtml: `<div id="return-detail-root"><div class="empty-state">Loading…</div></div>`
  });
  try {
    await drawReturnDetail(id);
  } catch (e) {
    $("#return-detail-root").innerHTML = `<div class="empty-state"><strong>Couldn't load this return.</strong><br>${escapeHtml(e.message)}</div>`;
  }
}

async function drawReturnDetail(id) {
  const r = await api.returns.get(id);
  $(".page-title").textContent = `RET-${r.id} — ${r.orderNumber}`;
  const sub = $(".page-sub");
  if (sub) sub.textContent = `${r.customerName} · requested ${fmtDate(new Date(r.requestedAt))}`;

  let actionsHtml = "";
  if (r.status === "REQUESTED") {
    actionsHtml = `
      <button class="btn btn-danger" id="ret-reject">Reject request</button>
      <button class="btn btn-blue" id="ret-approve">Approve return</button>`;
  } else if (r.status === "APPROVED_AWAITING_ITEM") {
    actionsHtml = `<button class="btn btn-blue" id="ret-receive">Log item received</button>`;
  } else if (r.status === "RECEIVED_INSPECTING") {
    actionsHtml = `
      <button class="btn" id="ret-replace">Approve replacement</button>
      <button class="btn btn-blue" id="ret-refund">Approve refund</button>`;
  } else if (r.status === "REFUNDED" || r.status === "REPLACED") {
    actionsHtml = `<button class="btn btn-blue" id="ret-close">Close case</button>`;
  }

  $("#return-detail-root").innerHTML = `
    <div class="settings-row"><div><div class="settings-row-title">Order</div></div><a class="link-action" href="../orders/detail.html?id=${r.orderId}">${escapeHtml(r.orderNumber)}</a></div>
    <div class="settings-row"><div><div class="settings-row-title">Customer</div></div><span>${escapeHtml(r.customerName)}</span></div>
    <div class="settings-row"><div><div class="settings-row-title">Reason / activity log</div></div><span style="max-width:320px;text-align:right;font-size:12.5px;color:var(--navy-600);">${escapeHtml(r.reason)}</span></div>
    <div class="settings-row"><div><div class="settings-row-title">Requested on</div></div><span>${fmtDate(new Date(r.requestedAt))}</span></div>
    <div class="settings-row"><div><div class="settings-row-title">Status</div></div>${statusBadge(RETURN_STATUS_LABEL[r.status] || r.status)}</div>
    ${r.resolution ? `<div class="settings-row"><div><div class="settings-row-title">Resolution</div></div><span>${escapeHtml(r.resolution)}</span></div>` : ""}
    <div class="modal-foot" style="padding-left:0;padding-right:0;">${actionsHtml || '<span class="text-muted" style="font-size:12.5px;">This case is closed.</span>'}</div>
  `;

  $("#ret-approve")?.addEventListener("click", () => runReturnAction(id, () => api.returns.approve(id), "Return approved — awaiting item."));
  $("#ret-reject")?.addEventListener("click", () => openRejectModal(id));
  $("#ret-receive")?.addEventListener("click", () => openReceiveModal(id));
  $("#ret-replace")?.addEventListener("click", () => openResolveModal(id, "REPLACED"));
  $("#ret-refund")?.addEventListener("click", () => openResolveModal(id, "REFUNDED"));
  $("#ret-close")?.addEventListener("click", () => runReturnAction(id, () => api.returns.close(id), "Case closed."));
}

async function runReturnAction(id, action, successMsg) {
  try {
    await action();
    toast(successMsg, "success");
    await drawReturnDetail(id);
  } catch (e) {
    toast(e.message || "That action couldn't be completed.", "error");
  }
}

function openRejectModal(id) {
  openModal({
    title: "Reject return request",
    danger: true,
    confirmLabel: "Reject request",
    bodyHtml: `<div class="form-field full"><label>Reason for rejection</label><textarea name="reason" placeholder="Shown internally and used to notify the customer" required></textarea></div>`,
    onSubmit: async (data) => {
      if (!data.reason.trim()) { toast("Enter a rejection reason.", "error"); return; }
      await runReturnAction(id, () => api.returns.reject(id, data.reason.trim()), "Return request rejected.");
    }
  });
}

function openReceiveModal(id) {
  openModal({
    title: "Log item received",
    confirmLabel: "Log receipt",
    bodyHtml: `
      <div class="form-field full"><label>Condition</label>
        <select name="condition"><option value="PASSED">Passed inspection — sellable</option><option value="FAILED">Failed inspection — damaged/unsellable</option></select>
      </div>
      <div class="form-field full"><label>Notes (optional)</label><textarea name="note" placeholder="Any details about the item's condition"></textarea></div>
    `,
    onSubmit: async (data) => {
      await runReturnAction(id, () => api.returns.receive(id, { condition: data.condition, note: data.note }), "Item receipt logged — inspecting.");
    }
  });
}

async function openResolveModal(id, resolution) {
  let warehouses = [];
  try { warehouses = await api.inventory.warehouses(); } catch (e) { /* fall through */ }
  openModal({
    title: resolution === "REFUNDED" ? "Approve refund" : "Approve replacement",
    confirmLabel: resolution === "REFUNDED" ? "Approve refund" : "Approve replacement",
    bodyHtml: `
      <div class="form-field full"><label><input type="checkbox" name="restock" checked style="width:auto;display:inline-block;margin-right:6px;">Item passed inspection — return it to stock</label></div>
      <div class="form-field full"><label>Warehouse to restock at</label>
        <select name="warehouseId">${warehouses.map(w => `<option value="${w.id}">${escapeHtml(w.name)}</option>`).join("")}</select>
      </div>
    `,
    onSubmit: async (data) => {
      const restock = !!data.restock;
      if (restock && !data.warehouseId) { toast("Select a warehouse to restock at.", "error"); return; }
      await runReturnAction(id, () => api.returns.resolve(id, {
        resolution, restock, warehouseId: restock ? Number(data.warehouseId) : null
      }), `Return resolved — ${resolution.toLowerCase()}. Inventory and customer profile updated.`);
    }
  });
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initReturnsIndex() { renderReturnsIndex(); }
function initReturnDetail() { renderReturnDetailPage(Number(getQueryParam("id"))); }
