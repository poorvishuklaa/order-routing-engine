package com.orderrouting.entity;

public enum RecommendationStatus {
    /** Valid, waiting for an admin. */
    PENDING,
    APPROVED,
    REJECTED,
    /** The AI found nothing worth changing. */
    NO_CHANGES,
    /** The AI call failed or its answer did not pass validation. Never shown for approval. */
    FAILED
}
