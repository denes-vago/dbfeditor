package com.vd.dbfeditor.ui.dialog;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.TextEditSupport;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public final class StructureEditorDialog {
    private static final String VALID_FIELD_TYPES = "CDFLMN";

    private StructureEditorDialog() {
    }

    public static Result show(JFrame owner, Localization localization, DBFEngine.DBFFile dbf) {
        JDialog dialog = new JDialog(owner, localization.text("dialog.structure.title"), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        DefaultTableModel model = new DefaultTableModel(
            new Object[] {
                localization.text("dialog.structure.column.name"),
                localization.text("dialog.structure.column.type"),
                localization.text("dialog.structure.column.length"),
                localization.text("dialog.structure.column.decimals")
            },
            0
        );
        List<Integer> sourceIndexes = new ArrayList<>();
        for (int i = 0; i < dbf.fields().size(); i++) {
            DBFEngine.FieldDescriptor field = dbf.fields().get(i);
            model.addRow(new Object[] {field.name(), String.valueOf(field.type()), field.length(), field.decimalCount()});
            sourceIndexes.add(i);
        }

        JTable structureTable = new JTable(model);
        JTextField nameEditorField = new JTextField();
        TextEditSupport.installUndoSupport(nameEditorField);
        ((AbstractDocument) nameEditorField.getDocument()).setDocumentFilter(new MaxLengthDocumentFilter(11));
        structureTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(nameEditorField));
        JTextField typeEditorField = new JTextField();
        TextEditSupport.installUndoSupport(typeEditorField);
        ((AbstractDocument) typeEditorField.getDocument()).setDocumentFilter(new FieldTypeDocumentFilter());
        structureTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeEditorField));
        dialog.add(new JScrollPane(structureTable), BorderLayout.CENTER);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        dialog.add(statusLabel, BorderLayout.NORTH);

        JButton addFieldButton = new JButton(localization.text("dialog.structure.add_field"));
        JButton removeFieldButton = new JButton(localization.text("dialog.structure.remove_field"));
        JButton okButton = new JButton(localization.text("button.ok"));
        JButton cancelButton = new JButton(localization.text("button.cancel"));

        addFieldButton.addActionListener(e -> {
            model.addRow(new Object[] {"NEWFIELD", "C", 20, 0});
            sourceIndexes.add(-1);
            int newRow = model.getRowCount() - 1;
            structureTable.getSelectionModel().setSelectionInterval(newRow, newRow);
        });

        removeFieldButton.addActionListener(e -> {
            int selectedRow = structureTable.getSelectedRow();
            if (selectedRow >= 0) {
                model.removeRow(selectedRow);
                sourceIndexes.remove(selectedRow);
            }
        });

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(addFieldButton);
        leftButtons.add(removeFieldButton);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightButtons.add(okButton);
        rightButtons.add(cancelButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(leftButtons, BorderLayout.WEST);
        bottomPanel.add(rightButtons, BorderLayout.EAST);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        final Result[] resultHolder = new Result[1];
        okButton.addActionListener(e -> {
            try {
                if (structureTable.isEditing()) {
                    structureTable.getCellEditor().stopCellEditing();
                }
                resultHolder[0] = collectStructureEdits(localization, model, sourceIndexes);
                dialog.dispose();
            } catch (IllegalArgumentException ex) {
                statusLabel.setText(ex.getMessage());
            }
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.setSize(760, 420);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return resultHolder[0];
    }

    private static Result collectStructureEdits(Localization localization, DefaultTableModel model, List<Integer> sourceIndexes) {
        if (model.getRowCount() == 0) {
            throw new IllegalArgumentException(localization.text("dialog.structure.error.empty"));
        }

        List<DBFEngine.FieldDescriptor> fields = new ArrayList<>();
        List<Integer> mappings = new ArrayList<>();
        List<String> usedNames = new ArrayList<>();

        for (int row = 0; row < model.getRowCount(); row++) {
            String name = String.valueOf(model.getValueAt(row, 0)).trim();
            String typeText = String.valueOf(model.getValueAt(row, 1)).trim().toUpperCase();
            String lengthText = String.valueOf(model.getValueAt(row, 2)).trim();
            String decimalsText = String.valueOf(model.getValueAt(row, 3)).trim();

            int length;
            int decimals;
            try {
                length = Integer.parseInt(lengthText);
                decimals = Integer.parseInt(decimalsText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(localization.text("dialog.structure.error.number", row + 1));
            }

            char type = typeText.isEmpty() ? ' ' : typeText.charAt(0);
            DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor(name, type, length, decimals);
            String fieldError = DBFEngine.validateFieldDefinition(field);
            if (fieldError != null) {
                throw new IllegalArgumentException(localization.text("dialog.structure.error.field", row + 1, fieldError));
            }
            if (usedNames.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
                throw new IllegalArgumentException(localization.text("dialog.structure.error.duplicate", name));
            }

            usedNames.add(name);
            fields.add(field);
            mappings.add(sourceIndexes.get(row));
        }

        return new Result(fields, mappings);
    }

    public record Result(List<DBFEngine.FieldDescriptor> fields, List<Integer> sourceIndexes) {
    }

    private static final class MaxLengthDocumentFilter extends DocumentFilter {
        private final int maxLength;

        MaxLengthDocumentFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String incoming = text == null ? "" : text;
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            StringBuilder builder = new StringBuilder(current);
            builder.replace(offset, offset + length, incoming);
            String limited = builder.length() > maxLength ? builder.substring(0, maxLength) : builder.toString();
            fb.replace(0, fb.getDocument().getLength(), limited, attrs);
        }
    }

    private static final class FieldTypeDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String incoming = text == null ? "" : text.toUpperCase();
            StringBuilder builder = new StringBuilder(fb.getDocument().getText(0, fb.getDocument().getLength()));
            builder.replace(offset, offset + length, incoming);
            String filtered = filterValidType(builder.toString());
            fb.replace(0, fb.getDocument().getLength(), filtered, attrs);
        }

        private String filterValidType(String value) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (VALID_FIELD_TYPES.indexOf(ch) >= 0) {
                    return String.valueOf(ch);
                }
            }
            return "";
        }
    }
}
