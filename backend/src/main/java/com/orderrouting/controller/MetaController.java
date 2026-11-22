package com.orderrouting.controller;

import com.orderrouting.entity.*;
import com.orderrouting.repository.FacilityRepository;
import com.orderrouting.service.RoutingConfigValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Option lists for the frontend dropdowns. */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final FacilityRepository facilityRepository;

    public record QueueOption(Long id, String code, String name) {
    }

    @GetMapping
    public Map<String, Object> meta() {
        List<QueueOption> queues = facilityRepository.findByTypeOrderByCode(FacilityType.QUEUE).stream()
                .map(f -> new QueueOption(f.getId(), f.getCode(), f.getName())).toList();
        return Map.of(
                "shippingMethods", ShippingMethod.values(),
                "priorities", OrderPriority.values(),
                "salesChannels", SalesChannel.values(),
                "orderSorts", OrderSortBy.values(),
                "facilitySorts", FacilitySort.values(),
                "failActions", FailAction.values(),
                "routingStatuses", RoutingStatus.values(),
                "carriers", RoutingConfigValidator.CARRIERS.stream().sorted().toList(),
                "queues", queues);
    }
}
