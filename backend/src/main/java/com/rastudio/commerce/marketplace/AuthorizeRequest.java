package com.rastudio.commerce.marketplace;
/** Body for POST /api/marketplaces/{name}/authorize. apiKey/apiSecret are required for API_KEY marketplaces
 *  (Amazon, Flipkart, WooCommerce, Magento, Meesho) and ignored for OAuth marketplaces (Shopify, Etsy), where
 *  authorization is simulated via the sandbox adapter until a real OAuth client is configured. */
public record AuthorizeRequest(String apiKey, String apiSecret) {}