package com.orderrouting.seed;

import com.orderrouting.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SampleDataLoaderTest {

    @Autowired FacilityRepository facilityRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired RoutingGroupRepository routingGroupRepository;
    @Autowired CarrierTransitRepository carrierTransitRepository;
    @Autowired InventoryRepository inventoryRepository;

    @Test
    void loadsSampleData() {
        assertThat(facilityRepository.count()).isEqualTo(10);
        assertThat(salesOrderRepository.count()).isEqualTo(60);
        assertThat(routingGroupRepository.count()).isEqualTo(1);
        assertThat(inventoryRepository.count()).isEqualTo(8 * 6);
        assertThat(carrierTransitRepository.count()).isEqualTo(18 * 18 * 2);
    }
}
