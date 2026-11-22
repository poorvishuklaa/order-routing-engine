package com.orderrouting.service;

import com.orderrouting.dto.RoutingDtos.RoutingDto;
import com.orderrouting.dto.RoutingDtos.RoutingGroupDto;
import com.orderrouting.dto.RoutingDtos.RoutingRuleDto;
import com.orderrouting.entity.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Converts routing entities to DTOs (must be called inside a transaction). */
@Component
public class RoutingGroupMapper {

    public RoutingGroupDto toDto(RoutingGroup g) {
        return new RoutingGroupDto(g.getId(), g.getName(), g.getDescription(), g.getStatus(),
                g.getCronExpression(), g.getVersion(), g.getCreatedAt(), g.getUpdatedAt(),
                g.getRoutings().stream().map(this::toDto).toList());
    }

    public RoutingDto toDto(Routing r) {
        return new RoutingDto(r.getId(), r.getName(), r.getSequenceNum(), r.getStatus(),
                r.getQueueFacility() == null ? null : r.getQueueFacility().getId(),
                new LinkedHashSet<>(r.getShippingMethods()), new LinkedHashSet<>(r.getPriorities()),
                new LinkedHashSet<>(r.getSalesChannels()), r.getOrderSortBy(),
                r.getRules().stream().map(this::toDto).toList());
    }

    public RoutingRuleDto toDto(RoutingRule rule) {
        return new RoutingRuleDto(rule.getId(), rule.getName(), rule.getSequenceNum(), rule.getStatus(),
                rule.getFacilityGroup() == null ? null : rule.getFacilityGroup().getId(),
                rule.getCarrier(), rule.getMaxDistanceMiles(), rule.getMaxTransitDays(), rule.getSafetyStock(),
                rule.isAllowPartialAllocation(), rule.isOverrideCapacity(), new ArrayList<>(rule.getFacilitySorts()),
                rule.getFailAction(), rule.getMoveToQueue() == null ? null : rule.getMoveToQueue().getId(),
                rule.getAutoCancelDays());
    }
}
