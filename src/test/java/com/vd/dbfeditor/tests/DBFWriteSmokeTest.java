package com.vd.dbfeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DBFWriteSmokeTest {

    @Test
    void writeSmokeTestPersistsSimpleEdit() throws Exception {
        Charset charset = Charset.forName("ISO-8859-1");
        Path source = Path.of("IR_SZAM.DBF");
        Path target = Path.of("IR_SZAM_test.DBF");

        DBFEngine.DBFFile dbf = DBFEngine.read(source, charset);
        String originalValue = dbf.records().get(0).get(1);
        dbf.records().get(0).set(1, "TESZT");

        try {
            DBFEngine.write(target, charset, dbf);
            DBFEngine.DBFFile saved = DBFEngine.read(target, charset);

            assertEquals("TESZT", saved.records().get(0).get(1));
            assertEquals(dbf.records().size(), saved.records().size());
        } finally {
            dbf.records().get(0).set(1, originalValue);
            Files.deleteIfExists(target);
        }
    }
}
