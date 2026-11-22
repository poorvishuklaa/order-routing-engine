package com.orderrouting.service;

import com.orderrouting.entity.*;
import com.orderrouting.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end run of the sample routing group against the sample data. */
@SpringBootTest
class RoutingExecutionServiceTest {

    @Autowired RoutingExecutionService executionService;
    @Autowired RoutingGroupRepository groupRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired RoutingDecisionRepository decisionRepository;
    @Autowired TransactionTemplate tx;

    @Test
    void runRoutesOrdersWithoutOverselling() {
        Long groupId = groupRepository.findAll().stream()
                .filter(g -> g.getName().equals("US Orders")).findFirst().orElseThrow().getId();

        RoutingRun run = executionService.runGroup(groupId, RunTrigger.MANUAL);

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getItemsTried()).isPositive();
        assertThat(run.getItemsPlaced()).isPositive();
        assertThat(run.getItemsPlaced()).isLessThanOrEqualTo(run.getItemsTried());

        // No facility gave away more than it had
        for (Inventory inv : inventoryRepository.findAll()) {
            assertThat(inv.getReserved() + inv.getMinStock())
                    .as("inventory %s", inv.getId())
                    .isLessThanOrEqualTo(Math.max(inv.getOnHand(), inv.getMinStock()));
        }

        tx.executeWithoutResult(s -> {
            List<OrderItem> items = orderItemRepository.findAll();
            for (OrderItem item : items) {
                if (item.getStatus() == OrderItemStatus.ALLOCATED) {
                    assertThat(item.getFacility().isQueue()).isFalse();
                } else {
                    assertThat(item.getFacility().isQueue()).isTrue();
                }
            }
        });

        List<RoutingDecision> decisions = decisionRepository.findAll().stream()
                .filter(d -> run.getId().equals(d.getRunId())).toList();
        assertThat(decisions).isNotEmpty();
        assertThat(decisions.stream().filter(d -> d.getOutcome() == DecisionOutcome.ALLOCATED).count())
                .isEqualTo(run.getItemsPlaced());
        assertThat(decisions).allSatisfy(d -> assertThat(d.getReason()).isNotBlank());

        // A second run has nothing new to place
        RoutingRun second = executionService.runGroup(groupId, RunTrigger.MANUAL);
        assertThat(second.getItemsPlaced()).isZero();
    }
}
