package com.orderrouting.insights;

import java.util.List;
import java.util.Map;

/** Small, aggregated summaries. Used by the dashboard and given to the AI (no customer names/addresses). */
public final class InsightDtos {

    private InsightDtos() {
    }

    public record RuleSummary(Long ruleId, String name, String status, String facilityGroup, String carrier,
                              String facilitySorts, String maxDistanceMiles, String maxTransitDays, int safetyStock,
                              boolean allowPartialAllocation, boolean overrideCapacity, String failAction) {
    }

    public record RoutingSummary(Long routingId, String name, String status, String queue,
                                 List<String> shippingMethods, String orderSortBy, List<RuleSummary> rules) {
    }

    public record GroupConfigSummary(Long groupId, String name, String status, String schedule,
                                     List<RoutingSummary> routings) {
    }

    public record CountByKey(String key, long count) {
    }

    public record RoutingStats(int days, long itemsAttempted, long itemsAllocated, long itemsNotRouted,
                               long itemsMovedToQueue, long itemsRejected, double allocationRatePercent,
                               Double averageTransitDays, long storeAllocations, long warehouseAllocations,
                               List<CountByKey> allocationsByFacility, List<CountByKey> allocationsByRule,
                               List<RegionTransit> transitByRegion) {
    }

    public record RegionTransit(String zip3, String area, long allocations, double averageTransitDays) {
    }

    public record UnroutedReason(String rule, String reason, long items) {
    }

    public record FacilityStock(String facilityCode, String type, String postalCode, int totalAvailable,
                                Map<String, Integer> availableBySku, List<String> brokeringDisabledSkus) {
    }

    public record CapacityUsage(String facilityCode, String type, Integer dailyCapacity, int ordersToday,
                                String utilization) {
    }

    public record RegionDemand(String zip3, String area, long orders, double sharePercent) {
    }

    public record FacilityGroupInfo(Long id, String code, String name, List<String> facilities) {
    }

    public record DashboardData(RoutingStats stats, List<UnroutedReason> unroutedReasons,
                                List<CapacityUsage> capacity, List<RegionDemand> demand) {
    }
}
