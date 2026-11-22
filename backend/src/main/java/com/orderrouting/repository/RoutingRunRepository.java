package com.orderrouting.repository;

import com.orderrouting.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface RoutingRunRepository extends JpaRepository<RoutingRun, Long> {

    Page<RoutingRun> findByRoutingGroupIdOrderByStartedAtDesc(Long routingGroupId, Pageable pageable);

    Page<RoutingRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<RoutingRun> findByStatus(RunStatus status);

    @Modifying
    @Query("delete from RoutingRun r where r.startedAt < :before")
    int deleteOlderThan(LocalDateTime before);
}
