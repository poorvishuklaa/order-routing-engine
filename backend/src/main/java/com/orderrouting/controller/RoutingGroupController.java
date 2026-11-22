package com.orderrouting.controller;

import com.orderrouting.dto.RoutingDtos.CreateGroupRequest;
import com.orderrouting.dto.RoutingDtos.RoutingGroupDto;
import com.orderrouting.dto.RoutingDtos.RoutingGroupSummary;
import com.orderrouting.entity.RoutingGroup;
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

    @GetMapping
    public List<RoutingGroupSummary> list() {
        return groupService.findAll().stream()
                .sorted(Comparator.comparing(RoutingGroup::getId))
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

    private RoutingGroupSummary summary(RoutingGroup g) {
        return new RoutingGroupSummary(g.getId(), g.getName(), g.getDescription(), g.getStatus(),
                g.getCronExpression(), null, null, null, g.getRoutings().size(), g.getUpdatedAt());
    }
}
