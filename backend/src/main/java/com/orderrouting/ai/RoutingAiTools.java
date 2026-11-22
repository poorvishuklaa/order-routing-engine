package com.orderrouting.ai;

import com.orderrouting.insights.InsightDtos.*;
import com.orderrouting.insights.RoutingInsightsService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * Tools the LLM may call while investigating. All are READ-ONLY and return
 * aggregated data (counts, ZIP3 regions), never customer names or addresses.
 * One instance is created per request and is locked to a single routing group.
 */
public class RoutingAiTools {

    private final RoutingInsightsService insights;
    private final Long groupId;
    private final List<String> calls;

    public RoutingAiTools(RoutingInsightsService insights, Long groupId, List<String> calls) {
        this.insights = insights;
        this.groupId = groupId;
        this.calls = calls;
    }

    @Tool(description = "Get the routing group's routings and rules with their current settings. "
            + "The ruleId values and setting formats returned here are the only valid ones for recommendations.")
    public GroupConfigSummary getRoutingGroupConfig() {
        calls.add("getRoutingGroupConfig");
        return insights.groupConfig(groupId);
    }

    @Tool(description = "Routing results for the last N days: items attempted, allocated, not routed, moved to queue, "
            + "allocation rate, average transit days, allocations by facility and by rule, and average transit days "
            + "by customer region (ZIP3).")
    public RoutingStats getRoutingStats(@ToolParam(description = "Days to look back, 1 to 30") int days) {
        calls.add("getRoutingStats(" + days + ")");
        return insights.routingStats(groupId, clamp(days));
    }

    @Tool(description = "Items that were NOT routed or were moved to a queue in the last N days, "
            + "grouped by rule (with ruleId) and reason, largest first.")
    public List<UnroutedReason> getUnroutedReasons(@ToolParam(description = "Days to look back, 1 to 30") int days) {
        calls.add("getUnroutedReasons(" + days + ")");
        return insights.unroutedReasons(groupId, clamp(days));
    }

    @Tool(description = "Available stock (on hand - reserved - min stock) per facility and SKU, "
            + "and SKUs where brokering is disabled.")
    public List<FacilityStock> getInventoryByFacility() {
        calls.add("getInventoryByFacility");
        return insights.inventoryByFacility();
    }

    @Tool(description = "Orders received today vs daily capacity for each facility.")
    public List<CapacityUsage> getFacilityCapacityUsage() {
        calls.add("getFacilityCapacityUsage");
        return insights.capacityUsage();
    }

    @Tool(description = "Where customers are: order count and share by destination region (ZIP3) for the last N days.")
    public List<RegionDemand> getCustomerDemandByRegion(@ToolParam(description = "Days to look back, 1 to 30") int days) {
        calls.add("getCustomerDemandByRegion(" + days + ")");
        return insights.demandByRegion(clamp(days));
    }

    @Tool(description = "All facility groups with their codes and member facilities. "
            + "Use the group code as the value for FACILITY_GROUP changes.")
    public List<FacilityGroupInfo> getFacilityGroups() {
        calls.add("getFacilityGroups");
        return insights.facilityGroups();
    }

    private static int clamp(int days) {
        return Math.max(1, Math.min(days, 30));
    }
}
