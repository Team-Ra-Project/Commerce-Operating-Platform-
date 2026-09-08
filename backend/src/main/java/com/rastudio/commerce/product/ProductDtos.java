package com.rastudio.commerce.product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ProductDtos {

  public record CategoryDto(Long id, String name) {}

  public record VariantInput(Long id, String name, String sku, BigDecimal priceOverride) {}

  public record VariantDto(Long id, String name, String sku, BigDecimal priceOverride) {}

  public record ListingDto(String marketplace, String status, String lastError, LocalDateTime publishedAt) {}

  public record ProductRequest(
      String name,
      String description,
      String categoryName,
      BigDecimal basePrice,
      List<VariantInput> variants) {}

  public record PublishRequest(List<String> marketplaces) {}

  public record ProductResponse(
      Long id,
      String name,
      String description,
      Long categoryId,
      String categoryName,
      BigDecimal basePrice,
      String status,
      String imagePath,
      List<VariantDto> variants,
      List<ListingDto> listings,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {}

  public record PublishResult(ProductResponse product, List<ListingDto> results, boolean anySucceeded) {}
}
