package com.vd.dbfeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DBFEngineUnitTest {

    @Test
    void validateFieldDefinitionAcceptsValidCharacterField() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("NAME", 'C', 20, 0);
        assertNull(DBFEngine.validateFieldDefinition(field));
    }

    @Test
    void validateFieldDefinitionRejectsTooLongFieldName() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("TOO_LONG_NAME", 'C', 20, 0);
        String error = DBFEngine.validateFieldDefinition(field);
        assertNotNull(error);
        assertTrue(error.contains("mezőnév"));
    }

    @Test
    void validateValueAcceptsValidDateFormats() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("DATEFLD", 'D', 8, 0);
        assertNull(DBFEngine.validateValue(field, "2024-03-15", DBFEngine.DEFAULT_CHARSET));
        assertNull(DBFEngine.validateValue(field, "20240315", DBFEngine.DEFAULT_CHARSET));
    }

    @Test
    void validateValueRejectsInvalidCalendarDate() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("DATEFLD", 'D', 8, 0);
        String error = DBFEngine.validateValue(field, "2024-02-30", DBFEngine.DEFAULT_CHARSET);
        assertNotNull(error);
        assertTrue(error.contains("dátum") || error.contains("Dátum"));
    }

    @Test
    void validateValueRejectsTooManyDecimalPlaces() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("AMOUNT", 'N', 8, 2);
        String error = DBFEngine.validateValue(field, "12.345", DBFEngine.DEFAULT_CHARSET);
        assertNotNull(error);
        assertTrue(error.contains("tizedes"));
    }

    @Test
    void validateValueAcceptsLogicalAliases() {
        DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor("ACTIVE", 'L', 1, 0);
        assertNull(DBFEngine.validateValue(field, "true", DBFEngine.DEFAULT_CHARSET));
        assertNull(DBFEngine.validateValue(field, "N", DBFEngine.DEFAULT_CHARSET));
    }

    @Test
    void writeReadRoundTripPreservesSchemaAndValues() throws Exception {
        Charset charset = Charset.forName("IBM852");
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
            records,
            new ArrayList<>(List.of(false, false)),
            new ArrayList<>()
        );

        Path tempFile = Files.createTempFile("dbfengine-unit-", ".dbf");
        try {
            DBFEngine.write(tempFile, charset, original);
            DBFEngine.DBFFile loaded = DBFEngine.read(tempFile, charset);

            assertEquals(4, loaded.fields().size());
            assertEquals(2, loaded.records().size());
            assertEquals("ALFA", loaded.records().get(0).get(0).trim());
            assertEquals("12.50", loaded.records().get(0).get(1).trim());
            assertEquals("true", loaded.records().get(0).get(2));
            assertEquals("2024-03-15", loaded.records().get(0).get(3));
            assertEquals("false", loaded.records().get(1).get(2));
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(Path.of(tempFile.toString().replaceFirst("\\.[^.]+$", "") + ".DBT"));
        }
    }

    @Test
    void deletedFlagsRoundTripPreservesDeletedRecords() throws Exception {
        Charset charset = Charset.forName("IBM852");
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("NAME", 'C', 12, 0)
        );

        List<List<String>> records = new ArrayList<>();
        records.add(new ArrayList<>(List.of("ACTIVE")));
        records.add(new ArrayList<>(List.of("DELETED")));

        DBFEngine.DBFFile original = new DBFEngine.DBFFile(
            0x03,
            LocalDate.of(2024, 3, 15),
            records.size(),
            32 + fields.size() * 32 + 1,
            1 + 12,
            fields,
            records,
            new ArrayList<>(List.of(false, true)),
            new ArrayList<>()
        );

        Path tempFile = Files.createTempFile("dbfengine-deleted-", ".dbf");
        try {
            DBFEngine.write(tempFile, charset, original);
            DBFEngine.DBFFile loaded = DBFEngine.read(tempFile, charset);

            assertEquals(2, loaded.deletedFlags().size());
            assertFalse(loaded.deletedFlags().get(0));
            assertTrue(loaded.deletedFlags().get(1));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void memoRoundTripPreservesLongText() throws Exception {
        Charset charset = Charset.forName("IBM852");
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
            records,
            new ArrayList<>(List.of(false)),
            new ArrayList<>()
        );

        Path tempFile = Files.createTempFile("dbfengine-memo-", ".dbf");
        Path tempMemoFile = Path.of(tempFile.toString().replaceFirst("\\.[^.]+$", "") + ".DBT");
        try {
            DBFEngine.write(tempFile, charset, original);
            DBFEngine.DBFFile loaded = DBFEngine.read(tempFile, charset);

            assertTrue(Files.exists(tempMemoFile));
            assertEquals("ENTRY", loaded.records().get(0).get(0).trim());
            assertEquals(memoText, loaded.records().get(0).get(1));
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempMemoFile);
        }
    }

    @Test
    void missingMemoFileProducesWarning() throws Exception {
        Charset charset = Charset.forName("IBM852");
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("TITLE", 'C', 12, 0),
            new DBFEngine.FieldDescriptor("MEMO", 'M', 10, 0)
        );

        List<List<String>> records = new ArrayList<>();
        records.add(new ArrayList<>(List.of("ENTRY", "Memo text")));

        DBFEngine.DBFFile original = new DBFEngine.DBFFile(
            0x83,
            LocalDate.of(2024, 3, 15),
            records.size(),
            32 + fields.size() * 32 + 1,
            1 + 12 + 10,
            fields,
            records,
            new ArrayList<>(List.of(false)),
            new ArrayList<>()
        );

        Path tempFile = Files.createTempFile("dbfengine-missing-memo-", ".dbf");
        Path tempMemoFile = Path.of(tempFile.toString().replaceFirst("\\.[^.]+$", "") + ".DBT");
        try {
            DBFEngine.write(tempFile, charset, original);
            Files.deleteIfExists(tempMemoFile);

            DBFEngine.DBFFile loaded = DBFEngine.read(tempFile, charset);
            assertFalse(loaded.memoWarnings().isEmpty());
            assertTrue(loaded.memoWarnings().get(0).contains(".DBT"));
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempMemoFile);
        }
    }
}
