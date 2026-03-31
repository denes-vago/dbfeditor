package com.vd.dbfeditor.ui;

import com.vd.dbfeditor.dbf.DBFEngine;
import javax.swing.table.AbstractTableModel;

public final class DBFTableModel extends AbstractTableModel {
    private DBFEngine.DBFFile dbf;

    public void setDbf(DBFEngine.DBFFile dbf) {
        this.dbf = dbf;
        fireTableStructureChanged();
    }

    public void fireRowUpdated(int rowIndex) {
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    public void fireRowInserted(int rowIndex) {
        fireTableRowsInserted(rowIndex, rowIndex);
    }

    @Override
    public int getRowCount() {
        return dbf == null ? 0 : dbf.records().size();
    }

    @Override
    public int getColumnCount() {
        return dbf == null ? 0 : dbf.fields().size();
    }

    @Override
    public String getColumnName(int column) {
        return dbf == null ? "" : dbf.fields().get(column).name();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (dbf == null) {
            return "";
        }
        return dbf.records().get(rowIndex).get(columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
