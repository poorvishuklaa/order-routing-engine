package com.orderrouting.service;

import com.orderrouting.engine.EngineModel.*;
import com.orderrouting.engine.GeoUtils;
import com.orderrouting.engine.RoutingEngine;
import com.orderrouting.entity.*;
import com.orderrouting.exception.InsufficientStockException;
import com.orderrouting.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Routes ONE order through ONE routing's rules, in its own transaction.
 * A failure here never undoes other orders. Optimistic lock conflicts are
 * retried with fresh data. In dry-run mode the transaction is rolled back,
 * so the exact same logic can be used for "test drive" without changing data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRouter {

    private static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate transactionTemplate;
    private final SalesOrderRepository orderRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityGroupRepository facilityGroupRepository;
    private final InventoryRepository inventoryRepository;
    private final FacilityDailyCountRepository dailyCountRepository;
    private final CarrierTransitRepository transitRepository;
    private final PostalCodeRepository postalCodeRepository;
    private final OrderItemExclusionRepository exclusionRepository;
    private final RoutingDecisionRepository decisionRepository;
    private final AllocationService allocationService;
    private final RoutingEngine engine = new RoutingEngine();

    public record Result(int itemsConsidered, int itemsPlaced, List<RoutingDecision> decisions) {
        static Result empty() {
            return new Result(0, 0, List.of());
        }
    }

    public Result route(Long orderId, RoutingPlan plan, RoutingPlan.RoutingStep routing, Long runId, boolean dryRun) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> {
                    Result result = routeInTransaction(orderId, plan, routing, runId, dryRun);
                    if (dryRun) {
                        status.setRollbackOnly();
                    }
                    return result;
                });
            } catch (ObjectOptimisticLockingFailureException | InsufficientStockException
                     | DataIntegrityViolationException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Giving up on order {} after {} attempts: {}", orderId, attempt, e.getMessage());
                    throw e;
                }
                log.info("Concurrent change while routing order {} (attempt {}), retrying", orderId, attempt);
            }
        }
    }

    private Result routeInTransaction(Long orderId, RoutingPlan plan, RoutingPlan.RoutingStep routing,
                                      Long runId, boolean dryRun) {
        SalesOrder order = orderRepository.findWithItems(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.APPROVED) {
            return Result.empty();
        }
        List<OrderItem> items = order.getItems().stream()
                .filter(i -> i.getStatus() == OrderItemStatus.QUEUED && i.getFacility().isQueue())
                .filter(i -> routing.queueFacilityId() == null || i.getFacility().getId().equals(routing.queueFacilityId()))
                .toList();
        if (items.isEmpty()) {
            return Result.empty();
        }

        String zip3 = GeoUtils.zip3(order.getShipToPostalCode());
        PostalCode pc = postalCodeRepository.findById(order.getShipToPostalCode()).orElse(null);
        ShipTo shipTo = new ShipTo(pc == null ? null : pc.getLatitude(), pc == null ? null : pc.getLongitude(), zip3);

        Map<Long, Set<Long>> exclusions = exclusionRepository
                .findByOrderItemIdIn(items.stream().map(OrderItem::getId).toList()).stream()
                .collect(Collectors.groupingBy(e -> e.getOrderItem().getId(),
                        Collectors.mapping(e -> e.getFacility().getId(), Collectors.toSet())));

        Map<Long, OrderItem> remaining = new LinkedHashMap<>();
        items.forEach(i -> remaining.put(i.getId(), i));
        List<RoutingDecision> decisions = new ArrayList<>();
        int placed = 0;
        LocalDate today = LocalDate.now();

        for (RoutingPlan.RuleStep rule : routing.rules()) {
            List<Facility> facilities = facilityGroupRepository.findById(rule.facilityGroupId())
                    .map(g -> g.getFacilities().stream()
                            .filter(f -> f.isActive() && !f.isQueue())
                            .sorted(Comparator.comparing(Facility::getId)).toList())
                    .orElse(List.of());
            List<ItemRequest> requests = remaining.values().stream()
                    .map(i -> new ItemRequest(i.getId(), i.getProduct().getId(), i.getProduct().getSku(), i.getQuantity(),
                            exclusions.getOrDefault(i.getId(), Set.of())))
                    .toList();

            RuleResult result = engine.evaluate(rule.config(), shipTo, requests,
                    candidates(facilities, requests, today), transitMap(rule.carrier(), zip3, facilities));

            // Reserve stock for what the rule placed
            Map<Long, Facility> facilityById = facilities.stream().collect(Collectors.toMap(Facility::getId, f -> f));
            Set<Long> facilitiesUsed = new LinkedHashSet<>();
            for (Assignment a : result.assignments()) {
                OrderItem item = remaining.remove(a.itemId());
                Facility facility = facilityById.get(a.facilityId());
                allocationService.allocate(item, facility);
                facilitiesUsed.add(facility.getId());
                decisions.add(decision(order, item, plan, routing, rule, runId, DecisionOutcome.ALLOCATED,
                        facility, a.reason(), a.transitDays(), a.distanceMiles(), zip3));
                placed++;
            }
            facilitiesUsed.forEach(id -> allocationService.countOrder(facilityById.get(id), today));

            if (remaining.isEmpty()) {
                break;
            }

            // Apply the rule's action to items it could not place
            String reason = "Rule '" + rule.name() + "': " + result.unplacedReason();
            if (rule.failAction() == FailAction.MOVE_TO_QUEUE) {
                Facility queue = facilityRepository.findById(rule.moveToQueueId()).orElseThrow();
                for (OrderItem item : remaining.values()) {
                    item.setFacility(queue);
                    item.setLastQueue(queue);
                    decisions.add(decision(order, item, plan, routing, rule, runId, DecisionOutcome.MOVED_TO_QUEUE,
                            queue, reason + " -> moved to " + queue.getCode(), null, null, zip3));
                }
                remaining.clear();
                break;
            }
            for (OrderItem item : remaining.values()) {
                String itemReason = reason;
                if (rule.failAction() == FailAction.AUTO_CANCEL && rule.autoCancelDays() != null) {
                    if (item.getAutoCancelDate() == null) {
                        item.setAutoCancelDate(today.plusDays(rule.autoCancelDays()));
                    }
                    itemReason += " (auto-cancel on " + item.getAutoCancelDate() + ")";
                }
                decisions.add(decision(order, item, plan, routing, rule, runId, DecisionOutcome.NOT_ROUTED,
                        null, itemReason, null, null, zip3));
            }
        }

        if (!dryRun) {
            decisionRepository.saveAll(decisions);
        }
        return new Result(items.size(), placed, decisions);
    }

    private List<FacilityCandidate> candidates(List<Facility> facilities, List<ItemRequest> requests, LocalDate today) {
        if (facilities.isEmpty()) {
            return List.of();
        }
        List<Long> facilityIds = facilities.stream().map(Facility::getId).toList();
        Set<Long> productIds = requests.stream().map(ItemRequest::productId).collect(Collectors.toSet());

        Map<Long, Map<Long, Stock>> stock = new HashMap<>();
        for (Inventory inv : inventoryRepository.findForFacilitiesAndProducts(facilityIds, productIds)) {
            stock.computeIfAbsent(inv.getFacility().getId(), k -> new HashMap<>())
                    .put(inv.getProduct().getId(), new Stock(inv.getAvailable(), inv.isAllowBrokering()));
        }
        Map<Long, Integer> ordersToday = dailyCountRepository.findByFacilityIdInAndDate(facilityIds, today).stream()
                .collect(Collectors.toMap(c -> c.getFacility().getId(), FacilityDailyCount::getOrderCount));

        return facilities.stream().map(f -> new FacilityCandidate(f.getId(), f.getCode(), f.getLatitude(),
                f.getLongitude(), GeoUtils.zip3(f.getPostalCode()),
                f.getDailyCapacity() == null ? null : f.getDailyCapacity() - ordersToday.getOrDefault(f.getId(), 0),
                stock.getOrDefault(f.getId(), Map.of()))).toList();
    }

    private Map<String, Integer> transitMap(String carrier, String destZip3, List<Facility> facilities) {
        Set<String> origins = facilities.stream().map(f -> GeoUtils.zip3(f.getPostalCode()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (destZip3 == null || origins.isEmpty()) {
            return Map.of();
        }
        return transitRepository.findByCarrierAndDestZip3AndOriginZip3In(carrier, destZip3, origins).stream()
                .collect(Collectors.toMap(CarrierTransit::getOriginZip3, CarrierTransit::getTransitDays, (a, b) -> a));
    }

    private RoutingDecision decision(SalesOrder order, OrderItem item, RoutingPlan plan, RoutingPlan.RoutingStep routing,
                                     RoutingPlan.RuleStep rule, Long runId, DecisionOutcome outcome, Facility facility,
                                     String reason, Integer transitDays, Double distance, String zip3) {
        RoutingDecision d = new RoutingDecision();
        d.setRunId(runId);
        d.setOrderId(order.getId());
        d.setOrderNumber(order.getOrderNumber());
        d.setOrderItemId(item.getId());
        d.setProductSku(item.getProduct().getSku());
        d.setQuantity(item.getQuantity());
        d.setRoutingGroupId(plan.groupId());
        d.setRoutingId(routing.routingId());
        d.setRoutingName(routing.name());
        d.setRuleId(rule.ruleId());
        d.setRuleName(rule.name());
        if (facility != null) {
            d.setFacilityId(facility.getId());
            d.setFacilityCode(facility.getCode());
        }
        d.setOutcome(outcome);
        d.setReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        d.setTransitDays(transitDays);
        d.setDistanceMiles(distance);
        d.setShipToZip3(zip3);
        return d;
    }
}
