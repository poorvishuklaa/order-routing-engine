package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** How many orders a facility received on a given day (for the daily capacity check). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"facility_id", "count_date"}))
@Getter
@Setter
@NoArgsConstructor
public class FacilityDailyCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Facility facility;

    @Column(name = "count_date", nullable = false)
    private LocalDate date;

    private int orderCount;

    @Version
    private Long version;

    public FacilityDailyCount(Facility facility, LocalDate date) {
        this.facility = facility;
        this.date = date;
    }
}
