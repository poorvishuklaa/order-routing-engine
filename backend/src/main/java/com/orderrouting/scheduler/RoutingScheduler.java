package com.orderrouting.scheduler;

import com.orderrouting.entity.GroupStatus;
import com.orderrouting.entity.RoutingGroup;
import com.orderrouting.entity.RunTrigger;
import com.orderrouting.repository.RoutingGroupRepository;
import com.orderrouting.service.RoutingExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Keeps one cron job per ACTIVE routing group. Each group has its own cron
 * expression, which @Scheduled cannot do, so jobs are registered at runtime.
 * Cron runs in the server's timezone.
 */
@Slf4j
@Component
public class RoutingScheduler {

    private final ThreadPoolTaskScheduler taskScheduler;
    private final RoutingGroupRepository groupRepository;
    private final RoutingExecutionService executionService;
    private final boolean enabled;
    private final Map<Long, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();

    public RoutingScheduler(ThreadPoolTaskScheduler routingTaskScheduler, RoutingGroupRepository groupRepository,
                            RoutingExecutionService executionService,
                            @Value("${app.scheduler.enabled:true}") boolean enabled) {
        this.taskScheduler = routingTaskScheduler;
        this.groupRepository = groupRepository;
        this.executionService = executionService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleActiveGroups() {
        groupRepository.findByStatus(GroupStatus.ACTIVE).forEach(this::reschedule);
    }

    /** Registers, replaces or removes the job to match the group's status and cron. */
    public void reschedule(RoutingGroup group) {
        cancel(group.getId());
        if (!enabled || group.getStatus() != GroupStatus.ACTIVE || !isValid(group.getCronExpression())) {
            return;
        }
        Long groupId = group.getId();
        ScheduledFuture<?> future = taskScheduler.schedule(() -> runSafely(groupId),
                new CronTrigger(group.getCronExpression(), TimeZone.getDefault()));
        jobs.put(groupId, future);
        log.info("Scheduled routing group {} with cron '{}'", groupId, group.getCronExpression());
    }

    public void cancel(Long groupId) {
        ScheduledFuture<?> existing = jobs.remove(groupId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    public static boolean isValid(String cron) {
        return cron != null && CronExpression.isValidExpression(cron);
    }

    /** Next fire time in server time, or null if the group is not scheduled. */
    public static LocalDateTime nextRun(RoutingGroup group) {
        if (group.getStatus() != GroupStatus.ACTIVE || !isValid(group.getCronExpression())) {
            return null;
        }
        return CronExpression.parse(group.getCronExpression()).next(LocalDateTime.now());
    }

    public static String describe(String cron) {
        if (cron == null) return "Not scheduled";
        return switch (cron) {
            case "0 0/5 * * * *" -> "Every 5 minutes";
            case "0 0/15 * * * *" -> "Every 15 minutes";
            case "0 0/30 * * * *" -> "Every 30 minutes";
            case "0 0 * * * *" -> "Every hour";
            case "0 0 0/6 * * *" -> "Every 6 hours";
            case "0 0 0 * * *" -> "Daily at midnight";
            default -> "Custom: " + cron;
        };
    }

    private void runSafely(Long groupId) {
        try {
            executionService.runGroup(groupId, RunTrigger.SCHEDULED);
        } catch (Exception e) {
            log.error("Scheduled run of group {} failed", groupId, e);
        }
    }
}
