package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.ui.DBFTableModel;
import com.vd.dbfeditor.ui.TabHeader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import javax.swing.JPanel;
import javax.swing.JTable;

final class DocumentState {
    Path path;
    Charset charset;
    DBFEngine.DBFFile dbf;
    boolean modified;
    final JPanel panel;
    final JTable table;
    final DBFTableModel tableModel;
    final TabHeader tabHeader;

    DocumentState(
        Path path,
        Charset charset,
        DBFEngine.DBFFile dbf,
        boolean modified,
        JPanel panel,
        JTable table,
        DBFTableModel tableModel,
        TabHeader tabHeader
    ) {
        this.path = path;
        this.charset = charset;
        this.dbf = dbf;
        this.modified = modified;
        this.panel = panel;
        this.table = table;
        this.tableModel = tableModel;
        this.tabHeader = tabHeader;
    }
}
