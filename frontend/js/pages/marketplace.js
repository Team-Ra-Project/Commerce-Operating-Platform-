/* ============================================================================
   MARKETPLACE INTEGRATION — Phase 5 (real backend)
   Buttons/actions: Connect Marketplace, Authorize/Connect, Test Connection,
   Sync Now, View Sync Log / Error Log, Disconnect Store.
   Backed by /api/marketplaces/* (see docs/BUTTON_ACTIONS.md section 2 and
   docs/FULL_WORKFLOW.md section 4). No mock data is used on this module's
   own pages; MARKETPLACE_DETAILS in data.js remains only for the dashboard
   and settings pages, which are outside this phase.
   index.html = grid. connect.html and sync-logs.html are dedicated pages.
   ============================================================================ */

const MARKETPLACE_META = {
  SHOPIFY:     { name: "Shopify",     color: "#95BF47" },
  AMAZON:      { name: "Amazon",      color: "#FF9900" },
  FLIPKART:    { name: "Flipkart",    color: "#2874F0" },
  WOOCOMMERCE: { name: "WooCommerce", color: "#7F54B3" },
  MAGENTO:     { name: "Magento",     color: "#EE672F" },
  MEESHO:      { name: "Meesho",      color: "#9F2089" },
  ETSY:        { name: "Etsy",        color: "#F1641E" }
};
const MARKETPLACE_ORDER = Object.keys(MARKETPLACE_META);
const OAUTH_MARKETPLACES = ["SHOPIFY", "ETSY"];

function mpStatusLabel(status) {
  return {
    NOT_CONNECTED: "Not Connected",
    CONNECTED: "Connected",
    SYNCING: "Syncing",
    ACTION_NEEDED: "Action Needed",
    DISCONNECTED: "Disconnected"
  }[status] || status;
}
function mpIsConnected(status) {
  return status !== "NOT_CONNECTED" && status !== "DISCONNECTED";
}

let mpCache = [];

/* -------------------------------- INDEX -------------------------------- */
function renderMarketplace() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Channels</div>
        <h1 class="page-title">Marketplace Integration</h1>
        <p class="page-sub">Manage connections, sync health, and listings across every channel you sell on.</p>
      </div>
      <div class="page-actions"><a class="btn btn-blue" href="connect.html">+ Connect marketplace</a></div>
    </div>

    <div class="grid grid-3" id="mp-grid"><div class="empty-state">Loading marketplace connections…</div></div>
  `;
  loadMarketplaceGrid();
}

async function loadMarketplaceGrid() {
  try {
    mpCache = await api.marketplaces.list();
    drawMarketplaceGrid();
  } catch (e) {
    $("#mp-grid").innerHTML = `<div class="empty-state"><strong>Couldn't load marketplace connections.</strong><br>${escapeHtml(e.message)}</div>`;
  }
}

function drawMarketplaceGrid() {
  $("#mp-grid").innerHTML = MARKETPLACE_ORDER.map(key => {
    const meta = MARKETPLACE_META[key];
    const m = mpCache.find(x => x.marketplaceName === key) || {
      marketplaceName: key,
      authType: OAUTH_MARKETPLACES.includes(key) ? "OAUTH" : "API_KEY",
      status: "NOT_CONNECTED",
      productSyncPct: 0, inventorySyncPct: 0, orderSyncPct: 0,
      lastSyncAt: null, publishedProductCount: 0
    };
    const connected = mpIsConnected(m.status);
    return `
    <div class="card mp-card">
      <div class="mp-top">
        <div class="mp-logo" style="background:${meta.color}">${meta.name[0]}</div>
        <div class="mp-meta">
          <strong>${meta.name}</strong>
          <span>${m.publishedProductCount} products listed · ${m.authType === "OAUTH" ? "OAuth" : "API Key"}</span>
        </div>
        ${statusBadge(mpStatusLabel(m.status))}
      </div>
      <div>
        <div class="mp-sync-row"><span>Product sync</span><strong>${m.productSyncPct}%</strong></div>
        <div class="rank-bar-track"><div class="rank-bar-fill" style="width:${m.productSyncPct}%;background:${meta.color}"></div></div>
        <div class="mp-sync-row"><span>Inventory sync</span><strong>${m.inventorySyncPct}%</strong></div>
        <div class="rank-bar-track"><div class="rank-bar-fill" style="width:${m.inventorySyncPct}%;background:${meta.color}"></div></div>
        <div class="mp-sync-row"><span>Order sync</span><strong>${m.orderSyncPct}%</strong></div>
        <div class="rank-bar-track"><div class="rank-bar-fill" style="width:${m.orderSyncPct}%;background:${meta.color}"></div></div>
      </div>
      <div class="mp-sync-row" style="border-top:none;padding-top:0;"><span>Last sync</span><span class="text-muted">${m.lastSyncAt ? fmtDateTime(new Date(m.lastSyncAt)) : "Never"}</span></div>
      <div class="action-group mt-8">
        ${connected
          ? `<button class="btn btn-sm" onclick="syncMarketplaceNow('${key}')">↻ Sync now</button>`
          : `<a class="btn btn-sm btn-blue" href="connect.html">Connect</a>`}
        <a class="btn btn-sm btn-ghost" href="sync-logs.html?name=${key}">View log</a>
        ${connected ? `<button class="btn btn-sm btn-danger" onclick="disconnectMarketplace('${key}')">Disconnect</button>` : ""}
      </div>
    </div>`;
  }).join("");
}

async function syncMarketplaceNow(name) {
  const meta = MARKETPLACE_META[name];
  toast(`Syncing ${meta.name}…`);
  try {
    await api.marketplaces.sync(name);
    toast(`${meta.name} sync complete.`, "success");
    await loadMarketplaceGrid();
    renderSidebar();
  } catch (e) {
    toast(e.message || `Sync failed for ${meta.name}.`, "error");
  }
}

function disconnectMarketplace(name) {
  const meta = MARKETPLACE_META[name];
  openModal({
    title: `Disconnect ${meta.name}`,
    danger: true,
    confirmLabel: "Disconnect store",
    bodyHtml: `<p style="font-size:13.5px;color:var(--navy-600);line-height:1.6;">This revokes stored credentials and stops future syncing for <strong>${meta.name}</strong>. Previously synced products, orders, and inventory data will remain in the platform but will no longer update.</p>`,
    onSubmit: async () => {
      try {
        await api.marketplaces.disconnect(name);
        toast(`${meta.name} disconnected.`, "success");
        await loadMarketplaceGrid();
      } catch (e) {
        toast(e.message || `Couldn't disconnect ${meta.name}.`, "error");
      }
    }
  });
}

/* ------------------------------ SYNC LOGS ------------------------------- */
async function renderSyncLogPage(name) {
  const meta = MARKETPLACE_META[name];
  if (!meta) { $("#content").innerHTML = `<div class="empty-state"><strong>Marketplace not found.</strong></div>`; return; }
  openPage({
    title: `${meta.name} — Sync & Error Log`,
    eyebrow: "Channels",
    backHref: "index.html",
    hideFooter: true,
    wide: true,
    bodyHtml: `<div id="mp-log-body"><div class="empty-state">Loading…</div></div>`
  });
  try {
    const [detail, logEntries] = await Promise.all([api.marketplaces.get(name), api.marketplaces.logs(name)]);
    $("#mp-log-body").innerHTML = `
      <div class="stat-strip" style="margin-top:0;padding-top:0;border-top:none;">
        <div><strong>${detail.productSyncPct}%</strong><span>Product sync</span></div>
        <div><strong>${detail.inventorySyncPct}%</strong><span>Inventory sync</span></div>
        <div><strong>${detail.orderSyncPct}%</strong><span>Order sync</span></div>
      </div>
      <h4 style="font-size:13px;margin-top:20px;margin-bottom:10px;">Recent sync activity</h4>
      ${logEntries.length ? logEntries.map(l => `
        <div class="settings-row"><div><div class="settings-row-title">${escapeHtml(l.event)}</div></div><span class="text-muted" style="font-size:12px;">${fmtDateTime(new Date(l.createdAt))}</span></div>
      `).join("") : `<div class="empty-state">No sync activity yet.</div>`}
      ${detail.status === "ACTION_NEEDED" ? `<p style="margin-top:16px;font-size:13px;color:var(--red-600);">⚠ This connection needs re-authentication. Click "Sync now" after reconnecting, or use Connect marketplace to re-authorize.</p>` : ""}
    `;
  } catch (e) {
    $("#mp-log-body").innerHTML = `<div class="empty-state"><strong>Couldn't load sync log.</strong><br>${escapeHtml(e.message)}</div>`;
  }
}

/* ---------------------------- CONNECT WIZARD ----------------------------- */
async function renderConnectWizardPage() {
  let step = 1;
  let chosen = null;
  let existingList = [];
  try { existingList = await api.marketplaces.list(); } catch (e) { /* treat all as not-connected if this fails */ }

  function isConnected(key) {
    const m = existingList.find(x => x.marketplaceName === key);
    return m && mpIsConnected(m.status);
  }

  function render() {
    if (step === 1) {
      openPage({
        title: "Connect marketplace — Step 1 of 3",
        eyebrow: "Select marketplace",
        backHref: "index.html",
        hideFooter: true,
        wide: true,
        bodyHtml: `
          <div class="grid grid-3">
            ${MARKETPLACE_ORDER.map(key => {
              const meta = MARKETPLACE_META[key];
              const connected = isConnected(key);
              return `<button type="button" class="mp-pick ${connected ? "mp-pick-disabled" : ""}" data-mp="${key}" ${connected ? "disabled" : ""}>
                <div class="mp-logo" style="background:${meta.color}">${meta.name[0]}</div>
                <span>${meta.name}</span>
                ${connected ? '<span class="text-muted" style="font-size:11px;">Already connected</span>' : '<span class="text-muted" style="font-size:11px;">Not connected</span>'}
              </button>`;
            }).join("")}
          </div>
        `
      });
      $$(".mp-pick:not(.mp-pick-disabled)").forEach(b => b.addEventListener("click", () => { chosen = b.dataset.mp; step = 2; render(); }));
    } else if (step === 2) {
      const meta = MARKETPLACE_META[chosen];
      const oauthType = OAUTH_MARKETPLACES.includes(chosen);
      openPage({
        title: `Connect marketplace — Step 2 of 3`,
        eyebrow: `Authorize ${meta.name}`,
        backHref: "index.html",
        hideFooter: true,
        bodyHtml: (oauthType ? `
          <p style="font-size:13.5px;color:var(--navy-600);line-height:1.6;">${meta.name} uses OAuth. Clicking below simulates the ${meta.name} consent screen and grants RA Studio access to your store through a sandbox adapter — this workspace has no live ${meta.name} OAuth client configured yet.</p>
        ` : `
          <div class="form-grid">
            <div class="form-field full"><label>API Key</label><input name="apiKey" placeholder="Enter ${meta.name} API key" required></div>
            <div class="form-field full"><label>API Secret</label><input name="apiSecret" type="password" placeholder="Enter API secret" required></div>
          </div>
        `) + `<div class="modal-foot" style="padding-left:0;padding-right:0;"><button type="button" class="btn" id="wiz-back">Back</button><button type="button" class="btn btn-blue" id="wiz-authorize">Authorize &amp; continue</button></div>`
      });
      $("#wiz-back").addEventListener("click", () => { step = 1; render(); });
      $("#wiz-authorize").addEventListener("click", async () => {
        let payload = {};
        if (!oauthType) {
          const key = $("[name=apiKey]"), secret = $("[name=apiSecret]");
          if (!key.value.trim() || !secret.value.trim()) { toast("Please enter both API key and secret.", "error"); return; }
          payload = { apiKey: key.value.trim(), apiSecret: secret.value.trim() };
        }
        const btn = $("#wiz-authorize");
        btn.disabled = true; btn.textContent = "Authorizing…";
        try {
          await api.marketplaces.authorize(chosen, payload);
          step = 3; render();
        } catch (e) {
          toast(e.message || "Authorization failed.", "error");
          btn.disabled = false; btn.textContent = "Authorize & continue";
        }
      });
    } else if (step === 3) {
      const meta = MARKETPLACE_META[chosen];
      openPage({
        title: `Connect marketplace — Step 3 of 3`,
        eyebrow: `Test connection to ${meta.name}`,
        hideFooter: true,
        bodyHtml: `<div id="test-conn-body" style="text-align:center;padding:20px 0;"><div class="spinner"></div><p style="margin-top:14px;font-size:13.5px;color:var(--navy-600);">Testing connection to ${meta.name}…</p></div>`
      });
      (async () => {
        try {
          await api.marketplaces.testConnection(chosen);
          $("#test-conn-body").innerHTML = `<div style="font-size:34px;">✅</div><p style="margin-top:10px;font-size:13.5px;color:var(--green-600);font-weight:600;">Connection successful — initial sync complete.</p>`;
          toast(`${meta.name} connected and synced.`, "success");
          renderSidebar();
          setTimeout(() => { location.href = "index.html"; }, 900);
        } catch (e) {
          $("#test-conn-body").innerHTML = `
            <div style="font-size:34px;">⚠️</div>
            <p style="margin-top:10px;font-size:13.5px;color:var(--red-600);font-weight:600;">${escapeHtml(e.message || "Connection test failed.")}</p>
            <div class="modal-foot" style="padding-left:0;padding-right:0;justify-content:center;"><button type="button" class="btn" id="wiz-retry">Back to credentials</button></div>
          `;
          $("#wiz-retry").addEventListener("click", () => { step = 2; render(); });
        }
      })();
    }
  }
  render();
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initMarketplaceIndex() { renderMarketplace(); }
function initMarketplaceConnect() { renderConnectWizardPage(); }
function initMarketplaceSyncLogs() { renderSyncLogPage(getQueryParam("name")); }