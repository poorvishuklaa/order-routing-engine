package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit trail: what happened to an order item when a rule tried it, and why.
 * Used by the dashboard and as the main data source for AI recommendations.
 * Stores ids (not relations) so history survives config changes.
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_decision_created", columnList = "createdAt"),
        @Index(name = "idx_decision_item", columnList = "orderItemId")
})
@Getter
@Setter
@NoArgsConstructor
public class RoutingDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;
    private Long orderId;
    private String orderNumber;
    private Long orderItemId;
    private String productSku;
    private int quantity;

    private Long routingGroupId;
    private Long routingId;
    private String routingName;
    private Long ruleId;
    private String ruleName;

    /** Chosen facility (null when not routed). */
    private Long facilityId;
    private String facilityCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionOutcome outcome;

    @Column(length = 500)
    private String reason;

    private Integer transitDays;
    private Double distanceMiles;
    private String shipToZip3;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
