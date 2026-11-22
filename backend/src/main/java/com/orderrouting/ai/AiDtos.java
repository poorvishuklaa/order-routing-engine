package com.orderrouting.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.orderrouting.entity.ChangeableField;
import com.orderrouting.entity.RecommendationStatus;

import java.time.LocalDateTime;
import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    /** The structured answer the LLM must return. Spring AI turns this into a JSON schema. */
    public record AiRecommendationResponse(
            @JsonPropertyDescription("Short title of the recommendation, max 80 characters")
            String title,
            @JsonPropertyDescription("Why these changes help, citing numbers from the tools")
            String reason,
            @JsonPropertyDescription("What should improve after the change")
            String expectedImpact,
            @JsonPropertyDescription("At most 3 rule changes. Empty list if nothing should change.")
            List<RuleChange> changes) {
    }

    public record RuleChange(
            @JsonPropertyDescription("ruleId from getRoutingGroupConfig")
            Long ruleId,
            @JsonPropertyDescription("Field to change")
            ChangeableField field,
            @JsonPropertyDescription("Current value exactly as shown by getRoutingGroupConfig")
            String oldValue,
            @JsonPropertyDescription("New value in the same format")
            String newValue) {
    }

    public record ChangeView(Long ruleId, String ruleName, String routingName, ChangeableField field,
                             String oldValue, String newValue) {
    }

    public record AiRecommendationDto(Long id, Long routingGroupId, String title, String reason,
                                      String expectedImpact, List<ChangeView> changes, RecommendationStatus status,
                                      String errorMessage, List<String> toolCalls, String model, String createdBy,
                                      LocalDateTime createdAt, String reviewedBy, LocalDateTime reviewedAt,
                                      String reviewNote) {
    }

    public record AiStatus(boolean configured, String model) {
    }

    public record ReviewRequest(String note) {
    }
}
