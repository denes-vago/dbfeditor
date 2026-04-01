package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

final class DocumentModel {
    String displayName;
    Path path;
    Charset charset;
    DBFEngine.DBFFile dbf;
    boolean modified;
    final Deque<FieldContentEdit> undoStack = new ArrayDeque<>();
    final Deque<FieldContentEdit> redoStack = new ArrayDeque<>();
    String filterText = "";
    boolean filterCaseSensitive;
    int filterColumnIndex = -1;
    boolean showDeletedRecords;

    DocumentModel(String displayName, Path path, Charset charset, DBFEngine.DBFFile dbf, boolean modified) {
        this.displayName = displayName;
        this.path = path;
        this.charset = charset;
        this.dbf = dbf;
        this.modified = modified;
    }

    record FieldContentEdit(List<List<String>> beforeRecords, List<List<String>> afterRecords) {
    }
}
