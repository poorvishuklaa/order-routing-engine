package com.orderrouting.service;

import com.orderrouting.engine.EngineModel.RuleConfig;
import com.orderrouting.entity.*;

import java.util.List;
import java.util.Set;

/**
 * A detached, read-only copy of a routing group's ACTIVE routings and rules,
 * taken at the start of a run so the run uses one consistent configuration.
 */
public record RoutingPlan(Long groupId, String groupName, List<RoutingStep> routings) {

    public record RoutingStep(Long routingId, String name, Long queueFacilityId,
                              Set<ShippingMethod> shippingMethods, Set<OrderPriority> priorities,
                              Set<SalesChannel> salesChannels, OrderSortBy orderSortBy, List<RuleStep> rules) {
    }

    public record RuleStep(Long ruleId, String name, Long facilityGroupId, String carrier, RuleConfig config,
                           FailAction failAction, Long moveToQueueId, Integer autoCancelDays) {
    }

    public static RoutingPlan of(RoutingGroup group) {
        List<RoutingStep> steps = group.getRoutings().stream()
                .filter(r -> r.getStatus() == RoutingStatus.ACTIVE)
                .map(r -> new RoutingStep(r.getId(), r.getName(),
                        r.getQueueFacility() == null ? null : r.getQueueFacility().getId(),
                        Set.copyOf(r.getShippingMethods()), Set.copyOf(r.getPriorities()),
                        Set.copyOf(r.getSalesChannels()), r.getOrderSortBy(),
                        r.getRules().stream()
                                .filter(rule -> rule.getStatus() == RoutingStatus.ACTIVE)
                                .map(RoutingPlan::ruleStep)
                                .toList()))
                .toList();
        return new RoutingPlan(group.getId(), group.getName(), steps);
    }

    private static RuleStep ruleStep(RoutingRule rule) {
        RuleConfig config = new RuleConfig(rule.getId(), rule.getName(), rule.getMaxDistanceMiles(),
                rule.getMaxTransitDays(), rule.getSafetyStock(), rule.isAllowPartialAllocation(),
                rule.isOverrideCapacity(), List.copyOf(rule.getFacilitySorts()));
        return new RuleStep(rule.getId(), rule.getName(), rule.getFacilityGroup().getId(), rule.getCarrier(), config,
                rule.getFailAction(), rule.getMoveToQueue() == null ? null : rule.getMoveToQueue().getId(),
                rule.getAutoCancelDays());
    }
}
