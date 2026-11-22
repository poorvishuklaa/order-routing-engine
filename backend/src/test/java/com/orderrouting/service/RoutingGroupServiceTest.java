package com.orderrouting.service;

import com.orderrouting.dto.RoutingDtos.*;
import com.orderrouting.entity.FailAction;
import com.orderrouting.entity.FacilitySort;
import com.orderrouting.entity.ShippingMethod;
import com.orderrouting.exception.BadRequestException;
import com.orderrouting.repository.FacilityGroupRepository;
import com.orderrouting.repository.FacilityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RoutingGroupServiceTest {

    @Autowired RoutingGroupService service;
    @Autowired FacilityGroupRepository facilityGroupRepository;
    @Autowired FacilityRepository facilityRepository;

    private RoutingRuleDto rule(Long id, String name, FailAction action, Long queueId) {
        Long groupId = facilityGroupRepository.findByCode("ALL_WAREHOUSES").orElseThrow().getId();
        return new RoutingRuleDto(id, name, 0, null, groupId, "UPS_GROUND", null, 3, 0, false, false,
                List.of(FacilitySort.TRANSIT_TIME), action, queueId, null);
    }

    @Test
    void savesWholeGroupAndRemovesMissingRules() {
        RoutingGroupDto created = service.create(new CreateGroupRequest("Test group", "desc"));
        RoutingDto routing = new RoutingDto(null, "All orders", 0, null, null,
                Set.of(ShippingMethod.STANDARD), Set.of(), Set.of(), null,
                List.of(rule(null, "R1", FailAction.NEXT_RULE, null), rule(null, "R2", FailAction.NEXT_RULE, null)));
        RoutingGroupDto saved = service.update(created.id(), new RoutingGroupDto(created.id(), "Test group", "desc",
                null, null, created.version(), null, null, List.of(routing)));

        assertThat(saved.routings()).hasSize(1);
        assertThat(saved.routings().get(0).rules()).extracting(RoutingRuleDto::name).containsExactly("R1", "R2");

        // Send back only R2 -> R1 must be deleted
        RoutingDto savedRouting = saved.routings().get(0);
        RoutingRuleDto r2 = savedRouting.rules().get(1);
        RoutingDto changed = new RoutingDto(savedRouting.id(), "All orders", 1, null, null,
                savedRouting.shippingMethods(), Set.of(), Set.of(), null, List.of(r2));
        RoutingGroupDto updated = service.update(created.id(), new RoutingGroupDto(created.id(), "Test group", "desc",
                null, null, saved.version(), null, null, List.of(changed)));

        assertThat(updated.routings().get(0).rules()).extracting(RoutingRuleDto::name).containsExactly("R2");
        assertThat(updated.routings().get(0).rules().get(0).id()).isEqualTo(r2.id());
    }

    @Test
    void rejectsInvalidRule() {
        RoutingGroupDto created = service.create(new CreateGroupRequest("Bad group", null));
        RoutingDto routing = new RoutingDto(null, "All", 0, null, null, Set.of(), Set.of(), Set.of(), null,
                List.of(rule(null, "Move", FailAction.MOVE_TO_QUEUE, null)));
        assertThatThrownBy(() -> service.update(created.id(), new RoutingGroupDto(created.id(), "Bad group", null,
                null, null, created.version(), null, null, List.of(routing))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("MOVE_TO_QUEUE needs a queue facility");
    }

    @Test
    void rejectsStaleVersion() {
        RoutingGroupDto created = service.create(new CreateGroupRequest("Versioned", null));
        assertThatThrownBy(() -> service.update(created.id(), new RoutingGroupDto(created.id(), "Versioned", null,
                null, null, created.version() + 5, null, null, List.of())))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
