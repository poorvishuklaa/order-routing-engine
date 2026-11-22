package com.orderrouting.dto;

import com.orderrouting.entity.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderSummary(Long id, String orderNumber, String customerName, String shipToPostalCode,
                               ShippingMethod shippingMethod, OrderPriority priority, SalesChannel salesChannel,
                               OrderStatus status, LocalDateTime orderDate, int itemCount, int allocatedCount) {
    }

    public record OrderItemDto(Long id, Long productId, String sku, String productName, int quantity,
                               Long facilityId, String facilityCode, FacilityType facilityType,
                               OrderItemStatus status, LocalDate autoCancelDate) {
    }

    public record OrderDetail(OrderSummary order, List<OrderItemDto> items) {
    }
}
