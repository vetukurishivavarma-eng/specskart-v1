package com.specskart.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {
    List<SupplierInvoice> findByStoreIdOrderByInvoiceDateDesc(UUID storeId);
    List<SupplierInvoice> findByStatusInOrderByInvoiceDateAsc(List<String> statuses);
    boolean existsBySupplierIdAndInvoiceNumber(UUID supplierId, String invoiceNumber);
}
