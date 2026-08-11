package com.carle7.energytracker.repository;

// The date-truncation grain shared by the usage aggregate/breakdown query families in
// UsageRepositoryImpl - carries only the SQL expression (verbatim from the queries this
// replaces) so every grain-specific query is built from one shared template instead of one
// hand-copied query per grain.
enum Granularity {
    // Unlike the other grains, HALF_HOUR doesn't truncate - z.local_time is already the finest
    // resolution usage is recorded at, so each row IS a half-hour interval rather than a bucket
    // of them. Its period column is a full timestamp (LocalDateTime), not a DATE like the others.
    HALF_HOUR("z.local_time"),
    DAY("CAST(z.local_time AS DATE)"),
    // H2's DATE_TRUNC('WEEK', ...) truncates to the ISO-8601 week start, i.e. Monday.
    WEEK("CAST(DATE_TRUNC('WEEK', z.local_time) AS DATE)"),
    MONTH("CAST(DATE_TRUNC('MONTH', z.local_time) AS DATE)"),
    YEAR("CAST(DATE_TRUNC('YEAR', z.local_time) AS DATE)");

    private final String sqlExpression;

    Granularity(String sqlExpression) {
        this.sqlExpression = sqlExpression;
    }

    String sqlExpression() {
        return sqlExpression;
    }
}
