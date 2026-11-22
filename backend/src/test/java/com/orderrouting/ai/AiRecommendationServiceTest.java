package com.orderrouting.ai;

import com.orderrouting.ai.AiDtos.AiRecommendationDto;
import com.orderrouting.ai.AiDtos.AiRecommendationResponse;
import com.orderrouting.ai.AiDtos.RuleChange;
import com.orderrouting.ai.RecommendationAdvisor.AdvisorResult;
import com.orderrouting.entity.*;
import com.orderrouting.repository.AiRecommendationRepository;
import com.orderrouting.repository.RoutingGroupRepository;
import com.orderrouting.repository.RoutingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** The LLM is mocked: these tests check validation and the approval flow, not the model. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AiRecommendationServiceTest {

    @MockitoBean RecommendationAdvisor advisor;

    @Autowired AiRecommendationService service;
    @Autowired RoutingGroupRepository groupRepository;
    @Autowired RoutingRuleRepository ruleRepository;
    @Autowired AiRecommendationRepository recommendationRepository;
    @Autowired TransactionTemplate tx;

    Long groupId;
    Long warehousesRuleId;   // "Warehouses only", sorts = INVENTORY
    Long nearbyRuleId;       // "Nearby stores", max distance = 100

    @BeforeEach
    void setUp() {
        when(advisor.isConfigured()).thenReturn(true);
        when(advisor.modelName()).thenReturn("test-model");
        groupId = groupRepository.findAll().stream().filter(g -> g.getName().equals("US Orders"))
                .findFirst().orElseThrow().getId();
        warehousesRuleId = ruleId("Warehouses only");
        nearbyRuleId = ruleId("Nearby stores");
    }

    private Long ruleId(String name) {
        return ruleRepository.findAll().stream().filter(r -> r.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    private void aiReturns(RuleChange... changes) {
        AiRecommendationResponse response = new AiRecommendationResponse("Prioritize transit time",
                "West coast orders go to NJ (4.6 days avg).", "Faster delivery", List.of(changes));
        when(advisor.recommend(anyLong())).thenReturn(new AdvisorResult(response, List.of("getRoutingStats(7)")));
    }

    @Test
    void validRecommendationIsPendingAndApprovalUpdatesRules() {
        aiReturns(new RuleChange(warehousesRuleId, ChangeableField.FACILITY_SORTS, "INVENTORY", "TRANSIT_TIME,INVENTORY"),
                new RuleChange(nearbyRuleId, ChangeableField.MAX_DISTANCE_MILES, "100", "150"));

        AiRecommendationDto rec = service.generate(groupId, "admin");
        assertThat(rec.status()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(rec.changes()).hasSize(2);
        assertThat(rec.toolCalls()).containsExactly("getRoutingStats(7)");

        // Nothing changes before approval
        tx.executeWithoutResult(s -> assertThat(ruleRepository.findById(warehousesRuleId).orElseThrow()
                .getFacilitySorts()).containsExactly(FacilitySort.INVENTORY));

        AiRecommendationDto approved = service.approve(rec.id(), "admin", "looks good");
        assertThat(approved.status()).isEqualTo(RecommendationStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo("admin");

        tx.executeWithoutResult(s -> {
            assertThat(ruleRepository.findById(warehousesRuleId).orElseThrow().getFacilitySorts())
                    .containsExactly(FacilitySort.TRANSIT_TIME, FacilitySort.INVENTORY);
            assertThat(ruleRepository.findById(nearbyRuleId).orElseThrow().getMaxDistanceMiles()).isEqualTo(150);
        });
    }

    @Test
    void wrongOldValueFailsValidation() {
        aiReturns(new RuleChange(nearbyRuleId, ChangeableField.MAX_DISTANCE_MILES, "50", "150"));
        AiRecommendationDto rec = service.generate(groupId, "admin");
        assertThat(rec.status()).isEqualTo(RecommendationStatus.FAILED);
        assertThat(rec.errorMessage()).contains("does not match current value '100'");
    }

    @Test
    void unknownRuleAndInvalidValueFailValidation() {
        aiReturns(new RuleChange(99999L, ChangeableField.SAFETY_STOCK, "0", "5"),
                new RuleChange(warehousesRuleId, ChangeableField.FACILITY_SORTS, "INVENTORY", "FASTEST"));
        AiRecommendationDto rec = service.generate(groupId, "admin");
        assertThat(rec.status()).isEqualTo(RecommendationStatus.FAILED);
        assertThat(rec.errorMessage()).contains("rule does not exist").contains("Invalid value 'FASTEST'");
        // Validation never changes the real rule
        tx.executeWithoutResult(s -> assertThat(ruleRepository.findById(warehousesRuleId).orElseThrow()
                .getFacilitySorts()).containsExactly(FacilitySort.INVENTORY));
    }

    @Test
    void outOfRangeValueFailsWithSameRulesAsManualSave() {
        aiReturns(new RuleChange(nearbyRuleId, ChangeableField.MAX_DISTANCE_MILES, "100", "99999"));
        AiRecommendationDto rec = service.generate(groupId, "admin");
        assertThat(rec.status()).isEqualTo(RecommendationStatus.FAILED);
        assertThat(rec.errorMessage()).contains("max distance must be between 1 and 5000 miles");
    }

    @Test
    void approvalFailsIfRuleChangedAfterRecommendation() {
        aiReturns(new RuleChange(nearbyRuleId, ChangeableField.MAX_DISTANCE_MILES, "100", "150"));
        AiRecommendationDto rec = service.generate(groupId, "admin");

        tx.executeWithoutResult(s -> ruleRepository.findById(nearbyRuleId).orElseThrow().setMaxDistanceMiles(80));

        assertThatThrownBy(() -> service.approve(rec.id(), "admin", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was changed after this recommendation");
        assertThat(recommendationRepository.findById(rec.id()).orElseThrow().getStatus())
                .isEqualTo(RecommendationStatus.PENDING);
    }

    @Test
    void rejectLeavesRulesUnchanged() {
        aiReturns(new RuleChange(nearbyRuleId, ChangeableField.MAX_DISTANCE_MILES, "100", "150"));
        AiRecommendationDto rec = service.generate(groupId, "admin");
        AiRecommendationDto rejected = service.reject(rec.id(), "admin", "not now");
        assertThat(rejected.status()).isEqualTo(RecommendationStatus.REJECTED);
        tx.executeWithoutResult(s -> assertThat(ruleRepository.findById(nearbyRuleId).orElseThrow()
                .getMaxDistanceMiles()).isEqualTo(100));
    }
}
