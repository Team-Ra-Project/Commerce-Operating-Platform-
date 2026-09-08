/* ============================================================================
   PRODUCT MANAGEMENT (Phase 6 — real backend)
   Buttons/actions: Add New Product, Edit Product, Delete Product, Add Variant,
   Upload Image(s), Set Pricing, Assign Category, Select Marketplaces to
   Publish, Publish to Marketplaces, Save as Draft.
   Backed by GET/POST/PUT/DELETE /api/products, /api/categories,
   /api/products/{id}/variants, /api/products/{id}/image,
   /api/products/{id}/publish, /api/products/{id}/save-draft.
   Stock/threshold/warehouse are intentionally NOT shown here — those belong
   to the inventory_item/warehouse tables (Phase 7), not the product tables
   this phase implements (category, product, product_variant,
   product_marketplace_listing).
   index.html = list. create.html / detail.html?id= are real pages, per the
   multi-page brief's example.
   ============================================================================ */
let productFilters = { search: "", category: "All", status: "All statuses" };
const MARKETPLACE_ENUM = { "Shopify": "SHOPIFY", "Amazon": "AMAZON", "Flipkart": "FLIPKART", "WooCommerce": "WOOCOMMERCE", "Magento": "MAGENTO", "Meesho": "MEESHO", "Etsy": "ETSY" };
const PRODUCT_STATUSES = ["Draft", "Published"];

function productStatusLabel(status) { return status === "PUBLISHED" ? "Published" : "Draft"; }
function listingStatusBadge(status) {
  const map = { PUBLISHED: "green", FAILED: "red", PENDING: "amber" };
  return `<span class="badge ${map[status] || "gray"}">${status}</span>`;
}

/* -------------------------------- INDEX -------------------------------- */
function renderProducts() {
  $("#content").innerHTML = `
    <div class="page-head">
      <div>
        <div class="page-eyebrow">Catalog</div>
        <h1 class="page-title">Product Management</h1>
        <p class="page-sub" id="products-summary">Loading products…</p>
      </div>
      <div class="page-actions"><a class="btn btn-blue" href="create.html">+ Add product</a></div>
    </div>

    <div class="card">
      <div class="table-toolbar">
        <input type="text" class="text-input" id="product-search" placeholder="Search products…" style="min-width:240px;">
        <div class="toolbar-filters">
          <select class="select-input" id="product-category-filter"><option>All</option></select>
          <select class="select-input" id="product-status-filter">
            <option>All statuses</option>
            ${PRODUCT_STATUSES.map(s => `<option>${s}</option>`).join("")}
          </select>
        </div>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead><tr>
            <th>Product</th><th>Category</th><th>Variant</th><th>Price</th><th>Status</th><th>Published to</th><th></th>
          </tr></thead>
          <tbody id="product-table-body"><tr><td colspan="7"><div class="empty-state"><strong>Loading…</strong></div></td></tr></tbody>
        </table>
      </div>
      <div class="pagination"><span id="product-count"></span><span>Showing all matching results</span></div>
    </div>
  `;
  const q = getQueryParam("q");
  if (q) { productFilters.search = q.toLowerCase(); $("#product-search").value = q; }

  Promise.all([api.products.list(), api.categories.list()])
    .then(([products, categories]) => {
      productIndexState.products = products;
      productIndexState.categories = categories;
      $("#product-category-filter").innerHTML = `<option>All</option>` + categories.map(c => `<option>${escapeHtml(c.name)}</option>`).join("");
      $("#products-summary").textContent = `${products.length} products across ${categories.length} categories.`;
      drawProductTable();
    })
    .catch(err => {
      $("#products-summary").textContent = "Could not load products.";
      $("#product-table-body").innerHTML = `<tr><td colspan="7"><div class="empty-state"><div class="e-ico">⚠️</div><strong>Could not load products</strong>${escapeHtml(err.message || "")}</div></td></tr>`;
    });

  $("#product-search").addEventListener("input", e => { productFilters.search = e.target.value.toLowerCase(); drawProductTable(); });
  $("#product-category-filter").addEventListener("change", e => { productFilters.category = e.target.value; drawProductTable(); });
  $("#product-status-filter").addEventListener("change", e => { productFilters.status = e.target.value; drawProductTable(); });
}

const productIndexState = { products: [], categories: [] };

function drawProductTable() {
  let rows = productIndexState.products.filter(p => {
    const matchSearch = !productFilters.search || p.name.toLowerCase().includes(productFilters.search);
    const matchCat = !productFilters.category || productFilters.category === "All" || p.categoryName === productFilters.category;
    const matchStatus = !productFilters.status || productFilters.status === "All statuses" || productStatusLabel(p.status) === productFilters.status;
    return matchSearch && matchCat && matchStatus;
  });
  $("#product-count").textContent = `${rows.length} products`;
  $("#product-table-body").innerHTML = rows.length ? rows.map(p => {
    const variants = p.variants || [];
    const published = (p.listings || []).filter(l => l.status === "PUBLISHED");
    return `
    <tr>
      <td><div class="row-flex">${productThumb(p)}<div><div class="cell-strong">${escapeHtml(p.name)}</div><div class="cell-sub">PRD-${p.id}${p.status === "DRAFT" ? " · <span style='color:var(--amber-600)'>Draft</span>" : ""}</div></div></div></td>
      <td>${escapeHtml(p.categoryName || "—")}</td>
      <td>${variants.length > 1 ? `${variants.length} variants` : (variants[0] ? escapeHtml(variants[0].name) : "—")}</td>
      <td class="cell-strong">${moneyDec(p.basePrice)}</td>
      <td>${statusBadge(productStatusLabel(p.status))}</td>
      <td>${published.length ? published.map(l => `<span class="chip">${l.marketplace}</span>`).join(" ") : '<span class="text-muted" style="font-size:12px;">Not published</span>'}</td>
      <td>
        <div class="action-group">
          <a class="icon-action" title="Edit" href="detail.html?id=${p.id}">✎</a>
          <button class="icon-action" title="Delete" onclick="deleteProduct(${p.id})">🗑</button>
        </div>
      </td>
    </tr>
  `; }).join("") : `<tr><td colspan="7"><div class="empty-state"><div class="e-ico">🔍</div><strong>No products found</strong>Try adjusting your search or filters.</div></td></tr>`;
}

function productThumb(p) {
  const url = api.products.assetUrl(p.imagePath);
  return url ? `<div class="thumb" style="background-image:url('${url}');background-size:cover;background-position:center;"></div>` : `<div class="thumb">🏷️</div>`;
}

function deleteProduct(id) {
  openModal({
    title: "Delete product",
    bodyHtml: `<p style="font-size:13.5px;color:var(--navy-600);line-height:1.6;">This will permanently remove the product from your catalog. It will need to be manually delisted from any marketplaces it was published to. This action can't be undone.</p>`,
    confirmLabel: "Delete product",
    danger: true,
    onSubmit: () => {
      api.products.remove(id)
        .then(() => { toast("Product deleted.", "success"); return api.products.list(); })
        .then(products => { productIndexState.products = products; drawProductTable(); })
        .catch(err => toast(err.message || "Could not delete product.", "error"));
    }
  });
}

/* ---------------------------- CREATE / DETAIL ---------------------------- */
function renderProductForm(existing) {
  const isEdit = !!existing;
  $("#content").innerHTML = `<div class="empty-state"><strong>Loading…</strong></div>`;

  Promise.all([api.categories.list(), Promise.resolve(existing)])
    .then(([categories]) => buildProductForm(isEdit, existing, categories))
    .catch(err => { $("#content").innerHTML = `<div class="empty-state"><strong>Could not load form.</strong>${escapeHtml(err.message || "")}</div>`; });
}

function buildProductForm(isEdit, p, categories) {
  const variants = isEdit ? p.variants.map(v => ({ ...v })) : [{ id: null, name: "", sku: "", priceOverride: null }];
  const publishedTo = isEdit ? (p.listings || []).filter(l => l.status === "PUBLISHED").map(l => l.marketplace) : [];
  const listingByMarketplace = {};
  if (isEdit) (p.listings || []).forEach(l => { listingByMarketplace[l.marketplace] = l; });

  openPage({
    title: isEdit ? "Edit product — " + p.name : "Add product",
    eyebrow: "Catalog",
    backHref: "index.html",
    hideFooter: true,
    wide: true,
    bodyHtml: `
      <div class="form-grid">
        <div class="form-field full"><label>Product name</label><input name="name" value="${isEdit ? escapeHtml(p.name) : ""}" placeholder="e.g. Nord Oversized Hoodie" required></div>
        <div class="form-field full"><label>Description</label><textarea name="description" rows="2" placeholder="Short product description">${isEdit && p.description ? escapeHtml(p.description) : ""}</textarea></div>
        <div class="form-field"><label>Category</label>
          <select name="category">${categories.map(c => `<option ${isEdit && p.categoryName === c.name ? "selected" : ""}>${escapeHtml(c.name)}</option>`).join("")}</select>
        </div>
        <div class="form-field"><label>Price</label><input name="price" type="number" step="0.01" min="0" value="${isEdit ? p.basePrice : ""}" required></div>
        <div class="form-field full">
          <label>Product image</label>
          <input type="file" name="image" accept="image/png,image/jpeg,image/webp,image/gif" id="prod-image-input">
          <div class="text-muted" style="font-size:12px;margin-top:4px;" id="prod-image-name">
            ${isEdit && p.imagePath ? `Current: <img src="${api.products.assetUrl(p.imagePath)}" alt="" style="height:28px;vertical-align:middle;border-radius:4px;margin-left:4px;">` : "No image uploaded yet."}
          </div>
        </div>
      </div>

      <div class="mt-16">
        <div class="flex-between"><label style="font-size:12.5px;font-weight:600;color:var(--navy-600);">Variants</label><button type="button" class="btn btn-sm" id="add-variant-btn">+ Add variant</button></div>
        <div id="variant-rows" class="mt-8"></div>
      </div>

      <div class="mt-16">
        <label style="font-size:12.5px;font-weight:600;color:var(--navy-600);display:block;margin-bottom:8px;">Publish to marketplaces</label>
        <div class="checkbox-grid" id="publish-checks">
          ${MARKETPLACES_LIST.map(m => {
            const code = MARKETPLACE_ENUM[m];
            const listing = listingByMarketplace[code];
            const statusNote = listing ? ` ${listingStatusBadge(listing.status)}` : "";
            return `<label class="checkbox-row"><input type="checkbox" value="${code}" ${publishedTo.includes(code) ? "checked" : ""}> ${m}${statusNote}</label>`;
          }).join("")}
        </div>
        ${isEdit ? renderListingErrors(p.listings) : ""}
      </div>
    `
  });

  const form = $("#page-form");
  const footer = document.createElement("div");
  footer.className = "modal-foot";
  footer.innerHTML = `
    <a class="btn" href="index.html">Cancel</a>
    <button type="button" class="btn" id="save-draft-btn">Save as draft</button>
    <button type="button" class="btn btn-blue" id="publish-btn">${isEdit ? "Save & publish" : "Publish product"}</button>
  `;
  form.appendChild(footer);

  function drawVariantRows() {
    $("#variant-rows").innerHTML = variants.map((v, i) => `
      <div class="variant-row" data-idx="${i}">
        <input type="text" class="variant-name" placeholder="e.g. Black / M" value="${escapeHtml(v.name || "")}">
        <input type="text" class="variant-sku" placeholder="SKU (auto-generated if blank)" value="${escapeHtml(v.sku || "")}">
        <input type="number" class="variant-price" placeholder="Price override" step="0.01" min="0" value="${v.priceOverride != null ? v.priceOverride : ""}" style="max-width:130px;">
        ${variants.length > 1 ? `<button type="button" class="icon-action remove-variant" title="Remove">✕</button>` : `<span></span>`}
      </div>
    `).join("");
    $$(".variant-row").forEach(row => {
      const idx = Number(row.dataset.idx);
      row.querySelector(".variant-name").addEventListener("input", e => variants[idx].name = e.target.value);
      row.querySelector(".variant-sku").addEventListener("input", e => variants[idx].sku = e.target.value);
      row.querySelector(".variant-price").addEventListener("input", e => variants[idx].priceOverride = e.target.value === "" ? null : Number(e.target.value));
      const rm = row.querySelector(".remove-variant");
      if (rm) rm.addEventListener("click", () => { variants.splice(idx, 1); drawVariantRows(); });
    });
  }
  drawVariantRows();
  $("#add-variant-btn").addEventListener("click", () => { variants.push({ id: null, name: "", sku: "", priceOverride: null }); drawVariantRows(); });

  let uploadedImageFile = null;
  $("#prod-image-input").addEventListener("change", e => {
    if (e.target.files[0]) { uploadedImageFile = e.target.files[0]; $("#prod-image-name").textContent = "Selected: " + uploadedImageFile.name; }
  });

  function collectRequest() {
    const fd = new FormData(form);
    const cleanVariants = variants.filter(v => v.name && v.name.trim()).map(v => ({
      id: v.id || null, name: v.name.trim(), sku: v.sku ? v.sku.trim() : null,
      priceOverride: v.priceOverride === "" || v.priceOverride == null ? null : Number(v.priceOverride)
    }));
    const checkedMarketplaces = $$("#publish-checks input:checked").map(c => c.value);
    return {
      request: {
        name: fd.get("name"), description: fd.get("description") || null,
        categoryName: fd.get("category"), basePrice: Number(fd.get("price")),
        variants: cleanVariants
      },
      checkedMarketplaces
    };
  }

  function afterSaveImage(productId) {
    if (!uploadedImageFile) return Promise.resolve();
    return api.products.uploadImage(productId, uploadedImageFile);
  }

  function saveProduct(asDraft) {
    const { request, checkedMarketplaces } = collectRequest();
    if (!request.name || !request.name.trim() || !request.basePrice || request.basePrice <= 0) {
      toast("Please fill in product name and a price greater than zero.", "error"); return;
    }
    const savePromise = isEdit ? api.products.update(p.id, request) : api.products.create(request);
    savePromise
      .then(saved => afterSaveImage(saved.id).then(() => saved))
      .then(saved => {
        if (asDraft) {
          return api.products.saveDraft(saved.id).then(() => {
            toast("Product saved as draft.", "success");
            location.href = "index.html";
          });
        }
        if (checkedMarketplaces.length === 0) {
          toast("Product saved, but no marketplaces were selected to publish to.", "success");
          location.href = "index.html";
          return;
        }
        return api.products.publish(saved.id, checkedMarketplaces).then(res => {
          const succeeded = (res.results || []).filter(r => r.status === "PUBLISHED").length;
          const failed = (res.results || []).length - succeeded;
          if (succeeded && !failed) toast(`Product published to ${succeeded} marketplace(s).`, "success");
          else if (succeeded && failed) toast(`Published to ${succeeded} marketplace(s), ${failed} failed — check marketplace connections.`, "success");
          else toast(`Publish failed for all selected marketplaces — check they're connected under Marketplace Integration.`, "error");
          location.href = "index.html";
        });
      })
      .catch(err => toast(err.message || "Could not save product.", "error"));
  }

  $("#save-draft-btn").addEventListener("click", () => saveProduct(true));
  $("#publish-btn").addEventListener("click", () => saveProduct(false));
  form.addEventListener("submit", (e) => e.preventDefault());
}

function renderListingErrors(listings) {
  const failed = (listings || []).filter(l => l.status === "FAILED" && l.lastError);
  if (!failed.length) return "";
  return `<div class="text-muted" style="font-size:12px;margin-top:8px;color:var(--red-600, #c0392b);">
    ${failed.map(l => `${l.marketplace}: ${escapeHtml(l.lastError)}`).join("<br>")}
  </div>`;
}

/* -------------------------------- BOOTSTRAP ------------------------------ */
function initProductsIndex() { renderProducts(); }
function initProductCreate() { renderProductForm(null); }
function initProductDetail() {
  const id = getQueryParam("id");
  $("#content").innerHTML = `<div class="empty-state"><strong>Loading…</strong></div>`;
  api.products.get(id)
    .then(p => renderProductForm(p))
    .catch(() => { $("#content").innerHTML = `<div class="empty-state"><strong>Product not found.</strong></div>`; });
}
