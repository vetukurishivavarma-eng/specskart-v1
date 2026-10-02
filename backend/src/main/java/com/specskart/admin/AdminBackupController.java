package com.specskart.admin;

import com.specskart.audit.AuditLogService;
import com.specskart.shared.ApiException;
import com.specskart.shared.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ADMIN-only: the whole database as one zip -- one CSV per table (Postgres COPY), a manifest
 * with row counts, and a psql restore script that loads the tables in foreign-key order.
 *
 * Exists because the database's IP allow-list can lock pg_dump out from wherever the owner
 * happens to be; this runs inside Render next to the database. Read-only, one consistent
 * snapshot (REPEATABLE READ), streamed so memory stays flat.
 *
 * ponytail: data only -- the schema is the Flyway migrations in this repo. Restore = point the
 * app at an empty database once (Flyway builds the schema), then run restore.sql from the
 * unzipped folder. Swap for a real pg_dump job if the database outgrows a streamed CSV export.
 */
@RestController
@RequestMapping("/api/admin/backup")
public class AdminBackupController {

    private static final Logger log = LoggerFactory.getLogger(AdminBackupController.class);
    /** Flyway's own bookkeeping: rebuilt by the migrations themselves, never restored over. */
    private static final String FLYWAY = "flyway_schema_history";

    private final DataSource dataSource;
    private final CurrentUser currentUser;
    private final AuditLogService audit;

    public AdminBackupController(DataSource dataSource, CurrentUser currentUser, AuditLogService audit) {
        this.dataSource = dataSource;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping
    public void download(HttpServletResponse res, Authentication auth) throws SQLException, IOException {
        String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").withZone(ZoneOffset.UTC).format(Instant.now());
        try (Connection c = dataSource.getConnection()) {
            CopyManager copy;
            try {
                copy = c.unwrap(PGConnection.class).getCopyAPI();
            } catch (SQLException notPostgres) {
                throw ApiException.badRequest("BACKUP_UNSUPPORTED", "Backups only work against PostgreSQL.");
            }
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (Statement s = c.createStatement()) {
                    s.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
                }
                List<String> tables = tables(c);
                List<String> order = loadOrder(tables, foreignKeys(c));

                res.setContentType("application/zip");
                res.setHeader("Content-Disposition", "attachment; filename=\"specskart-backup-" + stamp + ".zip\"");
                StringBuilder manifest = new StringBuilder("table,rows\n");
                long total = 0;
                try (ZipOutputStream zip = new ZipOutputStream(res.getOutputStream())) {
                    OutputStream keepOpen = new FilterOutputStream(zip) {
                        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
                        @Override public void close() throws IOException { flush(); }
                    };
                    for (String t : order) {
                        zip.putNextEntry(new ZipEntry("data/" + t + ".csv"));
                        long rows = copy.copyOut("COPY public.\"" + t + "\" TO STDOUT WITH (FORMAT csv, HEADER true)", keepOpen);
                        zip.closeEntry();
                        manifest.append(t).append(',').append(rows).append('\n');
                        total += rows;
                    }
                    put(zip, "manifest.csv", manifest.toString());
                    put(zip, "restore.sql", restoreScript(order));
                    put(zip, "README.txt", readme(stamp, order.size(), total));
                }
                log.info("database backup streamed: {} tables, {} rows", order.size(), total);
                audit.record("BACKUP", "database", "DOWNLOAD", currentUser.idOf(auth), currentUser.nameOf(auth),
                        null, order.size() + " tables · " + total + " rows");
            } finally {
                c.rollback();
                c.setAutoCommit(auto);
            }
        }
    }

    private static List<String> tables(Connection c) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE' order by table_name")) {
            while (r.next()) out.add(r.getString(1));
        }
        return out;
    }

    /** child table -> the tables it references. */
    private static Map<String, Set<String>> foreignKeys(Connection c) throws SQLException {
        Map<String, Set<String>> deps = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "select tc.table_name, ccu.table_name from information_schema.table_constraints tc "
                        + "join information_schema.constraint_column_usage ccu "
                        + "  on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema "
                        + "where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = 'public'")) {
            while (r.next()) deps.computeIfAbsent(r.getString(1), k -> new HashSet<>()).add(r.getString(2));
        }
        return deps;
    }

    /** Parents before children, so a plain COPY FROM per table never trips a foreign key.
     *  Self-references are ignored; a genuine cycle falls back to alphabetical for the rest. */
    static List<String> loadOrder(List<String> tables, Map<String, Set<String>> deps) {
        List<String> order = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        List<String> left = new ArrayList<>(tables);
        Collections.sort(left);
        while (!left.isEmpty()) {
            boolean progressed = false;
            for (Iterator<String> it = left.iterator(); it.hasNext(); ) {
                String t = it.next();
                boolean ready = deps.getOrDefault(t, Set.of()).stream()
                        .allMatch(p -> p.equals(t) || placed.contains(p) || !tables.contains(p));
                if (ready) {
                    order.add(t);
                    placed.add(t);
                    it.remove();
                    progressed = true;
                }
            }
            if (!progressed) { // a cycle: keep going rather than lose tables
                order.addAll(left);
                break;
            }
        }
        return order;
    }

    static String restoreScript(List<String> order) {
        List<String> data = order.stream().filter(t -> !t.equals(FLYWAY)).toList();
        StringBuilder sql = new StringBuilder("""
                -- Specskart restore. Run from the unzipped folder, against a database the app has
                -- already started on once (so Flyway has built the schema):
                --   psql "<connection url>" -f restore.sql
                \\set ON_ERROR_STOP on
                BEGIN;
                """);
        // Emptied first: Flyway seeds some rows (price list, admins) that the backup also holds.
        sql.append("TRUNCATE ").append(String.join(", ", data.stream().map(t -> "public.\"" + t + "\"").toList()))
                .append(" CASCADE;\n");
        for (String t : data) {
            sql.append("\\copy public.\"").append(t).append("\" FROM 'data/").append(t)
                    .append(".csv' WITH (FORMAT csv, HEADER true)\n");
        }
        return sql.append("COMMIT;\n").toString();
    }

    private static String readme(String stamp, int tables, long rows) {
        return "Specskart database backup, taken " + stamp + " UTC: " + tables + " tables, " + rows + " rows.\n\n"
                + "data/*.csv     one file per table (Postgres CSV with a header row)\n"
                + "manifest.csv   row count per table -- compare after a restore\n"
                + "restore.sql    loads everything back in foreign-key order\n\n"
                + "To restore into a new database:\n"
                + "  1. Point the API's DB_URL/DB_USERNAME/DB_PASSWORD at the new, empty database and deploy\n"
                + "     once, so Flyway creates the schema.\n"
                + "  2. From this folder: psql \"<new database external URL>\" -f restore.sql\n"
                + "  3. Check the row counts against manifest.csv.\n";
    }

    private static void put(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
