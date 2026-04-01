package com.vd.dbfeditor.export;

public enum SqlDialect {
    GENERIC("sql.dialect.generic"),
    MYSQL("sql.dialect.mysql"),
    POSTGRESQL("sql.dialect.postgresql"),
    SQLITE("sql.dialect.sqlite");

    private final String labelKey;

    SqlDialect(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}
