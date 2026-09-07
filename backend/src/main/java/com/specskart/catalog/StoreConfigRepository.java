package com.specskart.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreConfigRepository extends JpaRepository<StoreConfig, Integer> {

    default StoreConfig current() {
        return findById(1).orElseGet(() -> save(new StoreConfig()));
    }
}
