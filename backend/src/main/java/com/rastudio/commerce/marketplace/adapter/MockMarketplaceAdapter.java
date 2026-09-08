package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceConnection; import com.rastudio.commerce.marketplace.MarketplaceName;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated sandbox adapter used for every marketplace until a real, credential-backed implementation is wired in
 * (roadmap Phase 5: "Use MOCK/SANDBOX adapters where real credentials are unavailable" — no marketplace API
 * credentials are configured anywhere in this project, so every marketplace runs in mock mode). It never makes a
 * network call and never claims a real store is connected; MarketplaceConnection.isMock is always true for
 * connections created through this adapter. A future real adapter (e.g. calling the real Shopify Admin API) just
 * needs to implement MarketplaceAdapter directly and be registered as a Spring bean for that MarketplaceName —
 * MarketplaceService resolves adapters by marketplace() alone.
 */
public abstract class MockMarketplaceAdapter implements MarketplaceAdapter {
  private final MarketplaceName marketplace;
  protected MockMarketplaceAdapter(MarketplaceName marketplace) { this.marketplace = marketplace; }
  @Override public MarketplaceName marketplace() { return marketplace; }

  @Override public ConnectionTestResult testConnection(MarketplaceConnection connection, DecryptedCredentials credentials) {
    if (credentials == null || credentials.isBlank()) {
      return new ConnectionTestResult(false, "No credentials on file for this connection — authorize the store again before testing.");
    }
    return new ConnectionTestResult(true, marketplace.name() + " sandbox: credentials accepted, store reachable (mock adapter — no live API call was made).");
  }

  @Override public SyncResult sync(MarketplaceConnection connection, DecryptedCredentials credentials) {
    var r = ThreadLocalRandom.current();
    return new SyncResult(true, marketplace.name() + " sandbox sync completed (mock adapter — no live API call was made).",
        r.nextInt(70, 101), r.nextInt(70, 101), r.nextInt(80, 101));
  }
}