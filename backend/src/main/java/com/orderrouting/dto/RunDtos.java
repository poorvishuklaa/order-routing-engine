package com.orderrouting.dto;

import com.orderrouting.entity.RunStatus;
import com.orderrouting.entity.RunTrigger;

import java.time.LocalDateTime;

public final class RunDtos {

    private RunDtos() {
    }

    public record RoutingRunDto(Long id, Long routingGroupId, String routingGroupName, RunTrigger triggerType,
                                RunStatus status, LocalDateTime startedAt, LocalDateTime finishedAt,
                                int itemsTried, int itemsPlaced, String errorMessage) {
    }
}
