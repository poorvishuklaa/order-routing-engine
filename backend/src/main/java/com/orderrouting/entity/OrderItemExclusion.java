package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A facility that must not be used for an order item again (e.g. it rejected the item). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"order_item_id", "facility_id"}))
@Getter
@Setter
@NoArgsConstructor
public class OrderItemExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private OrderItem orderItem;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Facility facility;

    private String reason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public OrderItemExclusion(OrderItem orderItem, Facility facility, String reason) {
        this.orderItem = orderItem;
        this.facility = facility;
        this.reason = reason;
    }
}
