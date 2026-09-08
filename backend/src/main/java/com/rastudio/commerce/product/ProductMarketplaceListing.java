package com.rastudio.commerce.product;
import com.rastudio.commerce.marketplace.MarketplaceName;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_marketplace_listing")
public class ProductMarketplaceListing {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Enumerated(EnumType.STRING)
  @Column(name = "marketplace_name", nullable = false)
  public MarketplaceName marketplaceName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public ListingStatus status = ListingStatus.PENDING;

  @Column(name = "external_listing_id")
  public String externalListingId;

  @Column(name = "last_error")
  public String lastError;

  @Column(name = "published_at")
  public LocalDateTime publishedAt;
}
