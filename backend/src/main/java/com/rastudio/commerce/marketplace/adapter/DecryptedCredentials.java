package com.rastudio.commerce.marketplace.adapter;
public record DecryptedCredentials(String apiKey, String apiSecret, String oauthToken) {
  public boolean isBlank() { return (apiKey == null || apiKey.isBlank()) && (oauthToken == null || oauthToken.isBlank()); }
}