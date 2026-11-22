package com.orderrouting.repository;

import com.orderrouting.entity.RoutingLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface RoutingLockRepository extends JpaRepository<RoutingLock, String> {

    /** Atomic "take the lock if free or stale". Returns 1 when the lock was taken. */
    @Modifying
    @Query("update RoutingLock l set l.locked = true, l.lockedAt = :now, l.owner = :owner " +
            "where l.name = :name and (l.locked = false or l.lockedAt < :staleBefore)")
    int tryAcquire(String name, String owner, LocalDateTime now, LocalDateTime staleBefore);

    @Modifying
    @Query("update RoutingLock l set l.locked = false, l.owner = null where l.name = :name and l.owner = :owner")
    int release(String name, String owner);
}
