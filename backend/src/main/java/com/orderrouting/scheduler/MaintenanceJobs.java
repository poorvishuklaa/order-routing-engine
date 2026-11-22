package com.orderrouting.scheduler;

import com.orderrouting.entity.OrderItem;
import com.orderrouting.entity.OrderItemStatus;
import com.orderrouting.repository.OrderItemRepository;
import com.orderrouting.repository.RoutingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Background housekeeping jobs. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceJobs {

    private final RoutingRunRepository runRepository;
    private final OrderItemRepository orderItemRepository;

    /** Every night at 2 AM: delete run history older than 30 days. */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldRuns() {
        int deleted = runRepository.deleteOlderThan(LocalDateTime.now().minusDays(30));
        log.info("Deleted {} old routing runs", deleted);
    }

    /** Every hour: cancel queued items whose auto-cancel date has passed. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void autoCancelExpiredItems() {
        LocalDate today = LocalDate.now();
        Specification<OrderItem> expired = (root, q, cb) -> cb.and(
                cb.equal(root.get("status"), OrderItemStatus.QUEUED),
                cb.lessThan(root.get("autoCancelDate"), today));
        List<OrderItem> items = orderItemRepository.findAll(expired);
        items.forEach(i -> i.setStatus(OrderItemStatus.CANCELLED));
        if (!items.isEmpty()) {
            log.info("Auto-cancelled {} order items", items.size());
        }
    }
}
