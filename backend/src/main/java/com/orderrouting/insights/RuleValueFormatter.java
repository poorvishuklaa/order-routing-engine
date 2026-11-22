package com.orderrouting.insights;

import com.orderrouting.entity.*;
import com.orderrouting.exception.BadRequestException;
import com.orderrouting.repository.FacilityGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads and writes rule fields as simple text, the format AI recommendations use:
 * FACILITY_SORTS "TRANSIT_TIME,INVENTORY" (or "DEFAULT"), numbers or "NONE",
 * "true"/"false", and facility group CODE for FACILITY_GROUP.
 */
@Component
@RequiredArgsConstructor
public class RuleValueFormatter {

    private final FacilityGroupRepository facilityGroupRepository;

    public String current(RoutingRule rule, ChangeableField field) {
        return switch (field) {
            case FACILITY_SORTS -> rule.getFacilitySorts().isEmpty() ? "DEFAULT"
                    : rule.getFacilitySorts().stream().map(Enum::name).collect(Collectors.joining(","));
            case MAX_DISTANCE_MILES -> rule.getMaxDistanceMiles() == null ? "NONE" : rule.getMaxDistanceMiles().toString();
            case MAX_TRANSIT_DAYS -> rule.getMaxTransitDays() == null ? "NONE" : rule.getMaxTransitDays().toString();
            case SAFETY_STOCK -> Integer.toString(rule.getSafetyStock());
            case ALLOW_PARTIAL_ALLOCATION -> Boolean.toString(rule.isAllowPartialAllocation());
            case FACILITY_GROUP -> rule.getFacilityGroup() == null ? "NONE" : rule.getFacilityGroup().getCode();
        };
    }

    /** Sets the field from text. Throws BadRequestException if the value cannot be parsed. */
    public void apply(RoutingRule rule, ChangeableField field, String value) {
        String v = normalize(field, value);
        try {
            switch (field) {
                case FACILITY_SORTS -> {
                    List<FacilitySort> sorts = new ArrayList<>();
                    if (!v.equals("DEFAULT")) {
                        Arrays.stream(v.split(",")).map(FacilitySort::valueOf).forEach(sorts::add);
                    }
                    rule.getFacilitySorts().clear();
                    rule.getFacilitySorts().addAll(sorts);
                }
                case MAX_DISTANCE_MILES -> rule.setMaxDistanceMiles(v.equals("NONE") ? null : Integer.valueOf(v));
                case MAX_TRANSIT_DAYS -> rule.setMaxTransitDays(v.equals("NONE") ? null : Integer.valueOf(v));
                case SAFETY_STOCK -> rule.setSafetyStock(Integer.parseInt(v));
                case ALLOW_PARTIAL_ALLOCATION -> {
                    if (!v.equals("TRUE") && !v.equals("FALSE")) throw new IllegalArgumentException();
                    rule.setAllowPartialAllocation(v.equals("TRUE"));
                }
                case FACILITY_GROUP -> rule.setFacilityGroup(facilityGroupRepository.findByCode(v)
                        .orElseThrow(() -> new BadRequestException("Unknown facility group '" + value + "'")));
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value '" + value + "' for " + field);
        }
    }

    /** Uppercase, no spaces, so "transit_time, inventory" equals "TRANSIT_TIME,INVENTORY". */
    public String normalize(ChangeableField field, String value) {
        if (value == null) return "NONE";
        String v = value.trim().replace(" ", "").toUpperCase();
        if (v.isEmpty() || v.equals("NULL")) {
            return field == ChangeableField.FACILITY_SORTS ? "DEFAULT" : "NONE";
        }
        return v;
    }
}
