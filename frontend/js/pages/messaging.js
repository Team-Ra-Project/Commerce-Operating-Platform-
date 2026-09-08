/* ============================================================================
   WHATSAPP & EMAIL CAMPAIGNS (templates + single-template sends)
   Buttons/actions: Create Template, Submit for Approval (WhatsApp), Select
   Audience, Send Now, Schedule Send, View Delivery Report.
   Split out from the original "Retention" tab group into its own module.
   templates.html = template list. create-template.html is a new real page
   (was a modal). campaigns.html = delivery-report list for individual
   template sends (distinct from the Retention journeys module).
   campaign-detail.html?id= is a new real page for one send's delivery report
   (was a modal).
   ============================================================================ */
function renderTemplatesIndex() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Engagement</div>
        <h1 class="page-title">WhatsApp &amp; Email Templates</h1>
        <p class="page-sub">Reusable, merge-field messages — WhatsApp templates require BSP/Meta approval; Email doesn't.</p>
      </div>
      <div class="page-actions">
        <a class="btn" href="../retention/index.html">📣 Retention Marketing</a>
        <a class="btn" href="campaigns.html">📊 View sends</a>
        <a class="btn btn-blue" href="create-template.html">+ Create template</a>
      </div>
    </div>
    <div class="card">
      <div class="table-wrap" style="border:none;">
        <table class="data-table">
          <thead><tr><th>Template</th><th>Channel</th><th>Preview</th><th>Status</th><th></th></tr></thead>
          <tbody id="template-body"><tr><td colspan="5"><div class="empty-state"><strong>Loading…</strong></div></td></tr></tbody>
        </table>
      </div>
    </div>
  `;
  api.templates.list()
    .then(list => { templateIndexState.templates = list; drawTemplateTable(); })
    .catch(err => { $("#template-body").innerHTML = `<tr><td colspan="5"><div class="empty-state"><strong>Could not load templates.</strong>${escapeHtml(err.message || "")}</div></td></tr>`; });
}

const templateIndexState = { templates: [] };
function templateChannelLabel(channel) { return channel === "WHATSAPP" ? "WhatsApp" : "Email"; }
function templateStatusLabel(status) {
  return { DRAFT: "Draft", PENDING_APPROVAL: "Pending Approval", APPROVED: "Approved", REJECTED: "Rejected" }[status] || status;
}

function drawTemplateTable() {
  const list = templateIndexState.templates;
  $("#template-body").innerHTML = list.length ? list.map(t => `
    <tr>
      <td class="cell-strong">${escapeHtml(t.name)}</td>
      <td><span class="chip ${t.channel === "WHATSAPP" ? "chip-wa" : "chip-em"}">${templateChannelLabel(t.channel)}</span></td>
      <td class="text-muted" style="font-size:12px;max-width:260px;">${escapeHtml(t.content).slice(0, 70)}${t.content.length > 70 ? "…" : ""}</td>
      <td>${statusBadge(templateStatusLabel(t.approvalStatus))}</td>
      <td>
        <div class="action-group">
          <a class="icon-action" title="Edit" href="create-template.html?id=${t.id}">✎</a>
          ${t.channel === "WHATSAPP" && t.approvalStatus === "DRAFT" ? `<button class="btn btn-sm" onclick="submitForApproval(${t.id})">Submit for approval</button>` : ""}
          ${t.channel === "WHATSAPP" && t.approvalStatus === "REJECTED" ? `<button class="btn btn-sm" onclick="submitForApproval(${t.id})">Resubmit</button>` : ""}
        </div>
      </td>
    </tr>
  `).join("") : `<tr><td colspan="5"><div class="empty-state"><div class="e-ico">💬</div><strong>No templates yet</strong>Create one to start messaging customers.</div></td></tr>`;
}

function submitForApproval(id) {
  api.templates.submitForApproval(id)
    .then(t => {
      toast(t.approvalStatus === "APPROVED" ? `"${t.name}" approved.` : t.approvalStatus === "REJECTED" ? `"${t.name}" was rejected — edit and resubmit.` : `"${t.name}" submitted for approval.`,
        t.approvalStatus === "REJECTED" ? "error" : "success");
      return api.templates.list();
    })
    .then(list => { templateIndexState.templates = list; if ($("#template-body")) drawTemplateTable(); })
    .catch(err => toast(err.message || "Could not submit template.", "error"));
}

function openSendModal(templateId) {
  const t = TEMPLATES.find(x => x.id === templateId);
  openModal({
    title: "Send — " + t.name,
    confirmLabel: "Continue",
    bodyHtml: `
      <div class="form-grid">
        <div class="form-field full"><label>Select audience</label>
          <select name="audienceType">
            <option value="segment">CRM segment</option>
            <option value="all">All opted-in customers</option>
          </select>
        </div>
        <div class="form-field full" id="segment-picker-wrap"><label>Segment</label>
          <select name="segmentId">${SEGMENTS.map(s => `<option value="${s.id}">${s.name} (${segmentSize(s)})</option>`).join("")}</select>
        </div>
        <div class="form-field full"><label>Send timing</label>
          <select name="timing"><option value="now">Send now</option><option value="scheduled">Schedule for later</option></select>
        </div>
        <div class="form-field full hidden" id="schedule-time-wrap"><label>Scheduled date/time</label><input name="scheduleTime" type="datetime-local"></div>
      </div>
    `,
    onSubmit: (data) => {
      let audience = data.audienceType === "segment" ? computeSegmentMembers(SEGMENTS.find(s => s.id === data.segmentId).rule) : CUSTOMERS;
      audience = audience.filter(c => t.channel === "WhatsApp" ? c.optIn.whatsapp : c.optIn.email);
      const sent = audience.length;
      const delivered = Math.round(sent * randFloat(0.93, 0.99));
      const opened = Math.round(delivered * randFloat(0.5, 0.75));
      const clicked = Math.round(opened * randFloat(0.25, 0.5));
      const record = { id: uid("SEND"), templateId: t.id, templateName: t.name, channel: t.channel, audienceSize: audience.length, sent, delivered, opened, clicked, replied: t.channel === "WhatsApp" ? Math.round(opened * 0.12) : 0, bounced: sent - delivered, scheduled: data.timing === "scheduled", scheduleTime: data.scheduleTime || null, sentOn: new Date() };
      TEMPLATE_SENDS.unshift(record);
      if (data.timing === "scheduled") toast(`"${t.name}" scheduled for ${data.scheduleTime || "later"}. ${sent} opted-in recipients queued.`, "success");
      else toast(`"${t.name}" sent to ${sent} opted-in recipients via ${t.channel}.`, "success");
    }
  });
  $("[name=audienceType]").addEventListener("change", e => { $("#segment-picker-wrap").classList.toggle("hidden", e.target.value !== "segment"); });
  $("[name=timing]").addEventListener("change", e => { $("#schedule-time-wrap").classList.toggle("hidden", e.target.value !== "scheduled"); });
}

/* ------------------------------ CREATE TEMPLATE ---------------------------- */
function renderTemplateFormPage(id) {
  $("#content").innerHTML = `<div class="empty-state"><strong>Loading…</strong></div>`;
  const load = id ? api.templates.get(id) : Promise.resolve(null);
  load.then(t => buildTemplateForm(t)).catch(err => {
    $("#content").innerHTML = `<div class="empty-state"><strong>Could not load template.</strong>${escapeHtml(err.message || "")}</div>`;
  });
}

function buildTemplateForm(t) {
  const isEdit = !!t;
  openPage({
    title: isEdit ? "Edit template — " + t.name : "Create template",
    eyebrow: "Engagement",
    backHref: "templates.html",
    confirmLabel: "Save template",
    bodyHtml: `
      <div class="form-grid">
        <div class="form-field full"><label>Template name</label><input name="name" value="${isEdit ? escapeHtml(t.name) : ""}" placeholder="e.g. Reorder Reminder — 10% Off" required></div>
        <div class="form-field full"><label>Channel</label>
          <select name="channel">
            <option value="WHATSAPP" ${isEdit && t.channel === "WHATSAPP" ? "selected" : ""}>WhatsApp</option>
            <option value="EMAIL" ${isEdit && t.channel === "EMAIL" ? "selected" : ""}>Email</option>
          </select>
        </div>
        <div class="form-field full"><label>Message content</label>
          <textarea name="content" placeholder="Use merge fields like {customer_name}, {product_name}, {discount_code}" required>${isEdit ? escapeHtml(t.content) : ""}</textarea>
        </div>
      </div>
      <p class="text-muted" style="font-size:12px;margin-top:6px;">Supported merge fields: {customer_name}, {product_name}, {discount_code}, {order_number}, {brand_name}. WhatsApp templates require BSP/Meta approval before they can be used in a journey; Email templates don't.</p>
      ${isEdit && t.approvalStatus === "PENDING_APPROVAL" ? `<p class="text-muted" style="font-size:12px;margin-top:6px;color:var(--amber-600);">This template is awaiting approval and can't be edited until that resolves.</p>` : ""}
    `
  });

  const form = $("#page-form");
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const fd = new FormData(form);
    const payload = { name: fd.get("name"), channel: fd.get("channel"), content: fd.get("content") };
    const save = isEdit ? api.templates.update(t.id, payload) : api.templates.create(payload);
    save.then((saved) => {
      toast(isEdit ? "Template updated." : (saved.channel === "WHATSAPP" ? "Template created as Draft — submit for BSP/Meta approval before using it." : "Template created and ready to use."), "success");
      location.href = "templates.html";
    }).catch(err => toast(err.message || "Could not save template.", "error"));
  });
}

/* --------------------------------- SENDS ---------------------------------- */
function renderMessagingCampaignsIndex() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Engagement</div>
        <h1 class="page-title">WhatsApp &amp; Email — Sends</h1>
        <p class="page-sub">Delivery and engagement data for every template send.</p>
      </div>
      <div class="page-actions"><a class="btn" href="templates.html">💬 Templates</a></div>
    </div>
    <div class="card">
      <div class="card-head"><h3>Delivery Reports</h3></div>
      <div class="table-wrap" style="border:none;">
        <table class="data-table">
          <thead><tr><th>Template</th><th>Channel</th><th>Sent</th><th>Delivered</th><th>Read/Opened</th><th>Clicked</th><th></th></tr></thead>
          <tbody id="template-sends-body"></tbody>
        </table>
      </div>
    </div>
  `;
  drawTemplateSendsTable();
}
function drawTemplateSendsTable() {
  $("#template-sends-body").innerHTML = TEMPLATE_SENDS.length ? TEMPLATE_SENDS.map(s => `
    <tr>
      <td class="cell-strong">${s.templateName}</td>
      <td><span class="chip ${s.channel === "WhatsApp" ? "chip-wa" : "chip-em"}">${s.channel}</span></td>
      <td>${s.sent}</td><td>${s.delivered}</td><td>${s.opened}</td><td>${s.clicked}</td>
      <td><a class="btn btn-sm btn-ghost" href="campaign-detail.html?id=${s.id}">View report</a></td>
    </tr>
  `).join("") : `<tr><td colspan="7"><div class="empty-state"><div class="e-ico">📭</div><strong>No sends yet</strong>Send an approved template to see delivery data here.</div></td></tr>`;
}

function renderMessagingCampaignDetailPage(sendId) {
  const s = TEMPLATE_SENDS.find(x => x.id === sendId);
  if (!s) { $("#content").innerHTML = `<div class="empty-state"><strong>Send not found.</strong></div>`; return; }
  openPage({
    title: "Delivery report — " + s.templateName,
    eyebrow: "Engagement",
    backHref: "campaigns.html",
    hideFooter: true,
    wide: true,
    bodyHtml: `
      <div class="grid grid-3">
        ${kpiCard("Sent", s.sent, "", "up", "📤")}
        ${kpiCard("Delivered", s.delivered, "", "up", "📬")}
        ${kpiCard("Read / Opened", s.opened, "", "up", "👁️")}
        ${kpiCard("Clicked", s.clicked, "", "up", "🔗")}
        ${kpiCard("Replied", s.replied, "", "up", "💬")}
        ${kpiCard("Bounced", s.bounced, "", "down", "⚠️")}
      </div>
      <p class="text-muted mt-16" style="font-size:12.5px;">${s.scheduled ? "Scheduled send" : "Sent immediately"} via ${s.channel} on ${fmtDateTime(s.sentOn)}. Replies route to the Support/CRM inbox; unsubscribes are recorded automatically.</p>
    `
  });
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initMessagingTemplates() { renderTemplatesIndex(); }
function initMessagingCreateTemplate() { renderTemplateFormPage(getQueryParam("id")); }
function initMessagingCampaigns() { renderMessagingCampaignsIndex(); }
function initMessagingCampaignDetail() { renderMessagingCampaignDetailPage(getQueryParam("id")); }
