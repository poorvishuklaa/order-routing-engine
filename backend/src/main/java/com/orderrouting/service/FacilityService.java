package com.orderrouting.service;

import com.orderrouting.dto.FacilityDtos.*;
import com.orderrouting.entity.Facility;
import com.orderrouting.entity.FacilityDailyCount;
import com.orderrouting.entity.FacilityGroup;
import com.orderrouting.entity.Inventory;
import com.orderrouting.exception.BadRequestException;
import com.orderrouting.exception.NotFoundException;
import com.orderrouting.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final FacilityGroupRepository groupRepository;
    private final FacilityDailyCountRepository dailyCountRepository;
    private final InventoryRepository inventoryRepository;
    private final RoutingRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public List<FacilityDto> listFacilities() {
        Map<Long, Integer> today = ordersToday();
        return facilityRepository.findAll().stream()
                .sorted(Comparator.comparing(Facility::getType).thenComparing(Facility::getCode))
                .map(f -> toDto(f, today))
                .toList();
    }

    @Transactional
    public FacilityDto updateCapacity(Long facilityId, CapacityRequest request) {
        if (request.dailyCapacity() != null && request.dailyCapacity() < 0) {
            throw new BadRequestException("Daily capacity cannot be negative");
        }
        Facility facility = facility(facilityId);
        facility.setDailyCapacity(request.dailyCapacity());
        return toDto(facility, ordersToday());
    }

    @Transactional(readOnly = true)
    public List<FacilityGroupDto> listGroups() {
        Map<Long, Integer> today = ordersToday();
        return groupRepository.findAll().stream().map(g -> toDto(g, today)).toList();
    }

    @Transactional(readOnly = true)
    public FacilityGroupDto getGroup(Long id) {
        return toDto(group(id), ordersToday());
    }

    @Transactional
    public FacilityGroupDto saveGroup(Long id, FacilityGroupRequest request) {
        FacilityGroup group = id == null ? new FacilityGroup() : group(id);
        groupRepository.findByCode(request.code())
                .filter(existing -> !existing.getId().equals(group.getId()))
                .ifPresent(existing -> {
                    throw new BadRequestException("Facility group code '" + request.code() + "' already exists");
                });
        group.setCode(request.code());
        group.setName(request.name());
        group.setDescription(request.description());
        group.getFacilities().clear();
        if (request.facilityIds() != null) {
            for (Long facilityId : request.facilityIds()) {
                Facility f = facility(facilityId);
                if (f.isQueue()) {
                    throw new BadRequestException("Queue " + f.getCode() + " cannot be added to a facility group");
                }
                group.getFacilities().add(f);
            }
        }
        return toDto(groupRepository.save(group), ordersToday());
    }

    @Transactional
    public void deleteGroup(Long id) {
        FacilityGroup group = group(id);
        List<String> rules = usedBy(id);
        if (!rules.isEmpty()) {
            throw new BadRequestException("Facility group is used by rules: " + String.join(", ", rules));
        }
        groupRepository.delete(group);
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> listInventory(Long facilityId) {
        return inventoryRepository.findAllWithDetails().stream()
                .filter(i -> facilityId == null || i.getFacility().getId().equals(facilityId))
                .map(this::toDto)
                .toList();
    }

    private InventoryDto toDto(Inventory i) {
        return new InventoryDto(i.getId(), i.getFacility().getId(), i.getFacility().getCode(), i.getProduct().getId(),
                i.getProduct().getSku(), i.getProduct().getName(), i.getOnHand(), i.getReserved(), i.getMinStock(),
                i.getAvailable(), i.isAllowBrokering());
    }

    private List<String> usedBy(Long groupId) {
        return ruleRepository.findByFacilityGroupId(groupId).stream()
                .map(r -> r.getRouting().getRoutingGroup().getName() + " / " + r.getRouting().getName() + " / " + r.getName())
                .toList();
    }

    private FacilityGroupDto toDto(FacilityGroup g, Map<Long, Integer> today) {
        List<FacilityDto> facilities = g.getFacilities().stream()
                .sorted(Comparator.comparing(Facility::getCode))
                .map(f -> toDto(f, today)).toList();
        return new FacilityGroupDto(g.getId(), g.getCode(), g.getName(), g.getDescription(), facilities,
                g.getId() == null ? List.of() : usedBy(g.getId()));
    }

    private FacilityDto toDto(Facility f, Map<Long, Integer> today) {
        return new FacilityDto(f.getId(), f.getCode(), f.getName(), f.getType(), f.getPostalCode(), f.getLatitude(),
                f.getLongitude(), f.getDailyCapacity(), today.getOrDefault(f.getId(), 0), f.isActive());
    }

    private Map<Long, Integer> ordersToday() {
        return dailyCountRepository.findByDate(LocalDate.now()).stream()
                .collect(Collectors.toMap(c -> c.getFacility().getId(), FacilityDailyCount::getOrderCount));
    }

    private Facility facility(Long id) {
        return facilityRepository.findById(id).orElseThrow(() -> new NotFoundException("Facility " + id + " not found"));
    }

    private FacilityGroup group(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> new NotFoundException("Facility group " + id + " not found"));
    }
}
