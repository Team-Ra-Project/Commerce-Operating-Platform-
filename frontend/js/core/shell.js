/* ============================================================================
   APPLICATION SHELL — shared across every authenticated page.
   Each HTML page sets two globals BEFORE this script loads:
     window.__BASE__   "" for root-level pages, "../" for pages one folder deep
     window.__MODULE__ the nav id this page belongs to (see MODULE_PATHS below)
   This file renders the sidebar + wires the header, and replaces the old
   SPA router (navigateTo) with real page navigation — every onclick/data-nav
   attribute copied over from the original app still works unchanged because
   navigateTo() below still exists, it just now sets location.href instead of
   swapping an in-page section.
   ============================================================================ */

const NAV_ITEMS = [
  { id: "executive", label: "Executive Dashboard", icon: "📊" },
  { id: "marketplace", label: "Marketplace Integration", icon: "🛒" },
  { id: "products", label: "Product Management", icon: "🏷️" },
  { id: "inventory", label: "Inventory Management", icon: "📦" },
  { id: "orders", label: "Order Management", icon: "🧾" },
  { id: "returns", label: "Returns & Shipping", icon: "↩️" },
  { id: "crm", label: "CRM", icon: "👥" },
  { id: "competitor", label: "Competitor Analysis", icon: "🎯" },
  { id: "automation", label: "Automation", icon: "⚙️" },
  { id: "analytics", label: "Analytics & Reporting", icon: "📈" },
  { id: "bi", label: "Business Intelligence", icon: "🧠" },
  { id: "retention", label: "Retention Marketing", icon: "📣" },
  { id: "messaging", label: "WhatsApp & Email", icon: "💬" },
  { id: "broadcasts", label: "Bulk Sharing", icon: "📡" },
  { id: "settings", label: "Settings", icon: "⚙" }
];

const MODULE_PATHS = {
  executive: "dashboard.html",
  marketplace: "marketplace/index.html",
  products: "products/index.html",
  inventory: "inventory/index.html",
  orders: "orders/index.html",
  returns: "returns/index.html",
  crm: "crm/customers.html",
  competitor: "competitors/index.html",
  automation: "automation/index.html",
  analytics: "analytics/reports.html",
  bi: "business-intelligence/index.html",
  retention: "retention/index.html",
  messaging: "messaging/templates.html",
  broadcasts: "broadcasts/index.html",
  settings: "settings/index.html"
};

let currentModule = window.__MODULE__ || "executive";
let dashboardFilters = { range: "30", store: "All" };

/* ---------------------------------------------------------------------------
   AUTH GUARD + LOGOUT (login itself is handled inline on login.html)
--------------------------------------------------------------------------- */
(function authGuard() {
  if (!localStorage.getItem("ra_access_token")) {
    location.href = (window.__BASE__ || "") + "login.html";
  }
})();

/* BUGFIX: the topbar user chip ("Alex Nair" / "Operations Admin") and the
   sidebar "Workspace: Northwind Retail Co." line were static placeholder
   markup on every page, never replaced with the real signed-in user even
   though api.auth.login()/me() already store it in localStorage as
   "ra_user". Fill them in from that real session data, with the old
   placeholder copy only as a fallback if it's ever missing. */
const ROLE_LABELS = {
  BUSINESS_OWNER_ADMIN: "Business Owner / Admin",
  OPERATIONS_MANAGER: "Operations Manager",
  MARKETING_MANAGER: "Marketing Manager",
  SUPPORT_CRM_AGENT: "Support / CRM Agent",
  WAREHOUSE_STAFF: "Warehouse Staff",
  ANALYST_VIEWER: "Analyst / Viewer"
};

function initials(name) {
  if (!name) return "??";
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] || "") + (parts[1]?.[0] || "")).toUpperCase() || name.slice(0, 2).toUpperCase();
}

function renderUserIdentity() {
  let user = null;
  try { user = JSON.parse(localStorage.getItem("ra_user") || "null"); } catch (e) { user = null; }
  if (!user) return;
  const nameEl = $(".user-chip-text strong");
  const roleEl = $(".user-chip-text span");
  const avatarEl = $(".user-chip .avatar");
  if (nameEl) nameEl.textContent = user.fullName || nameEl.textContent;
  if (roleEl) roleEl.textContent = ROLE_LABELS[user.role] || user.role || roleEl.textContent;
  if (avatarEl) avatarEl.textContent = initials(user.fullName);
  const workspaceEl = $(".sidebar-plan p");
  if (workspaceEl && window.api && api.request) {
    api.request("/organization").then((org) => {
      if (org && org.name) workspaceEl.textContent = `Workspace: ${org.name}`;
    }).catch(() => null);
  }
}

function wireLogout() {
  const el = $("#logout-link");
  if (el) el.addEventListener("click", (e) => {
    e.preventDefault();
    const finish = () => {
      localStorage.removeItem("ra_access_token");
      localStorage.removeItem("ra_refresh_token");
      localStorage.removeItem("ra_user");
      location.href = (window.__BASE__ || "") + "login.html";
    };
    if (window.api && api.auth) api.auth.logout().catch(() => null).finally(finish); else finish();
  });
}

/* ---------------------------------------------------------------------------
   SIDEBAR / NAVIGATION  (real multi-page navigation, not an in-page router)
--------------------------------------------------------------------------- */
async function renderSidebar() {
  const nav = $("#sidebar-nav");
  if (!nav) return;
  nav.innerHTML = NAV_ITEMS.map(item => `
    <button class="nav-item ${item.id === currentModule ? "active" : ""}" data-nav="${item.id}">
      <span class="nav-icon">${item.icon}</span>
      <span class="nav-label">${item.label}</span>
      ${item.id === "inventory" ? `<span class="nav-badge" id="inventory-nav-badge" hidden></span>` : ""}
      ${item.id === "messaging" || item.id === "retention" ? `<span class="nav-badge" id="approval-nav-badge" hidden></span>` : ""}
    </button>
  `).join("");
  $$(".nav-item", nav).forEach(btn => btn.addEventListener("click", () => navigateTo(btn.dataset.nav)));
  try {
    const [inventory, templates] = await Promise.all([api.inventory.list(), api.templates.list()]);
    const rows = inventory && inventory.rows ? inventory.rows : [];
    const lowStock = rows.filter(row => row.status && row.status !== "In Stock").length;
    const pendingApprovals = (templates || []).filter(t => ["PENDING_APPROVAL", "Pending Approval"].includes(t.status)).length;
    const inventoryBadge = $("#inventory-nav-badge");
    const approvalBadges = $$("#approval-nav-badge");
    if (inventoryBadge) {
      inventoryBadge.textContent = lowStock;
      inventoryBadge.hidden = lowStock === 0;
    }
    approvalBadges.forEach(badge => {
      badge.textContent = pendingApprovals;
      badge.hidden = pendingApprovals === 0;
    });
  } catch (_) {
    // The page remains navigable when the optional sidebar counters cannot load.
  }
}

/* Real page navigation — same call signature as the old SPA router, so every
   existing data-nav / navigateTo("x") call site across every page works
   unchanged. opts is accepted (and ignored) for the same reason. */
function navigateTo(moduleId, opts = {}) {
  const path = MODULE_PATHS[moduleId] || "dashboard.html";
  location.href = (window.__BASE__ || "") + path;
}

document.addEventListener("click", (e) => {
  const navEl = e.target.closest("[data-nav]");
  if (navEl && navEl.tagName === "A") { e.preventDefault(); navigateTo(navEl.dataset.nav); closePanels(); }
});
document.addEventListener("click", (e) => {
  const tEl = e.target.closest("[data-nav-target]");
  if (tEl) navigateTo(tEl.dataset.navTarget);
});

function initSidebarToggle() {
  const btn = $("#sidebar-toggle");
  if (btn) btn.addEventListener("click", () => {
    $("#app-shell").classList.toggle("sidebar-collapsed");
    $("#app-shell").classList.toggle("mobile-nav-open");
  });
}

/* ---------------------------------------------------------------------------
   TOPBAR PANELS — notifications + user menu
--------------------------------------------------------------------------- */
function initTopbarPanels() {
  const notifBtn = $("#notif-btn");
  if (notifBtn) notifBtn.addEventListener("click", (e) => { e.stopPropagation(); toggleNotifPanel(); });
  const userBtn = $("#user-menu-btn");
  if (userBtn) userBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    $("#user-panel").classList.toggle("hidden");
    $("#notif-panel").classList.add("hidden");
  });
  document.addEventListener("click", () => closePanels());
  if (window.api && api.notifications) api.notifications.unreadCount().then(result => {
    const count = Number(result && (result.count ?? result.unreadCount) || 0);
    const dot = $("#notif-dot");
    if (dot) dot.style.display = count ? "" : "none";
  }).catch(() => null);
}
function closePanels() {
  if ($("#notif-panel")) $("#notif-panel").classList.add("hidden");
  if ($("#user-panel")) $("#user-panel").classList.add("hidden");
}
async function toggleNotifPanel() {
  const panel = $("#notif-panel");
  const willOpen = panel.classList.contains("hidden");
  closePanels();
  if (willOpen) {
    panel.innerHTML = `<div class="notif-panel-head">Notifications <button class="btn btn-sm" id="mark-all-notifications">Mark all as read</button></div><div class="empty-state">Loading…</div>`;
    panel.classList.remove("hidden");
    try {
      const notifications = await api.notifications.list();
      panel.innerHTML = `<div class="notif-panel-head">Notifications <button class="btn btn-sm" id="mark-all-notifications">Mark all as read</button></div>` +
        ((notifications || []).length ? notifications.map(n => `<div class="notif-item ${n.isRead ? "" : "unread"}" data-notification-id="${n.id}" ${n.linkModule ? `data-nav-target="${escapeHtml(n.linkModule)}"` : ""}><div class="n-ico">●</div><div><strong>${escapeHtml(n.title)}</strong><span>${n.createdAt ? fmtDateTime(new Date(n.createdAt)) : ""}</span></div></div>`).join("") : `<div class="empty-state">No notifications.</div>`);
      $("#mark-all-notifications").addEventListener("click", e => { e.stopPropagation(); api.notifications.readAll().then(() => { if ($("#notif-dot")) $("#notif-dot").style.display = "none"; toggleNotifPanel(); }).catch(err => toast(err.message, "error")); });
      $$(".notif-item", panel).forEach(item => item.addEventListener("click", () => {
        const notificationId = item.dataset.notificationId;
        api.notifications.read(notificationId).catch(() => null);
        if (item.dataset.navTarget) navigateTo(item.dataset.navTarget);
      }));
      if ($("#notif-dot")) $("#notif-dot").style.display = "none";
    } catch (e) {
      panel.innerHTML = `<div class="notif-panel-head">Notifications</div><div class="empty-state"><strong>Couldn't load notifications.</strong><br>${escapeHtml(e.message)}</div>`;
    }
  }
}

/* ---------------------------------------------------------------------------
   GLOBAL SEARCH — jumps to the matching module's list page with ?q= set;
   the target page's own init reads it and pre-fills/applies its filter.
--------------------------------------------------------------------------- */
function initGlobalSearch() {
  const el = $("#global-search");
  if (!el) return;
  el.addEventListener("keydown", async (e) => {
    if (e.key !== "Enter") return;
    const q = e.target.value.trim();
    if (!q) return;
    const ql = q.toLowerCase();
    const base = window.__BASE__ || "";
    try {
      const [products, orders, customers] = await Promise.all([api.products.list(), api.orders.list(), api.customers.list()]);
      if ((products || []).some(p => String(p.name || "").toLowerCase().includes(ql))) {
        location.href = base + "products/index.html?q=" + encodeURIComponent(q); return;
      }
      if ((orders || []).some(o => [o.orderNumber, o.id, o.customerName].some(value => String(value || "").toLowerCase().includes(ql)))) {
        location.href = base + "orders/index.html?q=" + encodeURIComponent(q); return;
      }
      if ((customers || []).some(c => String(c.fullName || c.name || "").toLowerCase().includes(ql))) {
        location.href = base + "crm/customers.html?q=" + encodeURIComponent(q); return;
      }
      toast(`No results for "${q}".`);
    } catch (error) {
      toast(error.message || "Search is unavailable right now.", "error");
    }
  });
}

/* ---------------------------------------------------------------------------
   SHELL BOOTSTRAP — call once per page, after the DOM's shell markup exists
--------------------------------------------------------------------------- */
function initShell() {
  renderSidebar();
  renderUserIdentity();
  wireLogout();
  initSidebarToggle();
  initTopbarPanels();
  initGlobalSearch();
}

/* ---------------------------------------------------------------------------
   SVG CHART HELPERS
--------------------------------------------------------------------------- */
function lineChartSVG(data, opts = {}) {
  const w = opts.width || 640, h = opts.height || 220, pad = 28;
  if (!data || data.length === 0) return `<svg viewBox="0 0 ${w} ${h}"></svg>`;
  if (data.length === 1) {
    const d = data[0];
    return `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">
      <circle cx="${w / 2}" cy="${h / 2}" r="4" fill="#7C4FEE"/>
      <text x="${w / 2}" y="${h / 2 - 14}" font-size="12" fill="#33415C" text-anchor="middle">${d.value}</text>
      <text x="${w / 2}" y="${h - 6}" font-size="10.5" fill="#8894AC" text-anchor="middle">${d.label}</text>
    </svg>`;
  }
  const max = Math.max(...data.map(d => d.value)) * 1.15 || 1;
  const min = 0;
  const stepX = (w - pad * 2) / (data.length - 1);
  const points = data.map((d, i) => {
    const x = pad + i * stepX;
    const y = h - pad - ((d.value - min) / (max - min)) * (h - pad * 1.6);
    return [x, y];
  });
  const linePath = points.map((p, i) => (i === 0 ? "M" : "L") + p[0].toFixed(1) + "," + p[1].toFixed(1)).join(" ");
  const areaPath = linePath + ` L${points[points.length - 1][0]},${h - pad} L${points[0][0]},${h - pad} Z`;
  const dots = points.map((p, i) => `<circle cx="${p[0]}" cy="${p[1]}" r="3.5" fill="#7C4FEE" class="chart-dot"><title>${data[i].label}: ${data[i].value}</title></circle>`).join("");
  const labels = data.map((d, i) => `<text x="${points[i][0]}" y="${h - 6}" font-size="10.5" fill="#8894AC" text-anchor="middle">${d.label}</text>`).join("");
  const gridLines = [0, 1, 2, 3].map(i => {
    const y = pad + (i * (h - pad * 1.6)) / 3;
    return `<line x1="${pad}" y1="${y}" x2="${w - pad}" y2="${y}" stroke="#EEF1F7" stroke-width="1"/>`;
  }).join("");
  return `
  <svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">
    <defs>
      <linearGradient id="areaFill" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#7C4FEE" stop-opacity="0.18"/>
        <stop offset="100%" stop-color="#7C4FEE" stop-opacity="0"/>
      </linearGradient>
    </defs>
    ${gridLines}
    <path d="${areaPath}" fill="url(#areaFill)" />
    <path d="${linePath}" fill="none" stroke="#7C4FEE" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"/>
    ${dots}
    ${labels}
  </svg>`;
}

function barChartSVG(data, opts = {}) {
  const w = opts.width || 640, h = opts.height || 220, pad = 28;
  if (!data || data.length === 0) return `<svg viewBox="0 0 ${w} ${h}"></svg>`;
  const max = Math.max(...data.map(d => d.value)) * 1.15 || 1;
  const barW = ((w - pad * 2) / data.length) * 0.55;
  const gap = ((w - pad * 2) / data.length);
  const bars = data.map((d, i) => {
    const barH = (d.value / max) * (h - pad * 1.6);
    const x = pad + i * gap + (gap - barW) / 2;
    const y = h - pad - barH;
    return `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barW.toFixed(1)}" height="${barH.toFixed(1)}" rx="4" fill="#9B7BF5"><title>${d.label}: ${d.value}</title></rect>`;
  }).join("");
  const labels = data.map((d, i) => {
    const x = pad + i * gap + gap / 2;
    return `<text x="${x}" y="${h - 6}" font-size="10.5" fill="#8894AC" text-anchor="middle">${d.label}</text>`;
  }).join("");
  return `<svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">${bars}${labels}</svg>`;
}

/* ---------------------------------------------------------------------------
   STATUS BADGES + KPI CARD
--------------------------------------------------------------------------- */
function statusBadge(status) {
  const map = { "Connected": "green", "Syncing": "blue", "Action Needed": "amber", "Disconnected": "gray", "Not Connected": "gray", "In Stock": "green", "Low Stock": "amber", "Out of Stock": "red",
    "Paid": "green", "Pending": "amber", "Refunded": "blue", "Failed": "red",
    "New": "blue", "Confirmed": "blue", "Packed": "amber", "Shipped": "amber", "Out for Delivery": "amber", "Delivered": "green",
    "Approved": "green", "Approved – Awaiting Item": "blue", "Received – Inspecting": "amber", "Refunded/Replaced": "green", "Replaced": "green", "Rejected": "red", "Requested": "amber", "Closed": "gray",
    "Active": "green", "Draft": "gray", "Published": "green", "Paused": "gray", "Completed": "blue",
    "Sent": "blue", "Queued": "gray", "Read": "green", "Bounced": "red",
    "Pending Approval": "amber" };
  return `<span class="badge ${map[status] || "gray"}" style="margin-left:auto">${status}</span>`;
}

function kpiCard(label, value, delta, dir, icon, wide = false, navTarget = null) {
  return `
    <div class="card kpi-card ${navTarget ? "kpi-clickable" : ""}" ${navTarget ? `data-nav-target="${navTarget}"` : ""}>
      <div class="kpi-top">
        <span class="kpi-label">${label}</span>
        <div class="kpi-icon">${icon}</div>
      </div>
      <div class="kpi-value">${value}</div>
      ${delta ? `<span class="kpi-delta ${dir}">${dir === "up" ? "▲" : "▼"} ${delta} vs last month</span>` : `<span class="kpi-delta neutral">Current</span>`}
    </div>`;
}

/* ---------------------------------------------------------------------------
   MODAL SYSTEM — unchanged from the original SPA; still used for short,
   simple actions (confirmations, quick edits) per the multi-page brief.
--------------------------------------------------------------------------- */
function openModal({ title, bodyHtml, onSubmit, confirmLabel = "Save", danger = false, wide = false, hideFooter = false }) {
  const root = $("#modal-root");
  root.innerHTML = `
    <div class="modal-backdrop" id="modal-backdrop">
      <div class="modal-box" style="${wide ? "max-width:680px;" : ""}">
        <div class="modal-head">
          <h3>${title}</h3>
          <button class="icon-btn" id="modal-close">✕</button>
        </div>
        <form id="modal-form">
          <div class="modal-body">${bodyHtml}</div>
          ${hideFooter ? "" : `
          <div class="modal-foot">
            <button type="button" class="btn" id="modal-cancel">Cancel</button>
            <button type="submit" class="btn ${danger ? "btn-danger" : "btn-blue"}">${confirmLabel}</button>
          </div>`}
        </form>
      </div>
    </div>
  `;
  const close = () => { root.innerHTML = ""; };
  $("#modal-close").addEventListener("click", close);
  if ($("#modal-cancel")) $("#modal-cancel").addEventListener("click", close);
  $("#modal-backdrop").addEventListener("click", (e) => { if (e.target.id === "modal-backdrop") close(); });
  $("#modal-form").addEventListener("submit", (e) => {
    e.preventDefault();
    if (!onSubmit) return;
    const data = {};
    new FormData(e.target).forEach((v, k) => data[k] = v);
    onSubmit(data);
    close();
  });
}

/* ---------------------------------------------------------------------------
   PAGE-CARD — the "detail/create page" equivalent of openModal(). Same body
   markup and CSS classes as a modal (modal-body/modal-foot/form-grid, etc,
   which are all unscoped in style.css) so content copies over unchanged;
   it just mounts into the page instead of an overlay, with a Back link.
--------------------------------------------------------------------------- */
function openPage({ title, eyebrow = "", subtitle = "", backHref = null, backLabel = "← Back", bodyHtml, onSubmit, confirmLabel = "Save", danger = false, hideFooter = false, wide = false }) {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        ${backHref ? `<a href="${backHref}" class="link-action" style="display:inline-block;margin-bottom:8px;">${backLabel}</a>` : ""}
        ${eyebrow ? `<div class="page-eyebrow">${eyebrow}</div>` : ""}
        <h1 class="page-title">${title}</h1>
        ${subtitle ? `<p class="page-sub">${subtitle}</p>` : ""}
      </div>
    </div>
    <div class="card" style="${wide ? "" : "max-width:760px;"}">
      <form id="page-form">
        <div class="modal-body">${bodyHtml}</div>
        ${hideFooter ? "" : `
        <div class="modal-foot">
          ${backHref ? `<a href="${backHref}" class="btn">Cancel</a>` : ""}
          <button type="submit" class="btn ${danger ? "btn-danger" : "btn-blue"}">${confirmLabel}</button>
        </div>`}
      </form>
    </div>
  `;
  if (onSubmit) {
    $("#page-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const data = {};
      new FormData(e.target).forEach((v, k) => data[k] = v);
      onSubmit(data);
    });
  }
}

/* ---------------------------------------------------------------------------
   TOASTS
--------------------------------------------------------------------------- */
function toast(message, type = "") {
  const root = $("#toast-root");
  const el = document.createElement("div");
  el.className = `toast ${type}`;
  el.textContent = message;
  root.appendChild(el);
  setTimeout(() => {
    el.style.opacity = "0";
    el.style.transform = "translateY(6px)";
    el.style.transition = "all .2s";
    setTimeout(() => el.remove(), 220);
  }, 2600);
}