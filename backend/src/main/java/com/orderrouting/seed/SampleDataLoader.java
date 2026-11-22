package com.orderrouting.seed;

import com.orderrouting.engine.GeoUtils;
import com.orderrouting.entity.*;
import com.orderrouting.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Loads demo data on first start: postal codes, carrier transit times, facilities,
 * facility groups, products, inventory, approved orders and one routing group.
 * Runs only when the database is empty.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SampleDataLoader implements CommandLineRunner {

    public static final String BROKERING_QUEUE = "_NA_";
    public static final String UNFILLABLE_QUEUE = "UNFILLABLE";

    private final PostalCodeRepository postalCodeRepository;
    private final CarrierTransitRepository carrierTransitRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityGroupRepository facilityGroupRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final RoutingGroupRepository routingGroupRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (facilityRepository.count() > 0) {
            return;
        }
        log.info("Loading sample data");
        Map<String, PostalCode> zips = loadPostalCodes();
        loadCarrierTransit(zips.values());
        Map<String, Facility> facilities = loadFacilities(zips);
        Map<String, FacilityGroup> groups = loadFacilityGroups(facilities);
        List<Product> products = loadProducts();
        loadInventory(facilities, products);
        loadOrders(facilities.get(BROKERING_QUEUE), products);
        loadRoutingGroup(groups, facilities);
        log.info("Sample data loaded");
    }

    private Map<String, PostalCode> loadPostalCodes() {
        List<PostalCode> list = List.of(
                new PostalCode("07001", "Avenel", "NJ", 40.5834, -74.2765),
                new PostalCode("10001", "New York", "NY", 40.7506, -73.9972),
                new PostalCode("11201", "Brooklyn", "NY", 40.6940, -73.9900),
                new PostalCode("02108", "Boston", "MA", 42.3576, -71.0636),
                new PostalCode("19103", "Philadelphia", "PA", 39.9530, -75.1740),
                new PostalCode("30303", "Atlanta", "GA", 33.7525, -84.3915),
                new PostalCode("33101", "Miami", "FL", 25.7790, -80.1979),
                new PostalCode("60601", "Chicago", "IL", 41.8858, -87.6181),
                new PostalCode("75201", "Dallas", "TX", 32.7876, -96.7994),
                new PostalCode("77002", "Houston", "TX", 29.7560, -95.3650),
                new PostalCode("80202", "Denver", "CO", 39.7530, -104.9990),
                new PostalCode("85004", "Phoenix", "AZ", 33.4510, -112.0700),
                new PostalCode("89502", "Reno", "NV", 39.4977, -119.7768),
                new PostalCode("90012", "Los Angeles", "CA", 34.0614, -118.2385),
                new PostalCode("92101", "San Diego", "CA", 32.7190, -117.1630),
                new PostalCode("94105", "San Francisco", "CA", 37.7898, -122.3942),
                new PostalCode("97201", "Portland", "OR", 45.5075, -122.6900),
                new PostalCode("98101", "Seattle", "WA", 47.6114, -122.3305));
        postalCodeRepository.saveAll(list);
        Map<String, PostalCode> map = new LinkedHashMap<>();
        list.forEach(z -> map.put(z.getZip(), z));
        return map;
    }

    /**
     * Builds a synthetic ground transit table for two carriers from distance.
     * Real systems import this from carrier time-in-transit data.
     */
    private void loadCarrierTransit(Collection<PostalCode> zips) {
        List<CarrierTransit> rows = new ArrayList<>();
        for (PostalCode origin : zips) {
            for (PostalCode dest : zips) {
                double miles = GeoUtils.distanceMiles(origin.getLatitude(), origin.getLongitude(),
                        dest.getLatitude(), dest.getLongitude());
                String o = GeoUtils.zip3(origin.getZip());
                String d = GeoUtils.zip3(dest.getZip());
                rows.add(new CarrierTransit("UPS_GROUND", o, d, transitDays(miles, 250, 600, 1000, 1600)));
                rows.add(new CarrierTransit("FEDEX_GROUND", o, d, transitDays(miles, 300, 700, 1200, 1800)));
            }
        }
        carrierTransitRepository.saveAll(rows);
    }

    private int transitDays(double miles, int oneDay, int twoDay, int threeDay, int fourDay) {
        if (miles <= oneDay) return 1;
        if (miles <= twoDay) return 2;
        if (miles <= threeDay) return 3;
        if (miles <= fourDay) return 4;
        return 5;
    }

    private Map<String, Facility> loadFacilities(Map<String, PostalCode> zips) {
        List<Facility> list = new ArrayList<>();
        list.add(new Facility(BROKERING_QUEUE, "Brokering Queue", FacilityType.QUEUE, null, null, null, null));
        list.add(new Facility(UNFILLABLE_QUEUE, "Unfillable Queue", FacilityType.QUEUE, null, null, null, null));
        list.add(facility("NJ_WH", "New Jersey Warehouse", FacilityType.WAREHOUSE, "07001", 100, zips));
        list.add(facility("DAL_WH", "Dallas Warehouse", FacilityType.WAREHOUSE, "75201", 80, zips));
        list.add(facility("RENO_WH", "Reno Warehouse", FacilityType.WAREHOUSE, "89502", 80, zips));
        list.add(facility("CHI_WH", "Chicago Warehouse", FacilityType.WAREHOUSE, "60601", 80, zips));
        list.add(facility("BKLYN_ST", "Brooklyn Store", FacilityType.STORE, "11201", 20, zips));
        list.add(facility("LA_ST", "Los Angeles Store", FacilityType.STORE, "90012", 20, zips));
        list.add(facility("SF_ST", "San Francisco Store", FacilityType.STORE, "94105", 15, zips));
        list.add(facility("SEA_ST", "Seattle Store", FacilityType.STORE, "98101", 15, zips));
        facilityRepository.saveAll(list);
        Map<String, Facility> map = new LinkedHashMap<>();
        list.forEach(f -> map.put(f.getCode(), f));
        return map;
    }

    private Facility facility(String code, String name, FacilityType type, String zip, int capacity,
                              Map<String, PostalCode> zips) {
        PostalCode pc = zips.get(zip);
        return new Facility(code, name, type, zip, pc.getLatitude(), pc.getLongitude(), capacity);
    }

    private Map<String, FacilityGroup> loadFacilityGroups(Map<String, Facility> f) {
        FacilityGroup warehouses = new FacilityGroup("ALL_WAREHOUSES", "All Warehouses", "Every distribution center");
        warehouses.getFacilities().addAll(List.of(f.get("NJ_WH"), f.get("DAL_WH"), f.get("RENO_WH"), f.get("CHI_WH")));
        FacilityGroup stores = new FacilityGroup("ALL_STORES", "All Stores", "Retail stores that can ship");
        stores.getFacilities().addAll(List.of(f.get("BKLYN_ST"), f.get("LA_ST"), f.get("SF_ST"), f.get("SEA_ST")));
        FacilityGroup west = new FacilityGroup("WEST_COAST", "West Coast", "West coast warehouses and stores");
        west.getFacilities().addAll(List.of(f.get("RENO_WH"), f.get("LA_ST"), f.get("SF_ST"), f.get("SEA_ST")));
        facilityGroupRepository.saveAll(List.of(warehouses, stores, west));
        return Map.of("ALL_WAREHOUSES", warehouses, "ALL_STORES", stores, "WEST_COAST", west);
    }

    private List<Product> loadProducts() {
        List<Product> list = List.of(
                new Product("JKT-BLU-M", "Blue Jacket (M)", new BigDecimal("89.00")),
                new Product("JKT-BLU-L", "Blue Jacket (L)", new BigDecimal("89.00")),
                new Product("TEE-WHT-M", "White Tee (M)", new BigDecimal("19.00")),
                new Product("SNK-RUN-9", "Running Sneaker (9)", new BigDecimal("120.00")),
                new Product("CAP-BLK", "Black Cap", new BigDecimal("25.00")),
                new Product("BAG-TOTE", "Canvas Tote", new BigDecimal("35.00")));
        productRepository.saveAll(list);
        return list;
    }

    private void loadInventory(Map<String, Facility> facilities, List<Product> products) {
        Random random = new Random(7);
        List<Inventory> list = new ArrayList<>();
        for (Facility f : facilities.values()) {
            if (f.isQueue()) continue;
            for (Product p : products) {
                boolean warehouse = f.getType() == FacilityType.WAREHOUSE;
                int onHand = warehouse ? 15 + random.nextInt(60) : random.nextInt(12);
                int minStock = warehouse ? 0 : 1;
                Inventory inv = new Inventory(f, p, onHand, minStock);
                list.add(inv);
            }
        }
        // A few deliberate edge cases for demos
        list.stream().filter(i -> i.getFacility().getCode().equals("CHI_WH") && i.getProduct().getSku().equals("SNK-RUN-9"))
                .forEach(i -> i.setOnHand(2));
        list.stream().filter(i -> i.getFacility().getCode().equals("SEA_ST") && i.getProduct().getSku().equals("BAG-TOTE"))
                .forEach(i -> i.setAllowBrokering(false));
        inventoryRepository.saveAll(list);
    }

    private void loadOrders(Facility queue, List<Product> products) {
        String[] customerZips = {"94105", "90012", "92101", "98101", "97201", "89502", "85004", "80202",
                "75201", "77002", "60601", "30303", "33101", "19103", "02108", "10001", "11201"};
        String[] names = {"Ava Patel", "Liam Chen", "Noah Garcia", "Emma Johnson", "Olivia Smith", "Mia Brown",
                "Lucas Davis", "Sophia Wilson", "Ethan Moore", "Isabella Taylor", "Arjun Mehta", "Zoe Clark"};
        Random random = new Random(42);
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        List<SalesOrder> orders = new ArrayList<>();
        for (int n = 1; n <= 60; n++) {
            SalesOrder order = new SalesOrder();
            order.setOrderNumber(String.format("SO-%05d", 1000 + n));
            order.setCustomerName(names[random.nextInt(names.length)]);
            order.setShipToPostalCode(customerZips[random.nextInt(customerZips.length)]);
            int m = random.nextInt(10);
            order.setShippingMethod(m < 2 ? ShippingMethod.NEXT_DAY : m < 4 ? ShippingMethod.SECOND_DAY : ShippingMethod.STANDARD);
            order.setPriority(OrderPriority.values()[random.nextInt(3)]);
            order.setSalesChannel(random.nextInt(5) == 0 ? SalesChannel.MOBILE : SalesChannel.WEB);
            order.setStatus(OrderStatus.APPROVED);
            order.setOrderDate(start.plusMinutes(n * 45L));
            int itemCount = 1 + random.nextInt(2);
            List<Product> shuffled = new ArrayList<>(products);
            Collections.shuffle(shuffled, random);
            for (int i = 0; i < itemCount; i++) {
                order.addItem(new OrderItem(shuffled.get(i), 1 + random.nextInt(2), queue));
            }
            orders.add(order);
        }
        salesOrderRepository.saveAll(orders);
    }

    private void loadRoutingGroup(Map<String, FacilityGroup> groups, Map<String, Facility> facilities) {
        RoutingGroup group = new RoutingGroup();
        group.setName("US Orders");
        group.setDescription("Routes all approved US web orders: express orders try nearby stores first, "
                + "standard orders go to warehouses with stores as backup.");
        group.setCronExpression("0 0/15 * * * *");
        group.setStatus(GroupStatus.ACTIVE);

        Routing express = new Routing();
        express.setName("Express orders");
        express.setSequenceNum(1);
        express.setQueueFacility(facilities.get(BROKERING_QUEUE));
        express.getShippingMethods().addAll(List.of(ShippingMethod.NEXT_DAY, ShippingMethod.SECOND_DAY));
        express.setOrderSortBy(OrderSortBy.SHIPPING_METHOD_SPEED);
        express.addRule(rule("Nearby stores", 1, groups.get("ALL_STORES"), 100, null,
                List.of(FacilitySort.DISTANCE), false, FailAction.NEXT_RULE, null));
        express.addRule(rule("Fastest warehouse", 2, groups.get("ALL_WAREHOUSES"), null, 2,
                List.of(FacilitySort.TRANSIT_TIME, FacilitySort.INVENTORY), false, FailAction.NEXT_RULE, null));
        group.addRouting(express);

        Routing standard = new Routing();
        standard.setName("Standard orders");
        standard.setSequenceNum(2);
        standard.setQueueFacility(facilities.get(BROKERING_QUEUE));
        standard.getShippingMethods().add(ShippingMethod.STANDARD);
        standard.setOrderSortBy(OrderSortBy.ORDER_DATE);
        standard.addRule(rule("Warehouses only", 1, groups.get("ALL_WAREHOUSES"), null, null,
                List.of(FacilitySort.INVENTORY), false, FailAction.NEXT_RULE, null));
        RoutingRule backup = rule("Stores as backup", 2, groups.get("ALL_STORES"), null, null,
                List.of(FacilitySort.TRANSIT_TIME), true, FailAction.MOVE_TO_QUEUE, facilities.get(UNFILLABLE_QUEUE));
        backup.setSafetyStock(1);
        standard.addRule(backup);
        group.addRouting(standard);

        routingGroupRepository.save(group);
    }

    private RoutingRule rule(String name, int seq, FacilityGroup group, Integer maxDistance, Integer maxTransit,
                             List<FacilitySort> sorts, boolean partial, FailAction action, Facility moveTo) {
        RoutingRule rule = new RoutingRule();
        rule.setName(name);
        rule.setSequenceNum(seq);
        rule.setFacilityGroup(group);
        rule.setMaxDistanceMiles(maxDistance);
        rule.setMaxTransitDays(maxTransit);
        rule.getFacilitySorts().addAll(sorts);
        rule.setAllowPartialAllocation(partial);
        rule.setFailAction(action);
        rule.setMoveToQueue(moveTo);
        return rule;
    }
}
