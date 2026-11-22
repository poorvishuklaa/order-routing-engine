package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One step of a routing group. Decides WHICH waiting orders to pick (filters)
 * and in WHAT ORDER to try them (sort). Empty filter = no restriction.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Routing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private RoutingGroup routingGroup;

    @Column(nullable = false)
    private String name;

    private int sequenceNum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoutingStatus status = RoutingStatus.ACTIVE;

    // ----- Order filters -----

    /** Only orders waiting in this queue. Null = any queue. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Facility queueFacility;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "routing_shipping_method")
    @Column(name = "shipping_method")
    private Set<ShippingMethod> shippingMethods = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "routing_priority")
    @Column(name = "priority")
    private Set<OrderPriority> priorities = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "routing_sales_channel")
    @Column(name = "sales_channel")
    private Set<SalesChannel> salesChannels = new LinkedHashSet<>();

    // ----- Order sorting -----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderSortBy orderSortBy = OrderSortBy.ORDER_DATE;

    @OneToMany(mappedBy = "routing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum")
    private List<RoutingRule> rules = new ArrayList<>();

    public void addRule(RoutingRule rule) {
        rule.setRouting(this);
        rules.add(rule);
    }
}
