/* ============================================================================
   CRM — CUSTOMER MANAGEMENT
   Directory + Customer Detail (this file's renderCrmCustomers /
   renderCustomerDetailPage and segment pages are backed by the real
   /api/customers/* and /api/segments/* endpoints. Segment membership is
   calculated from order history by the backend.
   ============================================================================ */
const SEGMENT_RULE_OPTIONS = [
  ["cart_abandoned", "Cart abandoned in last 24 hours"],
  ["vip", "Lifetime spend above ₹1,20,000 (VIP)"],
  ["loyal", "Lifetime spend ₹55,000–₹1,20,000 (Loyal)"],
  ["lapsed", "No order in last 90+ days (Lapsed)"],
  ["recent_buyer", "Ordered within last 14 days (Recent buyer)"],
  ["new", "First-time customers, no repeat purchase (New)"]
];

function crmHeader(activeTab, summaryText, kpiHtml) {
  return `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Relationships</div>
        <h1 class="page-title">Customer Relationship Management</h1>
        <p class="page-sub">${summaryText}</p>
      </div>
      <div class="page-actions">
        ${activeTab === "directory" ? `<button class="btn btn-blue" id="crm-add-customer">+ Add customer</button>` : ""}
        <button class="btn" id="crm-export">⬇ Export customer list</button>
      </div>
    </div>
    <div class="grid grid-4">${kpiHtml}</div>
    <div class="tabs mt-24">
      <a class="tab-btn ${activeTab === "directory" ? "active" : ""}" href="customers.html">Customer Directory</a>
      <a class="tab-btn ${activeTab === "segments" ? "active" : ""}" href="segments.html">Segments</a>
    </div>
  `;
}

/* ------------------------------- DIRECTORY (Phase 10, real) ------------------------------- */
let crmCustomerCache = [];

async function renderCrmCustomers() {
  $("#content").innerHTML = crmHeader("directory", "Loading customers…", `<div class="empty-state">Loading…</div>`)
    + `<div id="crm-tab-content"><div class="empty-state">Loading…</div></div>`;
  try {
    crmCustomerCache = await api.customers.list();
  } catch (e) {
    $("#crm-tab-content").innerHTML = `<div class="empty-state"><strong>Couldn't load customers.</strong><br>${escapeHtml(e.message)}</div>`;
    return;
  }
  drawCrmDirectory();
}

function drawCrmDirectory() {
  const total = crmCustomerCache.length;
  const active = crmCustomerCache.filter(c => c.status === "ACTIVE").length;
  const repeat = crmCustomerCache.filter(c => c.orderCount > 1).length;
  const lifetimeValue = crmCustomerCache.reduce((sum, c) => sum + Number(c.totalSpent || 0), 0);

  $("#content").innerHTML = crmHeader("directory",
    `${total} customers · lifetime value, purchase history, and communication log.`,
    [
      kpiCard("Total Customers", total, "", "up", "👥"),
      kpiCard("Active", active, "", "up", "✅"),
      kpiCard("Repeat Customers", repeat, "", "up", "🔁"),
      kpiCard("Lifetime Value", money(lifetimeValue), "", "up", "💰")
    ].join("")
  ) + `<div id="crm-tab-content"></div>`;

  $("#crm-export").addEventListener("click", async () => {
    try {
      const csv = await api.customers.exportCsv();
      downloadFile("customer-list.csv", csv);
      toast("Customer list exported.", "success");
    } catch (e) {
      toast(e.message || "Couldn't export customer list.", "error");
    }
  });
  $("#crm-add-customer").addEventListener("click", openAddCustomerModal);

  $("#crm-tab-content").innerHTML = `
    <div class="card">
      <div class="card-head"><h3>Customer Directory</h3><input type="text" class="text-input" id="crm-search" placeholder="Search name, email, phone…" style="max-width:240px;"></div>
      <div class="table-wrap" style="border:none;">
        <table class="data-table">
          <thead><tr><th>Customer</th><th>Location</th><th>Orders</th><th>Lifetime Spend</th><th>Channel</th><th>Last Order</th><th></th></tr></thead>
          <tbody id="crm-directory-body"></tbody>
        </table>
      </div>
    </div>
  `;
  $("#crm-search").addEventListener("input", e => drawDirectoryRows(e.target.value.toLowerCase()));
  drawDirectoryRows("");
}

function openAddCustomerModal() {
  openModal({
    title: "Add customer",
    confirmLabel: "Add customer",
    bodyHtml: `
      <div class="form-grid">
        <div class="form-field full">
          <label>Full name</label>
          <input name="fullName" maxlength="150" required placeholder="Customer's full name">
        </div>
        <div class="form-field">
          <label>Email <span class="text-muted">(optional)</span></label>
          <input name="email" type="email" maxlength="190" placeholder="customer@example.com">
        </div>
        <div class="form-field">
          <label>Phone <span class="text-muted">(optional)</span></label>
          <input name="phone" maxlength="30" placeholder="+91 98765 43210">
        </div>
        <div class="form-field">
          <label>City <span class="text-muted">(optional)</span></label>
          <input name="city" maxlength="100" placeholder="City">
        </div>
        <div class="form-field">
          <label>Country <span class="text-muted">(optional)</span></label>
          <input name="country" maxlength="100" value="India" placeholder="Country">
        </div>
        <div class="form-field full">
          <label>Primary channel</label>
          <select name="primaryChannel">
            <option value="DIRECT_STORE" selected>Direct store</option>
            <option value="IN_STORE">In-store purchase</option>
            <option value="PHONE">Phone order</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div class="form-field full">
          <label>Marketing consent</label>
          <label style="font-weight:400;font-size:13px;display:block;margin-bottom:8px;">
            <input type="checkbox" name="emailOptIn"> Email marketing
          </label>
          <label style="font-weight:400;font-size:13px;display:block;">
            <input type="checkbox" name="whatsappOptIn"> WhatsApp marketing
          </label>
        </div>
      </div>
      <p class="text-muted" style="font-size:12px;margin:14px 0 0;">
        A customer with the same email or phone number cannot be added twice.
      </p>
    `,
    onSubmit: (data) => {
      const payload = {
        fullName: data.fullName,
        email: data.email || null,
        phone: data.phone || null,
        city: data.city || null,
        country: data.country || null,
        primaryChannel: data.primaryChannel || "DIRECT_STORE",
        emailOptIn: data.emailOptIn === "on",
        whatsappOptIn: data.whatsappOptIn === "on"
      };
      api.customers.create(payload)
        .then(() => {
          toast("Customer added to the CRM.", "success");
          return renderCrmCustomers();
        })
        .catch(e => toast(e.message || "Couldn't add customer.", "error"));
    }
  });
}

function drawDirectoryRows(search) {
  const rows = crmCustomerCache.filter(c => !search
    || (c.fullName || "").toLowerCase().includes(search)
    || (c.email || "").toLowerCase().includes(search)
    || (c.phone || "").includes(search));
  $("#crm-directory-body").innerHTML = rows.length ? rows.map(c => `
    <tr>
      <td><div class="row-flex"><div class="thumb" style="border-radius:50%;background:var(--blue-100);color:var(--blue-700);font-weight:700;font-family:var(--font-display);font-size:12px;">${initials(c.fullName)}</div><div><div class="cell-strong">${escapeHtml(c.fullName)}</div><div class="cell-sub">${escapeHtml(c.email || "No email on file")}</div></div></div></td>
      <td>${escapeHtml([c.city, c.country].filter(Boolean).join(", ") || "—")}</td>
      <td>${c.orderCount}</td>
      <td class="cell-strong">${money(c.totalSpent)}</td>
      <td>${escapeHtml(c.primaryChannel || "—")}</td>
      <td class="text-muted">${c.lastOrderAt ? fmtDate(new Date(c.lastOrderAt)) : "Never"}</td>
      <td><a class="btn btn-sm" href="customer-detail.html?id=${c.id}">View</a></td>
    </tr>`).join("") : `<tr><td colspan="7"><div class="empty-state"><strong>No customers found.</strong></div></td></tr>`;
}

/* ---------------------------- CUSTOMER DETAIL (Phase 10, real) ----------------------------- */
async function renderCustomerDetailPage(id) {
  openPage({
    title: "Loading…",
    eyebrow: "Relationships",
    subtitle: "Loading…",
    backHref: "customers.html",
    wide: true,
    hideFooter: true,
    bodyHtml: `<div id="cust-detail-root"><div class="empty-state">Loading…</div></div>`
  });
  try {
    await drawCustomerDetail(id);
  } catch (e) {
    $("#cust-detail-root").innerHTML = `<div class="empty-state"><strong>Couldn't load this customer.</strong><br>${escapeHtml(e.message)}</div>`;
  }
}

async function drawCustomerDetail(id) {
  const { customer: c, purchaseHistory, notes } = await api.customers.get(id);
  $(".page-title").textContent = c.fullName;
  const sub = $(".page-sub");
  if (sub) sub.textContent = [c.email, c.phone, [c.city, c.country].filter(Boolean).join(", ")].filter(Boolean).join(" · ");

  $("#cust-detail-root").innerHTML = `
    <div class="profile-card">
      <div class="profile-avatar">${initials(c.fullName)}</div>
      <div>
        <strong style="font-size:15px;color:var(--navy-900);">${escapeHtml(c.fullName)}</strong>
        <div class="text-muted" style="font-size:12.5px;margin-top:2px;">${escapeHtml(c.email || "No email")} · ${escapeHtml(c.phone || "No phone")}</div>
      </div>
      <span class="badge ${c.status === 'ACTIVE' ? 'blue' : ''}" style="margin-left:auto;">${escapeHtml(c.status)}</span>
    </div>
    <div class="stat-strip">
      <div><strong>${c.orderCount}</strong><span>Total orders</span></div>
      <div><strong>${money(c.totalSpent)}</strong><span>Lifetime spend</span></div>
      <div><strong>${escapeHtml(c.primaryChannel || "—")}</strong><span>Primary channel</span></div>
    </div>
    <div class="settings-row"><div class="settings-row-title">Opt-in status</div><span class="text-muted" style="font-size:12.5px;">${c.whatsappOptIn ? "✅ WhatsApp" : "🚫 WhatsApp"} · ${c.emailOptIn ? "✅ Email" : "🚫 Email"}</span></div>

    <h4 style="font-size:13px;margin-top:22px;margin-bottom:10px;">Purchase history</h4>
    ${purchaseHistory.length ? purchaseHistory.map(o => `
      <div class="settings-row"><div><div class="settings-row-title">${escapeHtml(o.orderNumber)}</div><div class="settings-row-sub"><a class="link-action" href="../orders/detail.html?id=${o.orderId}">${escapeHtml(o.marketplaceName)}</a> · ${fmtDate(new Date(o.placedAt))} · ${statusBadge(o.status)}</div></div><span class="cell-strong">${moneyDec(o.totalAmount)}</span></div>
    `).join("") : `<p class="text-muted" style="font-size:13px;">No purchases on record.</p>`}

    <div class="flex-between" style="margin-top:22px;">
      <h4 style="font-size:13px;margin:0;">Communication / notes</h4>
    </div>
    <div id="cust-notes" class="mt-8">
      ${notes.length ? notes.map(n => `<div class="settings-row"><div><div class="settings-row-title">${escapeHtml(n.noteText)}</div><div class="settings-row-sub">${escapeHtml(n.authorName)}</div></div><span class="text-muted" style="font-size:12px;">${fmtDateTime(new Date(n.createdAt))}</span></div>`).join("") : `<p class="text-muted" style="font-size:13px;">No notes logged yet.</p>`}
    </div>
    <div class="row-flex mt-8">
      <input type="text" class="text-input" id="cust-note-input" placeholder="Log a note or communication…" style="flex:1;">
      <button class="btn btn-sm btn-blue" id="cust-note-add">Add note</button>
    </div>
  `;
  $("#cust-note-add").addEventListener("click", async () => {
    const val = $("#cust-note-input").value.trim();
    if (!val) return;
    try {
      await api.customers.addNote(id, val);
      toast("Note logged to customer profile.", "success");
      await drawCustomerDetail(id);
    } catch (e) {
      toast(e.message || "Couldn't save this note.", "error");
    }
  });
}

/* -------------------------------- SEGMENTS (real API) -------------------------------- */
let crmSegments = [];
async function renderCrmSegments() {
  $("#content").innerHTML = crmHeader("segments", "Loading customer segments…", "") + `<div id="crm-tab-content"><div class="empty-state">Loading…</div></div>`;
  try { crmSegments = await api.segments.list() || []; drawSegmentTable(); } catch (e) { $("#crm-tab-content").innerHTML = `<div class="empty-state"><strong>Couldn't load segments.</strong><br>${escapeHtml(e.message)}</div>`; }
}
function drawSegmentTable() {
  $("#crm-tab-content").innerHTML = `<div class="card"><div class="card-head"><h3>Customer Segments</h3><a class="btn btn-sm btn-blue" href="segment-detail.html">+ Create segment</a></div><div class="table-wrap"><table class="data-table"><thead><tr><th>Segment</th><th>Rule</th><th>Members</th><th></th></tr></thead><tbody>${crmSegments.length ? crmSegments.map(s => `<tr><td class="cell-strong">${escapeHtml(s.name)}</td><td class="text-muted">${escapeHtml(s.description || s.ruleKey || "—")}</td><td><span class="badge blue">${s.memberCount || 0} customers</span></td><td><div class="action-group"><a class="icon-action" title="View" href="segment-detail.html?id=${s.id}">✎</a><button class="icon-action" title="Delete" onclick="deleteSegment(${s.id})">🗑</button></div></td></tr>`).join("") : `<tr><td colspan="4"><div class="empty-state">No segments configured.</div></td></tr>`}</tbody></table></div></div>`;
}
function deleteSegment(id) { openModal({ title: "Delete segment", danger: true, confirmLabel: "Delete segment", bodyHtml: `<p>This removes the segment permanently.</p>`, onSubmit: () => api.segments.remove(id).then(() => { toast("Segment deleted.", "success"); renderCrmSegments(); }).catch(e => toast(e.message, "error")) }); }
async function renderSegmentDetailPage(id) {
  let s = null; if (id) { try { s = (await api.segments.list()).find(x => Number(x.id) === Number(id)); } catch (e) { toast(e.message, "error"); } }
  const formHtml = `<div class="form-grid"><div class="form-field full"><label>Segment name</label><input name="name" value="${s ? escapeHtml(s.name) : ""}" required></div><div class="form-field full"><label>Rule</label><select name="ruleKey">${SEGMENT_RULE_OPTIONS.map(([k, label]) => `<option value="${k}" ${s && s.ruleKey === k ? "selected" : ""}>${label}</option>`).join("")}</select></div><div class="form-field full"><label>Description</label><textarea name="description">${s ? escapeHtml(s.description || "") : ""}</textarea></div></div>`;
  openPage({ title: s ? "Edit segment — " + s.name : "Create segment", eyebrow: "Relationships", backHref: "segments.html", confirmLabel: "Save segment", bodyHtml: formHtml + (s ? `<p class="text-muted mt-16">Current live membership: ${s.memberCount || 0} customers. Membership is recalculated from order history.</p>` : ""), onSubmit: data => {
    const payload = { name: data.name, ruleKey: data.ruleKey, description: data.description };
    const request = s ? api.segments.update(s.id, payload) : api.segments.create(payload);
    return request.then(() => { toast(s ? "Segment updated." : "Segment created.", "success"); location.href = "segments.html"; }).catch(e => toast(e.message, "error"));
  } });
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initCrmCustomers() { renderCrmCustomers(); }
function initCrmCustomerDetail() { renderCustomerDetailPage(Number(getQueryParam("id"))); }
function initCrmSegments() { renderCrmSegments(); }
function initCrmSegmentDetail() { renderSegmentDetailPage(getQueryParam("id")); }
