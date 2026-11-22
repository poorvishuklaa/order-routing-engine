package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer order. Named SalesOrder because ORDER is a reserved SQL word.
 * Simplification: one ship group per order (one address, one shipping method).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String orderNumber;

    private String customerName;

    @Column(nullable = false, length = 10)
    private String shipToPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShippingMethod shippingMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderPriority priority = OrderPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesChannel salesChannel = SalesChannel.WEB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.APPROVED;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
