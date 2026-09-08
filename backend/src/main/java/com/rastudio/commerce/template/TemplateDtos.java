package com.rastudio.commerce.template;

import java.time.LocalDateTime;
import java.util.List;

public class TemplateDtos {

  public record TemplateRequest(String name, String channel, String content) {}

  public record TemplateResponse(
      Long id, String name, String channel, String content, String approvalStatus,
      List<String> mergeFields, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
