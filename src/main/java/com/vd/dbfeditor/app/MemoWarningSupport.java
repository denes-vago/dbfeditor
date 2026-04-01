package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

final class MemoWarningSupport {
    private MemoWarningSupport() {
    }

    static void showWarnings(JFrame owner, Localization localization, DBFEngine.DBFFile dbf) {
        if (dbf == null || dbf.memoWarnings().isEmpty()) {
            return;
        }

        JOptionPane.showMessageDialog(
            owner,
            formatWarnings(dbf),
            localization.text("dialog.memo.title"),
            JOptionPane.WARNING_MESSAGE
        );
    }

    static String determineStatus(Localization localization, DBFEngine.DBFFile dbf) {
        if (!hasMemoField(dbf)) {
            return localization.text("status.memo.none");
        }
        return dbf.memoWarnings().isEmpty()
            ? localization.text("status.memo.ok")
            : localization.text("status.memo.warning");
    }

    private static boolean hasMemoField(DBFEngine.DBFFile dbf) {
        if (dbf == null) {
            return false;
        }
        for (DBFEngine.FieldDescriptor field : dbf.fields()) {
            if (field.type() == 'M') {
                return true;
            }
        }
        return false;
    }

    private static String formatWarnings(DBFEngine.DBFFile dbf) {
        return String.join("\n", dbf.memoWarnings());
    }
}
