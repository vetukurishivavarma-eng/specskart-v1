package com.specskart.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Staff (AGENT) run the till but must never see or change what a frame cost us. */
@SpringBootTest
@ActiveProfiles("mock")
class CostPriceIsAdminOnlyTest {

    @Autowired WebApplicationContext ctx;
    @Autowired ProductRepository products;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void staffSeeNoCostAndCannotChangeIt() throws Exception {
        Product p = new Product();
        p.setSlug("cost-test-" + System.nanoTime());
        p.setName("Cost Test");
        p.setPriceMinor(90000);
        p.setCostPriceMinor(40000);
        p.setStatus("ACTIVE");
        products.save(p);
        String url = "/api/admin/catalog/products/" + p.getId();

        mvc().perform(get(url).with(user("owner").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.costPriceMinor").value(40000));
        mvc().perform(get(url).with(user("staff").roles("AGENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.costPriceMinor").doesNotExist());

        mvc().perform(put(url).with(user("staff").roles("AGENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cost Test\",\"priceMinor\":90000,\"costPriceMinor\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.costPriceMinor").doesNotExist());
        assertThat(products.findById(p.getId()).orElseThrow().getCostPriceMinor()).isEqualTo(40000);
    }

    @Test
    void supplierInvoicesAreAdminOnly() throws Exception {
        mvc().perform(get("/api/admin/pos/invoices").with(user("staff").roles("AGENT"))).andExpect(status().isForbidden());
    }
}
