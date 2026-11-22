package com.orderrouting.controller;

import com.orderrouting.dto.RunDtos.RoutingRunDto;
import com.orderrouting.entity.DecisionOutcome;
import com.orderrouting.entity.RoutingDecision;
import com.orderrouting.entity.RunTrigger;
import com.orderrouting.repository.RoutingDecisionRepository;
import com.orderrouting.service.RoutingExecutionService;
import com.orderrouting.service.RunHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoutingRunController {

    private final RoutingExecutionService executionService;
    private final RunHistoryService historyService;
    private final RoutingDecisionRepository decisionRepository;

    /** Run the group once now. This routes real orders. */
    @PostMapping("/routing-groups/{id}/run")
    public RoutingRunDto runNow(@PathVariable Long id) {
        return historyService.toDto(executionService.runGroup(id, RunTrigger.MANUAL));
    }

    @GetMapping("/routing-runs")
    public Page<RoutingRunDto> runs(@RequestParam(required = false) Long groupId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return historyService.runs(groupId, page, Math.min(size, 100));
    }

    @GetMapping("/routing-decisions")
    public Page<RoutingDecision> decisions(@RequestParam(required = false) String orderNumber,
                                           @RequestParam(required = false) DecisionOutcome outcome,
                                           @RequestParam(required = false) Long ruleId,
                                           @RequestParam(required = false) Long runId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size) {
        return historyService.decisions(orderNumber, outcome, ruleId, runId, page, Math.min(size, 200));
    }

    @GetMapping("/orders/{id}/decisions")
    public List<RoutingDecision> orderDecisions(@PathVariable Long id) {
        return decisionRepository.findByOrderIdOrderByIdAsc(id);
    }
}
