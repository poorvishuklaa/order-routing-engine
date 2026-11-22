package com.orderrouting.repository;

import com.orderrouting.entity.RoutingDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface RoutingDecisionRepository extends JpaRepository<RoutingDecision, Long>,
        JpaSpecificationExecutor<RoutingDecision> {

    List<RoutingDecision> findByCreatedAtAfter(LocalDateTime after);

    List<RoutingDecision> findByRoutingGroupIdAndCreatedAtAfter(Long routingGroupId, LocalDateTime after);

    List<RoutingDecision> findByOrderIdOrderByIdAsc(Long orderId);

    Page<RoutingDecision> findAllByOrderByIdDesc(Pageable pageable);
}
