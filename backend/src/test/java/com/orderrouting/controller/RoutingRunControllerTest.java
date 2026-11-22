package com.orderrouting.controller;

import com.orderrouting.repository.RoutingGroupRepository;
import com.orderrouting.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoutingRunControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RoutingGroupRepository groupRepository;

    @Test
    void adminCanRunGroupNowAndSeeDecisions() throws Exception {
        String admin = "Bearer " + jwtService.generateToken("admin", "ADMIN");
        Long groupId = groupRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/routing-groups/" + groupId + "/run").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.routingGroupName").value("US Orders"));

        mockMvc.perform(get("/api/routing-decisions").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.page.totalElements").isNumber());
    }

    @Test
    void viewerCannotRunGroup() throws Exception {
        String viewer = "Bearer " + jwtService.generateToken("viewer", "VIEWER");
        mockMvc.perform(post("/api/routing-groups/1/run").header("Authorization", viewer))
                .andExpect(status().isForbidden());
    }
}
