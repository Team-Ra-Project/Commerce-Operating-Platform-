package com.rastudio.commerce.product;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.product.ProductDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductController {

  /* Every Product Management button in the Button & Action doc lists
     "Ops Manager, Admin" as Required Role. Reads are left open to any
     authenticated, tenant-scoped user (e.g. an Analyst/Viewer browsing the
     catalog, or other modules that will reference product data later) —
     the roadmap's RBAC table does not name Products under Analyst's
     read-only scope explicitly, but nothing restricts catalog reads to
     Ops/Admin either, and over-restricting reads here would break other
     already-authenticated roles from simply viewing the catalog. */
  private void requireCatalogWriteAccess(TenantPrincipal principal) {
    if (principal.role() != UserRole.BUSINESS_OWNER_ADMIN && principal.role() != UserRole.OPERATIONS_MANAGER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
          "Only an Operations Manager or Admin can manage products");
    }
  }

  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  @GetMapping("/api/categories")
  public List<CategoryDto> listCategories(@AuthenticationPrincipal TenantPrincipal p) {
    return service.listCategories(p.organizationId());
  }

  @GetMapping("/api/products")
  public List<ProductResponse> list(@AuthenticationPrincipal TenantPrincipal p) {
    return service.listProducts(p.organizationId());
  }

  @GetMapping("/api/products/{id}")
  public ProductResponse get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    return service.getProduct(p.organizationId(), id);
  }

  @PostMapping("/api/products")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductResponse create(@AuthenticationPrincipal TenantPrincipal p, @RequestBody ProductRequest request) {
    requireCatalogWriteAccess(p);
    return service.createProduct(p.organizationId(), p.userId(), request);
  }

  @PutMapping("/api/products/{id}")
  public ProductResponse update(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id,
      @RequestBody ProductRequest request) {
    requireCatalogWriteAccess(p);
    return service.updateProduct(p.organizationId(), id, request);
  }

  @DeleteMapping("/api/products/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireCatalogWriteAccess(p);
    service.deleteProduct(p.organizationId(), id);
  }

  @PostMapping("/api/products/{id}/variants")
  @ResponseStatus(HttpStatus.CREATED)
  public VariantDto addVariant(@AuthenticationPrincipal TenantPrincipal p, @PathVariable("id") Long productId,
      @RequestBody VariantInput input) {
    requireCatalogWriteAccess(p);
    return service.addVariant(p.organizationId(), productId, input);
  }

  @DeleteMapping("/api/products/{id}/variants/{variantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteVariant(@AuthenticationPrincipal TenantPrincipal p, @PathVariable("id") Long productId,
      @PathVariable Long variantId) {
    requireCatalogWriteAccess(p);
    service.deleteVariant(p.organizationId(), productId, variantId);
  }

  @PostMapping(value = "/api/products/{id}/image", consumes = "multipart/form-data")
  public ProductResponse uploadImage(@AuthenticationPrincipal TenantPrincipal p, @PathVariable("id") Long productId,
      @RequestParam("file") MultipartFile file) {
    requireCatalogWriteAccess(p);
    return service.uploadImage(p.organizationId(), productId, file);
  }

  @PostMapping("/api/products/{id}/publish")
  public Map<String, Object> publish(@AuthenticationPrincipal TenantPrincipal p, @PathVariable("id") Long productId,
      @RequestBody PublishRequest request) {
    requireCatalogWriteAccess(p);
    PublishResult result = service.publish(p.organizationId(), productId, request);
    return Map.of(
        "success", true,
        "product", result.product(),
        "results", result.results(),
        "anySucceeded", result.anySucceeded());
  }

  @PostMapping("/api/products/{id}/save-draft")
  public ProductResponse saveDraft(@AuthenticationPrincipal TenantPrincipal p, @PathVariable("id") Long productId) {
    requireCatalogWriteAccess(p);
    return service.saveAsDraft(p.organizationId(), productId);
  }
}
