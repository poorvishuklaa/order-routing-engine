package com.orderrouting.service;

import com.orderrouting.entity.RoutingLock;
import com.orderrouting.entity.RunStatus;
import com.orderrouting.repository.RoutingLockRepository;
import com.orderrouting.repository.RoutingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Ensures only one routing run at a time. A lock older than one hour is treated
 * as left behind by a crashed run and can be taken over; runs still marked
 * RUNNING at that point are marked FAILED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingLockService {

    static final String LOCK_NAME = "ROUTING";
    private static final int STALE_AFTER_MINUTES = 60;

    private final RoutingLockRepository lockRepository;
    private final RoutingRunRepository runRepository;
    private final TransactionTemplate transactionTemplate;

    /** Returns a lock token if acquired, or empty if another run holds the lock. */
    public Optional<String> tryAcquire() {
        String owner = UUID.randomUUID().toString();
        Boolean acquired = transactionTemplate.execute(status -> {
            if (!lockRepository.existsById(LOCK_NAME)) {
                lockRepository.saveAndFlush(new RoutingLock(LOCK_NAME));
            }
            LocalDateTime now = LocalDateTime.now();
            boolean ok = lockRepository.tryAcquire(LOCK_NAME, owner, now, now.minusMinutes(STALE_AFTER_MINUTES)) == 1;
            if (ok) {
                markUnfinishedRunsFailed();
            }
            return ok;
        });
        return Boolean.TRUE.equals(acquired) ? Optional.of(owner) : Optional.empty();
    }

    public void release(String owner) {
        transactionTemplate.executeWithoutResult(status -> lockRepository.release(LOCK_NAME, owner));
    }

    /** Holding the lock means no run is really in progress, so any RUNNING run was interrupted. */
    private void markUnfinishedRunsFailed() {
        runRepository.findByStatus(RunStatus.RUNNING).forEach(run -> {
            log.warn("Marking unfinished run {} as FAILED", run.getId());
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(LocalDateTime.now());
            run.setErrorMessage("Run did not finish (server stopped or crashed)");
        });
    }
}
