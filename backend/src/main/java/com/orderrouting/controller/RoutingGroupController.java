package com.orderrouting.controller;

import com.orderrouting.dto.RoutingDtos.CreateGroupRequest;
import com.orderrouting.dto.RoutingDtos.RoutingGroupDto;
import com.orderrouting.dto.RoutingDtos.RoutingGroupSummary;
import com.orderrouting.entity.GroupStatus;
import com.orderrouting.entity.RoutingGroup;
import com.orderrouting.entity.RoutingRun;
import com.orderrouting.repository.RoutingRunRepository;
import com.orderrouting.scheduler.RoutingScheduler;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import com.orderrouting.service.RoutingGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/routing-groups")
@RequiredArgsConstructor
public class RoutingGroupController {

    private final RoutingGroupService groupService;
    private final RoutingRunRepository runRepository;

    @GetMapping
    public List<RoutingGroupSummary> list() {
        return groupService.findAll().stream()
                .sorted(Comparator.comparing((RoutingGroup g) -> RoutingScheduler.nextRun(g),
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(RoutingGroup::getId))
                .map(this::summary)
                .toList();
    }

    @GetMapping("/{id}")
    public RoutingGroupDto get(@PathVariable Long id) {
        return groupService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutingGroupDto create(@Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(request);
    }

    @PutMapping("/{id}")
    public RoutingGroupDto update(@PathVariable Long id, @Valid @RequestBody RoutingGroupDto dto) {
        return groupService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        groupService.delete(id);
    }

    public record ScheduleRequest(@NotBlank String cronExpression) {
    }

    @PutMapping("/{id}/schedule")
    public RoutingGroupDto schedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return groupService.updateSchedule(id, request.cronExpression().trim());
    }

    @PostMapping("/{id}/activate")
    public RoutingGroupDto activate(@PathVariable Long id) {
        return groupService.updateStatus(id, GroupStatus.ACTIVE);
    }

    @PostMapping("/{id}/pause")
    public RoutingGroupDto pause(@PathVariable Long id) {
        return groupService.updateStatus(id, GroupStatus.DRAFT);
    }

    private RoutingGroupSummary summary(RoutingGroup g) {
        LocalDateTime lastRun = runRepository.findByRoutingGroupIdOrderByStartedAtDesc(g.getId(), PageRequest.of(0, 1))
                .stream().findFirst().map(RoutingRun::getStartedAt).orElse(null);
        return new RoutingGroupSummary(g.getId(), g.getName(), g.getDescription(), g.getStatus(),
                g.getCronExpression(), RoutingScheduler.describe(g.getCronExpression()),
                RoutingScheduler.nextRun(g), lastRun, g.getRoutings().size(), g.getUpdatedAt());
    }
}
