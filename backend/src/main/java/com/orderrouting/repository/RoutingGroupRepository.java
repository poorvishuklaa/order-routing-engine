package com.orderrouting.repository;

import com.orderrouting.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoutingGroupRepository extends JpaRepository<RoutingGroup, Long> {

    List<RoutingGroup> findByStatus(GroupStatus status);
}
