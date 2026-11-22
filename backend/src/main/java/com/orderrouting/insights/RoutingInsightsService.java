package com.orderrouting.insights;

import com.orderrouting.engine.GeoUtils;
import com.orderrouting.entity.*;
import com.orderrouting.exception.NotFoundException;
import com.orderrouting.insights.InsightDtos.*;
import com.orderrouting.repository.*;
import com.orderrouting.scheduler.RoutingScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only analytics over routing decisions, inventory, capacity and demand. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoutingInsightsService {

    private final RoutingGroupRepository groupRepository;
    private final RoutingDecisionRepository decisionRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityGroupRepository facilityGroupRepository;
    private final InventoryRepository inventoryRepository;
    private final FacilityDailyCountRepository dailyCountRepository;
    private final SalesOrderRepository orderRepository;
    private final PostalCodeRepository postalCodeRepository;
    private final RuleValueFormatter formatter;

    public GroupConfigSummary groupConfig(Long groupId) {
        RoutingGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Routing group " + groupId + " not found"));
        List<RoutingSummary> routings = group.getRoutings().stream().map(r -> new RoutingSummary(
                r.getId(), r.getName(), r.getStatus().name(),
                r.getQueueFacility() == null ? "ANY" : r.getQueueFacility().getCode(),
                r.getShippingMethods().stream().map(Enum::name).sorted().toList(),
                r.getOrderSortBy().name(),
                r.getRules().stream().map(rule -> new RuleSummary(rule.getId(), rule.getName(), rule.getStatus().name(),
                        formatter.current(rule, ChangeableField.FACILITY_GROUP), rule.getCarrier(),
                        formatter.current(rule, ChangeableField.FACILITY_SORTS),
                        formatter.current(rule, ChangeableField.MAX_DISTANCE_MILES),
                        formatter.current(rule, ChangeableField.MAX_TRANSIT_DAYS),
                        rule.getSafetyStock(), rule.isAllowPartialAllocation(), rule.isOverrideCapacity(),
                        rule.getFailAction().name())).toList())).toList();
        return new GroupConfigSummary(group.getId(), group.getName(), group.getStatus().name(),
                RoutingScheduler.describe(group.getCronExpression()), routings);
    }

    public RoutingStats routingStats(Long groupId, int days) {
        List<RoutingDecision> decisions = decisions(groupId, days);
        Map<Long, List<RoutingDecision>> byItem = decisions.stream()
                .collect(Collectors.groupingBy(RoutingDecision::getOrderItemId, LinkedHashMap::new, Collectors.toList()));
        // Final outcome per item = its last decision in the window
        Map<Long, RoutingDecision> last = new LinkedHashMap<>();
        byItem.forEach((item, list) -> last.put(item, list.stream().max(Comparator.comparing(RoutingDecision::getId)).orElseThrow()));

        long allocated = count(last.values(), DecisionOutcome.ALLOCATED);
        long notRouted = count(last.values(), DecisionOutcome.NOT_ROUTED);
        long moved = count(last.values(), DecisionOutcome.MOVED_TO_QUEUE);
        long rejected = decisions.stream().filter(d -> d.getOutcome() == DecisionOutcome.REJECTED).count();

        List<RoutingDecision> allocations = decisions.stream()
                .filter(d -> d.getOutcome() == DecisionOutcome.ALLOCATED).toList();
        Double avgTransit = allocations.stream().filter(d -> d.getTransitDays() != null)
                .mapToInt(RoutingDecision::getTransitDays).average().stream().boxed().findFirst().map(this::round1).orElse(null);

        Map<String, FacilityType> types = facilityRepository.findAll().stream()
                .collect(Collectors.toMap(Facility::getCode, Facility::getType));
        long store = allocations.stream().filter(d -> types.get(d.getFacilityCode()) == FacilityType.STORE).count();

        Map<String, String> areas = areas();
        List<RegionTransit> regions = allocations.stream().filter(d -> d.getShipToZip3() != null && d.getTransitDays() != null)
                .collect(Collectors.groupingBy(RoutingDecision::getShipToZip3))
                .entrySet().stream()
                .map(e -> new RegionTransit(e.getKey(), areas.getOrDefault(e.getKey(), "?"), e.getValue().size(),
                        round1(e.getValue().stream().mapToInt(RoutingDecision::getTransitDays).average().orElse(0))))
                .sorted(Comparator.comparing(RegionTransit::averageTransitDays).reversed())
                .toList();

        long attempted = last.size();
        return new RoutingStats(days, attempted, allocated, notRouted, moved, rejected,
                attempted == 0 ? 0 : round1(100.0 * allocated / attempted), avgTransit, store, allocations.size() - store,
                countBy(allocations, RoutingDecision::getFacilityCode),
                countBy(allocations, d -> d.getRoutingName() + " / " + d.getRuleName()),
                regions);
    }

    public List<UnroutedReason> unroutedReasons(Long groupId, int days) {
        return decisions(groupId, days).stream()
                .filter(d -> d.getOutcome() == DecisionOutcome.NOT_ROUTED || d.getOutcome() == DecisionOutcome.MOVED_TO_QUEUE)
                .collect(Collectors.groupingBy(d -> List.of(
                                d.getRoutingName() + " / " + d.getRuleName() + " (ruleId " + d.getRuleId() + ")",
                                stripRulePrefix(d.getReason())), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new UnroutedReason(e.getKey().get(0), e.getKey().get(1), e.getValue()))
                .sorted(Comparator.comparing(UnroutedReason::items).reversed())
                .toList();
    }

    public List<FacilityStock> inventoryByFacility() {
        Map<Facility, List<Inventory>> byFacility = inventoryRepository.findAllWithDetails().stream()
                .collect(Collectors.groupingBy(Inventory::getFacility, LinkedHashMap::new, Collectors.toList()));
        return byFacility.entrySet().stream().map(e -> {
            Map<String, Integer> bySku = new TreeMap<>();
            List<String> disabled = new ArrayList<>();
            e.getValue().forEach(i -> {
                bySku.put(i.getProduct().getSku(), i.getAvailable());
                if (!i.isAllowBrokering()) disabled.add(i.getProduct().getSku());
            });
            Facility f = e.getKey();
            return new FacilityStock(f.getCode(), f.getType().name(), f.getPostalCode(),
                    bySku.values().stream().mapToInt(Integer::intValue).sum(), bySku, disabled);
        }).toList();
    }

    public List<CapacityUsage> capacityUsage() {
        Map<Long, Integer> today = dailyCountRepository.findByDate(LocalDate.now()).stream()
                .collect(Collectors.toMap(c -> c.getFacility().getId(), FacilityDailyCount::getOrderCount));
        return facilityRepository.findAll().stream().filter(f -> !f.isQueue())
                .sorted(Comparator.comparing(Facility::getCode))
                .map(f -> {
                    int used = today.getOrDefault(f.getId(), 0);
                    String utilization = f.getDailyCapacity() == null ? "unlimited"
                            : f.getDailyCapacity() == 0 ? "closed" : Math.round(100.0 * used / f.getDailyCapacity()) + "%";
                    return new CapacityUsage(f.getCode(), f.getType().name(), f.getDailyCapacity(), used, utilization);
                }).toList();
    }

    public List<RegionDemand> demandByRegion(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<SalesOrder> orders = orderRepository.findAll().stream()
                .filter(o -> o.getOrderDate().isAfter(since)).toList();
        Map<String, String> areas = areas();
        Map<String, Long> counts = orders.stream()
                .collect(Collectors.groupingBy(o -> GeoUtils.zip3(o.getShipToPostalCode()), Collectors.counting()));
        long total = orders.size();
        return counts.entrySet().stream()
                .map(e -> new RegionDemand(e.getKey(), areas.getOrDefault(e.getKey(), "?"), e.getValue(),
                        total == 0 ? 0 : round1(100.0 * e.getValue() / total)))
                .sorted(Comparator.comparing(RegionDemand::orders).reversed())
                .toList();
    }

    public List<FacilityGroupInfo> facilityGroups() {
        return facilityGroupRepository.findAll().stream()
                .map(g -> new FacilityGroupInfo(g.getId(), g.getCode(), g.getName(),
                        g.getFacilities().stream().map(Facility::getCode).sorted().toList()))
                .toList();
    }

    public DashboardData dashboard(Long groupId, int days) {
        return new DashboardData(routingStats(groupId, days), unroutedReasons(groupId, days), capacityUsage(),
                demandByRegion(days));
    }

    private List<RoutingDecision> decisions(Long groupId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return groupId == null
                ? decisionRepository.findByCreatedAtAfter(since)
                : decisionRepository.findByRoutingGroupIdAndCreatedAtAfter(groupId, since);
    }

    private Map<String, String> areas() {
        return postalCodeRepository.findAll().stream().collect(Collectors.toMap(
                p -> GeoUtils.zip3(p.getZip()), p -> p.getCity() + ", " + p.getState(), (a, b) -> a));
    }

    private static String stripRulePrefix(String reason) {
        if (reason == null) return "";
        int i = reason.indexOf("': ");
        return reason.startsWith("Rule '") && i > 0 ? reason.substring(i + 3) : reason;
    }

    private static long count(Collection<RoutingDecision> list, DecisionOutcome outcome) {
        return list.stream().filter(d -> d.getOutcome() == outcome).count();
    }

    private static List<CountByKey> countBy(List<RoutingDecision> list, Function<RoutingDecision, String> key) {
        return list.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new CountByKey(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CountByKey::count).reversed()).toList();
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
