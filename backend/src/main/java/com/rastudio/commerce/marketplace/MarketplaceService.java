package com.rastudio.commerce.marketplace;
import com.rastudio.commerce.audit.AuditLogService;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.notification.NotificationService;
import com.rastudio.commerce.marketplace.adapter.*;
import com.rastudio.commerce.security.TenantPrincipal;
import jakarta.persistence.EntityManager; import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime; import java.util.*; import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5 — Marketplace Integration Foundation.
 * Every write here is Ops-Manager/Admin (Disconnect is Admin-only) and tenant-scoped to the caller's
 * organizationId, matching the pattern established in OrganizationController/UserService. Connect and
 * Disconnect — the two durable, account-level state changes — are audit-logged the same way Phase 3 logs
 * INVITE_USER/UPDATE_SYSTEM_CONFIG/etc.; Test Connection and Sync Now are routine/operational and are recorded
 * in marketplace_sync_log instead, which is this module's own equivalent of the audit trail.
 */
@Service
public class MarketplaceService {
  private static final Set<MarketplaceName> OAUTH_MARKETPLACES = Set.of(MarketplaceName.SHOPIFY, MarketplaceName.ETSY);

  private final MarketplaceConnectionRepository connections;
  private final MarketplaceSyncLogRepository syncLogs;
  private final CredentialCipherService cipher;
  private final Map<MarketplaceName, MarketplaceAdapter> adapters;
  private final AuditLogService audit;
  private final NotificationService notifications;

  @PersistenceContext private EntityManager entityManager;

  public MarketplaceService(MarketplaceConnectionRepository connections, MarketplaceSyncLogRepository syncLogs,
      CredentialCipherService cipher, List<MarketplaceAdapter> adapterBeans, AuditLogService audit,
      NotificationService notifications) {
    this.connections = connections;
    this.syncLogs = syncLogs;
    this.cipher = cipher;
    this.adapters = adapterBeans.stream().collect(Collectors.toMap(MarketplaceAdapter::marketplace, a -> a));
    this.audit = audit;
    this.notifications = notifications;
  }

  /** GET /api/marketplaces — one row per supported marketplace, synthesizing NOT_CONNECTED for those the org hasn't authorized yet. */
  public List<MarketplaceConnectionDto> list(Long organizationId) {
    var existing = connections.findByOrganizationId(organizationId).stream()
        .collect(Collectors.toMap(c -> c.marketplaceName, c -> c));
    var counts = publishedProductCounts(organizationId);
    return Arrays.stream(MarketplaceName.values())
        .map(name -> toDto(existing.get(name), name, counts.getOrDefault(name, 0)))
        .toList();
  }

  public MarketplaceConnectionDto get(Long organizationId, MarketplaceName name) {
    var conn = connections.findByOrganizationIdAndMarketplaceName(organizationId, name).orElse(null);
    return toDto(conn, name, publishedProductCount(organizationId, name));
  }

  /** POST /api/marketplaces/{name}/authorize — Authorize/Connect button. Stores encrypted credentials; the
   *  connection remains ACTION_NEEDED (pending verification) until Test Connection succeeds. */
  @Transactional
  public MarketplaceConnectionDto authorize(TenantPrincipal actor, MarketplaceName name, AuthorizeRequest request, HttpServletRequest http) {
    Long organizationId = actor.organizationId();
    var authType = OAUTH_MARKETPLACES.contains(name) ? MarketplaceAuthType.OAUTH : MarketplaceAuthType.API_KEY;
    String secretPayload;
    if (authType == MarketplaceAuthType.API_KEY) {
      if (request == null || isBlank(request.apiKey()) || isBlank(request.apiSecret())) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", name + " requires both an API key and an API secret");
      }
      secretPayload = request.apiKey().trim() + "::" + request.apiSecret().trim();
    } else {
      // Real OAuth marketplaces (Shopify, Etsy) exchange an authorization code from the consent-screen redirect
      // for an access token. No OAuth client is configured for this workspace, so a sandbox token is minted here —
      // is_mock stays true and the adapter never makes a live call with it.
      secretPayload = "mock-oauth-token-" + UUID.randomUUID();
    }

    boolean isNewConnection = connections.findByOrganizationIdAndMarketplaceName(organizationId, name).isEmpty();
    var conn = connections.findByOrganizationIdAndMarketplaceName(organizationId, name).orElseGet(() -> {
      var c = new MarketplaceConnection();
      c.organizationId = organizationId;
      c.marketplaceName = name;
      return c;
    });
    String previousStatus = conn.status == null ? "NOT_CONNECTED" : conn.status.name();
    conn.authType = authType;
    conn.credentialRef = cipher.encrypt(secretPayload);
    conn.isMock = true;
    conn.status = MarketplaceConnectionStatus.ACTION_NEEDED;
    conn = connections.save(conn);
    addLog(conn, authType == MarketplaceAuthType.OAUTH
        ? "OAuth authorization granted — pending connection test"
        : "API credentials saved — pending connection test", false);
    audit.record(organizationId, actor.userId(), isNewConnection ? "CONNECT_MARKETPLACE" : "RECONNECT_MARKETPLACE",
        "marketplace_connection", conn.id, previousStatus, conn.status.name(), http);
    return toDto(conn, name, publishedProductCount(organizationId, name));
  }

  /** POST /api/marketplaces/{name}/test-connection — Test Connection button. On success, also runs the sync
   *  the workflow doc describes as following a successful test (initial sync on first connect, otherwise a
   *  regular re-sync), matching "On success -> triggers Initial Sync". */
  @Transactional
  public MarketplaceConnectionDto testConnection(Long organizationId, MarketplaceName name) {
    var conn = requireConnection(organizationId, name);
    var adapter = adapterFor(name);
    var credentials = decrypt(conn);
    var result = adapter.testConnection(conn, credentials);
    if (!result.success()) {
      conn.status = MarketplaceConnectionStatus.ACTION_NEEDED;
      connections.save(conn);
      addLog(conn, "Connection test failed: " + result.message(), true);
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONNECTION_TEST_FAILED", result.message());
    }
    addLog(conn, "Connection test succeeded: " + result.message(), false);

    boolean firstConnect = conn.connectedAt == null;
    conn.status = MarketplaceConnectionStatus.SYNCING;
    connections.save(conn);
    var syncResult = adapter.sync(conn, credentials);
    applySyncResult(conn, syncResult, firstConnect
        ? "Initial full sync completed (products, inventory, orders)"
        : "Sync completed after reconnection");
    return toDto(conn, name, publishedProductCount(organizationId, name));
  }

  /** POST /api/marketplaces/{name}/sync — Sync Now button (on-demand, outside the scheduled/webhook cycle). */
  @Transactional
  public MarketplaceConnectionDto syncNow(Long organizationId, MarketplaceName name) {
    var conn = requireConnection(organizationId, name);
    if (conn.status == MarketplaceConnectionStatus.NOT_CONNECTED || conn.status == MarketplaceConnectionStatus.DISCONNECTED) {
      throw new ApiException(HttpStatus.CONFLICT, "NOT_CONNECTED", "Connect this marketplace before syncing");
    }
    var adapter = adapterFor(name);
    var credentials = decrypt(conn);
    var result = adapter.sync(conn, credentials);
    applySyncResult(conn, result, "On-demand sync completed (products, inventory, orders)");
    return toDto(conn, name, publishedProductCount(organizationId, name));
  }

  /** DELETE /api/marketplaces/{name} — Disconnect Store button (Admin only). Revokes stored credentials and
   *  stops future syncing; previously synced product/inventory/order records (owned by other phases' tables)
   *  are untouched. */
  @Transactional
  public MarketplaceConnectionDto disconnect(TenantPrincipal actor, MarketplaceName name, HttpServletRequest http) {
    Long organizationId = actor.organizationId();
    var conn = requireConnection(organizationId, name);
    String previousStatus = conn.status.name();
    conn.status = MarketplaceConnectionStatus.DISCONNECTED;
    conn.credentialRef = null;
    conn.productSyncPct = 0;
    conn.inventorySyncPct = 0;
    conn.orderSyncPct = 0;
    connections.save(conn);
    addLog(conn, "Store disconnected — credentials revoked", false);
    audit.record(organizationId, actor.userId(), "DISCONNECT_MARKETPLACE", "marketplace_connection", conn.id,
        previousStatus, conn.status.name(), http);
    return toDto(conn, name, publishedProductCount(organizationId, name));
  }

  /** GET /api/marketplaces/{name}/logs — View Sync Log / Error Log button. */
  public List<SyncLogDto> logs(Long organizationId, MarketplaceName name, int limit) {
    var conn = requireConnection(organizationId, name);
    return syncLogs.findByMarketplaceConnectionIdOrderByCreatedAtDesc(conn.id, PageRequest.of(0, limit)).stream()
        .map(l -> new SyncLogDto(l.id, l.event, l.isError, l.createdAt))
        .toList();
  }

  // ---------------------------------------------------------------------

  private void applySyncResult(MarketplaceConnection conn, SyncResult result, String successEvent) {
    if (!result.success()) {
      conn.status = MarketplaceConnectionStatus.ACTION_NEEDED;
      connections.save(conn);
      addLog(conn, "Sync failed: " + result.message(), true);
      // Roadmap Phase 14 SYNC_ERROR notification category.
      notifications.create(conn.organizationId, conn.marketplaceName + " sync failed: " + result.message(),
          "SYNC_ERROR", "marketplace", conn.id);
      throw new ApiException(HttpStatus.BAD_GATEWAY, "SYNC_FAILED", result.message());
    }
    conn.productSyncPct = result.productSyncPct();
    conn.inventorySyncPct = result.inventorySyncPct();
    conn.orderSyncPct = result.orderSyncPct();
    conn.lastSyncAt = LocalDateTime.now();
    if (conn.connectedAt == null) conn.connectedAt = LocalDateTime.now();
    conn.status = MarketplaceConnectionStatus.CONNECTED;
    connections.save(conn);
    addLog(conn, successEvent, false);
  }

  private void addLog(MarketplaceConnection conn, String event, boolean isError) {
    var l = new MarketplaceSyncLog();
    l.marketplaceConnectionId = conn.id;
    l.event = event;
    l.isError = isError;
    syncLogs.save(l);
  }

  private MarketplaceConnection requireConnection(Long organizationId, MarketplaceName name) {
    return connections.findByOrganizationIdAndMarketplaceName(organizationId, name)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MARKETPLACE_NOT_CONNECTED", name + " is not connected yet"));
  }

  private MarketplaceAdapter adapterFor(MarketplaceName name) {
    var adapter = adapters.get(name);
    if (adapter == null) {
      throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "ADAPTER_NOT_CONFIGURED", "No adapter is configured for " + name);
    }
    return adapter;
  }

  private DecryptedCredentials decrypt(MarketplaceConnection conn) {
    if (conn.credentialRef == null) return new DecryptedCredentials(null, null, null);
    String payload = cipher.decrypt(conn.credentialRef);
    if (conn.authType == MarketplaceAuthType.OAUTH) return new DecryptedCredentials(null, null, payload);
    String[] parts = payload.split("::", 2);
    return new DecryptedCredentials(parts[0], parts.length > 1 ? parts[1] : null, null);
  }

  private MarketplaceConnectionDto toDto(MarketplaceConnection conn, MarketplaceName name, int publishedProducts) {
    boolean oauth = OAUTH_MARKETPLACES.contains(name);
    if (conn == null) {
      return new MarketplaceConnectionDto(name.name(), oauth ? "OAUTH" : "API_KEY", MarketplaceConnectionStatus.NOT_CONNECTED.name(),
          true, 0, 0, 0, null, null, publishedProducts);
    }
    return new MarketplaceConnectionDto(name.name(), conn.authType.name(), conn.status.name(), conn.isMock,
        conn.productSyncPct, conn.inventorySyncPct, conn.orderSyncPct, conn.lastSyncAt, conn.connectedAt, publishedProducts);
  }

  /** Published-listing counts per marketplace, sourced from product_marketplace_listing (owned by the Product
   *  Management module). Reads only; always 0 until products exist and are published, which is correct and not
   *  fabricated. Uses a native query instead of a Product entity since this module doesn't own that table. */
  private Map<MarketplaceName, Integer> publishedProductCounts(Long organizationId) {
    List<Object[]> rows = entityManager.createNativeQuery(
        "SELECT pml.marketplace_name, COUNT(*) FROM product_marketplace_listing pml " +
        "JOIN product p ON p.id = pml.product_id " +
        "WHERE p.organization_id = :orgId AND pml.status = 'PUBLISHED' " +
        "GROUP BY pml.marketplace_name")
        .setParameter("orgId", organizationId)
        .getResultList();
    Map<MarketplaceName, Integer> result = new EnumMap<>(MarketplaceName.class);
    for (Object[] row : rows) result.put(MarketplaceName.valueOf((String) row[0]), ((Number) row[1]).intValue());
    return result;
  }

  private int publishedProductCount(Long organizationId, MarketplaceName name) {
    return publishedProductCounts(organizationId).getOrDefault(name, 0);
  }

  private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}