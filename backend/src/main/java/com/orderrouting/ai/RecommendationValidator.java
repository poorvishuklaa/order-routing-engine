package com.orderrouting.ai;

import com.orderrouting.ai.AiDtos.AiRecommendationResponse;
import com.orderrouting.ai.AiDtos.RuleChange;
import com.orderrouting.entity.RoutingRule;
import com.orderrouting.exception.BadRequestException;
import com.orderrouting.insights.RuleValueFormatter;
import com.orderrouting.repository.RoutingRuleRepository;
import com.orderrouting.service.RoutingConfigValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the LLM's answer before anyone sees it:
 * 1. shape (title, reason, at most 3 changes)
 * 2. every ruleId exists and belongs to this group
 * 3. oldValue matches the rule's current value (the AI read real data)
 * 4. newValue parses and the changed rule passes the same checks as a manual Save
 * Step 4 applies the change inside a transaction that is always rolled back.
 */
@Component
@RequiredArgsConstructor
public class RecommendationValidator {

    static final int MAX_CHANGES = 3;

    private final RoutingRuleRepository ruleRepository;
    private final RuleValueFormatter formatter;
    private final RoutingConfigValidator configValidator;
    private final TransactionTemplate transactionTemplate;

    public List<String> validate(Long groupId, AiRecommendationResponse response) {
        List<String> errors = new ArrayList<>();
        if (response == null) {
            return List.of("Empty response from AI");
        }
        if (isBlank(response.title())) errors.add("title is missing");
        if (isBlank(response.reason())) errors.add("reason is missing");
        List<RuleChange> changes = response.changes() == null ? List.of() : response.changes();
        if (changes.size() > MAX_CHANGES) {
            errors.add("at most " + MAX_CHANGES + " changes are allowed, got " + changes.size());
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Set<String> seen = new HashSet<>();
            for (RuleChange change : changes) {
                errors.addAll(validateChange(groupId, change, seen));
            }
            status.setRollbackOnly();
        });
        return errors;
    }

    private List<String> validateChange(Long groupId, RuleChange c, Set<String> seen) {
        String p = "change for rule " + c.ruleId() + ": ";
        if (c.ruleId() == null || c.field() == null) {
            return List.of("each change needs a ruleId and a field");
        }
        if (!seen.add(c.ruleId() + ":" + c.field())) {
            return List.of(p + c.field() + " is changed twice");
        }
        RoutingRule rule = ruleRepository.findById(c.ruleId()).orElse(null);
        if (rule == null || !rule.getRouting().getRoutingGroup().getId().equals(groupId)) {
            return List.of(p + "rule does not exist in this routing group");
        }
        String current = formatter.current(rule, c.field());
        if (!formatter.normalize(c.field(), c.oldValue()).equals(formatter.normalize(c.field(), current))) {
            return List.of(p + "oldValue '" + c.oldValue() + "' does not match current value '" + current + "'");
        }
        if (formatter.normalize(c.field(), c.newValue()).equals(formatter.normalize(c.field(), current))) {
            return List.of(p + "newValue is the same as the current value");
        }
        try {
            formatter.apply(rule, c.field(), c.newValue());
        } catch (BadRequestException e) {
            return List.of(p + e.getMessage());
        }
        return configValidator.validateRule(rule).stream().map(e -> p + e).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
