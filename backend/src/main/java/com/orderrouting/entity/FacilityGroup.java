package com.orderrouting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/** A named list of facilities, e.g. "All Warehouses". Routing rules point to a group. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class FacilityGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(name = "facility_group_member",
            joinColumns = @JoinColumn(name = "facility_group_id"),
            inverseJoinColumns = @JoinColumn(name = "facility_id"))
    private Set<Facility> facilities = new LinkedHashSet<>();

    public FacilityGroup(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
}
