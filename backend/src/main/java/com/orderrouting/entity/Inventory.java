package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stock of one product at one facility.
 * available = onHand - reserved - minStock
 * The @Version column enables optimistic locking so two concurrent
 * reservations can never both succeed on stale data (prevents overselling).
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"facility_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Facility facility;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Product product;

    private int onHand;
    private int reserved;

    /** Units the facility must keep and never route. */
    private int minStock;

    /** If false, routing skips this product at this facility. */
    private boolean allowBrokering = true;

    @Version
    private Long version;

    public Inventory(Facility facility, Product product, int onHand, int minStock) {
        this.facility = facility;
        this.product = product;
        this.onHand = onHand;
        this.minStock = minStock;
    }

    public int getAvailable() {
        return Math.max(0, onHand - reserved - minStock);
    }
}
