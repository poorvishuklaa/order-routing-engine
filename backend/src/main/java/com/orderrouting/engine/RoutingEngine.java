package com.orderrouting.engine;

import com.orderrouting.engine.EngineModel.*;
import com.orderrouting.entity.FacilitySort;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The core routing logic for one rule and one order (ship group).
 *
 * 1. FILTER: keep facilities that pass every hard constraint
 *    (capacity, distance, transit time, stock after safety stock, brokering allowed, not excluded).
 * 2. RANK: sort them by the rule's facility sorts, then by "covers most items", then facility id.
 * 3. PLACE: partial off -> first facility that has every item; partial on -> best facility per item.
 *
 * The engine is a pure function: same input always gives the same output. It never touches the database.
 */
public class RoutingEngine {

    public static final List<FacilitySort> DEFAULT_SORTS = List.of(FacilitySort.TRANSIT_TIME, FacilitySort.DISTANCE);

    /** A facility that passed the facility-level checks, with derived values used for ranking. */
    record Eligible(FacilityCandidate facility, Integer transitDays, Double distanceMiles,
                    Set<Long> coverableItemIds, int totalUsableStock) {
    }

    public RuleResult evaluate(RuleConfig rule, ShipTo shipTo, List<ItemRequest> items,
                               List<FacilityCandidate> facilities, Map<String, Integer> transitDaysByOriginZip3) {
        if (items.isEmpty()) {
            return new RuleResult(List.of(), List.of(), null);
        }
        if (facilities.isEmpty()) {
            return notPlaced(items, "No facilities in the rule's facility group");
        }

        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        List<Eligible> eligible = new ArrayList<>();
        for (FacilityCandidate facility : facilities) {
            Integer transit = facility.zip3() == null ? null : transitDaysByOriginZip3.get(facility.zip3());
            Double distance = distance(facility, shipTo);

            String rejection = facilityLevelRejection(rule, facility, transit, distance);
            if (rejection != null) {
                rejectionCounts.merge(rejection, 1, Integer::sum);
                continue;
            }
            Set<Long> coverable = new LinkedHashSet<>();
            int usableStock = 0;
            for (ItemRequest item : items) {
                int usable = usableStock(rule, facility, item);
                usableStock += Math.max(0, usable);
                if (usable >= item.quantity()) {
                    coverable.add(item.itemId());
                }
            }
            if (coverable.isEmpty()) {
                rejectionCounts.merge("NO_STOCK", 1, Integer::sum);
                continue;
            }
            eligible.add(new Eligible(facility, transit, distance, coverable, usableStock));
        }

        eligible.sort(comparator(rule));

        RuleResult result = rule.allowPartialAllocation()
                ? placePartial(rule, items, eligible)
                : placeWhole(rule, items, eligible);

        if (!result.fullyPlaced() && result.unplacedReason() == null) {
            return new RuleResult(result.assignments(), result.unplacedItemIds(),
                    explain(rule, items, eligible, rejectionCounts, result.unplacedItemIds()));
        }
        return result;
    }

    /** Returns a rejection code if the facility fails a check that does not depend on the item. */
    private String facilityLevelRejection(RuleConfig rule, FacilityCandidate f, Integer transit, Double distance) {
        if (!rule.overrideCapacity() && f.remainingCapacity() != null && f.remainingCapacity() <= 0) {
            return "CAPACITY_FULL";
        }
        if (rule.maxDistanceMiles() != null && (distance == null || distance > rule.maxDistanceMiles())) {
            return "TOO_FAR";
        }
        if (rule.maxTransitDays() != null && (transit == null || transit > rule.maxTransitDays())) {
            return "TRANSIT_TOO_LONG";
        }
        return null;
    }

    /** Units this facility can give for the item; negative or zero means it cannot. */
    private int usableStock(RuleConfig rule, FacilityCandidate f, ItemRequest item) {
        if (item.excludedFacilityIds() != null && item.excludedFacilityIds().contains(f.facilityId())) {
            return 0;
        }
        Stock stock = f.stockByProduct().get(item.productId());
        if (stock == null || !stock.allowBrokering()) {
            return 0;
        }
        return stock.available() - rule.safetyStock();
    }

    private RuleResult placeWhole(RuleConfig rule, List<ItemRequest> items, List<Eligible> ranked) {
        for (Eligible e : ranked) {
            if (e.coverableItemIds().size() == items.size()) {
                List<Assignment> assignments = items.stream()
                        .map(i -> assignment(rule, i, e, "all items from one facility"))
                        .toList();
                return new RuleResult(assignments, List.of(), null);
            }
        }
        if (ranked.stream().anyMatch(e -> !e.coverableItemIds().isEmpty()) && items.size() > 1) {
            return new RuleResult(List.of(), ids(items),
                    "No single facility has every item in stock (partial allocation is off)");
        }
        return new RuleResult(List.of(), ids(items), null);
    }

    private RuleResult placePartial(RuleConfig rule, List<ItemRequest> items, List<Eligible> ranked) {
        List<Assignment> assignments = new ArrayList<>();
        List<Long> unplaced = new ArrayList<>();
        for (ItemRequest item : items) {
            Optional<Eligible> best = ranked.stream().filter(e -> e.coverableItemIds().contains(item.itemId())).findFirst();
            if (best.isPresent()) {
                assignments.add(assignment(rule, item, best.get(), "best facility for this item"));
            } else {
                unplaced.add(item.itemId());
            }
        }
        return new RuleResult(assignments, unplaced, null);
    }

    private Assignment assignment(RuleConfig rule, ItemRequest item, Eligible e, String how) {
        String reason = String.format("Rule '%s': %s; %s (%s, %s)", rule.name(), how, e.facility().code(),
                e.transitDays() == null ? "transit n/a" : e.transitDays() + "d transit",
                e.distanceMiles() == null ? "distance n/a" : Math.round(e.distanceMiles()) + " mi");
        return new Assignment(item.itemId(), e.facility().facilityId(), e.facility().code(),
                e.transitDays(), e.distanceMiles() == null ? null : round1(e.distanceMiles()), reason);
    }

    /** Rule sorts first, then "covers most items", then facility id so the result is always repeatable. */
    Comparator<Eligible> comparator(RuleConfig rule) {
        List<FacilitySort> sorts = rule.facilitySorts() == null || rule.facilitySorts().isEmpty()
                ? DEFAULT_SORTS : rule.facilitySorts();
        Comparator<Eligible> comparator = null;
        for (FacilitySort sort : sorts) {
            Comparator<Eligible> next = switch (sort) {
                case TRANSIT_TIME -> Comparator.comparing(Eligible::transitDays, Comparator.nullsLast(Comparator.naturalOrder()));
                case DISTANCE -> Comparator.comparing(Eligible::distanceMiles, Comparator.nullsLast(Comparator.naturalOrder()));
                case INVENTORY -> Comparator.comparingInt(Eligible::totalUsableStock).reversed();
                case CAPACITY_REMAINING -> Comparator.comparingInt((Eligible e) ->
                        e.facility().remainingCapacity() == null ? Integer.MAX_VALUE : e.facility().remainingCapacity()).reversed();
            };
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        Comparator<Eligible> tieBreakers = Comparator.comparingInt((Eligible e) -> e.coverableItemIds().size()).reversed()
                .thenComparing(e -> e.facility().facilityId());
        return comparator == null ? tieBreakers : comparator.thenComparing(tieBreakers);
    }

    private String explain(RuleConfig rule, List<ItemRequest> items, List<Eligible> eligible,
                           Map<String, Integer> rejections, List<Long> unplacedIds) {
        if (eligible.isEmpty()) {
            String top = rejections.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("NO_STOCK");
            return switch (top) {
                case "CAPACITY_FULL" -> "All facilities reached their daily capacity";
                case "TOO_FAR" -> "No facility within " + rule.maxDistanceMiles() + " miles";
                case "TRANSIT_TOO_LONG" -> "No facility within " + rule.maxTransitDays() + " transit days";
                default -> "No stock" + skus(items, unplacedIds) + (rule.safetyStock() > 0
                        ? " after safety stock of " + rule.safetyStock() : "");
            };
        }
        return "No stock" + skus(items, unplacedIds) + " in eligible facilities";
    }

    private String skus(List<ItemRequest> items, List<Long> unplacedIds) {
        String skus = items.stream().filter(i -> unplacedIds.contains(i.itemId()))
                .map(ItemRequest::sku).distinct().collect(Collectors.joining(", "));
        return skus.isEmpty() ? "" : " for " + skus;
    }

    private RuleResult notPlaced(List<ItemRequest> items, String reason) {
        return new RuleResult(List.of(), ids(items), reason);
    }

    private static List<Long> ids(List<ItemRequest> items) {
        return items.stream().map(ItemRequest::itemId).toList();
    }

    private static Double distance(FacilityCandidate f, ShipTo shipTo) {
        if (f.latitude() == null || f.longitude() == null || shipTo.latitude() == null || shipTo.longitude() == null) {
            return null;
        }
        return GeoUtils.distanceMiles(f.latitude(), f.longitude(), shipTo.latitude(), shipTo.longitude());
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
