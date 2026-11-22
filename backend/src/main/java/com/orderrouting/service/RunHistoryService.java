package com.orderrouting.service;

import com.orderrouting.dto.RunDtos.RoutingRunDto;
import com.orderrouting.entity.DecisionOutcome;
import com.orderrouting.entity.RoutingDecision;
import com.orderrouting.entity.RoutingRun;
import com.orderrouting.repository.RoutingDecisionRepository;
import com.orderrouting.repository.RoutingRunRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RunHistoryService {

    private final RoutingRunRepository runRepository;
    private final RoutingDecisionRepository decisionRepository;

    @Transactional(readOnly = true)
    public Page<RoutingRunDto> runs(Long groupId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<RoutingRun> runs = groupId == null
                ? runRepository.findAllByOrderByStartedAtDesc(pageable)
                : runRepository.findByRoutingGroupIdOrderByStartedAtDesc(groupId, pageable);
        return runs.map(this::toDto);
    }

    /** Re-reads the run in this transaction, so callers can pass a detached run. */
    @Transactional(readOnly = true)
    public RoutingRunDto toDto(RoutingRun run) {
        RoutingRun r = run.getId() == null ? run : runRepository.findById(run.getId()).orElse(run);
        return new RoutingRunDto(r.getId(),
                r.getRoutingGroup() == null ? null : r.getRoutingGroup().getId(),
                r.getRoutingGroup() == null ? null : r.getRoutingGroup().getName(),
                r.getTriggerType(), r.getStatus(), r.getStartedAt(), r.getFinishedAt(),
                r.getItemsTried(), r.getItemsPlaced(), r.getErrorMessage());
    }

    @Transactional(readOnly = true)
    public Page<RoutingDecision> decisions(String orderNumber, DecisionOutcome outcome, Long ruleId, Long runId,
                                           int page, int size) {
        Specification<RoutingDecision> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (orderNumber != null && !orderNumber.isBlank()) {
                p.add(cb.like(cb.lower(root.get("orderNumber")), "%" + orderNumber.toLowerCase() + "%"));
            }
            if (outcome != null) p.add(cb.equal(root.get("outcome"), outcome));
            if (ruleId != null) p.add(cb.equal(root.get("ruleId"), ruleId));
            if (runId != null) p.add(cb.equal(root.get("runId"), runId));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return decisionRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }
}
