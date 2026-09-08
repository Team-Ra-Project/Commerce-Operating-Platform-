/* ============================================================================
   RETENTION MARKETING (journeys) — Phase 20, real backend
   Buttons/actions: Create New Campaign/Journey, Select Segment, Set Retention
   Goal, Add Journey Step, Preview Journey, Activate Journey, Pause/Stop
   Journey, View Campaign Performance.
   Backed by GET/POST/PUT/DELETE /api/campaigns, /api/segments,
   /api/campaigns/{id}/activate, /pause, /messages.
   create.html and detail.html are real pages.
   ============================================================================ */
const RETENTION_GOAL_OPTIONS = [
  { value: "WIN_BACK", label: "Win-back" },
  { value: "CROSS_SELL", label: "Cross-sell" },
  { value: "LOYALTY_REWARD", label: "Loyalty reward" },
  { value: "REPLENISHMENT_REMINDER", label: "Replenishment reminder" }
];
function goalLabel(goal) { return (RETENTION_GOAL_OPTIONS.find(g => g.value === goal) || {}).label || goal; }
function campaignStatusLabel(status) { return { DRAFT: "Draft", ACTIVE: "Active", PAUSED: "Paused", COMPLETED: "Completed" }[status] || status; }

function renderRetentionIndex() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Engagement</div>
        <h1 class="page-title">Retention Marketing</h1>
        <p class="page-sub">Segment-driven journeys that mix WhatsApp &amp; Email touchpoints to keep customers coming back.</p>
      </div>
      <div class="page-actions">
        <a class="btn" href="../messaging/templates.html">💬 Templates</a>
        <a class="btn" href="../broadcasts/index.html">📡 Bulk Sharing</a>
        <a class="btn btn-blue" href="create.html">+ Create campaign / journey</a>
      </div>
    </div>
    <div class="grid grid-1" id="campaign-list" style="display:flex;flex-direction:column;gap:14px;"><div class="empty-state"><strong>Loading…</strong></div></div>
  `;
  api.retentionCampaigns.list()
    .then(list => { retentionIndexState.campaigns = list; drawCampaignList(); })
    .catch(err => { $("#campaign-list").innerHTML = `<div class="empty-state"><strong>Could not load campaigns.</strong>${escapeHtml(err.message || "")}</div>`; });
}

const retentionIndexState = { campaigns: [] };

function drawCampaignList() {
  const list = retentionIndexState.campaigns;
  $("#campaign-list").innerHTML = list.length ? list.map(c => `
    <div class="card campaign-card">
      <div class="flex-between">
        <div>
          <div class="cell-strong" style="font-size:14.5px;">${escapeHtml(c.name)}</div>
          <div class="cell-sub">Segment: ${escapeHtml(c.segmentName || "—")} (${c.segmentAudienceSize}) · Goal: ${goalLabel(c.goal)} · ${c.steps.length} step(s)</div>
        </div>
        ${statusBadge(campaignStatusLabel(c.status))}
      </div>
      <div class="journey-preview">
        ${c.steps.map((s, i) => `<span class="chip ${s.channel === "WHATSAPP" ? "chip-wa" : "chip-em"}">${s.channel === "WHATSAPP" ? "WhatsApp" : "Email"} · Day ${s.delayDays}</span>${i < c.steps.length - 1 ? '<span class="chip-arrow">→</span>' : ""}`).join("")}
      </div>
      <div class="stat-strip" style="padding-top:12px;margin-top:12px;">
        <div><strong>${c.enrolledCount}</strong><span>Enrolled</span></div>
        <div><strong>${c.sentCount}</strong><span>Sent</span></div>
        <div><strong>${c.convertedCount}</strong><span>Converted</span></div>
        <div><strong>${moneyDec(c.revenueAttributed)}</strong><span>Revenue attributed</span></div>
      </div>
      <div class="action-group mt-8">
        <a class="btn btn-sm btn-ghost" href="detail.html?id=${c.id}">View performance</a>
        ${c.status === "DRAFT" ? `<a class="btn btn-sm" href="create.html?id=${c.id}">Edit</a><button class="btn btn-sm btn-blue" onclick="activateCampaign(${c.id})">Activate journey</button>` : ""}
        ${c.status === "ACTIVE" ? `<button class="btn btn-sm btn-danger" onclick="pauseCampaign(${c.id})">Pause / stop</button>` : ""}
        ${c.status === "PAUSED" ? `<button class="btn btn-sm btn-blue" onclick="activateCampaign(${c.id})">Resume journey</button>` : ""}
      </div>
    </div>`).join("") : `<div class="empty-state"><div class="e-ico">📣</div><strong>No campaigns yet</strong>Create a journey to start re-engaging customers.</div>`;
}

function activateCampaign(id) {
  api.retentionCampaigns.activate(id)
    .then(c => { toast(`"${c.name}" activated. ${c.enrolledCount} matching customer(s) enrolled.`, "success"); return refreshWherever(id, c); })
    .catch(err => toast(err.message || "Could not activate campaign.", "error"));
}
function pauseCampaign(id) {
  api.retentionCampaigns.pause(id)
    .then(c => { toast(`"${c.name}" paused — no further sending until resumed.`, "success"); return refreshWherever(id, c); })
    .catch(err => toast(err.message || "Could not pause campaign.", "error"));
}
function refreshWherever(id, updated) {
  if ($("#campaign-list")) {
    const idx = retentionIndexState.campaigns.findIndex(c => c.id === id);
    if (idx >= 0) retentionIndexState.campaigns[idx] = updated;
    drawCampaignList();
  }
  if ($("#detail-root")) renderCampaignDetailPage(id);
}

/* ------------------------------ CREATE / EDIT ------------------------------ */
function renderCampaignFormPage(id) {
  $("#content").innerHTML = `<div class="empty-state"><strong>Loading…</strong></div>`;
  Promise.all([
    id ? api.retentionCampaigns.get(id) : Promise.resolve(null),
    api.segments.list(),
    api.templates.list()
  ]).then(([c, segments, templates]) => buildCampaignForm(c, segments, templates.filter(t => t.approvalStatus === "APPROVED")))
    .catch(err => { $("#content").innerHTML = `<div class="empty-state"><strong>Could not load form.</strong>${escapeHtml(err.message || "")}</div>`; });
}

function buildCampaignForm(c, segments, approvedTemplates) {
  const isEdit = !!c;
  if (isEdit && c.status !== "DRAFT") {
    $("#content").innerHTML = `<div class="empty-state"><strong>Only a draft campaign's journey can be edited.</strong><a class="link-action" href="detail.html?id=${c.id}">View performance instead →</a></div>`;
    return;
  }
  let steps = isEdit ? c.steps.map(s => ({ ...s })) : [{ channel: "WHATSAPP", messageTemplateId: (approvedTemplates.find(t => t.channel === "WHATSAPP") || {}).id || "", delayDays: 0 }];

  openPage({
    title: isEdit ? "Edit campaign — " + c.name : "Create campaign / journey",
    eyebrow: "Engagement",
    backHref: "index.html",
    wide: true,
    hideFooter: true,
    bodyHtml: `
      <div class="form-grid">
        <div class="form-field full"><label>Campaign name</label><input name="name" value="${isEdit ? escapeHtml(c.name) : ""}" placeholder="e.g. Post-Purchase Thank You" required></div>
        <div class="form-field"><label>1. Select segment</label>
          <select name="segmentId">${segments.map(s => `<option value="${s.id}" ${isEdit && c.segmentId === s.id ? "selected" : ""}>${escapeHtml(s.name)} (${s.memberCount})</option>`).join("")}</select>
        </div>
        <div class="form-field"><label>2. Set retention goal</label>
          <select name="goal">${RETENTION_GOAL_OPTIONS.map(g => `<option value="${g.value}" ${isEdit && c.goal === g.value ? "selected" : ""}>${g.label}</option>`).join("")}</select>
        </div>
      </div>
      <div class="mt-16">
        <div class="flex-between"><label style="font-size:12.5px;font-weight:600;color:var(--navy-600);">3. Build journey — timed WhatsApp / Email steps</label><button type="button" class="btn btn-sm" id="add-step-btn">+ Add journey step</button></div>
        <div id="journey-steps" class="mt-8"></div>
      </div>
      <div class="mt-16">
        <label style="font-size:12.5px;font-weight:600;color:var(--navy-600);display:block;margin-bottom:8px;">4. Preview timeline</label>
        <div id="journey-timeline-preview" class="journey-preview"></div>
      </div>
      <p class="text-muted mt-8" style="font-size:12px;">Need more templates? <a class="link-action" href="../messaging/create-template.html">Create one in WhatsApp &amp; Email Templates →</a> Only approved templates can be used in a journey.</p>
    `
  });

  const form = $("#page-form");
  const footer = document.createElement("div");
  footer.className = "modal-foot";
  footer.innerHTML = `
    <a class="btn" href="index.html">Cancel</a>
    <button type="button" class="btn" id="jb-save-draft">Save as draft</button>
    <button type="button" class="btn btn-blue" id="jb-activate">Activate journey</button>
  `;
  form.appendChild(footer);

  function templatesFor(channel) { return approvedTemplates.filter(t => t.channel === channel); }
  function drawSteps() {
    $("#journey-steps").innerHTML = steps.map((s, i) => `
      <div class="journey-step-row" data-idx="${i}">
        <select class="step-channel">
          <option value="WHATSAPP" ${s.channel === "WHATSAPP" ? "selected" : ""}>WhatsApp</option>
          <option value="EMAIL" ${s.channel === "EMAIL" ? "selected" : ""}>Email</option>
        </select>
        <select class="step-template">
          ${templatesFor(s.channel).map(t => `<option value="${t.id}" ${s.messageTemplateId === t.id ? "selected" : ""}>${escapeHtml(t.name)}</option>`).join("") || `<option value="">No approved templates for this channel</option>`}
        </select>
        <div class="row-flex"><span class="text-muted" style="font-size:12px;">Day</span><input type="number" min="0" class="step-delay" value="${s.delayDays}" style="width:60px;"></div>
        ${steps.length > 1 ? `<button type="button" class="icon-action remove-step" title="Remove">✕</button>` : `<span></span>`}
      </div>
    `).join("");
    $$(".journey-step-row").forEach(row => {
      const idx = Number(row.dataset.idx);
      row.querySelector(".step-channel").addEventListener("change", e => { steps[idx].channel = e.target.value; steps[idx].messageTemplateId = (templatesFor(e.target.value)[0] || {}).id || ""; drawSteps(); drawPreview(); });
      row.querySelector(".step-template").addEventListener("change", e => { steps[idx].messageTemplateId = Number(e.target.value) || ""; drawPreview(); });
      row.querySelector(".step-delay").addEventListener("input", e => { steps[idx].delayDays = Number(e.target.value) || 0; drawPreview(); });
      const rm = row.querySelector(".remove-step");
      if (rm) rm.addEventListener("click", () => { steps.splice(idx, 1); drawSteps(); drawPreview(); });
    });
    drawPreview();
  }
  function drawPreview() {
    $("#journey-timeline-preview").innerHTML = steps.map((s, i) => `<span class="chip ${s.channel === "WHATSAPP" ? "chip-wa" : "chip-em"}">${s.channel === "WHATSAPP" ? "WhatsApp" : "Email"} · Day ${s.delayDays}</span>${i < steps.length - 1 ? '<span class="chip-arrow">→</span>' : ""}`).join("") || `<span class="text-muted" style="font-size:12.5px;">Add at least one step.</span>`;
  }
  drawSteps();
  $("#add-step-btn").addEventListener("click", () => { steps.push({ channel: "WHATSAPP", messageTemplateId: (templatesFor("WHATSAPP")[0] || {}).id || "", delayDays: (steps[steps.length - 1]?.delayDays || 0) + 2 }); drawSteps(); });

  function collectPayload() {
    const fd = new FormData(form);
    return { name: fd.get("name"), segmentId: Number(fd.get("segmentId")), goal: fd.get("goal"), steps: steps.map((s, i) => ({ id: s.id || null, stepOrder: i + 1, channel: s.channel, messageTemplateId: s.messageTemplateId || null, delayDays: s.delayDays })) };
  }
  function save(activate) {
    const payload = collectPayload();
    if (!payload.name) { toast("Please name the campaign.", "error"); return; }
    if (!steps.length || steps.some(s => !s.messageTemplateId)) { toast("Every journey step needs an approved template. Approve templates under WhatsApp & Email Templates first.", "error"); return; }
    const savePromise = isEdit ? api.retentionCampaigns.update(c.id, payload) : api.retentionCampaigns.create(payload);
    savePromise
      .then(saved => activate ? api.retentionCampaigns.activate(saved.id) : Promise.resolve(saved))
      .then(saved => {
        toast(activate ? `Journey activated — ${saved.enrolledCount} matching customer(s) enrolled.` : "Campaign saved as draft.", "success");
        location.href = "index.html";
      })
      .catch(err => toast(err.message || "Could not save campaign.", "error"));
  }
  $("#jb-save-draft").addEventListener("click", () => save(false));
  $("#jb-activate").addEventListener("click", () => save(true));
  form.addEventListener("submit", (e) => e.preventDefault());
}

/* --------------------------------- DETAIL -------------------------------- */
function renderCampaignDetailPage(id) {
  $("#content").innerHTML = `<div id="detail-root"><div class="empty-state"><strong>Loading…</strong></div></div>`;
  Promise.all([api.retentionCampaigns.get(id), api.retentionCampaigns.messages(id)])
    .then(([c, messages]) => buildCampaignDetail(c, messages))
    .catch(() => { $("#content").innerHTML = `<div class="empty-state"><strong>Campaign not found.</strong></div>`; });
}

function buildCampaignDetail(c, messages) {
  openPage({
    title: "Performance — " + c.name,
    eyebrow: "Engagement",
    backHref: "index.html",
    hideFooter: true,
    wide: true,
    bodyHtml: `
      <div id="detail-root">
      <div class="grid grid-3">
        ${kpiCard("Enrolled", c.enrolledCount, "", "up", "👥")}
        ${kpiCard("Messages Sent", c.sentCount, "", "up", "📤")}
        ${kpiCard("Delivered", c.deliveredCount, "", "up", "📬")}
        ${kpiCard("Opened / Read", c.openedCount, "", "up", "👁️")}
        ${kpiCard("Clicked", c.clickedCount, "", "up", "🔗")}
        ${kpiCard("Converted → Order", c.convertedCount, "", "up", "🛒")}
      </div>
      <div class="card mt-16" style="box-shadow:none;border:1px dashed var(--border);">
        <div class="card-head"><h3>Revenue attributed</h3></div>
        <div class="kpi-value">${moneyDec(c.revenueAttributed)}</div>
        <p class="text-muted" style="font-size:12.5px;margin-top:6px;">Orders placed by enrolled customers after joining this journey — and before it finishes — are attributed back to it.</p>
      </div>
      <div class="card mt-16">
        <div class="card-head"><h3>Message log</h3></div>
        <div class="table-wrap" style="border:none;">
          <table class="data-table">
            <thead><tr><th>Customer</th><th>Channel</th><th>Status</th><th>Detail</th><th>Sent</th></tr></thead>
            <tbody>
              ${messages.length ? messages.map(m => `
                <tr>
                  <td>${escapeHtml(m.customerName || "—")}</td>
                  <td><span class="chip ${m.channel === "WHATSAPP" ? "chip-wa" : "chip-em"}">${m.channel === "WHATSAPP" ? "WhatsApp" : "Email"}</span></td>
                  <td>${statusBadge(m.status.charAt(0) + m.status.slice(1).toLowerCase())}</td>
                  <td class="text-muted" style="font-size:12px;">${escapeHtml(m.providerStatusDetail || "—")}</td>
                  <td>${m.sentAt ? fmtDateTime(new Date(m.sentAt)) : "—"}</td>
                </tr>`).join("") : `<tr><td colspan="5"><div class="empty-state"><strong>No messages sent yet</strong></div></td></tr>`}
            </tbody>
          </table>
        </div>
      </div>
      <div class="modal-foot" style="padding-left:0;padding-right:0;">
        ${c.status === "DRAFT" ? `<a class="btn" href="create.html?id=${c.id}">Edit journey</a><button class="btn btn-blue" onclick="activateCampaign(${c.id})">Activate journey</button>` : ""}
        ${c.status === "ACTIVE" ? `<button class="btn btn-danger" onclick="pauseCampaign(${c.id})">Pause / stop</button>` : ""}
        ${c.status === "PAUSED" ? `<button class="btn btn-blue" onclick="activateCampaign(${c.id})">Resume journey</button>` : ""}
      </div>
      </div>
    `
  });
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initRetentionIndex() { renderRetentionIndex(); }
function initRetentionCreate() { renderCampaignFormPage(getQueryParam("id")); }
function initRetentionDetail() { renderCampaignDetailPage(getQueryParam("id")); }
