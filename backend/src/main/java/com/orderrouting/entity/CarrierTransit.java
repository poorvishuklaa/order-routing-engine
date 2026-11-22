package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ground transit days for a carrier between two 3-digit postal code areas.
 * Example: UPS_GROUND, 895 (Reno) -> 941 (San Francisco) = 1 day.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"carrier", "originZip3", "destZip3"}))
@Getter
@Setter
@NoArgsConstructor
public class CarrierTransit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String carrier;

    @Column(nullable = false, length = 3)
    private String originZip3;

    @Column(nullable = false, length = 3)
    private String destZip3;

    private int transitDays;

    public CarrierTransit(String carrier, String originZip3, String destZip3, int transitDays) {
        this.carrier = carrier;
        this.originZip3 = originZip3;
        this.destZip3 = destZip3;
        this.transitDays = transitDays;
    }
}
