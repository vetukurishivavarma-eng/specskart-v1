package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    List<Store> findAllByOrderByNameAsc();

    java.util.Optional<Store> findByCodeIgnoreCase(String code);

    List<Store> findByActiveTrue();

    List<Store> findByActiveTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();
}
