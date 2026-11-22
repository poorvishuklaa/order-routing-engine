package com.orderrouting.service;

import com.orderrouting.dto.RoutingDtos.CreateGroupRequest;
import com.orderrouting.dto.RoutingDtos.RoutingGroupDto;
import com.orderrouting.entity.GroupStatus;
import com.orderrouting.exception.BadRequestException;
import com.orderrouting.scheduler.RoutingScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RoutingScheduleTest {

    @Autowired RoutingGroupService service;

    @Test
    void cannotActivateWithoutSchedule() {
        RoutingGroupDto group = service.create(new CreateGroupRequest("No schedule", null));
        assertThatThrownBy(() -> service.updateStatus(group.id(), GroupStatus.ACTIVE))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsInvalidCron() {
        RoutingGroupDto group = service.create(new CreateGroupRequest("Bad cron", null));
        assertThatThrownBy(() -> service.updateSchedule(group.id(), "every 5 minutes"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid cron");
    }

    @Test
    void activatesAndPausesWithValidCron() {
        RoutingGroupDto group = service.create(new CreateGroupRequest("Scheduled", null));
        service.updateSchedule(group.id(), "0 0/30 * * * *");
        assertThat(service.updateStatus(group.id(), GroupStatus.ACTIVE).status()).isEqualTo(GroupStatus.ACTIVE);
        assertThat(service.updateStatus(group.id(), GroupStatus.DRAFT).status()).isEqualTo(GroupStatus.DRAFT);
        assertThat(RoutingScheduler.describe("0 0/30 * * * *")).isEqualTo("Every 30 minutes");
    }
}
