package com.rastudio.commerce.competitor;

import com.rastudio.commerce.audit.AuditLogService;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.competitor.CompetitorDtos.*;
import com.rastudio.commerce.competitor.provider.CheckedPrice;
import com.rastudio.commerce.competitor.provider.CompetitorPriceProvider;
import com.rastudio.commerce.product.CategoryRepository;
import com.rastudio.commerce.product.Product;
import com.rastudio.commerce.product.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 12 — Competitor Analysis. Flow matches FULL_WORKFLOW.md Module 10: Add Competitor Product -> Scheduled
 * Check -> Price/Stock Comparison -> Alert on Change -> Review & Reprice. No competitor-site credentials or
 * scraping approval exist anywhere in this project (same position Phase 5 takes on marketplace APIs), so price
 * checks run through CompetitorPriceProvider/MockCompetitorPriceProvider — a real provider can be registered
 * later without this service changing at all.
 */
@Service
public class CompetitorService {

  private static final Logger log = LoggerFactory.getLogger(CompetitorService.class);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final CompetitorTrackedProductRepository tracked;
  private final CompetitorPriceHistoryRepository history;
  private final ProductRepository products;
  private final CategoryRepository categories;
  private final CompetitorPriceProvider priceProvider;
  private final AuditLogService audit;

  public CompetitorService(CompetitorTrackedProductRepository tracked, CompetitorPriceHistoryRepository history,
      ProductRepository products, CategoryRepository categories, CompetitorPriceProvider priceProvider,
      AuditLogService audit) {
    this.tracked = tracked;
    this.history = history;
    this.products = products;
    this.categories = categories;
    this.priceProvider = priceProvider;
    this.audit = audit;
  }

  /* -------------------------------- Add Competitor Product / Link to Own Product -------------------------------- */

  @Transactional
  public CompetitorSummaryDto addCompetitorProduct(Long organizationId, AddCompetitorProductRequest request) {
    if (request == null || request.linkedProductId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "linkedProductId is required");
    }
    if (request.competitorName() == null || request.competitorName().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "competitorName is required");
    }
    if (request.competitorPrice() == null || request.competitorPrice().signum() < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "competitorPrice is required");
    }
    Product product = products.findByIdAndOrganizationId(request.linkedProductId(), organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found"));

    CompetitorTrackedProduct t = new CompetitorTrackedProduct();
    t.organizationId = organizationId;
    t.linkedProductId = product.id;
    t.competitorName = request.competitorName().trim();
    t.competitorListingRef = request.competitorListingRef() == null ? null : request.competitorListingRef().trim();
    t.yourPrice = product.basePrice;
    t.competitorPrice = request.competitorPrice();
    t.alertThresholdPct = 10;
    tracked.save(t);

    writeHistoryPoint(t);
    return toSummary(t, product, null);
  }

  /* -------------------------------------------------- listing -------------------------------------------------- */

  public List<CompetitorSummaryDto> list(Long organizationId) {
    return tracked.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(t -> toSummary(t, products.findByIdAndOrganizationId(t.linkedProductId, organizationId).orElse(null),
            lastTwo(t.id)))
        .toList();
  }

  public CompetitorSummaryDto get(Long organizationId, Long id) {
    CompetitorTrackedProduct t = requireTracked(organizationId, id);
    return toSummary(t, products.findByIdAndOrganizationId(t.linkedProductId, organizationId).orElse(null), lastTwo(t.id));
  }

  /* ------------------------------------------- View Price History ------------------------------------------- */

  public List<PriceHistoryPointDto> priceHistory(Long organizationId, Long id) {
    requireTracked(organizationId, id); // 404s + org-scopes before exposing history rows
    return history.findByCompetitorTrackedProductIdOrderByRecordedAtAsc(id).stream()
        .map(h -> new PriceHistoryPointDto(h.yourPrice, h.competitorPrice, h.recordedAt))
        .toList();
  }

  /* -------------------------------------------- Set Alert Threshold -------------------------------------------- */

  @Transactional
  public CompetitorSummaryDto setAlertThreshold(Long organizationId, Long actorUserId, Long id,
      SetThresholdRequest request, HttpServletRequest http) {
    CompetitorTrackedProduct t = requireTracked(organizationId, id);
    if (request == null || request.alertThresholdPct() == null
        || request.alertThresholdPct() < 1 || request.alertThresholdPct() > 100) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "alertThresholdPct must be between 1 and 100");
    }
    int previous = t.alertThresholdPct;
    t.alertThresholdPct = request.alertThresholdPct();
    tracked.save(t);
    audit.record(organizationId, actorUserId, "SET_ALERT_THRESHOLD", "competitor_tracked_product", t.id,
        previous + "%", t.alertThresholdPct + "%", http);
    return toSummary(t, products.findByIdAndOrganizationId(t.linkedProductId, organizationId).orElse(null), lastTwo(t.id));
  }

  /* ------------------------------------------------ Reprice Product ------------------------------------------------ */

  /** Updates the business's own price on both the tracked pairing and the underlying Product — "re-publishes
   *  to marketplaces per Module 3's publish flow" per BUTTON_ACTIONS.md is intentionally not triggered here:
   *  Product Management's own Publish action (Phase 6) already owns pushing a price change out to connected
   *  marketplaces, so re-running that from a second module would duplicate publish logic rather than reuse it
   *  — the same reasoning Phase 9 uses for restocking only through InventoryService. */
  @Transactional
  public CompetitorSummaryDto reprice(Long organizationId, Long actorUserId, Long id, RepriceRequest request,
      HttpServletRequest http) {
    CompetitorTrackedProduct t = requireTracked(organizationId, id);
    if (request == null || request.newPrice() == null || request.newPrice().signum() <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "newPrice must be a positive amount");
    }
    Product product = products.findByIdAndOrganizationId(t.linkedProductId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Linked product not found"));

    BigDecimal previousPrice = product.basePrice;
    product.basePrice = request.newPrice();
    products.save(product);
    t.yourPrice = request.newPrice();
    tracked.save(t);
    writeHistoryPoint(t);

    audit.record(organizationId, actorUserId, "REPRICE_PRODUCT", "product", product.id,
        String.valueOf(previousPrice), String.valueOf(product.basePrice), http);
    return toSummary(t, product, lastTwo(t.id));
  }

  /* ---------------------------------------------- Scheduled Price Checks ---------------------------------------------- */

  /** "Scheduled Check" step of Module 10. Runs across every organization's tracked listings on a fixed
   *  interval (independent of any single HTTP request) and appends one price_history row per listing per run
   *  — the real trend data View Price History reads. Best-effort per listing: one bad row never stops the
   *  rest of the batch from being checked, mirroring AuditLogService's "never block the action it's
   *  describing" stance. */
  @Scheduled(fixedRateString = "${app.competitor.price-check-interval-ms:900000}") // every 15 minutes by default
  @Transactional
  public void scheduledPriceCheck() {
    List<CompetitorTrackedProduct> all = tracked.findAll();
    for (CompetitorTrackedProduct t : all) {
      try {
        CheckedPrice checked = priceProvider.checkPrice(t);
        t.competitorPrice = checked.competitorPrice();
        tracked.save(t);
        writeHistoryPoint(t);
      } catch (Exception e) {
        log.warn("Competitor price check failed for tracked listing {}: {}", t.id, e.getMessage());
      }
    }
  }

  /* ------------------------------------------------------ internals ------------------------------------------------------ */

  private CompetitorTrackedProduct requireTracked(Long organizationId, Long id) {
    return tracked.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPETITOR_PRODUCT_NOT_FOUND", "Tracked competitor product not found"));
  }

  private void writeHistoryPoint(CompetitorTrackedProduct t) {
    CompetitorPriceHistory h = new CompetitorPriceHistory();
    h.competitorTrackedProductId = t.id;
    h.yourPrice = t.yourPrice;
    h.competitorPrice = t.competitorPrice;
    history.save(h);
  }

  /** Last two history points (oldest first) for trend direction; empty/singleton lists mean "flat". */
  private List<CompetitorPriceHistory> lastTwo(Long trackedId) {
    List<CompetitorPriceHistory> all = history.findByCompetitorTrackedProductIdOrderByRecordedAtAsc(trackedId);
    return all.size() <= 2 ? all : all.subList(all.size() - 2, all.size());
  }

  private CompetitorSummaryDto toSummary(CompetitorTrackedProduct t, Product product, List<CompetitorPriceHistory> recentHistory) {
    String productName = product == null ? "Unknown product" : product.name;
    String categoryName = "Uncategorized";
    if (product != null && product.categoryId != null) {
      categoryName = categories.findById(product.categoryId).map(c -> c.name).orElse(categoryName);
    }

    BigDecimal gapPct = BigDecimal.ZERO;
    if (t.yourPrice != null && t.yourPrice.signum() > 0) {
      gapPct = t.yourPrice.subtract(t.competitorPrice)
          .divide(t.yourPrice, 4, RoundingMode.HALF_UP)
          .multiply(HUNDRED)
          .setScale(1, RoundingMode.HALF_UP);
    }
    boolean alertTriggered = gapPct.compareTo(BigDecimal.valueOf(t.alertThresholdPct)) >= 0;

    String trend = "flat";
    if (recentHistory != null && recentHistory.size() == 2) {
      int cmp = recentHistory.get(1).competitorPrice.compareTo(recentHistory.get(0).competitorPrice);
      trend = cmp > 0 ? "up" : cmp < 0 ? "down" : "flat";
    }

    return new CompetitorSummaryDto(t.id, t.linkedProductId, productName, categoryName, t.competitorName,
        t.competitorListingRef, t.yourPrice, t.competitorPrice, t.alertThresholdPct, gapPct, trend,
        alertTriggered, t.createdAt);
  }
}
