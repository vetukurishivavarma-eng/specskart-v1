package com.specskart.order;

import com.specskart.catalog.Product;
import com.specskart.catalog.ProductRepository;
import com.specskart.whatsapp.MockWhatsAppProvider;
import com.specskart.whatsapp.WhatsAppProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With the API's public origin known, staff get the full order slip as a PDF — and only the
 *  signed link Meta was given can open it. */
@SpringBootTest
@ActiveProfiles("mock")
@TestPropertySource(properties = {
        "specskart.whatsapp.staff-numbers=+260999000333",
        "specskart.whatsapp.asset-base-url=https://api.example.test"})
class StaffOrderPdfTest {

    @Autowired ProductRepository products;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired WhatsAppProvider whatsapp;
    @Autowired WebApplicationContext ctx;

    @Test
    void staffGetTheOrderSlipPdfAndOnlyTheSignedLinkOpensIt() throws Exception {
        Product p = new Product();
        p.setSlug("staff-pdf-frame-" + System.nanoTime());
        p.setName("Aviator Gold");
        p.setPriceMinor(800_00L);
        p.setStockQty(2);
        p.setStatus("ACTIVE");
        p.setLensable(true);
        p = products.save(p);

        var cart = carts.addItem(null, p.getId(), 1);
        carts.setLens(cart.token(), "SINGLE_VISION", null);
        var result = checkout.start(cart.token(), new OrderDtos.CheckoutRequest(
                "Grace Banda", "260971234567", null, "5 Great East Rd", "Lusaka", null, null));

        var mock = (MockWhatsAppProvider) whatsapp;
        int before = mock.outbox().size();
        checkout.confirmPayment(result.orderNo());

        var doc = mock.outbox().subList(before, mock.outbox().size()).stream()
                .filter(s -> "+260999000333".equals(s.toWaId()) && s.documentUrl() != null)
                .findFirst().orElseThrow();
        assertThat(doc.text())
                .contains("New order " + result.orderNo())
                .contains("Aviator Gold ×1")
                .contains("5 Great East Rd")
                .contains("Lenses: Single vision")
                .contains("NO prescription on file");
        String prefix = "https://api.example.test";
        assertThat(doc.documentUrl()).startsWith(prefix + "/api/public/staff-docs/orders/" + result.orderNo() + ".pdf?exp=");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        String path = doc.documentUrl().substring(prefix.length());
        byte[] pdf = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF-1.4").contains("Name: Grace Banda");

        mvc.perform(get(path.replaceAll("sig=[^&]+", "sig=forged"))).andExpect(status().isForbidden());
    }
}
