package com.orderrouting.dto;

import com.orderrouting.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Request/response objects for routing groups, routings and rules. */
public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record CreateGroupRequest(@NotBlank String name, String description) {
    }

    public record RoutingGroupSummary(Long id, String name, String description, GroupStatus status,
                                      String cronExpression, String scheduleDescription,
                                      LocalDateTime nextRunAt, LocalDateTime lastRunAt,
                                      int routingCount, LocalDateTime updatedAt) {
    }

    /** The whole group. PUT replaces routings and rules with exactly what is sent. */
    public record RoutingGroupDto(Long id,
                                  @NotBlank String name,
                                  String description,
                                  GroupStatus status,
                                  String cronExpression,
                                  Long version,
                                  LocalDateTime createdAt,
                                  LocalDateTime updatedAt,
                                  @Valid List<RoutingDto> routings) {
    }

    public record RoutingDto(Long id,
                             @NotBlank String name,
                             int sequenceNum,
                             RoutingStatus status,
                             Long queueFacilityId,
                             Set<ShippingMethod> shippingMethods,
                             Set<OrderPriority> priorities,
                             Set<SalesChannel> salesChannels,
                             OrderSortBy orderSortBy,
                             @Valid List<RoutingRuleDto> rules) {
    }

    public record RoutingRuleDto(Long id,
                                 @NotBlank String name,
                                 int sequenceNum,
                                 RoutingStatus status,
                                 Long facilityGroupId,
                                 String carrier,
                                 Integer maxDistanceMiles,
                                 Integer maxTransitDays,
                                 Integer safetyStock,
                                 boolean allowPartialAllocation,
                                 boolean overrideCapacity,
                                 List<FacilitySort> facilitySorts,
                                 FailAction failAction,
                                 Long moveToQueueId,
                                 Integer autoCancelDays) {
    }
}
