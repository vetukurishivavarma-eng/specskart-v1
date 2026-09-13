package com.specskart.lens;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LensPricingOptionRepository extends JpaRepository<LensPricingOption, java.util.UUID> {
    Optional<LensPricingOption> findByCode(String code);
    List<LensPricingOption> findAllByOrderByCodeAsc();
}
