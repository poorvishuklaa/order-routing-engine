package com.orderrouting.service;

import com.orderrouting.entity.RunTrigger;
import com.orderrouting.insights.InsightDtos.*;
import com.orderrouting.insights.RoutingInsightsService;
import com.orderrouting.repository.RoutingGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoutingInsightsServiceTest {

    @Autowired RoutingInsightsService insights;
    @Autowired RoutingExecutionService executionService;
    @Autowired RoutingGroupRepository groupRepository;

    @Test
    void summarizesRoutingResults() {
        Long groupId = groupRepository.findAll().stream().filter(g -> g.getName().equals("US Orders"))
                .findFirst().orElseThrow().getId();
        executionService.runGroup(groupId, RunTrigger.MANUAL);

        RoutingStats stats = insights.routingStats(groupId, 7);
        assertThat(stats.itemsAttempted()).isPositive();
        assertThat(stats.itemsAllocated()).isPositive();
        assertThat(stats.averageTransitDays()).isNotNull();
        assertThat(stats.allocationsByFacility()).isNotEmpty();

        List<UnroutedReason> reasons = insights.unroutedReasons(groupId, 7);
        assertThat(reasons).allSatisfy(r -> assertThat(r.reason()).doesNotStartWith("Rule '"));

        GroupConfigSummary config = insights.groupConfig(groupId);
        assertThat(config.routings()).hasSize(2);
        assertThat(config.routings().get(1).rules().get(0).facilitySorts()).isEqualTo("INVENTORY");

        assertThat(insights.demandByRegion(7)).isNotEmpty();
        assertThat(insights.capacityUsage()).hasSize(8);
    }
}
