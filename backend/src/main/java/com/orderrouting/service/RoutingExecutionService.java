package com.orderrouting.service;

import com.orderrouting.entity.*;
import com.orderrouting.exception.NotFoundException;
import com.orderrouting.repository.RoutingGroupRepository;
import com.orderrouting.repository.RoutingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Runs a routing group: for each active routing (in sequence), finds matching
 * orders and routes them one by one. Each order is its own transaction, so one
 * bad order is logged and skipped without undoing the rest of the run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingExecutionService {

    private final RoutingGroupRepository groupRepository;
    private final RoutingRunRepository runRepository;
    private final OrderSelector orderSelector;
    private final OrderRouter orderRouter;
    private final TransactionTemplate transactionTemplate;
    private final RoutingLockService lockService;

    /**
     * Runs the group if no other run is in progress. If the lock is taken the
     * run is recorded as SKIPPED instead of waiting, so runs never pile up.
     */
    public RoutingRun runGroup(Long groupId, RunTrigger trigger) {
        RoutingPlan plan = loadPlan(groupId);
        Optional<String> lock = lockService.tryAcquire();
        if (lock.isEmpty()) {
            RoutingRun skipped = startRun(groupId, trigger);
            skipped.setStatus(RunStatus.SKIPPED);
            skipped.setFinishedAt(LocalDateTime.now());
            skipped.setErrorMessage("Skipped: another routing run is still in progress");
            return runRepository.save(skipped);
        }
        try {
            return execute(plan, groupId, trigger);
        } finally {
            lockService.release(lock.get());
        }
    }

    private RoutingRun execute(RoutingPlan plan, Long groupId, RunTrigger trigger) {
        RoutingRun run = startRun(groupId, trigger);
        int tried = 0;
        int placed = 0;
        List<String> errors = new java.util.ArrayList<>();
        try {
            for (RoutingPlan.RoutingStep routing : plan.routings()) {
                for (Long orderId : orderSelector.findOrderIds(routing)) {
                    try {
                        OrderRouter.Result result = orderRouter.route(orderId, plan, routing, run.getId(), false);
                        tried += result.itemsConsidered();
                        placed += result.itemsPlaced();
                    } catch (Exception e) {
                        log.error("Failed to route order {} in routing {}", orderId, routing.name(), e);
                        if (errors.size() < 5) {
                            errors.add("Order " + orderId + ": " + e.getMessage());
                        }
                    }
                }
            }
            run.setStatus(RunStatus.COMPLETED);
        } catch (Exception e) {
            log.error("Routing run {} failed", run.getId(), e);
            run.setStatus(RunStatus.FAILED);
            errors.add(0, e.getMessage());
        }
        run.setItemsTried(tried);
        run.setItemsPlaced(placed);
        run.setFinishedAt(LocalDateTime.now());
        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            run.setErrorMessage(message.length() > 2000 ? message.substring(0, 2000) : message);
        }
        return runRepository.save(run);
    }

    /**
     * Routes one order right away through every ACTIVE group (used after a facility
     * rejects an item). If another run holds the lock, the item simply waits in
     * its queue for the next scheduled run.
     */
    public Optional<RoutingRun> rerouteOrder(Long orderId) {
        Optional<String> lock = lockService.tryAcquire();
        if (lock.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<RoutingPlan> plans = transactionTemplate.execute(status -> groupRepository
                    .findByStatus(GroupStatus.ACTIVE).stream()
                    .sorted(java.util.Comparator.comparing(RoutingGroup::getId))
                    .map(g -> loadPlan(g.getId())).toList());
            RoutingRun run = startRun(plans.isEmpty() ? null : plans.get(0).groupId(), RunTrigger.REROUTE);
            int tried = 0;
            int placed = 0;
            for (RoutingPlan plan : plans) {
                for (RoutingPlan.RoutingStep routing : plan.routings()) {
                    if (orderSelector.matches(routing, orderId)) {
                        OrderRouter.Result result = orderRouter.route(orderId, plan, routing, run.getId(), false);
                        tried += result.itemsConsidered();
                        placed += result.itemsPlaced();
                    }
                }
            }
            run.setItemsTried(tried);
            run.setItemsPlaced(placed);
            run.setStatus(RunStatus.COMPLETED);
            run.setFinishedAt(LocalDateTime.now());
            return Optional.of(runRepository.save(run));
        } finally {
            lockService.release(lock.get());
        }
    }

    /** Reads the group's active routings and rules into a detached plan. */
    public RoutingPlan loadPlan(Long groupId) {
        return transactionTemplate.execute(status -> {
            RoutingGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new NotFoundException("Routing group " + groupId + " not found"));
            group.getRoutings().forEach(r -> r.getRules().forEach(rule -> rule.getFacilityGroup().getId()));
            return RoutingPlan.of(group);
        });
    }

    private RoutingRun startRun(Long groupId, RunTrigger trigger) {
        RoutingRun run = new RoutingRun();
        run.setRoutingGroup(groupId == null ? null : groupRepository.getReferenceById(groupId));
        run.setTriggerType(trigger);
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        return runRepository.save(run);
    }
}
