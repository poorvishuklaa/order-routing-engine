package com.orderrouting.engine;

import com.orderrouting.entity.FacilitySort;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain data objects the routing engine works with. They contain no JPA or
 * Spring types, so the engine can be unit tested and run as a dry run.
 */
public final class EngineModel {

    private EngineModel() {
    }

    /** The settings of one routing rule that matter to the engine. */
    public record RuleConfig(Long ruleId,
                             String name,
                             Integer maxDistanceMiles,
                             Integer maxTransitDays,
                             int safetyStock,
                             boolean allowPartialAllocation,
                             boolean overrideCapacity,
                             List<FacilitySort> facilitySorts) {
    }

    /** Where the order ships to. */
    public record ShipTo(Double latitude, Double longitude, String zip3) {
    }

    /** One order item waiting to be placed. */
    public record ItemRequest(Long itemId, Long productId, String sku, int quantity, Set<Long> excludedFacilityIds) {
    }

    /** Stock of one product at a facility, already reduced by min stock and reservations. */
    public record Stock(int available, boolean allowBrokering) {
    }

    /** A facility from the rule's facility group, with everything needed to decide. */
    public record FacilityCandidate(Long facilityId,
                                    String code,
                                    Double latitude,
                                    Double longitude,
                                    String zip3,
                                    Integer remainingCapacity,
                                    Map<Long, Stock> stockByProduct) {
    }

    /** One item assigned to one facility. */
    public record Assignment(Long itemId, Long facilityId, String facilityCode, Integer transitDays,
                             Double distanceMiles, String reason) {
    }

    /** What a rule decided for the items it was given. */
    public record RuleResult(List<Assignment> assignments, List<Long> unplacedItemIds, String unplacedReason) {

        public boolean fullyPlaced() {
            return unplacedItemIds.isEmpty();
        }
    }
}
