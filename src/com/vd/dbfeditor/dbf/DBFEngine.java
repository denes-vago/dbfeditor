package com.vd.dbfeditor.dbf;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class DBFEngine {
    public static final Charset DEFAULT_CHARSET = Charset.forName("IBM437");

    private DBFEngine() {
    }

    public static DBFFile read(Path path, Charset charset) throws IOException {
        return DBFReader.read(path, charset);
    }

    public static void write(Path path, Charset charset, DBFFile dbf) throws IOException {
        DBFWriter.write(path, charset, dbf);
    }

    public static String validateValue(FieldDescriptor field, String value, Charset charset) {
        return DBFValidator.validateValue(field, value, charset);
    }

    public static String validateFieldDefinition(FieldDescriptor field) {
        return DBFValidator.validateFieldDefinition(field);
    }

    public record FieldDescriptor(String name, char type, int length, int decimalCount) {
    }

    public record DBFFile(
        int version,
        LocalDate lastUpdate,
        long recordCount,
        int headerLength,
        int recordLength,
        List<FieldDescriptor> fields,
        List<List<String>> records
    ) {
    }
}
