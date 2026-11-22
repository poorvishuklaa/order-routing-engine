package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One line of an order. While waiting, facility points to a QUEUE facility.
 * After routing, facility points to the warehouse/store that will ship it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private SalesOrder order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Product product;

    private int quantity;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Facility facility;

    /** The queue the item was in before it was allocated (used when re-routing). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Facility lastQueue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderItemStatus status = OrderItemStatus.QUEUED;

    private LocalDate autoCancelDate;

    @Version
    private Long version;

    public OrderItem(Product product, int quantity, Facility queue) {
        this.product = product;
        this.quantity = quantity;
        this.facility = queue;
        this.lastQueue = queue;
    }
}
