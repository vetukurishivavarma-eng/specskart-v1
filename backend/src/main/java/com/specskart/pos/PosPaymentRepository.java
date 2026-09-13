package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PosPaymentRepository extends JpaRepository<PosPayment, UUID> {
    List<PosPayment> findBySaleId(UUID saleId);
}
