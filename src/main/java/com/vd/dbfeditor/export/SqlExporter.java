package com.vd.dbfeditor.export;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.util.List;
import java.util.Locale;

public final class SqlExporter {
    private SqlExporter() {
    }

    public static String export(DBFEngine.DBFFile dbf, String tableName, SqlDialect dialect) {
        StringBuilder builder = new StringBuilder();
        builder.append("CREATE TABLE ").append(sqlIdentifier(tableName, dialect)).append(" (").append(System.lineSeparator());
        for (int i = 0; i < dbf.fields().size(); i++) {
            DBFEngine.FieldDescriptor field = dbf.fields().get(i);
            builder.append("    ")
                .append(sqlIdentifier(field.name(), dialect))
                .append(' ')
                .append(sqlType(field, dialect));
            if (i < dbf.fields().size() - 1) {
                builder.append(',');
            }
            builder.append(System.lineSeparator());
        }
        builder.append(");").append(System.lineSeparator()).append(System.lineSeparator());

        for (List<String> record : dbf.records()) {
            builder.append("INSERT INTO ").append(sqlIdentifier(tableName, dialect)).append(" (");
            for (int i = 0; i < dbf.fields().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(sqlIdentifier(dbf.fields().get(i).name(), dialect));
            }
            builder.append(") VALUES (");
            for (int i = 0; i < dbf.fields().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                String value = i < record.size() ? record.get(i) : "";
                builder.append(sqlValue(dbf.fields().get(i), value, dialect));
            }
            builder.append(");").append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String sqlType(DBFEngine.FieldDescriptor field, SqlDialect dialect) {
        return switch (field.type()) {
            case 'C' -> "VARCHAR(" + Math.max(field.length(), 1) + ")";
            case 'D' -> "DATE";
            case 'F', 'N' -> numericSqlType(field);
            case 'L' -> booleanSqlType(dialect);
            case 'M' -> "TEXT";
            default -> "TEXT";
        };
    }

    private static String numericSqlType(DBFEngine.FieldDescriptor field) {
        if (field.decimalCount() > 0) {
            return "DECIMAL(" + Math.max(field.length(), 1) + ", " + field.decimalCount() + ")";
        }
        return "INTEGER";
    }

    private static String booleanSqlType(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> "INTEGER";
            case GENERIC, MYSQL, POSTGRESQL -> "BOOLEAN";
        };
    }

    private static String sqlValue(DBFEngine.FieldDescriptor field, String value, SqlDialect dialect) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "NULL";
        }

        return switch (field.type()) {
            case 'F', 'N' -> normalized.replace(',', '.');
            case 'L' -> sqlBooleanValue(normalized, dialect);
            case 'D' -> sqlDateValue(normalized);
            default -> "'" + normalized.replace("'", "''") + "'";
        };
    }

    private static String sqlBooleanValue(String value, SqlDialect dialect) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        boolean truthy = switch (normalized) {
            case "T", "Y", "I", "1", "TRUE" -> true;
            case "F", "N", "0", "FALSE" -> false;
            default -> false;
        };
        boolean recognized = switch (normalized) {
            case "T", "Y", "I", "1", "TRUE", "F", "N", "0", "FALSE" -> true;
            default -> false;
        };
        if (!recognized) {
            return "NULL";
        }
        return switch (dialect) {
            case SQLITE -> truthy ? "1" : "0";
            case GENERIC, MYSQL, POSTGRESQL -> truthy ? "TRUE" : "FALSE";
        };
    }

    private static String sqlDateValue(String value) {
        String normalized = value.trim();
        if (normalized.length() == 8 && normalized.chars().allMatch(Character::isDigit)) {
            return "'" + normalized.substring(0, 4) + "-" + normalized.substring(4, 6) + "-" + normalized.substring(6, 8) + "'";
        }
        return "'" + normalized.replace("'", "''") + "'";
    }

    private static String sqlIdentifier(String name, SqlDialect dialect) {
        return switch (dialect) {
            case MYSQL -> "`" + name.replace("`", "``") + "`";
            case GENERIC, POSTGRESQL, SQLITE -> "\"" + name.replace("\"", "\"\"") + "\"";
        };
    }
}
