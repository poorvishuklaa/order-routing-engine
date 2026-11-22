package com.orderrouting.entity;

/** The only rule fields an AI recommendation is allowed to change. */
public enum ChangeableField {
    FACILITY_SORTS,
    MAX_DISTANCE_MILES,
    MAX_TRANSIT_DAYS,
    SAFETY_STOCK,
    ALLOW_PARTIAL_ALLOCATION,
    FACILITY_GROUP
}
