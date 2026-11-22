package com.orderrouting.controller;

import com.orderrouting.ai.AiDtos.AiRecommendationDto;
import com.orderrouting.ai.AiDtos.AiStatus;
import com.orderrouting.ai.AiDtos.ReviewRequest;
import com.orderrouting.ai.AiRecommendationService;
import com.orderrouting.entity.RecommendationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final AiRecommendationService service;

    @GetMapping("/status")
    public AiStatus status() {
        return service.status();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recommendations/generate")
    public AiRecommendationDto generate(@RequestParam Long groupId, Authentication auth) {
        return service.generate(groupId, auth.getName());
    }

    @GetMapping("/recommendations")
    public List<AiRecommendationDto> list(@RequestParam(required = false) RecommendationStatus status) {
        return service.list(status);
    }

    @GetMapping("/recommendations/{id}")
    public AiRecommendationDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recommendations/{id}/approve")
    public AiRecommendationDto approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request,
                                       Authentication auth) {
        return service.approve(id, auth.getName(), request == null ? null : request.note());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recommendations/{id}/reject")
    public AiRecommendationDto reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request,
                                      Authentication auth) {
        return service.reject(id, auth.getName(), request == null ? null : request.note());
    }
}
