package com.orderrouting.service;

import com.orderrouting.dto.FacilityDtos.InventoryDto;
import com.orderrouting.dto.OrderDtos.OrderDetail;
import com.orderrouting.dto.OrderDtos.OrderItemDto;
import com.orderrouting.entity.RoutingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test drive: shows where a saved group would route one order, WITHOUT changing data.
 * It runs the real routing code inside a transaction that is always rolled back,
 * so the result matches a real run exactly.
 */
@Service
@RequiredArgsConstructor
public class TestDriveService {

    private final TransactionTemplate transactionTemplate;
    private final RoutingExecutionService executionService;
    private final OrderSelector orderSelector;
    private final OrderRouter orderRouter;
    private final OrderService orderService;
    private final FacilityService facilityService;

    public record TestDriveResult(boolean matchedAnyRouting, String message, List<RoutingDecision> steps,
                                  List<OrderItemDto> resultingItems, List<InventoryDto> stockBefore) {
    }

    public TestDriveResult testDrive(Long groupId, Long orderId) {
        RoutingPlan plan = executionService.loadPlan(groupId);
        return transactionTemplate.execute(status -> {
            OrderDetail before = orderService.get(orderId);
            Set<Long> productIds = before.items().stream().map(OrderItemDto::productId).collect(Collectors.toSet());
            List<InventoryDto> stock = facilityService.listInventory(null).stream()
                    .filter(i -> productIds.contains(i.productId())).toList();

            List<RoutingDecision> steps = new ArrayList<>();
            boolean matched = false;
            for (RoutingPlan.RoutingStep routing : plan.routings()) {
                if (orderSelector.matches(routing, orderId)) {
                    matched = true;
                    steps.addAll(orderRouter.route(orderId, plan, routing, null, true).decisions());
                }
            }
            OrderDetail after = orderService.get(orderId);
            status.setRollbackOnly();

            String message;
            if (!matched) {
                message = "The order does not match the filters of any active routing (or has no queued items)";
            } else if (after.items().stream().allMatch(i -> i.facilityType() != com.orderrouting.entity.FacilityType.QUEUE)) {
                message = "All items would be routed";
            } else {
                message = "Some items would not be routed; see the steps for reasons";
            }
            return new TestDriveResult(matched, message, steps, after.items(), stock);
        });
    }
}
