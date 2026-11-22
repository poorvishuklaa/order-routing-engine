package com.orderrouting.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderrouting.ai.AiDtos.*;
import com.orderrouting.ai.RecommendationAdvisor.AdvisorResult;
import com.orderrouting.entity.AiRecommendation;
import com.orderrouting.entity.RecommendationStatus;
import com.orderrouting.entity.RoutingRule;
import com.orderrouting.exception.BadRequestException;
import com.orderrouting.exception.NotFoundException;
import com.orderrouting.insights.RuleValueFormatter;
import com.orderrouting.repository.AiRecommendationRepository;
import com.orderrouting.repository.RoutingGroupRepository;
import com.orderrouting.repository.RoutingRuleRepository;
import com.orderrouting.service.RoutingConfigValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Generate -> validate -> save as PENDING -> admin approves or rejects.
 * Approval is the ONLY path by which an AI suggestion changes routing rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private static final int COOLDOWN_SECONDS = 30;

    private final RecommendationAdvisor advisor;
    private final RecommendationValidator validator;
    private final AiRecommendationRepository repository;
    private final RoutingGroupRepository groupRepository;
    private final RoutingRuleRepository ruleRepository;
    private final RuleValueFormatter formatter;
    private final RoutingConfigValidator configValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AiStatus status() {
        return new AiStatus(advisor.isConfigured(), advisor.modelName());
    }

    public AiRecommendationDto generate(Long groupId, String username) {
        if (!advisor.isConfigured()) {
            throw new BadRequestException("AI is not configured. Set the ANTHROPIC_API_KEY environment variable and restart.");
        }
        if (!groupRepository.existsById(groupId)) {
            throw new NotFoundException("Routing group " + groupId + " not found");
        }
        repository.findTopByRoutingGroupIdOrderByIdDesc(groupId)
                .filter(last -> last.getCreatedAt() != null
                        && last.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(COOLDOWN_SECONDS)))
                .ifPresent(last -> {
                    throw new BadRequestException("Please wait " + COOLDOWN_SECONDS + " seconds between AI requests");
                });

        AiRecommendation rec = new AiRecommendation();
        rec.setRoutingGroupId(groupId);
        rec.setCreatedBy(username);
        rec.setModel(advisor.modelName());
        try {
            AdvisorResult result = advisor.recommend(groupId);
            rec.setToolCalls(truncate(String.join(",", result.toolCalls()), 1000));
            AiRecommendationResponse response = result.response();
            if (response != null) {
                rec.setTitle(truncate(response.title(), 255));
                rec.setReason(truncate(response.reason(), 4000));
                rec.setExpectedImpact(truncate(response.expectedImpact(), 1000));
                rec.setChangesJson(toJson(response.changes() == null ? List.of() : response.changes()));
            }
            List<String> errors = validator.validate(groupId, response);
            if (!errors.isEmpty()) {
                rec.setStatus(RecommendationStatus.FAILED);
                rec.setErrorMessage(truncate("Validation failed: " + String.join("; ", errors), 2000));
            } else if (response.changes() == null || response.changes().isEmpty()) {
                rec.setStatus(RecommendationStatus.NO_CHANGES);
            } else {
                rec.setStatus(RecommendationStatus.PENDING);
            }
        } catch (Exception e) {
            log.error("AI recommendation failed for group {}", groupId, e);
            rec.setStatus(RecommendationStatus.FAILED);
            rec.setErrorMessage(truncate("AI call failed: " + e.getMessage(), 2000));
        }
        AiRecommendation saved = repository.save(rec);
        return transactionTemplate.execute(status -> toDto(saved));
    }

    @Transactional(readOnly = true)
    public List<AiRecommendationDto> list(RecommendationStatus status) {
        List<AiRecommendation> recs = status == null ? repository.findAllByOrderByIdDesc()
                : repository.findByStatusOrderByIdDesc(status);
        return recs.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AiRecommendationDto get(Long id) {
        return toDto(load(id));
    }

    /** Applies every change in one transaction: all or nothing. */
    @Transactional
    public AiRecommendationDto approve(Long id, String username, String note) {
        AiRecommendation rec = load(id);
        if (rec.getStatus() != RecommendationStatus.PENDING) {
            throw new IllegalStateException("Recommendation is " + rec.getStatus() + ", only PENDING can be approved");
        }
        for (RuleChange change : changes(rec)) {
            RoutingRule rule = ruleRepository.findById(change.ruleId())
                    .orElseThrow(() -> new IllegalStateException("Rule " + change.ruleId() + " no longer exists"));
            String current = formatter.current(rule, change.field());
            if (!formatter.normalize(change.field(), current).equals(formatter.normalize(change.field(), change.oldValue()))) {
                throw new IllegalStateException("Rule '" + rule.getName() + "' was changed after this recommendation ("
                        + change.field() + " is now " + current + "). Generate a new recommendation.");
            }
            formatter.apply(rule, change.field(), change.newValue());
            List<String> errors = configValidator.validateRule(rule);
            if (!errors.isEmpty()) {
                throw new BadRequestException(errors);
            }
        }
        rec.setStatus(RecommendationStatus.APPROVED);
        rec.setReviewedBy(username);
        rec.setReviewedAt(LocalDateTime.now());
        rec.setReviewNote(truncate(note, 1000));
        return toDto(rec);
    }

    @Transactional
    public AiRecommendationDto reject(Long id, String username, String note) {
        AiRecommendation rec = load(id);
        if (rec.getStatus() != RecommendationStatus.PENDING) {
            throw new IllegalStateException("Recommendation is " + rec.getStatus() + ", only PENDING can be rejected");
        }
        rec.setStatus(RecommendationStatus.REJECTED);
        rec.setReviewedBy(username);
        rec.setReviewedAt(LocalDateTime.now());
        rec.setReviewNote(truncate(note, 1000));
        return toDto(rec);
    }

    private AiRecommendation load(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Recommendation " + id + " not found"));
    }

    private AiRecommendationDto toDto(AiRecommendation r) {
        List<ChangeView> views = changes(r).stream().map(c -> {
            RoutingRule rule = c.ruleId() == null ? null : ruleRepository.findById(c.ruleId()).orElse(null);
            return new ChangeView(c.ruleId(), rule == null ? "(unknown rule)" : rule.getName(),
                    rule == null ? null : rule.getRouting().getName(), c.field(), c.oldValue(), c.newValue());
        }).toList();
        List<String> tools = r.getToolCalls() == null || r.getToolCalls().isBlank() ? List.of()
                : Arrays.asList(r.getToolCalls().split(","));
        return new AiRecommendationDto(r.getId(), r.getRoutingGroupId(), r.getTitle(), r.getReason(),
                r.getExpectedImpact(), views, r.getStatus(), r.getErrorMessage(), tools, r.getModel(),
                r.getCreatedBy(), r.getCreatedAt(), r.getReviewedBy(), r.getReviewedAt(), r.getReviewNote());
    }

    private List<RuleChange> changes(AiRecommendation r) {
        if (r.getChangesJson() == null) return List.of();
        try {
            return objectMapper.readValue(r.getChangesJson(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toJson(List<RuleChange> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
