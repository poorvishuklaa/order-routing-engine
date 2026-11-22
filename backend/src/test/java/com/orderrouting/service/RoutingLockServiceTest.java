package com.orderrouting.service;

import com.orderrouting.entity.RoutingRun;
import com.orderrouting.entity.RunStatus;
import com.orderrouting.entity.RunTrigger;
import com.orderrouting.repository.RoutingGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RoutingLockServiceTest {

    @Autowired RoutingLockService lockService;
    @Autowired RoutingExecutionService executionService;
    @Autowired RoutingGroupRepository groupRepository;

    @Test
    void secondRunIsSkippedWhileLockIsHeld() {
        Optional<String> token = lockService.tryAcquire();
        assertThat(token).isPresent();
        try {
            assertThat(lockService.tryAcquire()).isEmpty();
            Long groupId = groupRepository.findAll().get(0).getId();
            RoutingRun run = executionService.runGroup(groupId, RunTrigger.SCHEDULED);
            assertThat(run.getStatus()).isEqualTo(RunStatus.SKIPPED);
        } finally {
            lockService.release(token.get());
        }
        Optional<String> again = lockService.tryAcquire();
        assertThat(again).isPresent();
        lockService.release(again.get());
    }
}
