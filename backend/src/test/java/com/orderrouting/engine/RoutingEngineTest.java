package com.orderrouting.engine;

import com.orderrouting.engine.EngineModel.*;
import com.orderrouting.entity.FacilitySort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for the routing engine (no Spring, no database). */
class RoutingEngineTest {

    private final RoutingEngine engine = new RoutingEngine();

    // Customer in San Francisco
    private final ShipTo sf = new ShipTo(37.7898, -122.3942, "941");

    private static final long JACKET = 1L;
    private static final long CAP = 2L;

    private FacilityCandidate facility(long id, String code, double lat, double lon, String zip3,
                                       Integer remainingCapacity, Map<Long, Stock> stock) {
        return new FacilityCandidate(id, code, lat, lon, zip3, remainingCapacity, stock);
    }

    private final FacilityCandidate nj = facility(1, "NJ_WH", 40.58, -74.27, "070", 0,
            Map.of(JACKET, new Stock(50, true), CAP, new Stock(50, true)));
    private final FacilityCandidate dallas = facility(2, "DAL_WH", 32.78, -96.79, "752", 40,
            Map.of(JACKET, new Stock(20, true), CAP, new Stock(20, true)));
    private final FacilityCandidate reno = facility(3, "RENO_WH", 39.49, -119.77, "895", 10,
            Map.of(JACKET, new Stock(8, true)));
    private final FacilityCandidate chicago = facility(4, "CHI_WH", 41.88, -87.61, "606", 70,
            Map.of(JACKET, new Stock(2, true), CAP, new Stock(30, true)));

    private final Map<String, Integer> transit = Map.of("070", 5, "752", 3, "895", 1, "606", 4);

    private RuleConfig rule(List<FacilitySort> sorts, boolean partial, int safetyStock) {
        return new RuleConfig(10L, "Warehouses", null, null, safetyStock, partial, false, sorts);
    }

    private ItemRequest item(long id, long product, int qty) {
        return new ItemRequest(id, product, "SKU" + product, qty, Set.of());
    }

    @Test
    void picksFastestTransitAndSkipsFullFacility() {
        RuleResult result = engine.evaluate(rule(List.of(FacilitySort.TRANSIT_TIME), false, 2),
                sf, List.of(item(100, JACKET, 1)), List.of(nj, dallas, reno, chicago), transit);

        // NJ is at capacity, Chicago has 2 - safety 2 = 0 usable, Reno is 1 day away
        assertThat(result.fullyPlaced()).isTrue();
        assertThat(result.assignments()).singleElement().extracting(Assignment::facilityCode).isEqualTo("RENO_WH");
    }

    @Test
    void inventorySortPrefersMostStock() {
        RuleResult result = engine.evaluate(rule(List.of(FacilitySort.INVENTORY), false, 0),
                sf, List.of(item(100, JACKET, 1)), List.of(nj, dallas, reno, chicago), transit);
        assertThat(result.assignments().get(0).facilityCode()).isEqualTo("DAL_WH");
    }

    @Test
    void wholeOrderMustComeFromOneFacilityWhenPartialIsOff() {
        // Reno is fastest but has no caps, so Dallas (has both) wins
        RuleResult result = engine.evaluate(rule(List.of(FacilitySort.TRANSIT_TIME), false, 0),
                sf, List.of(item(100, JACKET, 1), item(101, CAP, 1)), List.of(nj, dallas, reno), transit);
        assertThat(result.assignments()).extracting(Assignment::facilityCode).containsOnly("DAL_WH");
    }

    @Test
    void partialAllocationSplitsItemsAcrossFacilities() {
        RuleResult result = engine.evaluate(rule(List.of(FacilitySort.TRANSIT_TIME), true, 0),
                sf, List.of(item(100, JACKET, 1), item(101, CAP, 1)), List.of(nj, dallas, reno), transit);
        assertThat(result.assignments()).extracting(Assignment::facilityCode).containsExactly("RENO_WH", "DAL_WH");
    }

    @Test
    void maxDistanceFiltersEveryFacilityAndExplainsWhy() {
        RuleConfig nearby = new RuleConfig(10L, "Nearby", 100, null, 0, false, false, List.of(FacilitySort.DISTANCE));
        RuleResult result = engine.evaluate(nearby, sf, List.of(item(100, JACKET, 1)), List.of(dallas, reno), transit);
        assertThat(result.fullyPlaced()).isFalse();
        assertThat(result.unplacedReason()).isEqualTo("No facility within 100 miles");
    }

    @Test
    void excludedFacilityIsSkipped() {
        ItemRequest rejectedAtReno = new ItemRequest(100L, JACKET, "JKT", 1, Set.of(3L));
        RuleResult result = engine.evaluate(rule(List.of(FacilitySort.TRANSIT_TIME), false, 0),
                sf, List.of(rejectedAtReno), List.of(dallas, reno), transit);
        assertThat(result.assignments().get(0).facilityCode()).isEqualTo("DAL_WH");
    }

    @Test
    void tieIsBrokenByFacilityIdSoResultIsRepeatable() {
        FacilityCandidate a = facility(7, "A", 39.49, -119.77, "895", null, Map.of(JACKET, new Stock(5, true)));
        FacilityCandidate b = facility(5, "B", 39.49, -119.77, "895", null, Map.of(JACKET, new Stock(5, true)));
        for (int i = 0; i < 5; i++) {
            RuleResult result = engine.evaluate(rule(List.of(), false, 0), sf, List.of(item(100, JACKET, 1)),
                    List.of(a, b), transit);
            assertThat(result.assignments().get(0).facilityCode()).isEqualTo("B");
        }
    }

    @Test
    void reportsNoStockWithSafetyStock() {
        RuleResult result = engine.evaluate(rule(List.of(), false, 60), sf, List.of(item(100, JACKET, 1)),
                List.of(dallas, reno), transit);
        assertThat(result.unplacedReason()).isEqualTo("No stock for SKU1 after safety stock of 60");
    }
}
