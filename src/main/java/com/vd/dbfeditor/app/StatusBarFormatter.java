package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;

final class StatusBarFormatter {
    private StatusBarFormatter() {
    }

    static String format(Localization localization, DocumentModel document, DocumentView view, long fileSize) {
        int totalRows = document.dbf.records().size();
        int visibleRows = view.table.getRowCount();
        int deletedRows = countDeletedRows(document.dbf);
        String memoStatus = MemoWarningSupport.determineStatus(localization, document.dbf);
        String summaryKey = visibleRows == totalRows ? "status.summary" : "status.summary.filtered";

        return localization.text(
            summaryKey,
            String.format("%02X", document.dbf.version()),
            fileSize,
            document.charset.displayName(),
            visibleRows,
            totalRows,
            deletedRows,
            memoStatus
        );
    }

    private static int countDeletedRows(DBFEngine.DBFFile dbf) {
        int deletedRows = 0;
        for (Boolean deletedFlag : dbf.deletedFlags()) {
            if (Boolean.TRUE.equals(deletedFlag)) {
                deletedRows++;
            }
        }
        return deletedRows;
    }
}
