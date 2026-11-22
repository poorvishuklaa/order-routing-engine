package com.orderrouting.service;

import com.orderrouting.dto.OrderDtos.*;
import com.orderrouting.entity.OrderItem;
import com.orderrouting.entity.OrderItemStatus;
import com.orderrouting.entity.SalesOrder;
import com.orderrouting.exception.NotFoundException;
import com.orderrouting.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final SalesOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<OrderSummary> search(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
        Page<SalesOrder> orders = (query == null || query.isBlank())
                ? orderRepository.findAll(pageable)
                : orderRepository.findByOrderNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCase(query, query, pageable);
        return orders.map(this::summary);
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long id) {
        SalesOrder order = orderRepository.findWithItems(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
        return new OrderDetail(summary(order), order.getItems().stream().map(this::item).toList());
    }

    private OrderSummary summary(SalesOrder o) {
        int allocated = (int) o.getItems().stream().filter(i -> i.getStatus() == OrderItemStatus.ALLOCATED).count();
        return new OrderSummary(o.getId(), o.getOrderNumber(), o.getCustomerName(), o.getShipToPostalCode(),
                o.getShippingMethod(), o.getPriority(), o.getSalesChannel(), o.getStatus(), o.getOrderDate(),
                o.getItems().size(), allocated);
    }

    private OrderItemDto item(OrderItem i) {
        return new OrderItemDto(i.getId(), i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                i.getQuantity(), i.getFacility().getId(), i.getFacility().getCode(), i.getFacility().getType(),
                i.getStatus(), i.getAutoCancelDate());
    }
}
