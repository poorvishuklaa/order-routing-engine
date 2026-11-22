package com.orderrouting.controller;

import com.orderrouting.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FacilityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    private String bearer(String user, String role) {
        return "Bearer " + jwtService.generateToken(user, role);
    }

    @Test
    void viewerCanReadFacilities() throws Exception {
        mockMvc.perform(get("/api/facilities").header("Authorization", bearer("viewer", "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void viewerCannotCreateFacilityGroup() throws Exception {
        mockMvc.perform(post("/api/facility-groups").header("Authorization", bearer("viewer", "VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"X\",\"facilityIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void groupsListRulesThatUseThem() throws Exception {
        mockMvc.perform(get("/api/facility-groups").header("Authorization", bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usedByRules").isNotEmpty());
    }
}
