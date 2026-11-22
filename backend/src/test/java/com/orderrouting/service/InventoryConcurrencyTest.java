package com.orderrouting.service;

import com.orderrouting.entity.Inventory;
import com.orderrouting.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two transactions read the same inventory row (1 unit available) at the same
 * time and both try to reserve it. Optimistic locking must let only one win.
 */
@SpringBootTest
class InventoryConcurrencyTest {

    @Autowired InventoryRepository inventoryRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void onlyOneConcurrentReservationSucceeds() throws Exception {
        Long id = transactionTemplate.execute(s -> {
            Inventory inv = inventoryRepository.findAll().get(0);
            inv.setOnHand(1);
            inv.setReserved(0);
            inv.setMinStock(0);
            return inv.getId();
        });

        CountDownLatch bothRead = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Void> task = () -> {
            try {
                transactionTemplate.executeWithoutResult(s -> {
                    Inventory inv = inventoryRepository.findById(id).orElseThrow();
                    bothRead.countDown();
                    await(bothRead);
                    if (inv.getAvailable() >= 1) {
                        inv.setReserved(inv.getReserved() + 1);
                    }
                });
                success.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException e) {
                conflicts.incrementAndGet();
            }
            return null;
        };
        pool.invokeAll(java.util.List.of(task, task));
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        Inventory after = inventoryRepository.findById(id).orElseThrow();
        assertThat(after.getReserved()).isEqualTo(1);
        assertThat(after.getAvailable()).isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
