package com.orderrouting.repository;

import com.orderrouting.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByFacilityIdAndProductId(Long facilityId, Long productId);

    @Query("select i from Inventory i join fetch i.facility join fetch i.product " +
            "where i.facility.id in :facilityIds and i.product.id in :productIds")
    List<Inventory> findForFacilitiesAndProducts(Collection<Long> facilityIds, Collection<Long> productIds);

    @Query("select i from Inventory i join fetch i.facility join fetch i.product order by i.facility.code, i.product.sku")
    List<Inventory> findAllWithDetails();
}
