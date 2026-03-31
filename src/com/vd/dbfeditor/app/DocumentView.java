package com.vd.dbfeditor.app;

import com.vd.dbfeditor.ui.DBFTableModel;
import com.vd.dbfeditor.ui.TabHeader;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableRowSorter;

final class DocumentView {
    final JPanel panel;
    final JTable table;
    final DBFTableModel tableModel;
    final TableRowSorter<DBFTableModel> rowSorter;
    final TabHeader tabHeader;

    DocumentView(
        JPanel panel,
        JTable table,
        DBFTableModel tableModel,
        TableRowSorter<DBFTableModel> rowSorter,
        TabHeader tabHeader
    ) {
        this.panel = panel;
        this.table = table;
        this.tableModel = tableModel;
        this.rowSorter = rowSorter;
        this.tabHeader = tabHeader;
    }
}
