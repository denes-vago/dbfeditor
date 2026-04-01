package com.vd.dbfeditor.dbf;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DBFValidator {
    static final int MIN_HEADER_LENGTH = 32 + DBFIOUtil.FIELD_TERMINATOR_LENGTH;
    static final int MAX_FIELD_COUNT = 1024;
    static final int MAX_RECORD_LENGTH = 64 * 1024;
    static final long MAX_RECORD_COUNT = 2_000_000L;
    static final DateTimeFormatter DBF_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    static final Set<Character> SUPPORTED_FIELD_TYPES = Set.of('C', 'D', 'F', 'L', 'M', 'N');

    private DBFValidator() {
    }

    static void validateHeader(Path path, long fileSize, long recordCount, int headerLength, int recordLength) throws IOException {
        if (fileSize < MIN_HEADER_LENGTH) {
            throw new IOException("A fájl túl kicsi ahhoz, hogy érvényes DBF legyen: " + path.getFileName());
        }
        if (headerLength < MIN_HEADER_LENGTH || headerLength > fileSize) {
            throw new IOException("Érvénytelen DBF fejléc hossza: " + headerLength);
        }
        if (recordLength <= 0 || recordLength > MAX_RECORD_LENGTH) {
            throw new IOException("Érvénytelen DBF rekordhossz: " + recordLength);
        }
        if (recordCount < 0 || recordCount > MAX_RECORD_COUNT) {
            throw new IOException("A DBF fájlban túl sok rekord szerepel a fejléc szerint: " + recordCount);
        }
    }

    static void validateFieldDescriptor(Path path, int index, String name, char type, int length, int decimalCount) throws IOException {
        if (name.isBlank()) {
            throw new IOException("A DBF fejlécben üres mezőnév szerepel a(z) " + (index + 1) + ". mezőhelyen.");
        }
        if (!SUPPORTED_FIELD_TYPES.contains(type)) {
            throw new IOException("Nem támogatott mezőtípus a DBF fejlécben: " + type);
        }
        if (length <= 0 || length > 255) {
            throw new IOException("Érvénytelen mezőhossz a fejlécben: " + length);
        }
        if ((type == 'D' || type == 'L') && length != 1 && length != 8) {
            throw new IOException("Érvénytelen fix hosszú mező a fejlécben: " + name);
        }
        if (type == 'L' && length != 1) {
            throw new IOException("A logikai mező hossza csak 1 lehet: " + name);
        }
        if (type == 'D' && length != 8) {
            throw new IOException("A dátum mező hossza csak 8 lehet: " + name);
        }
        if ((type == 'N' || type == 'F') && decimalCount > length) {
            throw new IOException("Érvénytelen tizedes pontosság a fejlécben: " + name);
        }
        if (name.length() > 11) {
            throw new IOException("Túlságosan hosszú mezőnév a fejlécben a fájlban: " + path.getFileName());
        }
    }

    static void ensureRecordCountFits(Path path, long fileSize, int headerLength, int recordLength, long declaredCount) throws IOException {
        long dataBytes = Math.max(0L, fileSize - headerLength);
        long maxRecordsBySize = dataBytes / recordLength;
        long remainder = dataBytes % recordLength;
        if (remainder > 1) {
            throw new IOException("A DBF rekordblokk sérült vagy idegen adatot tartalmaz: " + path.getFileName());
        }
        if (declaredCount > maxRecordsBySize) {
            throw new IOException("A fejléc szerinti rekordszám nagyobb, mint ami a fájlméretből következik.");
        }
    }

    static void validateDbfForWrite(DBFEngine.DBFFile dbf, Charset charset) throws IOException {
        if (dbf == null) {
            throw new IOException("Nincs menthető DBF tartalom.");
        }
        if (dbf.fields().isEmpty()) {
            throw new IOException("Nincs menthető mezőleíró a DBF fájlban.");
        }
        for (DBFEngine.FieldDescriptor field : dbf.fields()) {
            validateFieldDescriptor(Path.of("<memory>"), 0, field.name(), field.type(), field.length(), field.decimalCount());
        }
        if (dbf.records().size() > MAX_RECORD_COUNT) {
            throw new IOException("Túl sok rekordot próbálsz menteni.");
        }
        for (List<String> row : dbf.records()) {
            for (int i = 0; i < dbf.fields().size(); i++) {
                DBFEngine.FieldDescriptor field = dbf.fields().get(i);
                String value = i < row.size() ? row.get(i) : "";
                String error = validateValue(field, value, charset);
                if (error != null) {
                    throw new IOException(field.name() + ": " + error);
                }
            }
        }
    }

    static String validateValue(DBFEngine.FieldDescriptor field, String value, Charset charset) {
        String safeValue = value == null ? "" : value;
        return switch (field.type()) {
            case 'D' -> validateDateValue(field, safeValue.trim());
            case 'L' -> validateLogicalValue(safeValue.trim());
            case 'M' -> null;
            case 'N', 'F' -> validateNumericValue(field, safeValue.trim(), charset);
            default -> validateCharacterValue(field, safeValue, charset);
        };
    }

    static String validateFieldDefinition(DBFEngine.FieldDescriptor field) {
        try {
            validateFieldDescriptor(Path.of("<memory>"), 0, field.name(), field.type(), field.length(), field.decimalCount());
            return null;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private static String validateDateValue(DBFEngine.FieldDescriptor field, String value) {
        if (value.isEmpty()) {
            return null;
        }
        String normalized = DBFValueCodec.normalizeDateForStorage(value);
        if (normalized == null || normalized.length() != field.length()) {
            return "A dátum mezőt yyyy-MM-dd vagy yyyyMMdd alakban add meg.";
        }
        try {
            LocalDate.parse(normalized, DBF_DATE);
        } catch (DateTimeParseException e) {
            return "A dátum érvénytelen naptári dátum.";
        }
        return null;
    }

    private static String validateLogicalValue(String value) {
        if (value.isEmpty()) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.equals("T") || upper.equals("F") || upper.equals("Y") || upper.equals("N") || upper.equals("TRUE") || upper.equals("FALSE")) {
            return null;
        }
        return "A logikai mezőben true/false, t/f vagy y/n érték adható meg.";
    }

    private static String validateNumericValue(DBFEngine.FieldDescriptor field, String value, Charset charset) {
        if (value.isEmpty()) {
            return null;
        }
        String normalized = value.replace(',', '.');
        BigDecimal number;
        try {
            number = new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return "A numerikus mezőben csak szám lehet.";
        }
        number = number.stripTrailingZeros();
        int scale = Math.max(0, number.scale());
        if (scale > field.decimalCount()) {
            return "A mezőben legfeljebb " + field.decimalCount() + " tizedesjegy lehet.";
        }
        String encodedValue = DBFValueCodec.formatNumericForStorage(field, normalized);
        byte[] encoded = encodedValue.getBytes(charset);
        if (encoded.length > field.length()) {
            return "A numerikus érték nem fér bele a " + field.length() + " karakteres mezőbe.";
        }
        return null;
    }

    private static String validateCharacterValue(DBFEngine.FieldDescriptor field, String value, Charset charset) {
        byte[] encoded = value.getBytes(charset);
        if (encoded.length > field.length()) {
            return "A mezőbe legfeljebb " + field.length() + " byte fér.";
        }
        return null;
    }
}
