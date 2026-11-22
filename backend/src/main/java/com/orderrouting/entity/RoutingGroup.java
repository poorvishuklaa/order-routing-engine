package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level routing setup. Runs on its own cron schedule and holds routings
 * that are executed one after another by sequence number.
 * Saving the group cascades to its routings and their rules.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RoutingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Spring 6-field cron, e.g. "0 0/15 * * * *" (every 15 minutes). */
    private String cronExpression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status = GroupStatus.DRAFT;

    @OneToMany(mappedBy = "routingGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNum")
    private List<Routing> routings = new ArrayList<>();

    /** Protects against two admins overwriting each other's edits. */
    @Version
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void addRouting(Routing routing) {
        routing.setRoutingGroup(this);
        routings.add(routing);
    }
}
