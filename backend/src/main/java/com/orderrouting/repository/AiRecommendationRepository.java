package com.orderrouting.repository;

import com.orderrouting.entity.AiRecommendation;
import com.orderrouting.entity.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {

    List<AiRecommendation> findAllByOrderByIdDesc();

    List<AiRecommendation> findByStatusOrderByIdDesc(RecommendationStatus status);

    Optional<AiRecommendation> findTopByRoutingGroupIdOrderByIdDesc(Long routingGroupId);
}
