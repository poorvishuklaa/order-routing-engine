package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One attempt to place an order. Decides WHICH facilities are allowed (hard
 * constraints) and HOW to rank them (facility sorts). Rules of a routing are
 * tried one after another until the order is placed.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RoutingRule {

    public static final String DEFAULT_CARRIER = "UPS_GROUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Routing routing;

    @Column(nullable = false)
    private String name;

    private int sequenceNum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoutingStatus status = RoutingStatus.ACTIVE;

    // ----- Facility filters (hard constraints) -----

    /** Required: only facilities in this group are considered. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private FacilityGroup facilityGroup;

    /** Carrier whose ground transit table is used for TRANSIT_TIME. */
    @Column(nullable = false, length = 30)
    private String carrier = DEFAULT_CARRIER;

    private Integer maxDistanceMiles;
    private Integer maxTransitDays;

    /** Facility must still have at least this many units after the order. */
    private int safetyStock;

    /** false = the whole order must ship from one facility. */
    private boolean allowPartialAllocation;

    /** true = ignore the facility's daily capacity. */
    private boolean overrideCapacity;

    // ----- Facility ranking -----

    /** Applied in order. Empty = default (TRANSIT_TIME, DISTANCE). Facility id is the final tie-breaker. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "routing_rule_sort")
    @OrderColumn(name = "sort_order")
    @Column(name = "sort_field")
    private List<FacilitySort> facilitySorts = new ArrayList<>();

    // ----- What to do with items this rule could not place -----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FailAction failAction = FailAction.NEXT_RULE;

    @ManyToOne(fetch = FetchType.LAZY)
    private Facility moveToQueue;

    private Integer autoCancelDays;
}
