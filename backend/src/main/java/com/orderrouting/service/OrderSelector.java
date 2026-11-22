package com.orderrouting.service;

import com.orderrouting.entity.*;
import com.orderrouting.repository.SalesOrderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the orders a routing should try, in the routing's sort order.
 * Filters are built with JPA Criteria (no SQL strings), always combined with AND.
 */
@Component
@RequiredArgsConstructor
public class OrderSelector {

    private final SalesOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<Long> findOrderIds(RoutingPlan.RoutingStep routing) {
        List<SalesOrder> orders = orderRepository.findAll(matching(routing));
        return orders.stream()
                .sorted(comparator(routing.orderSortBy()))
                .map(SalesOrder::getId)
                .toList();
    }

    /** True if this order matches the routing's order filters (used when re-routing a single order). */
    @Transactional(readOnly = true)
    public boolean matches(RoutingPlan.RoutingStep routing, Long orderId) {
        Specification<SalesOrder> byId = (root, q, cb) -> cb.equal(root.get("id"), orderId);
        return orderRepository.count(matching(routing).and(byId)) > 0;
    }

    static Specification<SalesOrder> matching(RoutingPlan.RoutingStep routing) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<SalesOrder, OrderItem> item = root.join("items");
            Join<OrderItem, Facility> facility = item.join("facility");
            List<Predicate> p = new ArrayList<>();
            // Always-on conditions
            p.add(cb.equal(root.get("status"), OrderStatus.APPROVED));
            p.add(cb.equal(item.get("status"), OrderItemStatus.QUEUED));
            p.add(cb.equal(facility.get("type"), FacilityType.QUEUE));
            // Routing filters (empty = no restriction)
            if (routing.queueFacilityId() != null) {
                p.add(cb.equal(facility.get("id"), routing.queueFacilityId()));
            }
            if (!routing.shippingMethods().isEmpty()) {
                p.add(root.get("shippingMethod").in(routing.shippingMethods()));
            }
            if (!routing.priorities().isEmpty()) {
                p.add(root.get("priority").in(routing.priorities()));
            }
            if (!routing.salesChannels().isEmpty()) {
                p.add(root.get("salesChannel").in(routing.salesChannels()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    static Comparator<SalesOrder> comparator(OrderSortBy sortBy) {
        Comparator<SalesOrder> byDate = Comparator.comparing(SalesOrder::getOrderDate).thenComparing(SalesOrder::getId);
        return switch (sortBy == null ? OrderSortBy.ORDER_DATE : sortBy) {
            case ORDER_DATE -> byDate;
            case PRIORITY -> Comparator.comparing(SalesOrder::getPriority).thenComparing(byDate);
            case SHIPPING_METHOD_SPEED -> Comparator.comparing(SalesOrder::getShippingMethod).thenComparing(byDate);
        };
    }
}
