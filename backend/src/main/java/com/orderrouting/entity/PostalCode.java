package com.orderrouting.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Postal code centroid, used to compute the distance between a facility and a customer. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostalCode {

    @Id
    private String zip;

    private String city;
    private String state;
    private double latitude;
    private double longitude;
}
