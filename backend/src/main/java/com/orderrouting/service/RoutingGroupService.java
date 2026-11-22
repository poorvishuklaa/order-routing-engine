package com.orderrouting.service;

import com.orderrouting.dto.RoutingDtos.*;
import com.orderrouting.entity.*;
import com.orderrouting.exception.NotFoundException;
import com.orderrouting.repository.FacilityGroupRepository;
import com.orderrouting.repository.FacilityRepository;
import com.orderrouting.repository.RoutingGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoutingGroupService {

    private final RoutingGroupRepository groupRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityGroupRepository facilityGroupRepository;
    private final RoutingConfigValidator validator;
    private final RoutingGroupMapper mapper;

    @Transactional(readOnly = true)
    public List<RoutingGroup> findAll() {
        List<RoutingGroup> groups = groupRepository.findAll();
        groups.forEach(g -> g.getRoutings().size());
        return groups;
    }

    @Transactional(readOnly = true)
    public RoutingGroupDto get(Long id) {
        return mapper.toDto(load(id));
    }

    @Transactional
    public RoutingGroupDto create(CreateGroupRequest request) {
        RoutingGroup group = new RoutingGroup();
        group.setName(request.name());
        group.setDescription(request.description());
        group.setStatus(GroupStatus.DRAFT);
        return mapper.toDto(groupRepository.save(group));
    }

    /**
     * Replaces the whole group (routings, filters, rules) in one transaction.
     * Existing children are updated by id, new ones are created and missing
     * ones are deleted by orphanRemoval. Schedule and status are not changed here.
     */
    @Transactional
    public RoutingGroupDto update(Long id, RoutingGroupDto dto) {
        RoutingGroup group = load(id);
        if (dto.version() != null && !dto.version().equals(group.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(RoutingGroup.class, id);
        }
        group.setName(dto.name());
        group.setDescription(dto.description());

        Map<Long, Routing> existing = group.getRoutings().stream()
                .collect(Collectors.toMap(Routing::getId, Function.identity()));
        List<Routing> updated = new ArrayList<>();
        List<RoutingDto> routingDtos = dto.routings() == null ? List.of() : dto.routings();
        for (int i = 0; i < routingDtos.size(); i++) {
            RoutingDto rd = routingDtos.get(i);
            Routing routing = rd.id() != null && existing.containsKey(rd.id()) ? existing.get(rd.id()) : new Routing();
            applyRouting(routing, rd, i + 1);
            routing.setRoutingGroup(group);
            updated.add(routing);
        }
        group.getRoutings().clear();
        group.getRoutings().addAll(updated);

        validator.validate(group);
        RoutingGroup saved = groupRepository.saveAndFlush(group);
        return mapper.toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        groupRepository.delete(load(id));
    }

    @Transactional(readOnly = true)
    public RoutingGroup load(Long id) {
        RoutingGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Routing group " + id + " not found"));
        group.getRoutings().forEach(r -> r.getRules().size());
        return group;
    }

    private void applyRouting(Routing routing, RoutingDto rd, int sequence) {
        routing.setName(rd.name());
        routing.setSequenceNum(sequence);
        routing.setStatus(rd.status() == null ? RoutingStatus.ACTIVE : rd.status());
        routing.setQueueFacility(rd.queueFacilityId() == null ? null : facility(rd.queueFacilityId()));
        replace(routing.getShippingMethods(), rd.shippingMethods());
        replace(routing.getPriorities(), rd.priorities());
        replace(routing.getSalesChannels(), rd.salesChannels());
        routing.setOrderSortBy(rd.orderSortBy() == null ? OrderSortBy.ORDER_DATE : rd.orderSortBy());

        Map<Long, RoutingRule> existing = routing.getRules().stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(RoutingRule::getId, Function.identity()));
        List<RoutingRule> rules = new ArrayList<>();
        List<RoutingRuleDto> ruleDtos = rd.rules() == null ? List.of() : rd.rules();
        for (int i = 0; i < ruleDtos.size(); i++) {
            RoutingRuleDto dto = ruleDtos.get(i);
            RoutingRule rule = dto.id() != null && existing.containsKey(dto.id()) ? existing.get(dto.id()) : new RoutingRule();
            applyRule(rule, dto, i + 1);
            rule.setRouting(routing);
            rules.add(rule);
        }
        routing.getRules().clear();
        routing.getRules().addAll(rules);
    }

    private void applyRule(RoutingRule rule, RoutingRuleDto d, int sequence) {
        rule.setName(d.name());
        rule.setSequenceNum(sequence);
        rule.setStatus(d.status() == null ? RoutingStatus.ACTIVE : d.status());
        rule.setFacilityGroup(d.facilityGroupId() == null ? null : facilityGroupRepository.findById(d.facilityGroupId())
                .orElseThrow(() -> new NotFoundException("Facility group " + d.facilityGroupId() + " not found")));
        rule.setCarrier(d.carrier() == null ? RoutingRule.DEFAULT_CARRIER : d.carrier());
        rule.setMaxDistanceMiles(d.maxDistanceMiles());
        rule.setMaxTransitDays(d.maxTransitDays());
        rule.setSafetyStock(d.safetyStock() == null ? 0 : d.safetyStock());
        rule.setAllowPartialAllocation(d.allowPartialAllocation());
        rule.setOverrideCapacity(d.overrideCapacity());
        rule.getFacilitySorts().clear();
        if (d.facilitySorts() != null) {
            rule.getFacilitySorts().addAll(d.facilitySorts());
        }
        rule.setFailAction(d.failAction() == null ? FailAction.NEXT_RULE : d.failAction());
        rule.setMoveToQueue(d.moveToQueueId() == null ? null : facility(d.moveToQueueId()));
        rule.setAutoCancelDays(d.autoCancelDays());
    }

    private Facility facility(Long id) {
        return facilityRepository.findById(id).orElseThrow(() -> new NotFoundException("Facility " + id + " not found"));
    }

    private static <T> void replace(Set<T> target, Set<T> values) {
        target.clear();
        if (values != null) {
            target.addAll(values);
        }
    }
}
