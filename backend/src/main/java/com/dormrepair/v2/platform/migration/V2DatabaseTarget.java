package com.dormrepair.v2.platform.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

/** Fixed local allow-list. No arbitrary URL parameters, hosts or production names. */
public final class V2DatabaseTarget {
    private static final Pattern URL = Pattern.compile("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):5432/(dorm_repair_v2_dev|dorm_repair_v2_test(?:_[a-z0-9]+)?)");
    private V2DatabaseTarget() {}
    public static String database(String url) {
        var match = URL.matcher(url == null ? "" : url);
        if (!match.matches()) throw new IllegalStateException("V2 migration target must be an explicitly isolated local v2_dev/v2_test database");
        return match.group(1);
    }
    public static void verify(Connection connection) throws SQLException {
        String expected = database(connection.getMetaData().getURL());
        if (!expected.equals(connection.getCatalog())) throw new IllegalStateException("V2 database catalog does not match its guarded URL");
        if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) throw new IllegalStateException("V2 migrations require PostgreSQL");
    }
    public static void requireTestReset(Connection connection) throws SQLException {
        verify(connection);
        if (!connection.getCatalog().matches("dorm_repair_v2_test(?:_[a-z0-9]+)?"))
            throw new IllegalStateException("Only v2_test databases may be reset by test fixtures");
    }
}
