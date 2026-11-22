package com.orderrouting.repository;

import com.orderrouting.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FacilityGroupRepository extends JpaRepository<FacilityGroup, Long> {

    Optional<FacilityGroup> findByCode(String code);
}
