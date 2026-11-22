package com.orderrouting.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A database lock row so only one routing run happens at a time, even with
 * several app instances. Two runs at once could give the same stock to two orders.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RoutingLock {

    @Id
    private String name;

    private boolean locked;
    private LocalDateTime lockedAt;
    private String owner;

    public RoutingLock(String name) {
        this.name = name;
    }
}
