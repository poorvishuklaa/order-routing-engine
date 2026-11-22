package com.orderrouting.repository;

import com.orderrouting.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {

    List<RoutingRule> findByFacilityGroupId(Long facilityGroupId);
}
