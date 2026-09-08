package com.rastudio.commerce.retention;

public class SegmentDtos {
  public record SegmentRequest(String name, String ruleKey, String description) {}

  public record SegmentResponse(Long id, String name, String ruleKey, String description, long memberCount) {}
}
