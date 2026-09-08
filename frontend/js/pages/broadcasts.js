/* ============================================================================
   BULK SHARING / BROADCAST — Phase 22, real backend
   Buttons/actions (BUTTON_ACTIONS.md 11b): Upload Contact List, Select Segment,
   Choose Content, Select Channels, Send Bulk Broadcast, View Bulk Send Report,
   Retry Failed Sends.
   Backed by GET/POST /api/broadcasts, /api/broadcasts/parse-csv,
   /api/broadcasts/{id}, /api/broadcasts/{id}/items, /api/broadcasts/{id}/retry.
   index.html, create.html and report.html are real pages.
   ============================================================================ */

function channelLabel(channel) { return { WHATSAPP: "WhatsApp", EMAIL: "Email", BOTH: "WhatsApp + Email" }[channel] || channel; }
function channelChip(channel) {
  if (channel === "BOTH") return `<span class="chip chip-wa">WhatsApp</span><span class="chip chip-em">Email</span>`;
  return `<span class="chip ${channel === "WHATSAPP" ? "chip-wa" : "chip-em"}">${channelLabel(channel)}</span>`;
}
function bulkStatusLabel(status) { return { PENDING: "Pending", IN_PROGRESS: "In Progress", COMPLETED: "Completed" }[status] || status; }
function itemStatusLabel(status) { return { PENDING: "Pending", SENT: "Sent", DELIVERED: "Delivered", FAILED: "Failed", OPTED_OUT: "Opted Out" }[status] || status; }
function truncate(text, n) { if (!text) return ""; return text.length > n ? text.slice(0, n).trim() + "…" : text; }

/* --------------------------------------- INDEX (history/list) --------------------------------------- */

function renderBroadcastsIndex() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Engagement</div>
        <h1 class="page-title">Bulk Sharing / Broadcast</h1>
        <p class="page-sub">Send a one-off WhatsApp and/or Email blast to a segment or an uploaded contact list, then track delivery.</p>
      </div>
      <div class="page-actions">
        <a class="btn" href="../retention/index.html">🔁 Retention Marketing</a>
        <a class="btn btn-blue" href="create.html">+ New broadcast</a>
      </div>
    </div>
    <div class="grid grid-1" id="broadcast-list" style="display:flex;flex-direction:column;gap:14px;"><div class="empty-state"><strong>Loading…</strong></div></div>
  `;
  api.broadcasts.list()
    .then(list => drawBroadcastList(list))
    .catch(err => { $("#broadcast-list").innerHTML = `<div class="empty-state"><strong>Could not load broadcasts.</strong>${escapeHtml(err.message || "")}</div>`; });
}

function drawBroadcastList(list) {
  $("#broadcast-list").innerHTML = list.length ? list.map(b => `
    <div class="card">
      <div class="flex-between">
        <div>
          <div class="cell-strong" style="font-size:14.5px;">${channelChip(b.channel)} <span class="text-muted" style="font-size:12px;font-weight:400;">${b.createdAt ? fmtDateTime(new Date(b.createdAt)) : ""}</span></div>
          <div class="cell-sub" style="margin-top:4px;max-width:640px;">${escapeHtml(truncate(b.content, 140))}</div>
        </div>
        ${statusBadge(bulkStatusLabel(b.status))}
      </div>
      <div class="stat-strip" style="padding-top:12px;margin-top:12px;">
        <div><strong>${b.listSize}</strong><span>List size</span></div>
        <div><strong>${b.sentCount}</strong><span>Sent</span></div>
        <div><strong>${b.deliveredCount}</strong><span>Delivered</span></div>
        <div><strong>${b.failedCount}</strong><span>Failed</span></div>
        <div><strong>${b.optedOutCount}</strong><span>Opted out</span></div>
      </div>
      <div class="action-group mt-8">
        <a class="btn btn-sm" href="report.html?id=${b.id}">View report</a>
        ${b.status === "COMPLETED" && b.failedCount > 0 ? `<button class="btn btn-sm btn-blue" onclick="retryBroadcast(${b.id})">Retry failed sends</button>` : ""}
      </div>
    </div>`).join("") : `<div class="empty-state"><div class="e-ico">📡</div><strong>No broadcasts yet</strong>Send your first bulk WhatsApp/Email blast to a segment or uploaded list.</div>`;
}

function retryBroadcast(id) {
  api.broadcasts.retry(id)
    .then(() => { toast("Retrying failed sends…", "success"); location.href = "report.html?id=" + id; })
    .catch(err => toast(err.message || "Could not retry this broadcast.", "error"));
}

/* ------------------------------------------- CREATE ------------------------------------------- */

const broadcastCreateState = {
  recipientSource: "SEGMENT",
  segmentId: "",
  segments: [],
  csvContacts: [],
  csvSummary: null,
  channel: "WHATSAPP",
  whatsappTemplates: [],
  whatsappTemplateId: "",
  emailSubject: "",
  emailContent: ""
};

function renderBroadcastCreatePage() {
  $("#content").innerHTML = `<div class="empty-state"><strong>Loading…</strong></div>`;
  Promise.all([api.segments.list(), api.templates.list()])
    .then(([segments, templates]) => {
      broadcastCreateState.segments = segments;
      broadcastCreateState.whatsappTemplates = templates.filter(t => t.channel === "WHATSAPP" && t.approvalStatus === "APPROVED");
      broadcastCreateState.segmentId = segments.length ? segments[0].id : "";
      broadcastCreateState.whatsappTemplateId = broadcastCreateState.whatsappTemplates.length ? broadcastCreateState.whatsappTemplates[0].id : "";
      buildBroadcastCreateForm();
    })
    .catch(err => { $("#content").innerHTML = `<div class="empty-state"><strong>Could not load form.</strong>${escapeHtml(err.message || "")}</div>`; });
}

function buildBroadcastCreateForm() {
  const s = broadcastCreateState;

  openPage({
    title: "New bulk broadcast",
    eyebrow: "Engagement",
    backHref: "index.html",
    wide: true,
    hideFooter: true,
    bodyHtml: `
      <div class="mt-8">
        <label style="font-size:12.5px;font-weight:600;color:var(--navy-600);">1. Recipients</label>
        <div class="form-grid mt-8">
          <div class="form-field"><label>Source</label>
            <select id="bc-source">
              <option value="SEGMENT" ${s.recipientSource === "SEGMENT" ? "selected" : ""}>Select a segment</option>
              <option value="UPLOAD" ${s.recipientSource === "UPLOAD" ? "selected" : ""}>Upload a contact list (CSV)</option>
            </select>
          </div>
          <div class="form-field" id="bc-segment-field">
            <label>Segment</label>
            <select id="bc-segment">
              ${s.segments.map(seg => `<option value="${seg.id}" ${s.segmentId == seg.id ? "selected" : ""}>${escapeHtml(seg.name)} (${seg.memberCount})</option>`).join("") || `<option value="">No segments yet</option>`}
            </select>
          </div>
        </div>
        <div id="bc-upload-field" style="display:none;" class="mt-8">
          <label style="font-size:12.5px;font-weight:600;color:var(--navy-600);">Upload contact list</label>
          <p class="text-muted" style="font-size:12px;margin:4px 0 8px;">CSV with a header row containing <code>email</code> and/or <code>phone</code> columns (an optional <code>name</code> column too). Format and consent are checked automatically.</p>
          <input type="file" id="bc-csv-file" accept=".csv,text/csv">
          <div id="bc-csv-summary" class="mt-8"></div>
        </div>
      </div>

      <div class="mt-16">
        <label style="font-size:12.5px;font-weight:600;color:var(--navy-600);">2. Select channel(s)</label>
        <div class="form-field mt-8">
          <select id="bc-channel">
            <option value="WHATSAPP" ${s.channel === "WHATSAPP" ? "selected" : ""}>WhatsApp only</option>
            <option value="EMAIL" ${s.channel === "EMAIL" ? "selected" : ""}>Email only</option>
            <option value="BOTH" ${s.channel === "BOTH" ? "selected" : ""}>WhatsApp + Email</option>
          </select>
        </div>
      </div>

      <div class="mt-16" id="bc-whatsapp-content">
        <label style="font-size:12.5px;font-weight:600;color:var(--navy-600);">WhatsApp content — approved template</label>
        <div class="form-field mt-8">
          <select id="bc-wa-template">
            ${s.whatsappTemplates.map(t => `<option value="${t.id}" ${s.whatsappTemplateId == t.id ? "selected" : ""}>${escapeHtml(t.name)}</option>`).join("") || `<option value="">No approved WhatsApp templates</option>`}
          </select>
        </div>
        <div id="bc-wa-preview" class="text-muted" style="font-size:12.5px;white-space:pre-wrap;background:var(--bg);border-radius:8px;padding:10px;margin-top:8px;"></div>
        <p class="text-muted" style="font-size:12px;margin-top:6px;">Marketing WhatsApp messages can only use an approved template. <a class="link-action" href="../messaging/templates.html">Manage templates →</a></p>
      </div>

      <div class="mt-16" id="bc-email-content">
        <label style="font-size:12.5px;font-weight:600;color:var(--navy-600);">Email content</label>
        <div class="form-grid mt-8">
          <div class="form-field full"><label>Subject</label><input id="bc-email-subject" value="${escapeHtml(s.emailSubject)}" placeholder="e.g. Our biggest sale of the season"></div>
          <div class="form-field full"><label>Body</label><textarea id="bc-email-body" rows="5" placeholder="Write the email content…">${escapeHtml(s.emailContent)}</textarea></div>
        </div>
      </div>
    `
  });

  const form = $("#page-form");
  const footer = document.createElement("div");
  footer.className = "modal-foot";
  footer.innerHTML = `
    <a class="btn" href="index.html">Cancel</a>
    <button type="button" class="btn btn-blue" id="bc-send">Send bulk broadcast</button>
  `;
  form.appendChild(footer);
  form.addEventListener("submit", e => e.preventDefault());

  function refreshVisibility() {
    $("#bc-segment-field").style.display = s.recipientSource === "SEGMENT" ? "" : "none";
    $("#bc-upload-field").style.display = s.recipientSource === "UPLOAD" ? "" : "none";
    $("#bc-whatsapp-content").style.display = s.channel === "EMAIL" ? "none" : "";
    $("#bc-email-content").style.display = s.channel === "WHATSAPP" ? "none" : "";
    const t = s.whatsappTemplates.find(t => t.id == s.whatsappTemplateId);
    $("#bc-wa-preview").textContent = t ? t.content : "";
  }
  refreshVisibility();

  $("#bc-source").addEventListener("change", e => { s.recipientSource = e.target.value; refreshVisibility(); });
  $("#bc-segment").addEventListener("change", e => { s.segmentId = e.target.value; });
  $("#bc-channel").addEventListener("change", e => { s.channel = e.target.value; refreshVisibility(); });
  $("#bc-wa-template").addEventListener("change", e => { s.whatsappTemplateId = e.target.value; refreshVisibility(); });
  $("#bc-email-subject").addEventListener("input", e => { s.emailSubject = e.target.value; });
  $("#bc-email-body").addEventListener("input", e => { s.emailContent = e.target.value; });

  $("#bc-csv-file").addEventListener("change", e => {
    const file = e.target.files[0];
    if (!file) return;
    $("#bc-csv-summary").innerHTML = `<div class="text-muted" style="font-size:12.5px;">Validating…</div>`;
    api.broadcasts.parseCsv(file)
      .then(res => {
        s.csvContacts = res.validContacts;
        s.csvSummary = res;
        $("#bc-csv-summary").innerHTML = `
          <div class="stat-strip">
            <div><strong>${res.validCount}</strong><span>Valid</span></div>
            <div><strong>${res.invalidCount}</strong><span>Invalid</span></div>
            <div><strong>${res.duplicateCount}</strong><span>Duplicates removed</span></div>
          </div>
          ${res.invalidRows.length ? `<div class="text-muted" style="font-size:11.5px;margin-top:6px;">First ${res.invalidRows.length} invalid row(s): ${res.invalidRows.map(r => escapeHtml(r.reason)).join("; ")}</div>` : ""}
        `;
      })
      .catch(err => { $("#bc-csv-summary").innerHTML = `<div class="text-muted" style="font-size:12.5px;color:var(--red-600);">${escapeHtml(err.message || "Could not read this file.")}</div>`; s.csvContacts = []; });
  });

  $("#bc-send").addEventListener("click", () => {
    if (s.channel !== "EMAIL" && !s.whatsappTemplateId) { toast("Choose an approved WhatsApp template, or switch to Email only.", "error"); return; }
    if (s.channel !== "WHATSAPP" && (!s.emailSubject.trim() || !s.emailContent.trim())) { toast("Enter an Email subject and body, or switch to WhatsApp only.", "error"); return; }
    if (s.recipientSource === "SEGMENT" && !s.segmentId) { toast("Select a segment.", "error"); return; }
    if (s.recipientSource === "UPLOAD" && !s.csvContacts.length) { toast("Upload a contact list with at least one valid contact.", "error"); return; }

    const payload = {
      channel: s.channel,
      recipientSource: s.recipientSource,
      segmentId: s.recipientSource === "SEGMENT" ? Number(s.segmentId) : null,
      contacts: s.recipientSource === "UPLOAD" ? s.csvContacts : null,
      whatsappTemplateId: s.channel !== "EMAIL" ? Number(s.whatsappTemplateId) : null,
      emailSubject: s.channel !== "WHATSAPP" ? s.emailSubject : null,
      emailContent: s.channel !== "WHATSAPP" ? s.emailContent : null
    };
    $("#bc-send").disabled = true;
    $("#bc-send").textContent = "Sending…";
    api.broadcasts.create(payload)
      .then(created => { toast(`Broadcast launched — sending to ${created.listSize} recipient(s).`, "success"); location.href = "report.html?id=" + created.id; })
      .catch(err => { toast(err.message || "Could not send this broadcast.", "error"); $("#bc-send").disabled = false; $("#bc-send").textContent = "Send bulk broadcast"; });
  });
}

/* ------------------------------------------- REPORT ------------------------------------------- */

let broadcastReportPollTimer = null;

function renderBroadcastReportPage(id) {
  if (!id) { location.href = "index.html"; return; }
  if (broadcastReportPollTimer) { clearInterval(broadcastReportPollTimer); broadcastReportPollTimer = null; }
  $("#content").innerHTML = `<div class="empty-state"><strong>Loading…</strong></div>`;
  loadBroadcastReport(id, true);
}

function loadBroadcastReport(id, isFirstLoad) {
  Promise.all([api.broadcasts.get(id), api.broadcasts.items(id)])
    .then(([b, items]) => {
      buildBroadcastReport(b, items);
      if (b.status !== "COMPLETED" && !broadcastReportPollTimer) {
        broadcastReportPollTimer = setInterval(() => loadBroadcastReport(id, false), 4000);
      } else if (b.status === "COMPLETED" && broadcastReportPollTimer) {
        clearInterval(broadcastReportPollTimer);
        broadcastReportPollTimer = null;
      }
    })
    .catch(() => {
      if (broadcastReportPollTimer) { clearInterval(broadcastReportPollTimer); broadcastReportPollTimer = null; }
      if (isFirstLoad) $("#content").innerHTML = `<div class="empty-state"><strong>Broadcast not found.</strong></div>`;
    });
}

function buildBroadcastReport(b, items) {
  const failedItems = items.filter(i => i.status === "FAILED");
  openPage({
    title: "Broadcast report",
    eyebrow: "Engagement",
    backHref: "index.html",
    hideFooter: true,
    wide: true,
    bodyHtml: `
      <div id="report-root">
      <div class="flex-between">
        <div>
          <div class="cell-strong">${channelChip(b.channel)}</div>
          <div class="cell-sub" style="margin-top:4px;">${b.createdAt ? fmtDateTime(new Date(b.createdAt)) : ""}</div>
        </div>
        ${statusBadge(bulkStatusLabel(b.status))}
      </div>
      <div class="card mt-16" style="box-shadow:none;border:1px dashed var(--border);">
        <div class="card-head"><h3>Message content</h3></div>
        <div style="font-size:13px;white-space:pre-wrap;">${escapeHtml(b.content)}</div>
      </div>
      <div class="grid grid-3 mt-16">
        ${kpiCard("List Size", b.listSize, "", "up", "👥")}
        ${kpiCard("Sent", b.sentCount, "", "up", "📤")}
        ${kpiCard("Delivered", b.deliveredCount, "", "up", "📬")}
        ${kpiCard("Failed", b.failedCount, "", "down", "⚠️")}
        ${kpiCard("Opted Out", b.optedOutCount, "", "down", "🚫")}
      </div>
      ${b.status !== "COMPLETED" ? `<p class="text-muted mt-8" style="font-size:12.5px;">Sending in progress — this page refreshes automatically.</p>` : ""}
      <div class="card mt-16">
        <div class="card-head flex-between"><h3>Recipients${failedItems.length ? ` — ${failedItems.length} failed` : ""}</h3>
          ${b.status === "COMPLETED" && b.failedCount > 0 ? `<button class="btn btn-sm btn-blue" id="report-retry-btn">Retry failed sends</button>` : ""}
        </div>
        <div class="table-wrap" style="border:none;">
          <table class="data-table">
            <thead><tr><th>Recipient</th><th>Status</th><th>Detail</th></tr></thead>
            <tbody>
              ${items.length ? items.map(i => `
                <tr>
                  <td>${escapeHtml(i.recipient || "—")}</td>
                  <td>${statusBadge(itemStatusLabel(i.status))}</td>
                  <td class="text-muted" style="font-size:12px;">${escapeHtml(i.failureReason || "—")}</td>
                </tr>`).join("") : `<tr><td colspan="3"><div class="empty-state"><strong>No recipients</strong></div></td></tr>`}
            </tbody>
          </table>
        </div>
      </div>
      </div>
    `
  });
  const retryBtn = $("#report-retry-btn");
  if (retryBtn) retryBtn.addEventListener("click", () => {
    retryBtn.disabled = true;
    api.broadcasts.retry(b.id)
      .then(() => { toast("Retrying failed sends…", "success"); loadBroadcastReport(b.id, false); })
      .catch(err => { toast(err.message || "Could not retry.", "error"); retryBtn.disabled = false; });
  });
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initBroadcastsIndex() { renderBroadcastsIndex(); }
function initBroadcastsCreate() { renderBroadcastCreatePage(); }
function initBroadcastsReport() { renderBroadcastReportPage(getQueryParam("id")); }
