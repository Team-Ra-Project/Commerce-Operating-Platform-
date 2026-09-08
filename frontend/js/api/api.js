/* ============================================================================
   API ABSTRACTION LAYER
   ============================================================================ */

const API_BASE_URL =
  window.RA_API_BASE_URL || "http://localhost:8080/api";

function saveSession(session) {
  if (!session || !session.accessToken) return false;

  localStorage.setItem("ra_access_token", session.accessToken);

  if (session.refreshToken) {
    localStorage.setItem("ra_refresh_token", session.refreshToken);
  }

  if (session.user) {
    localStorage.setItem("ra_user", JSON.stringify(session.user));
  }

  return true;
}

function clearSession() {
  localStorage.removeItem("ra_access_token");
  localStorage.removeItem("ra_refresh_token");
  localStorage.removeItem("ra_user");
}

function isRefreshableRequest(path) {
  return ![
    "/auth/register",
    "/auth/verify-email",
    "/auth/login",
    "/auth/refresh"
  ].includes(path);
}

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem("ra_refresh_token");

  if (!refreshToken) {
    return false;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        refreshToken: refreshToken
      })
    });

    const body = await response.json().catch(() => ({}));

    return response.ok && saveSession(body);
  } catch (error) {
    return false;
  }
}

async function apiRequest(path, options = {}, allowRefresh = true) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {})
  };

  const accessToken = localStorage.getItem("ra_access_token");

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  let response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers
    });
  } catch (error) {
    throw new Error(
      `Cannot reach the API at ${API_BASE_URL}. Make sure the backend is running and CORS is configured.`
    );
  }

  /*
   * Access tokens expire after a short period. Refresh once and retry the
   * original request so Settings and User Management continue working.
   */
  if (
    response.status === 401 &&
    allowRefresh &&
    isRefreshableRequest(path) &&
    await refreshAccessToken()
  ) {
    return apiRequest(path, options, false);
  }

  if (response.status === 204) {
    return null;
  }

  const body = await response.json().catch(() => ({}));

  if (!response.ok) {
    if (response.status === 401) {
      clearSession();
      throw new Error("Your session has expired. Please sign in again.");
    }

    if (response.status === 403) {
      throw new Error(
        body.message || "You are not authorized to perform this action."
      );
    }

    throw new Error(
      body.message || `Request failed (${response.status})`
    );
  }

  return body;
}

/* Same as apiRequest but for multipart/form-data (file uploads) — must NOT
   set a Content-Type header itself, or the browser-generated multipart
   boundary is lost and the server can't parse the body. Reuses the same
   401-refresh-and-retry behavior as apiRequest. */
async function apiUpload(path, formData, allowRefresh = true) {
  const headers = {};
  const accessToken = localStorage.getItem("ra_access_token");
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { method: "POST", headers, body: formData });
  } catch (error) {
    throw new Error(`Cannot reach the API at ${API_BASE_URL}. Make sure the backend is running and CORS is configured.`);
  }

  if (response.status === 401 && allowRefresh && await refreshAccessToken()) {
    return apiUpload(path, formData, false);
  }

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401) { clearSession(); throw new Error("Your session has expired. Please sign in again."); }
    if (response.status === 403) throw new Error(body.message || "You are not authorized to perform this action.");
    throw new Error(body.message || `Upload failed (${response.status})`);
  }
  return body;
}

/* Resolves a backend-relative asset path (e.g. "/uploads/products/x.png")
   returned as product.imagePath into an absolute URL the <img> tag can load,
   since the frontend and API are served from different origins. */
function apiAssetUrl(path) {
  if (!path) return null;
  if (/^https?:\/\//i.test(path)) return path;
  const origin = API_BASE_URL.replace(/\/api\/?$/, "");
  return origin + path;
}

/* Same as apiRequest but for endpoints that return plain text (e.g. a CSV
   export) rather than JSON. Reuses the same auth header and 401-refresh
   behavior; only the response body parsing differs. */
async function apiRequestRaw(path, options = {}, allowRefresh = true) {
  const headers = { ...(options.headers || {}) };
  const accessToken = localStorage.getItem("ra_access_token");
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  } catch (error) {
    throw new Error(`Cannot reach the API at ${API_BASE_URL}. Make sure the backend is running and CORS is configured.`);
  }

  if (response.status === 401 && allowRefresh && isRefreshableRequest(path) && await refreshAccessToken()) {
    return apiRequestRaw(path, options, false);
  }

  if (!response.ok) {
    if (response.status === 401) { clearSession(); throw new Error("Your session has expired. Please sign in again."); }
    if (response.status === 403) throw new Error("You are not authorized to perform this action.");
    const text = await response.text().catch(() => "");
    throw new Error(text || `Request failed (${response.status})`);
  }
  return response.text();
}

const api = {
  request: apiRequest,

  health: () => apiRequest("/health"),

  auth: {
    register: (payload) =>
      apiRequest("/auth/register", {
        method: "POST",
        body: JSON.stringify(payload)
      }),

    verifyEmail: (token) =>
      apiRequest("/auth/verify-email", {
        method: "POST",
        body: JSON.stringify({ token })
      }),

    login: (email, password) =>
      apiRequest("/auth/login", {
        method: "POST",
        body: JSON.stringify({
          email: email,
          password: password
        })
      }),

    refresh: () =>
      apiRequest("/auth/refresh", {
        method: "POST",
        body: JSON.stringify({
          refreshToken: localStorage.getItem("ra_refresh_token")
        })
      }),

    me: () => apiRequest("/auth/me"),

    logout: () =>
      apiRequest("/auth/logout", {
        method: "POST",
        body: JSON.stringify({
          refreshToken: localStorage.getItem("ra_refresh_token")
        })
      })
  },

  users: {
    list: () => apiRequest("/users"),

    invite: (fullName, email, role) =>
      apiRequest("/users/invite", {
        method: "POST",
        body: JSON.stringify({
          fullName: fullName,
          email: email,
          role: role
        })
      }),

    update: (id, patch) =>
      apiRequest(`/users/${id}`, {
        method: "PUT",
        body: JSON.stringify(patch)
      }),

    deactivate: (id) =>
      apiRequest(`/users/${id}/deactivate`, {
        method: "POST"
      }),

    invitationPreview: (token) =>
      apiRequest(`/users/invitation/${encodeURIComponent(token)}`),

    acceptInvitation: (token, password) =>
      apiRequest("/users/accept-invitation", {
        method: "POST",
        body: JSON.stringify({
          token: token,
          password: password
        })
      })
  },

  organization: {
    getConfig: () => apiRequest("/organization/config"),

    saveConfig: (config) =>
      apiRequest("/organization/config", {
        method: "PUT",
        body: JSON.stringify(config)
      })
  },

  // Dashboard (Phase 4 — real backend data, not mock arrays)
  dashboard: {
    summary: (range, marketplace) => apiRequest(`/dashboard/summary?range=${encodeURIComponent(range)}&marketplace=${encodeURIComponent(marketplace)}`),
    recentActivity: (limit = 10) => apiRequest(`/dashboard/recent-activity?limit=${limit}`)
  },

  analytics: {
    report: (filters = {}) => {
      const params = new URLSearchParams();
      ["type", "range", "marketplace", "category", "warehouseId"].forEach(key => {
        if (filters[key] !== undefined && filters[key] !== null && filters[key] !== "" &&
            !(["marketplace", "category"].includes(key) && filters[key] === "All")) {
          params.set(key, filters[key]);
        }
      });
      return apiRequest(`/analytics/report?${params.toString()}`);
    },
    reportCsv: (filters = {}) => {
      const params = new URLSearchParams();
      ["type", "range", "marketplace", "category", "warehouseId"].forEach(key => {
        if (filters[key] !== undefined && filters[key] !== null && filters[key] !== "" &&
            !(["marketplace", "category"].includes(key) && filters[key] === "All")) {
          params.set(key, filters[key]);
        }
      });
      return apiRequestRaw(`/analytics/report.csv?${params.toString()}`);
    }
  },

  bi: {
    overview: () => apiRequest("/bi/overview"),
    dismissRecommendation: (id) => apiRequest(`/bi/recommendations/${id}/dismiss`, { method: "POST" })
  },

  // Catalog (Phase 6 — real backend, replaces the PRODUCTS mock array for
  // the Product Management pages only; other still-mocked modules that read
  // the PRODUCTS/CATEGORIES globals directly are untouched by this phase)
  categories: {
    list: () => apiRequest("/categories")
  },
  products: {
    list: () => apiRequest("/products"),
    get: (id) => apiRequest(`/products/${id}`),
    create: (payload) => apiRequest("/products", { method: "POST", body: JSON.stringify(payload) }),
    update: (id, payload) => apiRequest(`/products/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
    remove: (id) => apiRequest(`/products/${id}`, { method: "DELETE" }),
    publish: (id, marketplaces) => apiRequest(`/products/${id}/publish`, { method: "POST", body: JSON.stringify({ marketplaces }) }),
    saveDraft: (id) => apiRequest(`/products/${id}/save-draft`, { method: "POST" }),
    uploadImage: (id, file) => { const fd = new FormData(); fd.append("file", file); return apiUpload(`/products/${id}/image`, fd); },
    assetUrl: apiAssetUrl
  },

  // Inventory (real API — Phase 7: Inventory Management. Used by frontend/inventory/*.html)
  inventory: {
    list: (warehouseId) => apiRequest(`/inventory${warehouseId ? `?warehouseId=${warehouseId}` : ""}`),
    warehouses: () => apiRequest("/inventory/warehouses"),
    createWarehouse: (payload) => apiRequest("/inventory/warehouses", { method: "POST", body: JSON.stringify(payload) }),
    stockCount: (payload) => apiRequest("/inventory/stock-count", { method: "POST", body: JSON.stringify(payload) }),
    setThreshold: (payload) => apiRequest("/inventory/threshold", { method: "PUT", body: JSON.stringify(payload) }),
    movements: (variantId, warehouseId, limit = 30) => apiRequest(`/inventory/movements?variantId=${variantId}&warehouseId=${warehouseId}&limit=${limit}`),
    exportCsv: (warehouseId) => apiRequestRaw(`/inventory/export${warehouseId ? `?warehouseId=${warehouseId}` : ""}`)
  },

  // Orders (real API — Phase 8: Order Management. Used by frontend/orders/*.html)
  orders: {
    list: (status, marketplace) => {
      const params = new URLSearchParams();
      if (status && status !== "All") params.set("status", status);
      if (marketplace && marketplace !== "All") params.set("marketplace", marketplace);
      const qs = params.toString();
      return apiRequest(`/orders${qs ? `?${qs}` : ""}`);
    },
    get: (id) => apiRequest(`/orders/${id}`),
    ingest: (payload) => apiRequest("/orders", { method: "POST", body: JSON.stringify(payload) }),
    confirm: (id) => apiRequest(`/orders/${id}/confirm`, { method: "POST" }),
    pack: (id) => apiRequest(`/orders/${id}/pack`, { method: "POST" }),
    ship: (id, payload) => apiRequest(`/orders/${id}/ship`, { method: "POST", body: JSON.stringify(payload) }),
    track: (id) => apiRequest(`/orders/${id}/track`, { method: "POST" })
  },

  // Returns (real API — Phase 9: Returns & Shipping Management. Used by frontend/returns/*.html)
  returns: {
    list: (status) => apiRequest(`/returns${status && status !== "All" ? `?status=${status}` : ""}`),
    get: (id) => apiRequest(`/returns/${id}`),
    create: (payload) => apiRequest("/returns", { method: "POST", body: JSON.stringify(payload) }),
    approve: (id) => apiRequest(`/returns/${id}/approve`, { method: "POST" }),
    reject: (id, reason) => apiRequest(`/returns/${id}/reject`, { method: "POST", body: JSON.stringify({ reason }) }),
    receive: (id, payload) => apiRequest(`/returns/${id}/receive`, { method: "POST", body: JSON.stringify(payload) }),
    resolve: (id, payload) => apiRequest(`/returns/${id}/resolve`, { method: "POST", body: JSON.stringify(payload) }),
    close: (id) => apiRequest(`/returns/${id}/close`, { method: "POST" })
  },

  // Customers / CRM / rule-based segments (real API)
  customers: {
    list: (search) => apiRequest(`/customers${search ? `?search=${encodeURIComponent(search)}` : ""}`),
    get: (id) => apiRequest(`/customers/${id}`),
    addNote: (id, noteText) => apiRequest(`/customers/${id}/notes`, { method: "POST", body: JSON.stringify({ noteText }) }),
    exportCsv: () => apiRequestRaw("/customers/export")
  },

  // Legacy mock accessors — still used by modules outside Phase 6 (orders,
  // competitors, dashboard, automation, etc.) that generate their own mock
  // data from these arrays at page load. Left unchanged.
  getProducts: () => PRODUCTS,
  getProduct: (id) => PRODUCTS.find((p) => p.id === id),

  getWarehouses: () => WAREHOUSES,

  getOrders: () => ORDERS,
  getOrder: (id) => ORDERS.find((o) => o.id === id),

  getReturns: () => RETURNS,
  getReturn: (id) => RETURNS.find((r) => r.id === id),

  getCustomers: () => CUSTOMERS,
  getCustomer: (id) => CUSTOMERS.find((c) => c.id === id),

  getSegments: () => SEGMENTS,
  getSegment: (id) => SEGMENTS.find((s) => s.id === id),

  getCompetitorData: () => COMPETITOR_DATA,
  getCompetitorRow: (id) =>
    COMPETITOR_DATA.find((d) => d.id === id),

  getAutomations: () => AUTOMATIONS,
  getAutomation: (id) =>
    AUTOMATIONS.find((a) => a.id === id),

  getMarketplaces: () => MARKETPLACE_DETAILS,
  getMarketplace: (name) =>
    MARKETPLACE_DETAILS.find((m) => m.name === name),

  // Marketplaces (real API — Phase 5: Marketplace Integration Foundation. Used by frontend/marketplace/*.html)
  marketplaces: {
    list: () => apiRequest("/marketplaces"),

    get: (name) => apiRequest(`/marketplaces/${name}`),

    authorize: (name, payload) =>
      apiRequest(`/marketplaces/${name}/authorize`, {
        method: "POST",
        body: JSON.stringify(payload || {})
      }),

    testConnection: (name) =>
      apiRequest(`/marketplaces/${name}/test-connection`, {
        method: "POST"
      }),

    sync: (name) =>
      apiRequest(`/marketplaces/${name}/sync`, {
        method: "POST"
      }),

    disconnect: (name) =>
      apiRequest(`/marketplaces/${name}`, {
        method: "DELETE"
      }),

    logs: (name, limit = 50) =>
      apiRequest(`/marketplaces/${name}/logs?limit=${limit}`)
  },

  // Message Templates (real API — Phase 19). Used by frontend/messaging/templates.html + create-template.html.
  templates: {
    list: () => apiRequest("/templates"),
    get: (id) => apiRequest(`/templates/${id}`),
    create: (payload) => apiRequest("/templates", { method: "POST", body: JSON.stringify(payload) }),
    update: (id, payload) => apiRequest(`/templates/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
    remove: (id) => apiRequest(`/templates/${id}`, { method: "DELETE" }),
    submitForApproval: (id) => apiRequest(`/templates/${id}/submit-for-approval`, { method: "POST" })
  },

  // Segments (real API — minimal Phase 20-supporting module, see backend Segment.java for scope notes).
  segments: {
    list: () => apiRequest("/segments"),
    create: (payload) => apiRequest("/segments", { method: "POST", body: JSON.stringify(payload) }),
    update: (id, payload) => apiRequest(`/segments/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
    remove: (id) => apiRequest(`/segments/${id}`, { method: "DELETE" })
  },

  competitors: {
    list: () => apiRequest("/competitors"),
    get: (id) => apiRequest(`/competitors/${id}`),
    history: (id) => apiRequest(`/competitors/${id}/history`),
    add: (payload) => apiRequest("/competitors", { method: "POST", body: JSON.stringify(payload) }),
    setThreshold: (id, alertThresholdPct) => apiRequest(`/competitors/${id}/threshold`, { method: "PUT", body: JSON.stringify({ alertThresholdPct }) }),
    reprice: (id, newPrice) => apiRequest(`/competitors/${id}/reprice`, { method: "POST", body: JSON.stringify({ newPrice }) })
  },

  automation: {
    list: () => apiRequest("/automation/rules"),
    get: (id) => apiRequest(`/automation/rules/${id}`),
    create: (payload) => apiRequest("/automation/rules", { method: "POST", body: JSON.stringify(payload) }),
    update: (id, payload) => apiRequest(`/automation/rules/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
    toggle: (id) => apiRequest(`/automation/rules/${id}/toggle`, { method: "POST" }),
    remove: (id) => apiRequest(`/automation/rules/${id}`, { method: "DELETE" }),
    runLog: (id) => apiRequest(`/automation/rules/${id}/run-log`)
  },

  notifications: {
    list: (unreadOnly = false) => apiRequest(`/notifications${unreadOnly ? "?unreadOnly=true" : ""}`),
    unreadCount: () => apiRequest("/notifications/unread-count"),
    read: (id) => apiRequest(`/notifications/${id}/read`, { method: "POST" }),
    readAll: () => apiRequest("/notifications/read-all", { method: "POST" })
  },

  // Retention Marketing journeys (real API — Phase 20). Used by frontend/retention/*.html.
  retentionCampaigns: {
    list: () => apiRequest("/campaigns"),
    get: (id) => apiRequest(`/campaigns/${id}`),
    create: (payload) => apiRequest("/campaigns", { method: "POST", body: JSON.stringify(payload) }),
    update: (id, payload) => apiRequest(`/campaigns/${id}`, { method: "PUT", body: JSON.stringify(payload) }),
    remove: (id) => apiRequest(`/campaigns/${id}`, { method: "DELETE" }),
    activate: (id) => apiRequest(`/campaigns/${id}/activate`, { method: "POST" }),
    pause: (id) => apiRequest(`/campaigns/${id}/pause`, { method: "POST" }),
    messages: (id) => apiRequest(`/campaigns/${id}/messages`)
  },

  // Phase 22 — Bulk Sharing / Broadcast, real backend (bulk_send / bulk_send_item).
  broadcasts: {
    list: () => apiRequest("/broadcasts"),
    get: (id) => apiRequest(`/broadcasts/${id}`),
    items: (id) => apiRequest(`/broadcasts/${id}/items`),
    parseCsv: (file) => { const fd = new FormData(); fd.append("file", file); return apiUpload("/broadcasts/parse-csv", fd); },
    create: (payload) => apiRequest("/broadcasts", { method: "POST", body: JSON.stringify(payload) }),
    retry: (id) => apiRequest(`/broadcasts/${id}/retry`, { method: "POST" })
  },

  // Legacy mock accessors remain only for deferred one-off blast pages that
  // are outside the Phase 1–20 backend scope.
  getCampaigns: () => CAMPAIGNS,
  getCampaign: (id) =>
    CAMPAIGNS.find((c) => c.id === id),

  getTemplateSends: () => TEMPLATE_SENDS,
  getTemplateSend: (id) =>
    TEMPLATE_SENDS.find((s) => s.id === id)
};