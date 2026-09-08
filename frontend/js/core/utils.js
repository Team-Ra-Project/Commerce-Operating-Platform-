/* ============================================================================
   RA Studio — Commerce Operations Platform — core utilities
   Ported unchanged from the original SPA build.
   ============================================================================ */
/* ============================================================================
   RA Studio — Commerce Operations Platform (Vanilla JS, no dependencies)
   Rebuilt to fully cover the Full Workflow Document and the
   Button & Action Documentation: every module, every documented button,
   wired to realistic dummy data and working state transitions.
   ============================================================================ */

/* ---------------------------------------------------------------------------
   1. UTILITIES
--------------------------------------------------------------------------- */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

function rand(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min; }
function randFloat(min, max, dec = 1) { return Number((Math.random() * (max - min) + min).toFixed(dec)); }
function pick(arr) { return arr[rand(0, arr.length - 1)]; }
function pickMany(arr, n) { return [...arr].sort(() => Math.random() - 0.5).slice(0, n); }
function money(n) { return "₹" + Number(n).toLocaleString("en-IN", { maximumFractionDigits: 0 }); }
function moneyDec(n) { return "₹" + Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
function pad(n) { return n.toString().padStart(2, "0"); }
function daysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d;
}
function fmtDate(d) {
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}
function fmtDateTime(d) {
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" }) + ", " + d.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" });
}
function initials(name) {
  return name.split(" ").map(w => w[0]).slice(0, 2).join("").toUpperCase();
}
function escapeHtml(str) {
  return String(str).replace(/[&<>"']/g, s => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[s]));
}
function uid(prefix) { return prefix + "-" + Math.random().toString(36).slice(2, 8).toUpperCase(); }

/* Client-side "download" — builds a Blob and triggers a save, no backend needed. */
function downloadFile(filename, content, mime = "text/csv") {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = filename;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function toCSV(rows, headers) {
  const esc = v => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const lines = [headers.map(esc).join(",")];
  rows.forEach(r => lines.push(headers.map(h => esc(r[h])).join(",")));
  return lines.join("\n");
}

/* Minimal hand-built single-page PDF (no libraries) — enough for a real,
   openable PDF export of simple report text, entirely client-side. */
function buildSimplePDF(title, lines) {
  const esc = s => String(s).replace(/[()\\]/g, c => "\\" + c);
  let y = 760;
  const textOps = [`BT /F1 16 Tf 50 ${y} Td (${esc(title)}) Tj ET`];
  y -= 30;
  lines.forEach(line => {
    if (y < 40) return;
    textOps.push(`BT /F1 11 Tf 50 ${y} Td (${esc(line)}) Tj ET`);
    y -= 18;
  });
  const content = textOps.join("\n");
  const objs = [];
  objs.push("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj");
  objs.push("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj");
  objs.push("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >> endobj");
  objs.push(`4 0 obj << /Length ${content.length} >> stream\n${content}\nendstream endobj`);
  objs.push("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj");
  let pdf = "%PDF-1.4\n";
  const offsets = [];
  objs.forEach(o => { offsets.push(pdf.length); pdf += o + "\n"; });
  const xrefStart = pdf.length;
  pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n`;
  offsets.forEach(off => { pdf += off.toString().padStart(10, "0") + " 00000 n \n"; });
  pdf += `trailer << /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`;
  return pdf;
}


function getQueryParam(name) {
  return new URLSearchParams(location.search).get(name);
}
