package com.vd.dbfeditor.dbf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

final class DBFValueCodec {
    private DBFValueCodec() {
    }

    static ValueReadResult formatValue(
        DBFEngine.FieldDescriptor field,
        byte[] bytes,
        int offset,
        Charset charset,
        byte[] memoData,
        int rowIndex
    ) {
        String raw = new String(bytes, offset, field.length(), charset);
        String trimmed = DBFIOUtil.trimRight(raw).trim();
        if (trimmed.isEmpty()) {
            return new ValueReadResult("", null);
        }
        return switch (field.type()) {
            case 'D' -> new ValueReadResult(formatDate(trimmed), null);
            case 'L' -> new ValueReadResult(formatLogical(trimmed), null);
            case 'M' -> {
                if (memoData != null) {
                    DBFIOUtil.MemoReadResult memo = DBFIOUtil.readMemo(memoData, charset, trimmed, field.name(), rowIndex);
                    yield new ValueReadResult(memo.value(), memo.warning());
                }
                yield new ValueReadResult(DBFIOUtil.trimRight(raw), null);
            }
            default -> new ValueReadResult(DBFIOUtil.trimRight(raw), null);
        };
    }

    static String normalizeForStorage(DBFEngine.FieldDescriptor field, String value) {
        String safeValue = value == null ? "" : value;
        return switch (field.type()) {
            case 'D' -> safeValue.trim().isEmpty() ? "" : normalizeDateForStorage(safeValue.trim());
            case 'L' -> normalizeLogicalForStorage(safeValue.trim());
            case 'M' -> safeValue;
            case 'N', 'F' -> formatNumericForStorage(field, safeValue.trim());
            default -> safeValue;
        };
    }

    static String normalizeDateForStorage(String value) {
        if (value.length() == 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            value = value.substring(0, 4) + value.substring(5, 7) + value.substring(8, 10);
        }
        if (value.length() != 8) {
            return null;
        }
        try {
            LocalDate.parse(value, DBFValidator.DBF_DATE);
            return value;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String formatNumericForStorage(DBFEngine.FieldDescriptor field, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            BigDecimal number = new BigDecimal(value.replace(',', '.'));
            if (field.decimalCount() == 0) {
                number = number.setScale(0, RoundingMode.UNNECESSARY);
            } else {
                number = number.setScale(field.decimalCount(), RoundingMode.UNNECESSARY);
            }
            return number.toPlainString();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static String formatDate(String value) {
        if (value.length() == 8) {
            return value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8);
        }
        return value;
    }

    private static String formatLogical(String value) {
        char flag = Character.toUpperCase(value.charAt(0));
        return switch (flag) {
            case 'T', 'Y' -> "true";
            case 'F', 'N' -> "false";
            default -> "?";
        };
    }

    private static String normalizeLogicalForStorage(String value) {
        if (value.isEmpty()) {
            return "";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "TRUE", "T", "Y" -> "T";
            case "FALSE", "F", "N" -> "F";
            default -> null;
        };
    }

    record ValueReadResult(String value, String warning) {
    }
}
