package com.orderrouting.ai;

import com.orderrouting.ai.AiDtos.AiRecommendationResponse;

import java.util.List;

/** Produces a rule recommendation for a routing group. Implemented with an LLM; mocked in tests. */
public interface RecommendationAdvisor {

    record AdvisorResult(AiRecommendationResponse response, List<String> toolCalls) {
    }

    AdvisorResult recommend(Long groupId);

    boolean isConfigured();

    String modelName();
}
