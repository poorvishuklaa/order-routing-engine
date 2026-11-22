package com.orderrouting.repository;

import com.orderrouting.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface OrderItemExclusionRepository extends JpaRepository<OrderItemExclusion, Long> {

    List<OrderItemExclusion> findByOrderItemIdIn(Collection<Long> orderItemIds);

    boolean existsByOrderItemIdAndFacilityId(Long orderItemId, Long facilityId);
}
