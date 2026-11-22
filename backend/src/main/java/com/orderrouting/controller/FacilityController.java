package com.orderrouting.controller;

import com.orderrouting.dto.FacilityDtos.*;
import com.orderrouting.service.FacilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityService facilityService;

    @GetMapping("/facilities")
    public List<FacilityDto> facilities() {
        return facilityService.listFacilities();
    }

    @PutMapping("/facilities/{id}/capacity")
    public FacilityDto updateCapacity(@PathVariable Long id, @RequestBody CapacityRequest request) {
        return facilityService.updateCapacity(id, request);
    }

    @GetMapping("/facility-groups")
    public List<FacilityGroupDto> groups() {
        return facilityService.listGroups();
    }

    @GetMapping("/facility-groups/{id}")
    public FacilityGroupDto group(@PathVariable Long id) {
        return facilityService.getGroup(id);
    }

    @PostMapping("/facility-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityGroupDto createGroup(@Valid @RequestBody FacilityGroupRequest request) {
        return facilityService.saveGroup(null, request);
    }

    @PutMapping("/facility-groups/{id}")
    public FacilityGroupDto updateGroup(@PathVariable Long id, @Valid @RequestBody FacilityGroupRequest request) {
        return facilityService.saveGroup(id, request);
    }

    @DeleteMapping("/facility-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long id) {
        facilityService.deleteGroup(id);
    }

    @GetMapping("/inventory")
    public List<InventoryDto> inventory(@RequestParam(required = false) Long facilityId) {
        return facilityService.listInventory(facilityId);
    }
}
