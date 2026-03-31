package com.vd.dbfeditor.test;

import com.vd.dbfeditor.DBFEngine;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DBFEngineUnitTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testValidateFieldDefinitionAcceptsValidCharacterField();
        testValidateFieldDefinitionRejectsTooLongFieldName();
        testValidateValueAcceptsValidDateFormats();
        testValidateValueRejectsInvalidCalendarDate();
        testValidateValueRejectsTooManyDecimalPlaces();
        testValidateValueAcceptsLogicalAliases();
        testWriteReadRoundTripPreservesSchemaAndValues();
        testMemoRoundTripPreservesLongText();

        System.out.println("OK - assertions=" + assertions);
    }

    // A valid character field definition should pass without any validation error.
    private static void testValidateFieldDefinitionAcceptsValidCharacterField() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("NAME", 'C', 20, 0);
        assertNull(DBFEngine.validateFieldDefinition(field), "A valid field definition should not produce an error.");
    }

    // Field names longer than the DBF limit must be rejected.
    private static void testValidateFieldDefinitionRejectsTooLongFieldName() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("TOO_LONG_NAME", 'C', 20, 0);
        String error = DBFEngine.validateFieldDefinition(field);
        assertNotNull(error, "A too-long field name should be rejected.");
        assertTrue(error.contains("mezőnév"), "The validation message should mention the field name problem.");
    }

    // The engine accepts both storage and editor-friendly date formats.
    private static void testValidateValueAcceptsValidDateFormats() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("DATEFLD", 'D', 8, 0);
        assertNull(DBFEngine.validateValue(field, "2024-03-15", DBFEngine.DEFAULT_CHARSET), "yyyy-MM-dd should be accepted.");
        assertNull(DBFEngine.validateValue(field, "20240315", DBFEngine.DEFAULT_CHARSET), "yyyyMMdd should be accepted.");
    }

    // Invalid calendar dates must fail even if the textual format itself looks correct.
    private static void testValidateValueRejectsInvalidCalendarDate() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("DATEFLD", 'D', 8, 0);
        String error = DBFEngine.validateValue(field, "2024-02-30", DBFEngine.DEFAULT_CHARSET);
        assertNotNull(error, "An invalid calendar date should be rejected.");
        assertTrue(error.contains("dátum") || error.contains("Dátum"), "The error message should indicate a date problem.");
    }

    // Numeric fields enforce the declared decimal precision.
    private static void testValidateValueRejectsTooManyDecimalPlaces() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("AMOUNT", 'N', 8, 2);
        String error = DBFEngine.validateValue(field, "12.345", DBFEngine.DEFAULT_CHARSET);
        assertNotNull(error, "Too many decimal places should be rejected.");
        assertTrue(error.contains("tizedes"), "The error message should indicate decimal precision.");
    }

    // Logical fields accept the common aliases used by the UI and DBF files.
    private static void testValidateValueAcceptsLogicalAliases() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("ACTIVE", 'L', 1, 0);
        assertNull(DBFEngine.validateValue(field, "true", DBFEngine.DEFAULT_CHARSET), "true should be accepted for logical fields.");
        assertNull(DBFEngine.validateValue(field, "N", DBFEngine.DEFAULT_CHARSET), "N should be accepted for logical fields.");
    }

    // Writing and then reading a DBF should preserve schema and normalized field values.
    private static void testWriteReadRoundTripPreservesSchemaAndValues() throws Exception {
        Charset charset = Charset.forName("Cp852");
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("NAME", 'C', 12, 0),
            new DBFEngine.FieldDescriptor("AMOUNT", 'N', 8, 2),
            new DBFEngine.FieldDescriptor("ACTIVE", 'L', 1, 0),
            new DBFEngine.FieldDescriptor("DATEFLD", 'D', 8, 0)
        );

        List<List<String>> records = new ArrayList<>();
        records.add(new ArrayList<>(List.of("ALFA", "12.50", "true", "2024-03-15")));
        records.add(new ArrayList<>(List.of("BETA", "-1.25", "N", "20240316")));

        DBFEngine.DBFFile original = new DBFEngine.DBFFile(
            0x03,
            LocalDate.of(2024, 3, 15),
            records.size(),
            32 + fields.size() * 32 + 1,
            1 + 12 + 8 + 1 + 8,
            fields,
            records
        );

        Path tempFile = Files.createTempFile("dbfengine-unit-", ".dbf");
        try {
            DBFEngine.write(tempFile, charset, original);
            DBFEngine.DBFFile loaded = DBFEngine.read(tempFile, charset);

            assertEquals(4, loaded.fields().size(), "The schema should keep all fields.");
            assertEquals(2, loaded.records().size(), "The record count should be preserved.");
            assertEquals("ALFA", loaded.records().get(0).get(0).trim(), "Character values should round-trip.");
            assertEquals("12.50", loaded.records().get(0).get(1).trim(), "Numeric values should round-trip.");
            assertEquals("true", loaded.records().get(0).get(2), "Logical values should normalize to true/false.");
            assertEquals("2024-03-15", loaded.records().get(0).get(3), "Date values should normalize to yyyy-MM-dd.");
            assertEquals("false", loaded.records().get(1).get(2), "Logical false aliases should round-trip.");
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(Path.of(tempFile.toString().replaceFirst("\\.[^.]+$", "") + ".DBT"));
        }
    }

    // Memo fields should be stored in the companion DBT file and read back as full text.
    private static void testMemoRoundTripPreservesLongText() throws Exception {
        Charset charset = Charset.forName("Cp852");
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("TITLE", 'C', 12, 0),
            new DBFEngine.FieldDescriptor("MEMO", 'M', 10, 0)
        );

        String memoText = "First line of memo\nSecond line of memo\nThird line of memo";
        List<List<String>> records = new ArrayList<>();
        records.add(new ArrayList<>(List.of("ENTRY", memoText)));

        DBFEngine.DBFFile original = new DBFEngine.DBFFile(
            0x83,
            LocalDate.of(2024, 3, 15),
            records.size(),
            32 + fields.size() * 32 + 1,
            1 + 12 + 10,
            fields,
            records
        );

        Path tempFile = Files.createTempFile("dbfengine-memo-", ".dbf");
        Path tempMemoFile = Path.of(tempFile.toString().replaceFirst("\\.[^.]+$", "") + ".DBT");
        try {
            DBFEngine.write(tempFile, charset, original);
            DBFEngine.DBFFile loaded = DBFEngine.read(tempFile, charset);

            assertTrue(Files.exists(tempMemoFile), "Writing a memo field should create a DBT file.");
            assertEquals("ENTRY", loaded.records().get(0).get(0).trim(), "The character field should round-trip.");
            assertEquals(memoText, loaded.records().get(0).get(1), "The memo field should round-trip through the DBT file.");
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempMemoFile);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertNull(Object value, String message) {
        assertions++;
        if (value != null) {
            throw new AssertionError(message + " Actual: " + value);
        }
    }

    private static void assertNotNull(Object value, String message) {
        assertions++;
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " Expected: " + expected + ", actual: " + actual);
        }
    }
}
