package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierInvoiceItemRepository extends JpaRepository<SupplierInvoiceItem, UUID> {
    List<SupplierInvoiceItem> findByInvoiceId(UUID invoiceId);
}
