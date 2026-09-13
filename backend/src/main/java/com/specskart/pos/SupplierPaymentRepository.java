package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {
    List<SupplierPayment> findByInvoiceId(UUID invoiceId);
}
