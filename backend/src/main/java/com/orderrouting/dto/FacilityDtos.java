package com.orderrouting.dto;

import com.orderrouting.entity.FacilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class FacilityDtos {

    private FacilityDtos() {
    }

    public record FacilityDto(Long id, String code, String name, FacilityType type, String postalCode,
                              Double latitude, Double longitude, Integer dailyCapacity,
                              int ordersToday, boolean active) {
    }

    /** dailyCapacity null = unlimited, 0 = takes no orders. */
    public record CapacityRequest(Integer dailyCapacity) {
    }

    public record FacilityGroupDto(Long id, String code, String name, String description,
                                   List<FacilityDto> facilities, List<String> usedByRules) {
    }

    public record FacilityGroupRequest(@NotBlank @Size(max = 20) String code,
                                       @NotBlank String name,
                                       String description,
                                       List<Long> facilityIds) {
    }

    public record InventoryDto(Long id, Long facilityId, String facilityCode, Long productId, String sku,
                               String productName, int onHand, int reserved, int minStock, int available,
                               boolean allowBrokering) {
    }
}
