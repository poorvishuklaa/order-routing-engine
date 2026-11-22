package com.orderrouting.service;

import com.orderrouting.entity.*;
import com.orderrouting.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business validation for a routing group. Used by the editor's Save and by
 * AI recommendation approval, so both paths follow exactly the same rules.
 */
@Component
public class RoutingConfigValidator {

    public static final Set<String> CARRIERS = Set.of("UPS_GROUND", "FEDEX_GROUND");

    public void validate(RoutingGroup group) {
        List<String> errors = new ArrayList<>();
        if (isBlank(group.getName())) {
            errors.add("Group name is required");
        }
        for (Routing routing : group.getRoutings()) {
            String r = "Routing '" + routing.getName() + "': ";
            if (isBlank(routing.getName())) {
                errors.add("Routing name is required");
            }
            if (routing.getQueueFacility() != null && !routing.getQueueFacility().isQueue()) {
                errors.add(r + "queue filter must be a QUEUE facility");
            }
            for (RoutingRule rule : routing.getRules()) {
                errors.addAll(validateRule(rule).stream().map(e -> r + e).toList());
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException(errors);
        }
    }

    public List<String> validateRule(RoutingRule rule) {
        List<String> errors = new ArrayList<>();
        String p = "rule '" + rule.getName() + "': ";
        if (isBlank(rule.getName())) {
            errors.add("Rule name is required");
        }
        if (rule.getFacilityGroup() == null) {
            errors.add(p + "facility group is required");
        }
        if (rule.getCarrier() == null || !CARRIERS.contains(rule.getCarrier())) {
            errors.add(p + "carrier must be one of " + CARRIERS);
        }
        if (rule.getMaxDistanceMiles() != null && (rule.getMaxDistanceMiles() < 1 || rule.getMaxDistanceMiles() > 5000)) {
            errors.add(p + "max distance must be between 1 and 5000 miles");
        }
        if (rule.getMaxTransitDays() != null && (rule.getMaxTransitDays() < 1 || rule.getMaxTransitDays() > 10)) {
            errors.add(p + "max transit days must be between 1 and 10");
        }
        if (rule.getSafetyStock() < 0) {
            errors.add(p + "safety stock cannot be negative");
        }
        Set<FacilitySort> seen = new HashSet<>();
        for (FacilitySort sort : rule.getFacilitySorts()) {
            if (sort == null || !seen.add(sort)) {
                errors.add(p + "facility sorts must not repeat");
                break;
            }
        }
        if (rule.getFailAction() == FailAction.MOVE_TO_QUEUE
                && (rule.getMoveToQueue() == null || !rule.getMoveToQueue().isQueue())) {
            errors.add(p + "MOVE_TO_QUEUE needs a queue facility");
        }
        if (rule.getFailAction() == FailAction.AUTO_CANCEL
                && (rule.getAutoCancelDays() == null || rule.getAutoCancelDays() < 1)) {
            errors.add(p + "AUTO_CANCEL needs auto cancel days of at least 1");
        }
        return errors;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
