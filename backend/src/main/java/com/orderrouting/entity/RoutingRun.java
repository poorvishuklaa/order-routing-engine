package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One execution of a routing group (scheduled, manual or re-route). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RoutingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private RoutingGroup routingGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status = RunStatus.RUNNING;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private int itemsTried;
    private int itemsPlaced;

    @Column(length = 2000)
    private String errorMessage;
}
