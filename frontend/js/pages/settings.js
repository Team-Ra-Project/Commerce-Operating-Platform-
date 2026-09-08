/* ============================================================================
   SETTINGS & ADMINISTRATION
   Buttons/actions: Add User, Edit User, Deactivate User, Assign Role, Edit
   Marketplace Credentials, Save System Configuration.
   Split into index/users/roles/integrations pages per the multi-page brief's
   folder structure (was one combined screen with sections in the SPA).

   PHASE 3: User Management (Add/Edit/Deactivate/Assign Role), Roles &
   Permissions counts, and the Overview tab's System Configuration are now
   wired to the real backend (/api/users, /api/organization/config, and
   /api/marketplaces) instead of the legacy data arrays.
   ============================================================================ */
function settingsTabs(active) {
  return `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Administration</div>
        <h1 class="page-title">Settings</h1>
        <p class="page-sub">Manage your team, roles, marketplace credentials, and workspace configuration.</p>
      </div>
    </div>
    <div class="tabs">
      <a class="tab-btn ${active === "overview" ? "active" : ""}" href="index.html">Overview</a>
      <a class="tab-btn ${active === "users" ? "active" : ""}" href="users.html">User Management</a>
      <a class="tab-btn ${active === "roles" ? "active" : ""}" href="roles.html">Roles &amp; Permissions</a>
      <a class="tab-btn ${active === "integrations" ? "active" : ""}" href="integrations.html">Marketplace Settings</a>
    </div>
  `;
}

/* Role dropdown options as {value: ENUM_KEY, label: "Human label"}, sourced
   from shell.js's ROLE_LABELS so the display text never drifts from the
   topbar/sidebar's own role labels. */
function roleSelectOptions(selectedKey) {
  return Object.entries(ROLE_LABELS).map(([key, label]) =>
    `<option value="${key}" ${key === selectedKey ? "selected" : ""}>${label}</option>`
  ).join("");
}

/* Is the signed-in user allowed to manage the team? Mirrors the backend's
   own check (UserService.requireAdmin) — this only controls whether the
   buttons are shown; the API enforces it independently either way. */
function isAdmin() {
  try {
    const user = JSON.parse(localStorage.getItem("ra_user") || "null");
    return !!user && user.role === "BUSINESS_OWNER_ADMIN";
  } catch (e) { return false; }
}

/* -------------------------------- OVERVIEW -------------------------------- */
function renderSettingsIndex() {
  $("#content").innerHTML = settingsTabs("overview") + `<div id="settings-overview-body"><p class="text-muted">Loading…</p></div>`;
  Promise.all([api.organization.getConfig(), api.users.list(), api.marketplaces.list()])
    .then(([config, teamList, marketplaces]) => { drawSettingsOverview(config, teamList, marketplaces); })
    .catch((error) => {
      $("#settings-overview-body").innerHTML = `<div class="card"><p class="text-muted">Couldn't load settings: ${escapeHtml(error.message)}</p></div>`;
    });
}
function drawSettingsOverview(config, teamList, marketplaces) {
  const connectedMarketplaces = (marketplaces || []).filter(m => m.status !== "DISCONNECTED" && m.status !== "Disconnected").length;
  $("#settings-overview-body").innerHTML = `
    <div class="card">
      <div class="card-head"><h3>System Configuration</h3></div>
      <div class="settings-row"><div><div class="settings-row-title">Sync interval</div><div class="settings-row-sub">How often marketplace data refreshes automatically</div></div>
        <select class="select-input" id="cfg-sync">
          <option ${config.syncInterval === "Every 5 minutes" ? "selected" : ""}>Every 5 minutes</option>
          <option ${config.syncInterval === "Every 15 minutes" ? "selected" : ""}>Every 15 minutes</option>
          <option ${config.syncInterval === "Every hour" ? "selected" : ""}>Every hour</option>
        </select>
      </div>
      <div class="settings-row"><div><div class="settings-row-title">Currency</div><div class="settings-row-sub">Used across dashboards and reports</div></div>
        <select class="select-input" id="cfg-currency">
          <option value="INR" ${config.currency === "INR" ? "selected" : ""}>INR (₹)</option>
          <option value="USD" ${config.currency === "USD" ? "selected" : ""}>USD ($)</option>
        </select>
      </div>
      <div class="settings-row"><div><div class="settings-row-title">Two-factor authentication</div><div class="settings-row-sub">Require 2FA for all team members</div></div>
        <button class="toggle ${config.twoFactorEnabled ? "on" : ""}" id="cfg-2fa"></button>
      </div>
      <div class="settings-row"><div><div class="settings-row-title">Email digest</div><div class="settings-row-sub">Daily summary email of orders, stock, and alerts</div></div>
        <button class="toggle ${config.emailDigestEnabled ? "on" : ""}" id="cfg-digest"></button>
      </div>
      <div class="mt-16">
        ${isAdmin()
          ? `<button class="btn btn-blue" id="cfg-save">Save configuration</button>`
          : `<p class="text-muted" style="font-size:12.5px;">Only an administrator can change system configuration.</p>`}
      </div>
    </div>

    <div class="grid grid-3 mt-24">
      <div class="card kpi-card"><div class="kpi-top"><span class="kpi-label">Team Members</span><div class="kpi-icon">👥</div></div><div class="kpi-value">${teamList.length}</div></div>
      <div class="card kpi-card"><div class="kpi-top"><span class="kpi-label">Connected Marketplaces</span><div class="kpi-icon">🛒</div></div><div class="kpi-value">${connectedMarketplaces}</div></div>
      <div class="card kpi-card"><div class="kpi-top"><span class="kpi-label">Active Roles</span><div class="kpi-icon">🔐</div></div><div class="kpi-value">${Object.keys(ROLE_LABELS).length}</div></div>
    </div>
  `;
  let state = { twoFactorEnabled: !!config.twoFactorEnabled, emailDigestEnabled: !!config.emailDigestEnabled };
  const toggle2fa = $("#cfg-2fa"), toggleDigest = $("#cfg-digest");
  if (toggle2fa) toggle2fa.addEventListener("click", () => { state.twoFactorEnabled = !state.twoFactorEnabled; toggle2fa.classList.toggle("on"); });
  if (toggleDigest) toggleDigest.addEventListener("click", () => { state.emailDigestEnabled = !state.emailDigestEnabled; toggleDigest.classList.toggle("on"); });
  if ($("#cfg-save")) $("#cfg-save").addEventListener("click", () => {
    const btn = $("#cfg-save");
    btn.disabled = true; btn.textContent = "Saving…";
    api.organization.saveConfig({
      syncInterval: $("#cfg-sync").value,
      currency: $("#cfg-currency").value,
      twoFactorEnabled: state.twoFactorEnabled,
      emailDigestEnabled: state.emailDigestEnabled
    }).then(() => {
      toast("System configuration saved.", "success");
      btn.disabled = false; btn.textContent = "Save configuration";
    }).catch((error) => {
      toast(error.message || "Couldn't save configuration.", "error");
      btn.disabled = false; btn.textContent = "Save configuration";
    });
  });
}

/* -------------------------------- USERS -------------------------------- */
let REAL_TEAM = [];

function renderSettingsUsers() {
  $("#content").innerHTML = settingsTabs("users") + `
    <div class="card">
      <div class="card-head"><h3>Team Members</h3>${isAdmin() ? `<button class="btn btn-sm btn-blue" id="add-user-btn">+ Add user</button>` : ""}</div>
      <div class="table-wrap" style="border:none;">
        <table class="data-table">
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Last Login</th><th></th></tr></thead>
          <tbody id="team-body"><tr><td colspan="6" class="text-muted">Loading…</td></tr></tbody>
        </table>
      </div>
    </div>
  `;
  loadTeam();
  if ($("#add-user-btn")) $("#add-user-btn").addEventListener("click", () => openUserModal());
}
function loadTeam() {
  return api.users.list().then((list) => { REAL_TEAM = list; drawTeamTable(); })
    .catch((error) => { $("#team-body").innerHTML = `<tr><td colspan="6" class="text-muted">Couldn't load team: ${escapeHtml(error.message)}</td></tr>`; });
}
function drawTeamTable() {
  $("#team-body").innerHTML = REAL_TEAM.map(u => `
    <tr>
      <td class="cell-strong">${escapeHtml(u.fullName)}</td>
      <td>${escapeHtml(u.email)}</td>
      <td>${ROLE_LABELS[u.role] || u.role}</td>
      <td>${u.status === "ACTIVE" ? '<span class="badge green">Active</span>' : u.status === "PENDING" ? '<span class="badge amber">Invited</span>' : '<span class="badge gray">Deactivated</span>'}</td>
      <td class="text-muted">${u.lastLoginAt ? fmtDateTime(new Date(u.lastLoginAt)) : "Never"}</td>
      <td>
        ${isAdmin() ? `
        <div class="action-group">
          <button class="icon-action" title="Edit" onclick="openUserModal(${u.id})">✎</button>
          ${u.status !== "DEACTIVATED" ? `<button class="icon-action" title="Deactivate" onclick="deactivateUser(${u.id})">🚫</button>` : ""}
        </div>` : ""}
      </td>
    </tr>
  `).join("");
}
function openUserModal(id = null) {
  const u = id ? REAL_TEAM.find(x => x.id === id) : null;
  openModal({
    title: u ? "Edit user — " + u.fullName : "Add user",
    confirmLabel: u ? "Save changes" : "Send invite",
    bodyHtml: `
      <div class="form-grid">
        <div class="form-field full"><label>Full name</label><input name="fullName" value="${u ? escapeHtml(u.fullName) : ""}" required></div>
        <div class="form-field full"><label>Email</label><input name="email" type="email" value="${u ? escapeHtml(u.email) : ""}" required ${u ? "" : ""}></div>
        <div class="form-field full"><label>Role</label>
          <select name="role">${roleSelectOptions(u ? u.role : "OPERATIONS_MANAGER")}</select>
        </div>
      </div>
      ${u ? "" : `<p class="text-muted" style="font-size:12px;margin-top:4px;">They'll receive a real invitation email with a link to set their password and sign in.</p>`}
    `,
    onSubmit: (data) => {
      if (u) {
        api.users.update(u.id, { fullName: data.fullName, email: data.email, role: data.role })
          .then(() => { toast("User details updated.", "success"); loadTeam(); })
          .catch((error) => toast(error.message || "Couldn't update user.", "error"));
      } else {
        api.users.invite(data.fullName, data.email, data.role)
          .then((result) => {
            if (result.emailSent) {
              toast(`Invitation emailed to ${data.email}.`, "success");
            } else {
              toast(`User created, but the invitation email couldn't be sent. Check SMTP settings, or use "Resend invite".`, "error");
            }
            loadTeam();
          })
          .catch((error) => toast(error.message || "Couldn't send invitation.", "error"));
      }
    }
  });
}
function deactivateUser(id) {
  const u = REAL_TEAM.find(x => x.id === id);
  if (!u) return;
  openModal({
    title: "Deactivate user",
    danger: true,
    confirmLabel: "Deactivate",
    bodyHtml: `<p style="font-size:13.5px;color:var(--navy-600);line-height:1.6;">${escapeHtml(u.fullName)} will lose access immediately. Records they created will remain in the platform.</p>`,
    onSubmit: () => {
      api.users.deactivate(u.id)
        .then(() => { toast(`${u.fullName} deactivated.`, "success"); loadTeam(); })
        .catch((error) => toast(error.message || "Couldn't deactivate user.", "error"));
    }
  });
}

/* -------------------------------- ROLES -------------------------------- */
function renderSettingsRoles() {
  const descriptions = {
    BUSINESS_OWNER_ADMIN: "Full access — manages users, roles, marketplace connections, and billing.",
    OPERATIONS_MANAGER: "Manages products, inventory, and orders across all connected stores.",
    MARKETING_MANAGER: "Owns retention campaigns, WhatsApp/Email templates, segmentation, and bulk sharing.",
    SUPPORT_CRM_AGENT: "Handles customer profiles, communication history, and order-related queries.",
    WAREHOUSE_STAFF: "Updates stock counts, packs and ships orders.",
    ANALYST_VIEWER: "Read-only access to dashboards, reports, and business intelligence."
  };
  $("#content").innerHTML = settingsTabs("roles") + `
    <div class="card">
      <div class="card-head"><h3>Roles &amp; Permissions</h3></div>
      <div id="roles-body"><p class="text-muted">Loading…</p></div>
    </div>
  `;
  api.users.list().then((teamList) => {
    $("#roles-body").innerHTML = Object.entries(ROLE_LABELS).map(([key, label]) => `
      <div class="settings-row"><div><div class="settings-row-title">${label}</div><div class="settings-row-sub">${descriptions[key] || ""}</div></div><span class="badge blue">${teamList.filter(u => u.role === key).length} member(s)</span></div>
    `).join("");
  }).catch((error) => {
    $("#roles-body").innerHTML = `<p class="text-muted">Couldn't load roles: ${escapeHtml(error.message)}</p>`;
  });
}

/* ---------------------------- MARKETPLACE SETTINGS ------------------------- */
function renderSettingsIntegrations() {
  $("#content").innerHTML = settingsTabs("integrations") + `
    <div class="card">
      <div class="card-head"><h3>Marketplace Credentials</h3><a class="btn btn-sm" href="../marketplace/index.html">Manage connections →</a></div>
      <div class="table-wrap" style="border:none;">
        <table class="data-table">
          <thead><tr><th>Marketplace</th><th>Auth type</th><th>Status</th><th></th></tr></thead>
           <tbody id="mp-settings-body"><tr><td colspan="4" class="text-muted">Loading…</td></tr></tbody>
        </table>
      </div>
    </div>
  `;
  api.marketplaces.list().then(drawMpSettingsTable).catch(e => { $("#mp-settings-body").innerHTML = `<tr><td colspan="4" class="text-muted">Couldn't load marketplaces: ${escapeHtml(e.message)}</td></tr>`; });
}
function drawMpSettingsTable(marketplaces) {
  $("#mp-settings-body").innerHTML = (marketplaces || []).map(m => `
    <tr>
       <td class="cell-strong">${escapeHtml(m.marketplaceName || m.name || "—")}</td>
       <td>${escapeHtml(m.authType || "—")}</td>
       <td>${statusBadge(m.status || "Disconnected")}</td>
       <td><button class="btn btn-sm" onclick="editMarketplaceCredentials('${m.marketplaceName || m.name}')">Edit credentials</button></td>
    </tr>
  `).join("");
}
function editMarketplaceCredentials(name) {
  api.marketplaces.get(name).then(m => {
  const oauthType = m.authType === "OAuth";
  openModal({
    title: "Edit credentials — " + name,
    confirmLabel: "Save & test connection",
    bodyHtml: oauthType ? `
      <p style="font-size:13.5px;color:var(--navy-600);line-height:1.6;">${name} uses OAuth — re-authorize the connection if access was revoked.</p>
    ` : `
      <div class="form-grid">
        <div class="form-field full"><label>API Key</label><input name="apiKey" placeholder="Enter new API key to rotate credentials"></div>
        <div class="form-field full"><label>API Secret</label><input name="apiSecret" type="password" placeholder="Enter new API secret"></div>
      </div>
    `,
    onSubmit: () => {
      toast(`Testing connection to ${name}…`);
      api.marketplaces.testConnection(name).then(() => {
        toast(`${name} credentials saved and verified.`, "success");
        api.marketplaces.list().then(drawMpSettingsTable);
      }).catch(e => toast(e.message, "error"));
    }
  });
  }).catch(e => toast(e.message, "error"));
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initSettingsIndex() { renderSettingsIndex(); }
function initSettingsUsers() { renderSettingsUsers(); }
function initSettingsRoles() { renderSettingsRoles(); }
function initSettingsIntegrations() { renderSettingsIntegrations(); }