package com.orderrouting.controller;

import com.orderrouting.insights.InsightDtos.DashboardData;
import com.orderrouting.insights.RoutingInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final RoutingInsightsService insightsService;

    @GetMapping
    public DashboardData dashboard(@RequestParam(required = false) Long groupId,
                                   @RequestParam(defaultValue = "7") int days) {
        return insightsService.dashboard(groupId, Math.max(1, Math.min(days, 90)));
    }
}
