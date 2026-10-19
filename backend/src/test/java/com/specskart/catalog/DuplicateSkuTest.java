package com.specskart.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A reused frame code must come back as a readable 409, not the catch-all 500 "Something went wrong." */
@SpringBootTest
@ActiveProfiles("mock")
class DuplicateSkuTest {

    @Autowired WebApplicationContext ctx;

    @Test
    void reusedCodeIsAClearConflict() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        String sku = "DUP-" + System.nanoTime();
        String body = "{\"name\":\"Legend\",\"sku\":\"" + sku + "\",\"priceMinor\":120000,\"frameCategoryCode\":\"CAT_EYE\",\"status\":\"ACTIVE\"}";

        mvc.perform(post("/api/admin/catalog/products").with(user("owner").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/catalog/products").with(user("owner").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace(sku, sku.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_TAKEN"));
    }
}
