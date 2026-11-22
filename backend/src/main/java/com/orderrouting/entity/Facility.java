package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A place orders can be fulfilled from (warehouse or store), or a virtual
 * queue (type QUEUE) where approved order items wait until they are routed.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacilityType type;

    private String postalCode;
    private Double latitude;
    private Double longitude;

    /** Max orders per day. Null means unlimited. */
    private Integer dailyCapacity;

    private boolean active = true;

    public Facility(String code, String name, FacilityType type, String postalCode,
                    Double latitude, Double longitude, Integer dailyCapacity) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dailyCapacity = dailyCapacity;
    }

    public boolean isQueue() {
        return type == FacilityType.QUEUE;
    }
}
