/* ============================================================================
   Dummy / mock data — ported unchanged from the original SPA build.
   Organized so it can later be swapped for real API calls (see js/api/api.js).
   ============================================================================ */
/* ---------------------------------------------------------------------------
   2. DUMMY DATA
--------------------------------------------------------------------------- */
const CATEGORIES = ["Apparel", "Footwear", "Home & Living", "Electronics Accessories", "Beauty", "Sports & Outdoors", "Kitchenware", "Bags & Travel"];
const PRODUCT_NOUNS = {
  "Apparel": ["Oversized Hoodie", "Linen Shirt", "Slim Fit Chinos", "Merino Sweater", "Denim Jacket", "Cotton Tee"],
  "Footwear": ["Running Sneaker", "Leather Loafer", "Canvas Slip-on", "Trail Runner", "Ankle Boot"],
  "Home & Living": ["Ceramic Vase", "Linen Throw", "Scented Candle", "Wall Shelf", "Table Lamp"],
  "Electronics Accessories": ["Wireless Earbuds", "USB-C Hub", "Phone Stand", "Power Bank 10K", "Laptop Sleeve"],
  "Beauty": ["Vitamin C Serum", "Matte Lipstick", "Hydrating Cream", "SPF 50 Sunscreen", "Clay Mask"],
  "Sports & Outdoors": ["Yoga Mat", "Resistance Bands", "Insulated Bottle", "Trail Backpack", "Camping Lantern"],
  "Kitchenware": ["Chef Knife Set", "Cast Iron Pan", "Pour-Over Kettle", "Bamboo Cutting Board", "Glass Storage Set"],
  "Bags & Travel": ["Weekender Duffel", "Laptop Backpack", "Packing Cubes Set", "Crossbody Bag", "Travel Organizer"]
};
const BRAND_PREFIX = ["Nord", "Aster", "Vero", "Lumen", "Kestrel", "Marlow", "Solace", "Birchline", "Cove", "Halden"];
const VARIANT_COLORS = ["Black", "Sand", "Slate", "Ivory", "Olive", "Rust", "Navy"];
const VARIANT_SIZES = ["S", "M", "L", "XL", "One Size"];
const STATUS_STOCK = ["In Stock", "Low Stock", "Out of Stock"];
const MARKETPLACES_LIST = ["Shopify", "Amazon", "Flipkart", "WooCommerce", "Magento", "Meesho", "Etsy"];
const WAREHOUSES = [
  { id: "WH-A", name: "Warehouse A — Newark, NJ", capacity: 78, status: "Healthy" },
  { id: "WH-B", name: "Warehouse B — Reno, NV", capacity: 92, status: "Near Capacity" },
  { id: "WH-C", name: "Warehouse C — Bengaluru, IN", capacity: 54, status: "Healthy" },
  { id: "WH-D", name: "Warehouse D — Rotterdam, NL", capacity: 31, status: "Underutilized" }
];

function computeStockStatus(stock, threshold) {
  return stock === 0 ? "Out of Stock" : stock < threshold ? "Low Stock" : "In Stock";
}

function generateProducts(count) {
  const list = [];
  for (let i = 0; i < count; i++) {
    const cat = pick(CATEGORIES);
    const noun = pick(PRODUCT_NOUNS[cat]);
    const brand = pick(BRAND_PREFIX);
    const stock = rand(0, 480);
    const threshold = 40;
    list.push({
      id: "PRD-" + (1000 + i),
      name: `${brand} ${noun}`,
      category: cat,
      variants: [{ id: uid("VAR"), name: `${pick(VARIANT_COLORS)} / ${pick(VARIANT_SIZES)}`, sku: "SKU-" + rand(10000, 99999) }],
      variant: "", // filled below from variants[0]
      price: randFloat(299, 17999, 0),
      stock: stock,
      threshold: threshold,
      status: computeStockStatus(stock, threshold),
      sold: rand(20, 3200),
      marketplace: pick(MARKETPLACES_LIST),
      publishedTo: pickMany(MARKETPLACES_LIST, rand(1, 4)),
      draft: false,
      warehouse: pick(WAREHOUSES).id,
      icon: pick(["👕", "👟", "🏺", "🎧", "🧴", "🧘", "🍳", "🎒"])
    });
    list[list.length - 1].variant = list[list.length - 1].variants[0].name;
  }
  return list;
}

const FIRST_NAMES = ["Aarav", "Priya", "Rohan", "Sara", "Ethan", "Maya", "Liam", "Zara", "Noah", "Ivy", "Kabir", "Elena", "Diego", "Nina", "Omar", "Chloe", "Yusuf", "Ana", "Leo", "Mia"];
const LAST_NAMES = ["Sharma", "Patel", "Cooper", "Kim", "Nguyen", "Rossi", "Khan", "Silva", "Andersson", "Mehta", "Okafor", "Fischer", "Novak", "Reyes", "Costa"];
const CITIES = [["Mumbai", "IN"], ["Austin", "US"], ["Toronto", "CA"], ["Berlin", "DE"], ["Manchester", "UK"], ["Sydney", "AU"], ["Singapore", "SG"], ["Dubai", "AE"], ["São Paulo", "BR"], ["Lagos", "NG"]];

function generateCustomers(count) {
  const list = [];
  for (let i = 0; i < count; i++) {
    const name = `${pick(FIRST_NAMES)} ${pick(LAST_NAMES)}`;
    const [city, country] = pick(CITIES);
    const orders = rand(1, 34);
    const spend = orders * randFloat(800, 6000, 0);
    const lastOrder = daysAgo(rand(0, 120));
    const segment = spend > 120000 ? "VIP" : spend > 55000 ? "Loyal" : orders <= 1 ? "New" : "Regular";
    list.push({
      id: "CUS-" + (500 + i),
      name, city, country,
      email: name.toLowerCase().replace(" ", ".") + "@mail.com",
      phone: "+1 555-01" + rand(10, 99),
      orders, spend,
      segment,
      lastOrder,
      cartAbandoned: Math.random() < 0.18,
      optIn: { whatsapp: Math.random() < 0.72, email: Math.random() < 0.9 },
      channel: pick(MARKETPLACES_LIST),
      notes: []
    });
  }
  return list;
}

const PAYMENT_STATUS = ["Paid", "Pending", "Refunded", "Failed"];
const ORDER_STAGES = ["New", "Confirmed", "Packed", "Shipped", "Out for Delivery", "Delivered"];
const COURIERS = ["BlueDart", "FedEx", "DHL Express", "Delhivery", "UPS"];

function generateOrders(count, products, customers) {
  const list = [];
  for (let i = 0; i < count; i++) {
    const p = pick(products);
    const c = pick(customers);
    const qty = rand(1, 4);
    const date = daysAgo(rand(0, 45));
    const stageIdx = rand(0, ORDER_STAGES.length - 1);
    const status = ORDER_STAGES[stageIdx];
    const history = ORDER_STAGES.slice(0, stageIdx + 1).map((s, idx) => ({
      status: s, time: daysAgo(rand(0, 45) - idx)
    }));
    list.push({
      id: "ORD-" + (78000 + i),
      customerId: c.id,
      customer: c.name,
      product: p.name,
      productId: p.id,
      marketplace: p.marketplace,
      qty,
      amount: Number((p.price * qty).toFixed(2)),
      payment: pick(PAYMENT_STATUS),
      status,
      trackingNumber: stageIdx >= 3 ? "TRK" + rand(100000000, 999999999) : null,
      courier: stageIdx >= 3 ? pick(COURIERS) : null,
      address: `${rand(10, 999)} ${pick(["Maple", "Oak", "5th", "Sunset", "Elm", "Pine"])} ${pick(["St", "Ave", "Blvd"])}, ${c.city}, ${c.country}`,
      history,
      date,
      returnId: null
    });
  }
  return list.sort((a, b) => b.date - a.date);
}

/* Returns — seeded so the Returns workflow has live data to act on */
let RETURN_SEQ = 1;
function seedReturns(orders) {
  const returns = [];
  const delivered = orders.filter(o => o.status === "Delivered");
  pickMany(delivered, Math.min(5, delivered.length)).forEach((o, i) => {
    const stageChoices = ["Requested", "Approved", "Item Received", "Refunded"];
    const stage = pick(stageChoices);
    const ret = {
      id: "RET-" + (4000 + RETURN_SEQ++),
      orderId: o.id,
      customer: o.customer,
      product: o.product,
      reason: pick(["Wrong size", "Item damaged in transit", "Changed my mind", "Not as described", "Defective item"]),
      status: stage === "Requested" ? "Requested" : stage === "Approved" ? "Approved – Awaiting Item" : stage === "Item Received" ? "Received – Inspecting" : "Refunded",
      requestedOn: daysAgo(rand(1, 20)),
      resolution: null
    };
    o.returnId = ret.id;
    returns.push(ret);
  });
  return returns;
}

const COMPETITORS = ["UrbanCrate", "Pivot & Co", "Northline Goods", "Ferro Market", "Basecamp Retail"];
function generateCompetitorData(products) {
  return CATEGORIES.map(cat => {
    const yourPrice = randFloat(999, 13999, 0);
    const competitorPrice = randFloat(899, 14499, 0);
    const linked = products.find(p => p.category === cat) || pick(products);
    return {
      id: uid("CMP"),
      category: cat,
      linkedProductId: linked.id,
      linkedProductName: linked.name,
      yourPrice,
      competitor: pick(COMPETITORS),
      competitorPrice,
      marketRank: rand(1, 5),
      trend: pick(["up", "down", "flat"]),
      alertThreshold: 10,
      history: Array.from({ length: 6 }, (_, i) => ({
        week: "Wk " + (i + 1),
        yourPrice: Number((yourPrice * randFloat(0.94, 1.06, 3)).toFixed(2)),
        competitorPrice: Number((competitorPrice * randFloat(0.9, 1.1, 3)).toFixed(2))
      }))
    };
  });
}

let AUTOMATIONS = [
  { id: uid("AUTO"), name: "Low stock alert → Slack #ops", trigger: "Inventory below threshold", condition: "Any category", action: "Notify Ops Manager (in-app + email)", icon: "📦", active: true, runs: 342, log: [] },
  { id: uid("AUTO"), name: "New order → Customer SMS confirmation", trigger: "Order created", condition: "All marketplaces", action: "Send SMS confirmation to customer", icon: "🧾", active: true, runs: 5121, log: [] },
  { id: uid("AUTO"), name: "Competitor price drop → Email alert", trigger: "Price change detected", condition: "Gap exceeds alert threshold", action: "Email Ops Manager", icon: "📉", active: true, runs: 128, log: [] },
  { id: uid("AUTO"), name: "Abandoned cart → WhatsApp follow-up (2h)", trigger: "Cart inactive 2h", condition: "Customer opted in to WhatsApp", action: "Send WhatsApp reminder + add to Retention segment", icon: "🛒", active: false, runs: 980, log: [] },
  { id: uid("AUTO"), name: "Delayed shipment → Support ticket", trigger: "Shipping SLA breached", condition: "Status = Shipped for 5+ days", action: "Create Support ticket", icon: "🚚", active: true, runs: 47, log: [] },
  { id: uid("AUTO"), name: "VIP customer → Priority support tag", trigger: "Lifetime spend > ₹1,20,000", condition: "Segment = VIP", action: "Tag customer as Priority Support", icon: "⭐", active: true, runs: 63, log: [] },
  { id: uid("AUTO"), name: "Order delivered → Retention journey enroll", trigger: "Order delivered", condition: "Customer opted in to Email or WhatsApp", action: "Enroll in \"Post-Purchase Thank You\" journey", icon: "🎯", active: true, runs: 214, log: [] }
];
AUTOMATIONS.forEach(a => {
  for (let i = 0; i < 4; i++) a.log.push({ time: daysAgo(rand(0, 14)), result: pick(["Success", "Success", "Success", "Skipped — condition not met"]) });
  a.log.sort((x, y) => y.time - x.time);
});

const MARKETPLACE_DETAILS = [
  { name: "Shopify", authType: "OAuth", color: "#95BF47", status: "Connected", products: 412, syncProd: 100, syncInv: 100, syncOrd: 100, connectedOn: daysAgo(210), lastSync: daysAgo(0), log: [] },
  { name: "Amazon", authType: "API Key", color: "#FF9900", status: "Connected", products: 388, syncProd: 98, syncInv: 96, syncOrd: 100, connectedOn: daysAgo(180), lastSync: daysAgo(0), log: [] },
  { name: "Flipkart", authType: "API Key", color: "#2874F0", status: "Connected", products: 301, syncProd: 94, syncInv: 89, syncOrd: 97, connectedOn: daysAgo(140), lastSync: daysAgo(0), log: [] },
  { name: "WooCommerce", authType: "API Key", color: "#7F54B3", status: "Connected", products: 275, syncProd: 100, syncInv: 100, syncOrd: 100, connectedOn: daysAgo(95), lastSync: daysAgo(0), log: [] },
  { name: "Magento", authType: "API Key", color: "#EE672F", status: "Syncing", products: 190, syncProd: 72, syncInv: 68, syncOrd: 81, connectedOn: daysAgo(30), lastSync: daysAgo(0), log: [] },
  { name: "Meesho", authType: "API Key", color: "#9F2089", status: "Connected", products: 210, syncProd: 91, syncInv: 85, syncOrd: 93, connectedOn: daysAgo(60), lastSync: daysAgo(0), log: [] },
  { name: "Etsy", authType: "OAuth", color: "#F1641E", status: "Action Needed", products: 96, syncProd: 40, syncInv: 35, syncOrd: 52, connectedOn: daysAgo(300), lastSync: daysAgo(4), log: [] }
];
MARKETPLACE_DETAILS.forEach(m => {
  for (let i = 0; i < 3; i++) m.log.push({ time: daysAgo(rand(0, 6)), event: pick(["Full sync completed", "Order sync completed", "Inventory sync completed", "Product sync failed — rate limited, retried"]) });
});
const UNCONNECTED_MARKETPLACES = []; // all 7 already connected in this demo workspace

let PRODUCTS = generateProducts(26);
let CUSTOMERS = generateCustomers(18);
let ORDERS = generateOrders(28, PRODUCTS, CUSTOMERS);
let RETURNS = seedReturns(ORDERS);
let COMPETITOR_DATA = generateCompetitorData(PRODUCTS);

const REVENUE_TREND = Array.from({ length: 12 }, (_, i) => ({
  label: ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"][i],
  value: rand(2800000, 7600000)
}));
const SALES_TREND_WEEK = Array.from({ length: 7 }, (_, i) => ({
  label: ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"][i],
  value: rand(120, 640)
}));

const NOTIFICATIONS = [
  { icon: "📦", title: "Low stock: Nord Oversized Hoodie", time: "6 minutes ago", nav: "inventory" },
  { icon: "🛍️", title: "New order ORD-78012 from Amazon", time: "22 minutes ago", nav: "orders" },
  { icon: "📉", title: "Competitor UrbanCrate dropped price on Footwear", time: "1 hour ago", nav: "competitor" },
  { icon: "✅", title: "Etsy sync requires re-authentication", time: "3 hours ago", nav: "marketplace" },
  { icon: "⭐", title: "Priya Patel just became a VIP customer", time: "5 hours ago", nav: "crm" }
];

/* ---------------------------------------------------------------------------
   2b. RETENTION MARKETING DATA — Segments, Templates, Campaigns, Bulk Sends
--------------------------------------------------------------------------- */
function computeSegmentMembers(ruleKey) {
  switch (ruleKey) {
    case "cart_abandoned": return CUSTOMERS.filter(c => c.cartAbandoned);
    case "vip": return CUSTOMERS.filter(c => c.segment === "VIP");
    case "loyal": return CUSTOMERS.filter(c => c.segment === "Loyal");
    case "lapsed": return CUSTOMERS.filter(c => (new Date() - c.lastOrder) / 86400000 > 90);
    case "recent_buyer": return CUSTOMERS.filter(c => (new Date() - c.lastOrder) / 86400000 <= 14);
    case "new": return CUSTOMERS.filter(c => c.segment === "New");
    default: return CUSTOMERS.slice();
  }
}

let SEGMENTS = [
  { id: uid("SEG"), name: "Cart Abandoned – 24h", rule: "cart_abandoned", desc: "Added to cart but did not complete checkout within 24 hours." },
  { id: uid("SEG"), name: "VIP – Top Spenders", rule: "vip", desc: "Lifetime spend above ₹1,20,000." },
  { id: uid("SEG"), name: "Loyal Customers", rule: "loyal", desc: "Lifetime spend between ₹55,000–₹1,20,000." },
  { id: uid("SEG"), name: "Lapsed – 90+ Days Inactive", rule: "lapsed", desc: "No order placed in the last 90 days." },
  { id: uid("SEG"), name: "Recent Buyers – Last 14 Days", rule: "recent_buyer", desc: "Placed an order within the last 14 days." },
  { id: uid("SEG"), name: "New Customers", rule: "new", desc: "Placed their first order and no repeat purchase yet." }
];
function segmentSize(seg) { return computeSegmentMembers(seg.rule).length; }

let TEMPLATES = [
  { id: uid("TPL"), name: "Order Delivered — Thank You", channel: "WhatsApp", content: "Hi {customer_name}, your order for {product_name} has been delivered! Thanks for shopping with us. 🎉", status: "Approved", createdOn: daysAgo(40) },
  { id: uid("TPL"), name: "Reorder Reminder — 10% Off", channel: "Email", content: "Hi {customer_name}, it's been a while! Use code {discount_code} for 10% off your next order.", status: "Approved", createdOn: daysAgo(35) },
  { id: uid("TPL"), name: "Cart Abandoned — Come Back", channel: "WhatsApp", content: "Hey {customer_name}, you left {product_name} in your cart. Complete your order before it's gone!", status: "Approved", createdOn: daysAgo(28) },
  { id: uid("TPL"), name: "VIP Early Access", channel: "Email", content: "Hi {customer_name}, as one of our VIP customers you get early access to our new collection.", status: "Pending Approval", createdOn: daysAgo(2) },
  { id: uid("TPL"), name: "Win-Back Final Nudge", channel: "WhatsApp", content: "Last chance, {customer_name}! Here's {discount_code} for 15% off — valid for 48 hours.", status: "Draft", createdOn: daysAgo(0) }
];

let CAMPAIGNS = [
  {
    id: uid("CMPN"), name: "Post-Purchase Thank You", segmentId: SEGMENTS[4].id, goal: "Loyalty Reward",
    steps: [
      { channel: "WhatsApp", templateId: TEMPLATES[0].id, delayDays: 0 },
      { channel: "Email", templateId: TEMPLATES[1].id, delayDays: 5 }
    ],
    status: "Active", createdOn: daysAgo(30),
    stats: { enrolled: 214, sent: 402, delivered: 388, opened: 261, clicked: 97, converted: 34, revenue: 494700 }
  },
  {
    id: uid("CMPN"), name: "Cart Abandonment Win-Back", segmentId: SEGMENTS[0].id, goal: "Win-Back",
    steps: [
      { channel: "WhatsApp", templateId: TEMPLATES[2].id, delayDays: 0 },
      { channel: "WhatsApp", templateId: TEMPLATES[4].id, delayDays: 3 }
    ],
    status: "Active", createdOn: daysAgo(18),
    stats: { enrolled: 63, sent: 108, delivered: 101, opened: 74, clicked: 41, converted: 19, revenue: 181900 }
  },
  {
    id: uid("CMPN"), name: "VIP Loyalty Perks", segmentId: SEGMENTS[1].id, goal: "Loyalty Reward",
    steps: [ { channel: "Email", templateId: TEMPLATES[3].id, delayDays: 0 } ],
    status: "Draft", createdOn: daysAgo(1),
    stats: { enrolled: 0, sent: 0, delivered: 0, opened: 0, clicked: 0, converted: 0, revenue: 0 }
  }
];

let BULK_SENDS = [
  { id: uid("BLK"), date: daysAgo(6), channel: "WhatsApp + Email", content: "Monsoon Sale — Flat 20% off catalog", listSize: 1240, sent: 1240, delivered: 1198, failed: 42, optedOut: 8 },
  { id: uid("BLK"), date: daysAgo(19), channel: "Email", content: "New Arrivals — Autumn Collection catalog link", listSize: 860, sent: 860, delivered: 843, failed: 17, optedOut: 3 }
];

/* ---------------------------------------------------------------------------
   3. NAV CONFIG
--------------------------------------------------------------------------- */

/* ---- moved here from page-specific chunks in the original SPA, since they
   are referenced by more than one page in the multi-page split ---- */
const TRIGGER_OPTIONS = ["Stock below threshold", "Order created", "Order delivered", "Cart inactive 2h", "Shipping SLA breached", "Price change detected", "Customer lifetime spend crosses threshold"];
const ACTION_OPTIONS = ["Notify Ops Manager (in-app + email)", "Send SMS confirmation to customer", "Send WhatsApp reminder to customer", "Email Ops Manager", "Create Support ticket", "Tag customer as Priority Support", "Enroll customer in a Retention journey", "Add product to reorder list"];
const RETENTION_GOALS = ["Win-Back", "Cross-Sell", "Loyalty Reward", "Replenishment Reminder"];
let TEMPLATE_SENDS = [];
const ROLE_OPTIONS = ["Business Owner / Admin", "Operations Manager", "Marketing Manager", "Support / CRM Agent", "Warehouse Staff", "Analyst / Viewer"];
let TEAM = [
  { id: uid("USR"), name: "Alex Nair", email: "alex.nair@rastudio.io", role: "Business Owner / Admin", status: "Active", lastLogin: new Date() },
  { id: uid("USR"), name: "Priya Menon", email: "priya.menon@rastudio.io", role: "Operations Manager", status: "Active", lastLogin: new Date(Date.now() - 3600e3) },
  { id: uid("USR"), name: "Diego Alvarez", email: "diego.a@rastudio.io", role: "Marketing Manager", status: "Active", lastLogin: new Date(Date.now() - 7200e3) },
  { id: uid("USR"), name: "Sara Kim", email: "sara.kim@rastudio.io", role: "Support / CRM Agent", status: "Active", lastLogin: new Date(Date.now() - 86400e3) },
  { id: uid("USR"), name: "Omar Hassan", email: "omar.h@rastudio.io", role: "Warehouse Staff", status: "Invited", lastLogin: null },
  { id: uid("USR"), name: "Nina Fischer", email: "nina.f@rastudio.io", role: "Analyst / Viewer", status: "Active", lastLogin: new Date(Date.now() - 172800e3) }
];
let SYSTEM_CONFIG = { syncInterval: "Every 15 minutes", currency: "INR (₹)", twoFactor: true, emailDigest: true };

/* Fixed (non-random) ids so cross-page links like trends.html?id=INS-2
   always resolve, regardless of which page generated the link. */
let BI_INSIGHTS = [
  { id: "INS-1", icon: "📈", title: "Sales increased 18% this month", body: "Growth is concentrated in the Footwear and Electronics Accessories categories, driven primarily by Amazon and Shopify channels.", detail: "Week-over-week growth accelerated after the last automation rollout. Footwear alone contributed 6.4 points of the 18% lift, with Electronics Accessories contributing 4.1 points. Amazon and Shopify represent 71% of the incremental revenue." },
  { id: "INS-2", icon: "📦", title: `${pick(PRODUCTS).name} may require restocking`, body: "Projected to stock out within 9 days at current sell-through rate. Consider raising the reorder threshold.", detail: "Based on the last 14 days of sell-through velocity, current stock will be exhausted in approximately 9 days. Raising the reorder threshold or placing a purchase order this week would avoid a stockout." },
  { id: "INS-3", icon: "🏆", title: "Amazon channel is generating highest revenue", body: "Amazon contributes 34% of total revenue with the highest average order value across all connected marketplaces.", detail: "Amazon's average order value is running 22% above the platform average, largely driven by bundled Apparel and Footwear purchases." },
  { id: "INS-4", icon: "🎯", title: "Competitor price gap widening in Beauty", body: "UrbanCrate has undercut your pricing by an average of 12% across Beauty SKUs over the last 2 weeks.", detail: "The gap has widened from 6% to 12% over two weeks. Consider using Competitor Analysis → Reprice on the affected Beauty SKUs, or bundling to offset the margin impact." },
  { id: "INS-5", icon: "👥", title: "VIP segment spend up 22% quarter-over-quarter", body: "Your top 8% of customers now account for 41% of total revenue — consider a loyalty tier expansion.", detail: "The VIP segment (see CRM → Segments) grew from 32% to 41% of total revenue this quarter. A Retention Marketing loyalty campaign targeting this segment could compound the trend." }
];
let BI_RECOMMENDATIONS = [
  { id: "REC-1", color: "#7C4FEE", text: "Increase ad spend allocation to Amazon by 15% based on ROAS trends." },
  { id: "REC-2", color: "#0E8F5F", text: "Bundle slow-moving Kitchenware SKUs with top sellers to improve turnover." },
  { id: "REC-3", color: "#B7791F", text: "Re-negotiate Etsy listing fees — sync health has dropped below threshold." },
  { id: "REC-4", color: "#D6483D", text: "Launch a win-back campaign for customers inactive over 90 days." }
];

/* ============================================================================
   SESSION PERSISTENCE LAYER
   Why this exists: in the original SPA, PRODUCTS/CUSTOMERS/ORDERS/etc. were
   generated once and stayed in memory for the whole session because the
   router only swapped visible sections — it never reloaded the page. In a
   real multi-page site, every navigation reloads this script, which would
   silently regenerate a brand-new random dataset each time. That would mean
   the SAME id (e.g. PRD-1042) could show different name/price/stock info
   depending which page you're on, and any edit you make would vanish the
   moment you navigate away.
   Fix: generate the dataset once per browser tab, save it to sessionStorage,
   and have every subsequent page load in that tab rehydrate from storage
   instead of regenerating. Every mutation made anywhere in the app is
   captured automatically on the way out via beforeunload/pagehide, with no
   need to sprinkle save calls through every button handler.
   This is a stand-in for a real backend, per the brief — once Spring Boot
   + MySQL exist, this whole block is deleted and js/api/api.js starts
   hitting real endpoints instead.
   ============================================================================ */
const RA_STATE_KEY = "ra_demo_state_v1";

function raDateReviver(key, value) {
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/.test(value)) return new Date(value);
  return value;
}

function persistState() {
  try {
    sessionStorage.setItem(RA_STATE_KEY, JSON.stringify({
      PRODUCTS, CUSTOMERS, ORDERS, RETURNS, COMPETITOR_DATA, MARKETPLACE_DETAILS,
      SEGMENTS, TEMPLATES, CAMPAIGNS, BULK_SENDS, AUTOMATIONS, TEMPLATE_SENDS,
      TEAM, SYSTEM_CONFIG, RETURN_SEQ, BI_INSIGHTS, BI_RECOMMENDATIONS
    }));
  } catch (e) { /* storage full or unavailable — demo continues without persistence */ }
}

(function hydrateFromSession() {
  const raw = sessionStorage.getItem(RA_STATE_KEY);
  if (!raw) { persistState(); return; }
  try {
    const saved = JSON.parse(raw, raDateReviver);
    PRODUCTS = saved.PRODUCTS; CUSTOMERS = saved.CUSTOMERS; ORDERS = saved.ORDERS; RETURNS = saved.RETURNS;
    COMPETITOR_DATA = saved.COMPETITOR_DATA; MARKETPLACE_DETAILS = saved.MARKETPLACE_DETAILS;
    SEGMENTS = saved.SEGMENTS; TEMPLATES = saved.TEMPLATES; CAMPAIGNS = saved.CAMPAIGNS;
    BULK_SENDS = saved.BULK_SENDS; AUTOMATIONS = saved.AUTOMATIONS; TEMPLATE_SENDS = saved.TEMPLATE_SENDS;
    TEAM = saved.TEAM; SYSTEM_CONFIG = saved.SYSTEM_CONFIG; RETURN_SEQ = saved.RETURN_SEQ || 1;
    if (saved.BI_INSIGHTS) BI_INSIGHTS = saved.BI_INSIGHTS;
    if (saved.BI_RECOMMENDATIONS) BI_RECOMMENDATIONS = saved.BI_RECOMMENDATIONS;
  } catch (e) { persistState(); }
})();

window.addEventListener("beforeunload", persistState);
window.addEventListener("pagehide", persistState);
