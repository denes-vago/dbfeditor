package com.vd.dbfeditor.ui;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public final class TableColumnSizer {
    private TableColumnSizer() {
    }

    public static void packColumns(JTable table) {
        int margin = 16;
        for (int columnIndex = 0; columnIndex < table.getColumnCount(); columnIndex++) {
            TableColumn column = table.getColumnModel().getColumn(columnIndex);
            int preferredWidth = preferredHeaderWidth(table, columnIndex);
            for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
                TableCellRenderer renderer = table.getCellRenderer(rowIndex, columnIndex);
                Component component = table.prepareRenderer(renderer, rowIndex, columnIndex);
                preferredWidth = Math.max(preferredWidth, component.getPreferredSize().width);
            }
            column.setPreferredWidth(preferredWidth + margin);
        }
    }

    private static int preferredHeaderWidth(JTable table, int columnIndex) {
        TableColumn column = table.getColumnModel().getColumn(columnIndex);
        TableCellRenderer renderer = column.getHeaderRenderer();
        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component component = renderer.getTableCellRendererComponent(
            table,
            column.getHeaderValue(),
            false,
            false,
            -1,
            columnIndex
        );
        return component.getPreferredSize().width;
    }
}
