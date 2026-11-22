package com.orderrouting.service;

import com.orderrouting.entity.*;
import com.orderrouting.repository.*;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RerouteAndTestDriveTest {

    @Autowired RoutingExecutionService executionService;
    @Autowired RerouteService rerouteService;
    @Autowired TestDriveService testDriveService;
    @Autowired RoutingGroupRepository groupRepository;
    @Autowired OrderItemRepository itemRepository;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired OrderItemExclusionRepository exclusionRepository;
    @Autowired TransactionTemplate tx;

    private Long groupId() {
        return groupRepository.findAll().stream().filter(g -> g.getName().equals("US Orders"))
                .findFirst().orElseThrow().getId();
    }

    @Test
    @Order(1)
    void testDriveDoesNotChangeData() {
        Long orderId = tx.execute(s -> itemRepository.findAll().stream()
                .filter(i -> i.getStatus() == OrderItemStatus.QUEUED && i.getFacility().isQueue())
                .findFirst().orElseThrow().getOrder().getId());
        int reservedBefore = inventoryRepository.findAll().stream().mapToInt(Inventory::getReserved).sum();

        TestDriveService.TestDriveResult result = testDriveService.testDrive(groupId(), orderId);

        assertThat(result.matchedAnyRouting()).isTrue();
        assertThat(result.steps()).isNotEmpty();
        assertThat(inventoryRepository.findAll().stream().mapToInt(Inventory::getReserved).sum()).isEqualTo(reservedBefore);
        boolean stillQueued = tx.execute(s -> itemRepository.findAll().stream()
                .filter(i -> i.getOrder().getId().equals(orderId))
                .anyMatch(i -> i.getFacility().isQueue()));
        assertThat(stillQueued).isTrue();
    }

    @Test
    @Order(2)
    void rejectedItemIsReroutedToAnotherFacility() {
        executionService.runGroup(groupId(), RunTrigger.MANUAL);
        Object[] picked = tx.execute(s -> itemRepository.findAll().stream()
                .filter(i -> i.getStatus() == OrderItemStatus.ALLOCATED)
                .map(i -> new Object[]{i.getId(), i.getFacility().getCode()})
                .findFirst().orElseThrow());
        Long itemId = (Long) picked[0];
        String firstFacility = (String) picked[1];

        RerouteService.RerouteResult result = rerouteService.rejectAndReroute(itemId, "Damaged stock");

        assertThat(result.rejectedBy()).isEqualTo(firstFacility);
        assertThat(result.rerouted()).isTrue();
        assertThat(result.facilityCode()).isNotEqualTo(firstFacility);
        assertThat(result.decisions()).extracting(RoutingDecision::getOutcome).contains(DecisionOutcome.REJECTED);
        assertThat(exclusionRepository.existsByOrderItemIdAndFacilityId(itemId,
                result.decisions().stream().filter(d -> d.getOutcome() == DecisionOutcome.REJECTED)
                        .findFirst().orElseThrow().getFacilityId())).isTrue();
    }
}
