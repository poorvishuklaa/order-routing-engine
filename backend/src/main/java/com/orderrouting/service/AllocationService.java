package com.orderrouting.service;

import com.orderrouting.entity.*;
import com.orderrouting.exception.InsufficientStockException;
import com.orderrouting.repository.FacilityDailyCountRepository;
import com.orderrouting.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Reserves and releases stock. Must run inside the caller's transaction.
 *
 * Overselling protection:
 * 1. The stock is re-checked right before reserving.
 * 2. Inventory has a @Version column. If another transaction changed the same
 *    row after we read it, the commit fails with an optimistic lock exception
 *    and the caller retries with fresh data instead of overselling.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AllocationService {

    private final InventoryRepository inventoryRepository;
    private final FacilityDailyCountRepository dailyCountRepository;

    /** Reserve stock for the item at the facility and move the item out of its queue. */
    public void allocate(OrderItem item, Facility facility) {
        Inventory inventory = inventoryRepository.findByFacilityIdAndProductId(facility.getId(), item.getProduct().getId())
                .orElseThrow(() -> new InsufficientStockException("No inventory record for " + item.getProduct().getSku()
                        + " at " + facility.getCode()));
        if (inventory.getAvailable() < item.getQuantity()) {
            throw new InsufficientStockException("Only " + inventory.getAvailable() + " of " + item.getProduct().getSku()
                    + " available at " + facility.getCode());
        }
        inventory.setReserved(inventory.getReserved() + item.getQuantity());

        if (item.getFacility().isQueue()) {
            item.setLastQueue(item.getFacility());
        }
        item.setFacility(facility);
        item.setStatus(OrderItemStatus.ALLOCATED);
        item.setAutoCancelDate(null);
    }

    /** Give the reserved stock back and put the item back in the queue it came from. */
    public void release(OrderItem item, Facility fallbackQueue) {
        Facility facility = item.getFacility();
        inventoryRepository.findByFacilityIdAndProductId(facility.getId(), item.getProduct().getId())
                .ifPresent(inv -> inv.setReserved(Math.max(0, inv.getReserved() - item.getQuantity())));
        item.setFacility(item.getLastQueue() != null ? item.getLastQueue() : fallbackQueue);
        item.setStatus(OrderItemStatus.QUEUED);
    }

    /** Count one more order against the facility's daily capacity. */
    public void countOrder(Facility facility, LocalDate date) {
        FacilityDailyCount count = dailyCountRepository.findByFacilityIdAndDate(facility.getId(), date)
                .orElseGet(() -> dailyCountRepository.save(new FacilityDailyCount(facility, date)));
        count.setOrderCount(count.getOrderCount() + 1);
    }
}
