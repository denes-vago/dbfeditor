package com.vd.dbfeditor.ui.dialog;

import com.vd.dbfeditor.export.SqlDialect;
import com.vd.dbfeditor.i18n.Localization;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public final class SqlDialectDialog {
    private SqlDialectDialog() {
    }

    public static SqlDialect show(JFrame owner, Localization localization) {
        SqlDialect[] dialects = SqlDialect.values();
        String[] labels = new String[dialects.length];
        for (int i = 0; i < dialects.length; i++) {
            labels[i] = localization.text(dialects[i].labelKey());
        }

        Object selected = JOptionPane.showInputDialog(
            owner,
            localization.text("dialog.export.sql.dialect.message"),
            localization.text("dialog.export.sql.dialect.title"),
            JOptionPane.QUESTION_MESSAGE,
            null,
            labels,
            labels[0]
        );
        if (selected == null) {
            return null;
        }

        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(selected)) {
                return dialects[i];
            }
        }
        return SqlDialect.GENERIC;
    }
}
