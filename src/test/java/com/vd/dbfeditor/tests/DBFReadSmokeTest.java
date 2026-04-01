package com.vd.dbfeditor.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DBFReadSmokeTest {

    @Test
    // Quick regression check: every DBF file in the project root can be opened.
    void allDbfFilesInProjectRootAreReadable() throws Exception {
        List<Path> dbfFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("."), "*.DBF")) {
            for (Path path : stream) {
                dbfFiles.add(path);
            }
        }

        assertFalse(dbfFiles.isEmpty(), "No DBF file was found in the folder.");

        Charset charset = DBFEngine.DEFAULT_CHARSET;
        for (Path dbfPath : dbfFiles) {
            try {
                DBFEngine.read(dbfPath, charset);
            } catch (IOException e) {
                fail("Failed to read " + dbfPath.getFileName() + ": " + e.getMessage());
            }
        }
    }
}
