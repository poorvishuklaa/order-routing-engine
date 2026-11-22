package com.orderrouting.controller;

import com.orderrouting.service.RerouteService;
import com.orderrouting.service.RerouteService.RerouteResult;
import com.orderrouting.service.TestDriveService;
import com.orderrouting.service.TestDriveService.TestDriveResult;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class RerouteController {

    private final RerouteService rerouteService;
    private final TestDriveService testDriveService;

    public record RejectRequest(String reason) {
    }

    public record TestDriveRequest(@NotNull Long orderId) {
    }

    /** A facility cannot fulfill the item: release stock, exclude the facility, route again. */
    @PostMapping("/order-items/{id}/reject")
    public RerouteResult reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest request) {
        return rerouteService.rejectAndReroute(id, request == null ? null : request.reason());
    }

    /** Dry run: where would this group send the order? Never changes data. */
    @PostMapping("/routing-groups/{id}/test-drive")
    public TestDriveResult testDrive(@PathVariable Long id, @RequestBody TestDriveRequest request) {
        return testDriveService.testDrive(id, request.orderId());
    }
}
