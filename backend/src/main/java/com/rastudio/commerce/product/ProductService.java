package com.rastudio.commerce.product;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.marketplace.MarketplaceConnectionStatus;
import com.rastudio.commerce.marketplace.MarketplaceConnectionRepository;
import com.rastudio.commerce.marketplace.MarketplaceName;
import com.rastudio.commerce.product.ProductDtos.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

  /* Seeded once per organization the first time its category list is read,
     so the catalog has usable categories without a separate setup step.
     These are the same category names the frontend previously hardcoded as
     static mock data (CATEGORIES in js/data/data.js) — seeding them as real
     organization-scoped rows is what replaces that mock array per Rule 4. */
  private static final List<String> DEFAULT_CATEGORIES = List.of(
      "Apparel", "Footwear", "Home & Living", "Electronics Accessories",
      "Beauty", "Sports & Outdoors", "Kitchenware", "Bags & Travel");

  private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
  private static final Set<String> ALLOWED_IMAGE_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  private final CategoryRepository categories;
  private final ProductRepository products;
  private final ProductVariantRepository variants;
  private final ProductMarketplaceListingRepository listings;
  private final MarketplaceConnectionRepository marketplaceConnections;
  private final ProductImageStorage imageStorage;

  public ProductService(CategoryRepository categories, ProductRepository products,
      ProductVariantRepository variants, ProductMarketplaceListingRepository listings,
      MarketplaceConnectionRepository marketplaceConnections, ProductImageStorage imageStorage) {
    this.categories = categories;
    this.products = products;
    this.variants = variants;
    this.listings = listings;
    this.marketplaceConnections = marketplaceConnections;
    this.imageStorage = imageStorage;
  }

  /* ------------------------------ categories ------------------------------ */

  @Transactional
  public List<CategoryDto> listCategories(Long organizationId) {
    if (!categories.existsByOrganizationId(organizationId)) {
      for (String name : DEFAULT_CATEGORIES) {
        Category c = new Category();
        c.organizationId = organizationId;
        c.name = name;
        categories.save(c);
      }
    }
    return categories.findByOrganizationIdOrderByNameAsc(organizationId).stream()
        .map(c -> new CategoryDto(c.id, c.name))
        .toList();
  }

  private Category findOrCreateCategory(Long organizationId, String rawName) {
    String name = rawName == null ? "" : rawName.trim();
    if (name.isEmpty()) return null;
    return categories.findByOrganizationIdAndNameIgnoreCase(organizationId, name)
        .orElseGet(() -> {
          Category c = new Category();
          c.organizationId = organizationId;
          c.name = name;
          return categories.save(c);
        });
  }

  /* ------------------------------- products -------------------------------- */

  @Transactional
  public List<ProductResponse> listProducts(Long organizationId) {
    List<Product> productList = products.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    if (productList.isEmpty()) return List.of();
    List<Long> ids = productList.stream().map(p -> p.id).toList();
    Map<Long, List<ProductVariant>> variantsByProduct = variants.findByProductIdInOrderByIdAsc(ids).stream()
        .collect(Collectors.groupingBy(v -> v.productId));
    Map<Long, List<ProductMarketplaceListing>> listingsByProduct =
        listings.findByProductIdInOrderByMarketplaceNameAsc(ids).stream()
            .collect(Collectors.groupingBy(l -> l.productId));
    Map<Long, Category> categoryById = categories.findByOrganizationIdOrderByNameAsc(organizationId).stream()
        .collect(Collectors.toMap(c -> c.id, c -> c));
    return productList.stream()
        .map(p -> toResponse(p, variantsByProduct.getOrDefault(p.id, List.of()),
            listingsByProduct.getOrDefault(p.id, List.of()), categoryById.get(p.categoryId)))
        .toList();
  }

  @Transactional
  public ProductResponse getProduct(Long organizationId, Long id) {
    Product p = requireProduct(organizationId, id);
    return toResponse(p, variants.findByProductIdOrderByIdAsc(p.id),
        listings.findByProductIdOrderByMarketplaceNameAsc(p.id),
        p.categoryId == null ? null : categories.findById(p.categoryId).orElse(null));
  }

  @Transactional
  public ProductResponse createProduct(Long organizationId, Long userId, ProductRequest request) {
    validateBasics(request);
    Product p = new Product();
    p.organizationId = organizationId;
    p.name = request.name().trim();
    p.description = request.description();
    p.basePrice = request.basePrice() == null ? BigDecimal.ZERO : request.basePrice();
    p.status = ProductStatus.DRAFT;
    p.createdBy = userId;
    Category category = findOrCreateCategory(organizationId, request.categoryName());
    p.categoryId = category == null ? null : category.id;
    products.save(p);

    List<ProductVariant> saved = reconcileVariants(organizationId, p.id, List.of(), request.variants());
    return toResponse(p, saved, List.of(), category);
  }

  @Transactional
  public ProductResponse updateProduct(Long organizationId, Long id, ProductRequest request) {
    validateBasics(request);
    Product p = requireProduct(organizationId, id);
    p.name = request.name().trim();
    p.description = request.description();
    p.basePrice = request.basePrice() == null ? BigDecimal.ZERO : request.basePrice();
    Category category = findOrCreateCategory(organizationId, request.categoryName());
    p.categoryId = category == null ? null : category.id;
    products.save(p);

    List<ProductVariant> existing = variants.findByProductIdOrderByIdAsc(p.id);
    List<ProductVariant> saved = reconcileVariants(organizationId, p.id, existing, request.variants());
    return toResponse(p, saved, listings.findByProductIdOrderByMarketplaceNameAsc(p.id), category);
  }

  @Transactional
  public void deleteProduct(Long organizationId, Long id) {
    Product p = requireProduct(organizationId, id);
    imageStorage.delete(p.imagePath);
    // product_variant and product_marketplace_listing both cascade on delete
    // per schema.sql (fk_variant_product / fk_pml_product ON DELETE CASCADE).
    products.delete(p);
  }

  private void validateBasics(ProductRequest request) {
    if (request == null || request.name() == null || request.name().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Product name is required");
    }
    if (request.basePrice() != null && request.basePrice().compareTo(BigDecimal.ZERO) < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Price cannot be negative");
    }
  }

  private Product requireProduct(Long organizationId, Long id) {
    return products.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found"));
  }

  /* ------------------------------- variants --------------------------------
     The create/edit product form manages the full variant list in one save
     (matching the existing frontend UX — variant rows are added/removed
     client-side, then the whole product is saved at once), so update/create
     reconcile the submitted list against what's stored. Dedicated
     add/delete endpoints are also exposed below for the "Add Variant" /
     variant-row-delete button actions used outside that flow. */

  private List<ProductVariant> reconcileVariants(Long organizationId, Long productId,
      List<ProductVariant> existing, List<VariantInput> requested) {
    if (requested == null) requested = List.of();
    Map<Long, ProductVariant> existingById = existing.stream().collect(Collectors.toMap(v -> v.id, v -> v));
    Set<Long> keepIds = requested.stream().map(VariantInput::id).filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    for (ProductVariant v : existing) {
      if (!keepIds.contains(v.id)) variants.delete(v);
    }
    List<ProductVariant> result = new ArrayList<>();
    for (VariantInput input : requested) {
      String name = input.name() == null ? "" : input.name().trim();
      if (name.isEmpty()) continue;
      ProductVariant v = input.id() != null ? existingById.get(input.id()) : null;
      if (v == null) v = new ProductVariant();
      v.productId = productId;
      v.organizationId = organizationId;
      v.name = name;
      v.sku = normalizeSku(input.sku());
      assertSkuAvailable(organizationId, v.sku, v.id);
      v.priceOverride = input.priceOverride();
      result.add(variants.save(v));
    }
    if (result.isEmpty()) {
      // Every product needs at least one purchasable line — mirrors the
      // frontend's own fallback of creating a "Default" variant when none
      // are provided.
      ProductVariant v = new ProductVariant();
      v.productId = productId;
      v.organizationId = organizationId;
      v.name = "Default";
      v.sku = normalizeSku(null);
      assertSkuAvailable(organizationId, v.sku, null);
      result.add(variants.save(v));
    }
    return result;
  }

  private String normalizeSku(String sku) {
    if (sku != null && !sku.trim().isEmpty()) return sku.trim();
    return "SKU-" + (100000 + (long) (Math.random() * 900000));
  }

  private void assertSkuAvailable(Long organizationId, String sku, Long ignoreVariantId) {
    boolean taken = ignoreVariantId == null
        ? variants.existsByOrganizationIdAndSkuIgnoreCase(organizationId, sku)
        : variants.existsByOrganizationIdAndSkuIgnoreCaseAndIdNot(organizationId, sku, ignoreVariantId);
    if (taken) {
      throw new ApiException(HttpStatus.CONFLICT, "SKU_ALREADY_EXISTS",
          "SKU \"" + sku + "\" is already used by another product in your catalog");
    }
  }

  @Transactional
  public VariantDto addVariant(Long organizationId, Long productId, VariantInput input) {
    Product p = requireProduct(organizationId, productId);
    if (input == null || input.name() == null || input.name().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Variant name is required");
    }
    ProductVariant v = new ProductVariant();
    v.productId = p.id;
    v.organizationId = organizationId;
    v.name = input.name().trim();
    v.sku = normalizeSku(input.sku());
    assertSkuAvailable(organizationId, v.sku, null);
    v.priceOverride = input.priceOverride();
    variants.save(v);
    return new VariantDto(v.id, v.name, v.sku, v.priceOverride);
  }

  @Transactional
  public void deleteVariant(Long organizationId, Long productId, Long variantId) {
    requireProduct(organizationId, productId);
    ProductVariant v = variants.findByIdAndProductIdAndOrganizationId(variantId, productId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variant not found"));
    if (variants.countByProductId(productId) <= 1) {
      throw new ApiException(HttpStatus.CONFLICT, "LAST_VARIANT",
          "A product must have at least one variant — edit it instead of deleting the last one");
    }
    variants.delete(v);
  }

  /* --------------------------------- image ---------------------------------- */

  @Transactional
  public ProductResponse uploadImage(Long organizationId, Long productId, MultipartFile file) {
    Product p = requireProduct(organizationId, productId);
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "No image file was uploaded");
    }
    if (file.getSize() > MAX_IMAGE_BYTES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Image must be 5MB or smaller");
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
          "Unsupported image type — use JPEG, PNG, WEBP, or GIF");
    }
    imageStorage.delete(p.imagePath);
    p.imagePath = imageStorage.store(file);
    products.save(p);
    return toResponse(p, variants.findByProductIdOrderByIdAsc(p.id),
        listings.findByProductIdOrderByMarketplaceNameAsc(p.id),
        p.categoryId == null ? null : categories.findById(p.categoryId).orElse(null));
  }

  /* -------------------------------- publish ----------------------------------
     Per Phase 6: "Implement marketplace validation before publishing."
     Validation here checks (a) the product itself is publishable and (b) the
     marketplace has a CONNECTED (or SYNCING) row in this organization's real
     Phase 5 marketplace_connection data (com.rastudio.commerce.marketplace —
     that module owns connect/disconnect/sync entirely; this method only
     reads its connection status). A successful validation records the
     listing as PUBLISHED with a placeholder external id — this module does
     not itself push to a live marketplace API; that's Phase 5's adapter
     layer (com.rastudio.commerce.marketplace.adapter), which already exists
     independently of product publishing. */

  @Transactional
  public PublishResult publish(Long organizationId, Long productId, PublishRequest request) {
    Product p = requireProduct(organizationId, productId);
    List<String> requested = request == null || request.marketplaces() == null
        ? List.of() : request.marketplaces();
    if (requested.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
          "Select at least one marketplace to publish to");
    }
    List<ProductVariant> productVariants = variants.findByProductIdOrderByIdAsc(p.id);
    List<MarketplaceConnectionStatus> connectedStatuses = List.of(MarketplaceConnectionStatus.CONNECTED, MarketplaceConnectionStatus.SYNCING);
    Map<MarketplaceName, MarketplaceConnectionStatus> connectionStatus = marketplaceConnections
        .findByOrganizationId(organizationId).stream()
        .collect(Collectors.toMap(c -> c.marketplaceName, c -> c.status, (a, b) -> a));

    String productError = null;
    if (p.name == null || p.name.isBlank()) productError = "Product is missing a name";
    else if (p.basePrice == null || p.basePrice.compareTo(BigDecimal.ZERO) <= 0) productError = "Product price must be set before publishing";
    else if (productVariants.isEmpty()) productError = "Product needs at least one variant before publishing";

    List<ListingDto> results = new ArrayList<>();
    boolean anySucceeded = false;
    for (String raw : requested) {
      MarketplaceName marketplace;
      try {
        marketplace = MarketplaceName.valueOf(raw.trim().toUpperCase());
      } catch (Exception e) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown marketplace: " + raw);
      }
      ProductMarketplaceListing listing = listings.findByProductIdAndMarketplaceName(p.id, marketplace)
          .orElseGet(() -> {
            ProductMarketplaceListing l = new ProductMarketplaceListing();
            l.productId = p.id;
            l.marketplaceName = marketplace;
            return l;
          });

      String error = productError;
      if (error == null && connectionStatus.get(marketplace) == null) {
        error = marketplace.name() + " is not connected for this organization. Connect it under Marketplace Integration first.";
      } else if (error == null && !connectedStatuses.contains(connectionStatus.get(marketplace))) {
        error = marketplace.name() + " connection status is " + connectionStatus.get(marketplace) + ", not CONNECTED.";
      }

      if (error == null) {
        listing.status = ListingStatus.PUBLISHED;
        listing.lastError = null;
        listing.publishedAt = LocalDateTime.now();
        listing.externalListingId = "MOCK-" + marketplace.name() + "-" + p.id;
        anySucceeded = true;
      } else {
        listing.status = ListingStatus.FAILED;
        listing.lastError = error;
        listing.publishedAt = null;
      }
      listings.save(listing);
      results.add(new ListingDto(marketplace.name(), listing.status.name(), listing.lastError, listing.publishedAt));
    }

    if (anySucceeded) p.status = ProductStatus.PUBLISHED;
    products.save(p);

    ProductResponse response = toResponse(p, productVariants,
        listings.findByProductIdOrderByMarketplaceNameAsc(p.id),
        p.categoryId == null ? null : categories.findById(p.categoryId).orElse(null));
    return new PublishResult(response, results, anySucceeded);
  }

  @Transactional
  public ProductResponse saveAsDraft(Long organizationId, Long productId) {
    // "Save as Draft" is a state action separate from publish — it simply
    // ensures the product is (or remains) in DRAFT without touching any
    // marketplace listings, per the Button & Action doc ("Product remains
    // in Draft status").
    Product p = requireProduct(organizationId, productId);
    p.status = ProductStatus.DRAFT;
    products.save(p);
    return toResponse(p, variants.findByProductIdOrderByIdAsc(p.id),
        listings.findByProductIdOrderByMarketplaceNameAsc(p.id),
        p.categoryId == null ? null : categories.findById(p.categoryId).orElse(null));
  }

  /* --------------------------------- mapping --------------------------------- */

  private ProductResponse toResponse(Product p, List<ProductVariant> productVariants,
      List<ProductMarketplaceListing> productListings, Category category) {
    List<VariantDto> variantDtos = productVariants.stream()
        .map(v -> new VariantDto(v.id, v.name, v.sku, v.priceOverride))
        .toList();
    List<ListingDto> listingDtos = productListings.stream()
        .map(l -> new ListingDto(l.marketplaceName.name(), l.status.name(), l.lastError, l.publishedAt))
        .toList();
    return new ProductResponse(p.id, p.name, p.description, p.categoryId,
        category == null ? null : category.name, p.basePrice, p.status.name(), p.imagePath,
        variantDtos, listingDtos, p.createdAt, p.updatedAt);
  }
}
