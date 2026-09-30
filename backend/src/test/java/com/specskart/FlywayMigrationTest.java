package com.specskart;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the production V1 migration against an H2 database in PostgreSQL-compatibility mode.
 * Catches gross SQL errors in db/migration without needing a real PostgreSQL / Testcontainers.
 * It is NOT a substitute for `flyway validate` against real PostgreSQL on first prod deploy.
 */
class FlywayMigrationTest {

    @Test
    void v1MigrationAppliesCleanly() {
        var result = flyway("51", false).migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);
        // V52 uses Postgres-only `on conflict`, which H2 can't parse. It already ran in prod, so it
        // can't be edited (checksum) -- record it as applied here and carry on with the rest.
        flyway("52", true).migrate();
        assertThat(flyway("latest", false).migrate().success).isTrue();
    }

    private static Flyway flyway(String target, boolean skip) {
        return Flyway.configure()
                .dataSource("jdbc:h2:mem:flyway_check;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .target(target)
                .skipExecutingMigrations(skip)
                .load();
    }
}
