package com.rastudio.commerce.retention;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CampaignDtos {

  public record CampaignStepInput(Long id, Integer stepOrder, String channel, Long messageTemplateId, Integer delayDays) {}

  public record CampaignRequest(String name, Long segmentId, String goal, List<CampaignStepInput> steps) {}

  public record CampaignStepResponse(Long id, Integer stepOrder, String channel, Long messageTemplateId, String templateName, Integer delayDays) {}

  public record CampaignResponse(
      Long id, String name, Long segmentId, String segmentName, long segmentAudienceSize, String goal, String status,
      List<CampaignStepResponse> steps, Integer enrolledCount, Integer sentCount, Integer deliveredCount,
      Integer openedCount, Integer clickedCount, Integer convertedCount, BigDecimal revenueAttributed,
      LocalDateTime createdAt, LocalDateTime updatedAt) {}

  public record MessageLogResponse(Long id, Long customerId, String customerName, String channel, String status,
      String providerStatusDetail, LocalDateTime sentAt) {}
}
