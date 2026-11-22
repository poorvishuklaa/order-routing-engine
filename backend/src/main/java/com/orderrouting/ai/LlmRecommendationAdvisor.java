package com.orderrouting.ai;

import com.orderrouting.ai.AiDtos.AiRecommendationResponse;
import com.orderrouting.insights.RoutingInsightsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agentic workflow with Spring AI: the LLM gets the task plus read-only tools,
 * decides which tools to call (Spring AI runs the tool-calling loop), and
 * finally returns JSON that Spring AI converts into AiRecommendationResponse.
 */
@Component
public class LlmRecommendationAdvisor implements RecommendationAdvisor {

    static final String SYSTEM_PROMPT = """
            You are an order routing analyst for an e-commerce company.
            A deterministic Java engine routes each order to a fulfillment facility using rules.
            For each routing (a set of orders), rules are tried in sequence. A rule:
            - FILTERS facilities: its facility group, max distance (miles), max transit days for the
              rule's carrier, safety stock (units that must remain after the order), daily capacity,
              and allowPartialAllocation (false = the whole order must ship from one facility).
            - RANKS the remaining facilities by facilitySorts, applied in order:
              TRANSIT_TIME (fewest days first), DISTANCE (closest first), INVENTORY (most stock first),
              CAPACITY_REMAINING (most free capacity first). DEFAULT means TRANSIT_TIME,DISTANCE.
            Items a rule cannot place go to the next rule, or are moved to a queue.

            Your job: use the tools to investigate how routing performed (start with
            getRoutingGroupConfig and getRoutingStats), find the biggest problems (unrouted items,
            slow transit for a region, overloaded facilities, stock not being used), and recommend
            at most 3 rule changes that fix them.

            Rules for your answer:
            - Only use ruleId values returned by getRoutingGroupConfig.
            - Only change these fields, with these value formats:
              FACILITY_SORTS: comma separated list, e.g. "TRANSIT_TIME,INVENTORY", or "DEFAULT"
              MAX_DISTANCE_MILES: whole number 1-5000, or "NONE"
              MAX_TRANSIT_DAYS: whole number 1-10, or "NONE"
              SAFETY_STOCK: whole number 0 or more
              ALLOW_PARTIAL_ALLOCATION: "true" or "false"
              FACILITY_GROUP: a facility group code from getFacilityGroups
            - oldValue must be copied exactly from getRoutingGroupConfig.
            - Explain the reason with concrete numbers from the tools.
            - Never invent data. If nothing should change, return an empty changes list.
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final RoutingInsightsService insights;
    private final String apiKey;
    private final String model;

    public LlmRecommendationAdvisor(ObjectProvider<ChatClient.Builder> chatClientBuilder,
                                    RoutingInsightsService insights,
                                    @Value("${spring.ai.anthropic.api-key:not-configured}") String apiKey,
                                    @Value("${spring.ai.anthropic.chat.options.model:unknown}") String model) {
        this.chatClientBuilder = chatClientBuilder;
        this.insights = insights;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public AdvisorResult recommend(Long groupId) {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        AiRecommendationResponse response = chatClientBuilder.getObject().build()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user("Analyze routing group " + groupId + " using data from the last 7 days and recommend rule changes.")
                .tools(new RoutingAiTools(insights, groupId, calls))
                .call()
                .entity(AiRecommendationResponse.class);
        return new AdvisorResult(response, List.copyOf(calls));
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("not-configured")
                && chatClientBuilder.getIfAvailable() != null;
    }

    @Override
    public String modelName() {
        return model;
    }
}
