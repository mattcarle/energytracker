package com.carle7.energytracker.repository;

// The date-truncation grain shared by the usage aggregate/breakdown query families in
// UsageRepositoryImpl - carries only the SQL expression (verbatim from the queries this
// replaces) so every grain-specific query is built from one shared template instead of one
// hand-copied query per grain.
enum Granularity {
    DAY("CAST(z.local_time AS DATE)"),
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
