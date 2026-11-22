package com.orderrouting.service;

import com.orderrouting.entity.*;
import com.orderrouting.exception.BadRequestException;
import com.orderrouting.exception.NotFoundException;
import com.orderrouting.repository.*;
import com.orderrouting.seed.SampleDataLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Automatic re-routing: when a facility says it cannot fulfill an item, the
 * reservation is released, the facility is excluded for that item, and the
 * order is routed again immediately.
 */
@Service
@RequiredArgsConstructor
public class RerouteService {

    private final TransactionTemplate transactionTemplate;
    private final OrderItemRepository itemRepository;
    private final FacilityRepository facilityRepository;
    private final OrderItemExclusionRepository exclusionRepository;
    private final RoutingDecisionRepository decisionRepository;
    private final AllocationService allocationService;
    private final RoutingExecutionService executionService;

    public record RerouteResult(Long orderItemId, String rejectedBy, OrderItemStatus status, String facilityCode,
                                boolean rerouted, String message, List<RoutingDecision> decisions) {
    }

    public RerouteResult rejectAndReroute(Long itemId, String reason) {
        String rejectedBy = transactionTemplate.execute(status -> {
            OrderItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new NotFoundException("Order item " + itemId + " not found"));
            if (item.getStatus() != OrderItemStatus.ALLOCATED) {
                throw new BadRequestException("Only allocated items can be rejected");
            }
            Facility facility = item.getFacility();
            Facility queue = facilityRepository.findByCode(SampleDataLoader.BROKERING_QUEUE).orElseThrow();
            allocationService.release(item, queue);
            if (!exclusionRepository.existsByOrderItemIdAndFacilityId(itemId, facility.getId())) {
                exclusionRepository.save(new OrderItemExclusion(item, facility, reason));
            }
            decisionRepository.save(rejectedDecision(item, facility, reason));
            return facility.getCode();
        });

        Long orderId = transactionTemplate.execute(s -> itemRepository.findById(itemId).orElseThrow().getOrder().getId());
        boolean rerouted = executionService.rerouteOrder(orderId).isPresent();

        return transactionTemplate.execute(status -> {
            OrderItem item = itemRepository.findById(itemId).orElseThrow();
            String message;
            if (!rerouted) {
                message = "Another routing run is in progress; the item will be routed in the next run";
            } else if (item.getStatus() == OrderItemStatus.ALLOCATED) {
                message = "Re-routed to " + item.getFacility().getCode();
            } else {
                message = "No other facility could take the item; it is waiting in " + item.getFacility().getCode();
            }
            List<RoutingDecision> decisions = decisionRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                    .filter(d -> d.getOrderItemId().equals(itemId)).toList();
            return new RerouteResult(itemId, rejectedBy, item.getStatus(), item.getFacility().getCode(), rerouted,
                    message, decisions);
        });
    }

    private RoutingDecision rejectedDecision(OrderItem item, Facility facility, String reason) {
        RoutingDecision d = new RoutingDecision();
        d.setOrderId(item.getOrder().getId());
        d.setOrderNumber(item.getOrder().getOrderNumber());
        d.setOrderItemId(item.getId());
        d.setProductSku(item.getProduct().getSku());
        d.setQuantity(item.getQuantity());
        d.setFacilityId(facility.getId());
        d.setFacilityCode(facility.getCode());
        d.setOutcome(DecisionOutcome.REJECTED);
        d.setReason("Rejected by " + facility.getCode() + (reason == null || reason.isBlank() ? "" : ": " + reason));
        return d;
    }
}
