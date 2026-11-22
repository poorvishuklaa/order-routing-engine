package com.orderrouting.repository;

import com.orderrouting.entity.FacilityDailyCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FacilityDailyCountRepository extends JpaRepository<FacilityDailyCount, Long> {

    Optional<FacilityDailyCount> findByFacilityIdAndDate(Long facilityId, LocalDate date);

    List<FacilityDailyCount> findByFacilityIdInAndDate(Collection<Long> facilityIds, LocalDate date);

    List<FacilityDailyCount> findByDate(LocalDate date);
}
