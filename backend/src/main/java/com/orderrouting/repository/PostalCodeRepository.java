package com.orderrouting.repository;

import com.orderrouting.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostalCodeRepository extends JpaRepository<PostalCode, String> {

}
