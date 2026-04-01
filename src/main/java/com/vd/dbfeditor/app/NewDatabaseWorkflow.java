package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.dialog.StructureEditorDialog;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

final class NewDatabaseWorkflow {
    private final JFrame owner;
    private final Localization localization;

    NewDatabaseWorkflow(JFrame owner, Localization localization) {
        this.owner = owner;
        this.localization = localization;
    }

    CreationResult create(Charset charset, String defaultDisplayName) {
        String displayName = requestDatabaseName(defaultDisplayName);
        if (displayName == null) {
            return null;
        }

        DBFEngine.DBFFile initialDbf = createDefaultNewDbf();
        StructureEditorDialog.Result result = StructureEditorDialog.show(owner, localization, initialDbf);
        if (result == null) {
            return null;
        }

        DBFEngine.DBFFile dbf = rebuildDbf(initialDbf, result.fields(), new ArrayList<>());
        return new CreationResult(displayName, charset, dbf);
    }

    private String requestDatabaseName(String defaultName) {
        Object input = JOptionPane.showInputDialog(
            owner,
            localization.text("dialog.new_database.message"),
            localization.text("dialog.new_database.title"),
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            defaultName
        );
        if (input == null) {
            return null;
        }

        String name = input.toString().trim();
        if (name.isEmpty()) {
            name = defaultName;
        }
        if (!name.toLowerCase().endsWith(".dbf")) {
            name = name + ".dbf";
        }
        return name;
    }

    private DBFEngine.DBFFile createDefaultNewDbf() {
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("NEWFIELD", 'C', 20, 0)
        );
        return rebuildDbf(
            new DBFEngine.DBFFile((byte) 0x03, LocalDate.now(), 0, 0, 0, fields, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()),
            fields,
            new ArrayList<>()
        );
    }

    private DBFEngine.DBFFile rebuildDbf(DBFEngine.DBFFile source, List<DBFEngine.FieldDescriptor> fields, List<List<String>> records) {
        int headerLength = 32 + fields.size() * 32 + 1;
        int recordLength = 1;
        for (DBFEngine.FieldDescriptor field : fields) {
            recordLength += field.length();
        }

        return new DBFEngine.DBFFile(
            source.version(),
            source.lastUpdate(),
            records.size(),
            headerLength,
            recordLength,
            fields,
            records,
            new ArrayList<>(),
            new ArrayList<>()
        );
    }

    record CreationResult(String displayName, Charset charset, DBFEngine.DBFFile dbf) {
    }
}
