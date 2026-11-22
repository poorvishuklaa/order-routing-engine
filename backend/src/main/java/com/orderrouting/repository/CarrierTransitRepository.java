package com.orderrouting.repository;

import com.orderrouting.entity.CarrierTransit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CarrierTransitRepository extends JpaRepository<CarrierTransit, Long> {

    List<CarrierTransit> findByCarrierAndDestZip3AndOriginZip3In(String carrier, String destZip3, Collection<String> originZip3s);
}
