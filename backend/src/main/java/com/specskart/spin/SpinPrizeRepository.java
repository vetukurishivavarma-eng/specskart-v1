package com.specskart.spin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpinPrizeRepository extends JpaRepository<SpinPrize, UUID> {
    List<SpinPrize> findByActiveTrueOrderBySortOrderAsc();
    List<SpinPrize> findAllByOrderBySortOrderAsc();
}
