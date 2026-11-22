package com.orderrouting.repository;

import com.orderrouting.entity.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    @EntityGraph(attributePaths = {"items", "items.product", "items.facility"})
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findWithItems(Long id);

    Page<SalesOrder> findByOrderNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCase(
            String orderNumber, String customerName, Pageable pageable);
}
