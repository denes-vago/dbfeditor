package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.ui.DBFTableModel;
import com.vd.dbfeditor.ui.TabHeader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.nio.charset.Charset;
import java.nio.file.Path;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableRowSorter;

final class DocumentState {
    Path path;
    Charset charset;
    DBFEngine.DBFFile dbf;
    boolean modified;
    final JPanel panel;
    final JTable table;
    final DBFTableModel tableModel;
    final TableRowSorter<DBFTableModel> rowSorter;
    final TabHeader tabHeader;
    final Deque<FieldContentEdit> undoStack = new ArrayDeque<>();
    final Deque<FieldContentEdit> redoStack = new ArrayDeque<>();
    String filterText = "";
    boolean filterCaseSensitive;

    DocumentState(
        Path path,
        Charset charset,
        DBFEngine.DBFFile dbf,
        boolean modified,
        JPanel panel,
        JTable table,
        DBFTableModel tableModel,
        TableRowSorter<DBFTableModel> rowSorter,
        TabHeader tabHeader
    ) {
        this.path = path;
        this.charset = charset;
        this.dbf = dbf;
        this.modified = modified;
        this.panel = panel;
        this.table = table;
        this.tableModel = tableModel;
        this.rowSorter = rowSorter;
        this.tabHeader = tabHeader;
    }

    record FieldContentEdit(java.util.List<java.util.List<String>> beforeRecords, java.util.List<java.util.List<String>> afterRecords) {
    }
}
