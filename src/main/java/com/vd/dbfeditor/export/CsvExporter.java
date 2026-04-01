package com.vd.dbfeditor.export;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.util.List;

public final class CsvExporter {
    private CsvExporter() {
    }

    public static String export(DBFEngine.DBFFile dbf) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < dbf.fields().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csvValue(dbf.fields().get(i).name()));
        }
        builder.append(System.lineSeparator());

        for (List<String> record : dbf.records()) {
            for (int i = 0; i < dbf.fields().size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                String value = i < record.size() ? record.get(i) : "";
                builder.append(csvValue(value));
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String csvValue(String value) {
        String normalized = value == null ? "" : value;
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }
}
