package com.specskart.admin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBackupControllerTest {

    @Test
    void parentsLoadBeforeChildrenAndNothingIsDropped() {
        var tables = List.of("sale_items", "flyway_schema_history", "leads", "sales", "stores", "lens_inquiries");
        var deps = Map.of(
                "sale_items", Set.of("sales"),
                "sales", Set.of("stores"),
                "lens_inquiries", Set.of("leads", "lens_inquiries")); // self-reference ignored
        var order = AdminBackupController.loadOrder(tables, deps);

        assertThat(order).containsExactlyInAnyOrderElementsOf(tables);
        assertThat(order.indexOf("stores")).isLessThan(order.indexOf("sales"));
        assertThat(order.indexOf("sales")).isLessThan(order.indexOf("sale_items"));
        assertThat(order.indexOf("leads")).isLessThan(order.indexOf("lens_inquiries"));
    }

    @Test
    void aCycleStillExportsEveryTable() {
        var order = AdminBackupController.loadOrder(List.of("a", "b", "c"),
                Map.of("a", Set.of("b"), "b", Set.of("a")));
        assertThat(order).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void restoreSkipsFlywayHistoryAndEmptiesSeededTablesFirst() {
        String sql = AdminBackupController.restoreScript(List.of("flyway_schema_history", "stores", "sales"));
        assertThat(sql).doesNotContain("data/flyway_schema_history.csv");
        assertThat(sql.indexOf("TRUNCATE")).isLessThan(sql.indexOf("\\copy public.\"stores\""));
        assertThat(sql.indexOf("\\copy public.\"stores\"")).isLessThan(sql.indexOf("\\copy public.\"sales\""));
    }
}
