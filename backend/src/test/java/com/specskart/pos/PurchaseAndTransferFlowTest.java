package com.specskart.pos;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("mock")
class PurchaseAndTransferFlowTest {

    @Autowired PurchaseService purchaseService;
    @Autowired TransferService transferService;
    @Autowired InventoryService inventoryService;
    @Autowired StoreRepository stores;
    @Autowired ProductRepository products;

    private Store storeA;
    private Store storeB;
    private Product product;

    @BeforeEach
    void setUp() {
        storeA = new Store();
        storeA.setName("Cairo Road");
        storeA.setCode("CR" + (int) (Math.random() * 100000));
        stores.save(storeA);

        storeB = new Store();
        storeB.setName("Manda Hill");
        storeB.setCode("MH" + (int) (Math.random() * 100000));
        stores.save(storeB);

        product = new Product();
        product.setSlug("purchase-test-frame-" + UUID.randomUUID());
        product.setName("Purchase Test Frame");
        product.setPriceMinor(25_000);
        products.save(product);
    }

    @Test
    void postingAnInvoicePutsStockOnTheShelfAndRecordsTheDebt() {
        var supplier = purchaseService.createSupplier(new PosDtos.SupplierUpsert("Test Optics Ltd", "", "", "", "", ""));

        var invoice = purchaseService.postInvoice(new PosDtos.PostInvoice(
                supplier.id(), storeA.getId(), "INV-001", LocalDate.now(), null, 5_000, "",
                List.of(new PosDtos.InvoiceItemRequest(product.getId(), "Purchase Test Frame", 10, 15_000))),
                null, "Admin");

        assertThat(invoice.totalMinor()).isEqualTo(10 * 15_000L + 5_000);
        assertThat(invoice.status()).isEqualTo("UNPAID");
        assertThat(inventoryService.quantityOf(storeA.getId(), product.getId())).isEqualTo(10);
    }

    @Test
    void duplicateInvoiceNumberForTheSameSupplierIsRefused() {
        var supplier = purchaseService.createSupplier(new PosDtos.SupplierUpsert("Dup Optics", "", "", "", "", ""));
        var req = new PosDtos.PostInvoice(supplier.id(), storeA.getId(), "INV-DUP", LocalDate.now(), null, 0, "",
                List.of(new PosDtos.InvoiceItemRequest(product.getId(), "Purchase Test Frame", 5, 10_000)));
        purchaseService.postInvoice(req, null, "Admin");

        assertThatThrownBy(() -> purchaseService.postInvoice(req, null, "Admin"))
                .hasMessageContaining("already recorded");
    }

    @Test
    void paymentsAccumulateAndFlipStatusToPaid() {
        var supplier = purchaseService.createSupplier(new PosDtos.SupplierUpsert("Pay Optics", "", "", "", "", ""));
        var invoice = purchaseService.postInvoice(new PosDtos.PostInvoice(
                supplier.id(), storeA.getId(), "INV-PAY", LocalDate.now(), null, 0, "",
                List.of(new PosDtos.InvoiceItemRequest(product.getId(), "Purchase Test Frame", 2, 10_000))),
                null, "Admin");
        assertThat(invoice.totalMinor()).isEqualTo(20_000);

        var afterFirst = purchaseService.recordPayment(invoice.id(),
                new PosDtos.RecordPayment(10_000, "CASH", null, null), null, "Admin");
        assertThat(afterFirst.status()).isEqualTo("PARTIAL");

        var afterSecond = purchaseService.recordPayment(invoice.id(),
                new PosDtos.RecordPayment(10_000, "CASH", null, null), null, "Admin");
        assertThat(afterSecond.status()).isEqualTo("PAID");
        assertThat(afterSecond.balanceMinor()).isEqualTo(0);

        assertThatThrownBy(() -> purchaseService.recordPayment(invoice.id(),
                new PosDtos.RecordPayment(1, "CASH", null, null), null, "Admin"))
                .hasMessageContaining("exceed");
    }

    @Test
    void transferMovesStockFromOneShopToAnother() {
        inventoryService.adjust(storeA.getId(), product.getId(), 10, "PURCHASE", "seed", null, null);

        var transfer = transferService.create(new PosDtos.CreateTransfer(
                storeA.getId(), storeB.getId(), "restocking", List.of(new PosDtos.TransferItemRequest(product.getId(), 4))), null);

        assertThat(transfer.status()).isEqualTo("IN_TRANSIT");
        assertThat(inventoryService.quantityOf(storeA.getId(), product.getId())).isEqualTo(6);
        assertThat(inventoryService.quantityOf(storeB.getId(), product.getId())).isEqualTo(0);

        var received = transferService.receive(transfer.id(), null);
        assertThat(received.status()).isEqualTo("COMPLETED");
        assertThat(inventoryService.quantityOf(storeB.getId(), product.getId())).isEqualTo(4);
    }

    @Test
    void cancellingAnInTransitTransferReturnsStockToTheSource() {
        inventoryService.adjust(storeA.getId(), product.getId(), 10, "PURCHASE", "seed", null, null);

        var transfer = transferService.create(new PosDtos.CreateTransfer(
                storeA.getId(), storeB.getId(), "", List.of(new PosDtos.TransferItemRequest(product.getId(), 3))), null);
        assertThat(inventoryService.quantityOf(storeA.getId(), product.getId())).isEqualTo(7);

        var cancelled = transferService.cancel(transfer.id(), null);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(inventoryService.quantityOf(storeA.getId(), product.getId())).isEqualTo(10);

        assertThatThrownBy(() -> transferService.receive(transfer.id(), null))
                .hasMessageContaining("awaiting receipt");
    }

    @Test
    void transferRefusesInsufficientStockAtSource() {
        assertThatThrownBy(() -> transferService.create(new PosDtos.CreateTransfer(
                storeA.getId(), storeB.getId(), "", List.of(new PosDtos.TransferItemRequest(product.getId(), 999))), null))
                .hasMessageContaining("Not enough stock");
    }
}
