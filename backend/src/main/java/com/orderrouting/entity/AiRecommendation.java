package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A rule-change suggestion from the AI. Applied to rules only after an admin approves it. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long routingGroupId;

    private String title;

    @Column(length = 4000)
    private String reason;

    @Column(length = 1000)
    private String expectedImpact;

    /** JSON array of RuleChange objects. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String changesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;

    @Column(length = 2000)
    private String errorMessage;

    /** Tools the AI called while investigating, in order. */
    @Column(length = 1000)
    private String toolCalls;

    private String model;
    private String createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private String reviewedBy;
    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewNote;
}
