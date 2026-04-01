package com.vd.dbfeditor.service;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.export.CsvExporter;
import com.vd.dbfeditor.export.ExportFormat;
import com.vd.dbfeditor.export.SqlDialect;
import com.vd.dbfeditor.export.SqlExporter;
import com.vd.dbfeditor.export.XlsxExporter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class DocumentFileService {
    private DocumentFileService() {
    }

    public static List<LoadedDocument> loadDocuments(List<Path> paths, Charset charset) throws IOException {
        List<LoadedDocument> loaded = new ArrayList<>();
        for (Path path : paths) {
            loaded.add(new LoadedDocument(path, charset, DBFEngine.read(path, charset)));
        }
        return loaded;
    }

    public static DBFEngine.DBFFile reload(Path path, Charset charset) throws IOException {
        return DBFEngine.read(path, charset);
    }

    public static DBFEngine.DBFFile save(Path path, Charset charset, DBFEngine.DBFFile dbf) throws IOException {
        DBFEngine.write(path, charset, dbf);
        return DBFEngine.read(path, charset);
    }

    public static void export(Path path, DBFEngine.DBFFile dbf, ExportFormat format, String tableName, SqlDialect sqlDialect)
        throws IOException {
        if (format == ExportFormat.XLSX) {
            try (OutputStream output = Files.newOutputStream(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                XlsxExporter.export(dbf, output);
            }
            return;
        }

        String content = format == ExportFormat.CSV
            ? CsvExporter.export(dbf)
            : SqlExporter.export(dbf, tableName, sqlDialect);
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public static DBFEngine.DBFFile copyDbf(DBFEngine.DBFFile source) {
        List<DBFEngine.FieldDescriptor> fields = new ArrayList<>(source.fields());
        List<List<String>> records = new ArrayList<>(source.records().size());
        for (List<String> row : source.records()) {
            records.add(new ArrayList<>(row));
        }
        List<Boolean> deletedFlags = new ArrayList<>(source.deletedFlags());
        List<String> memoWarnings = new ArrayList<>(source.memoWarnings());
        return new DBFEngine.DBFFile(
            source.version(),
            source.lastUpdate(),
            source.recordCount(),
            source.headerLength(),
            source.recordLength(),
            fields,
            records,
            deletedFlags,
            memoWarnings
        );
    }

    public record LoadedDocument(Path path, Charset charset, DBFEngine.DBFFile dbf) {
    }
}
