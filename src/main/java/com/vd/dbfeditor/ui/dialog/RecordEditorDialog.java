package com.vd.dbfeditor.ui.dialog;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.TextEditSupport;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class RecordEditorDialog {
    private RecordEditorDialog() {
    }

    public static List<String> show(
        JFrame owner,
        Localization localization,
        int rowIndex,
        List<DBFEngine.FieldDescriptor> fields,
        List<String> values,
        boolean isNewRecord,
        Charset charset
    ) {
        String titleKey = isNewRecord ? "dialog.editor.new_title" : "dialog.editor.edit_title";
        JDialog dialog = new JDialog(
            owner,
            localization.text(titleKey, rowIndex + 1),
            Dialog.ModalityType.APPLICATION_MODAL
        );
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel formPanel = new JPanel(new GridBagLayout());
        List<JTextComponent> editors = new ArrayList<>();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        for (int i = 0; i < fields.size(); i++) {
            DBFEngine.FieldDescriptor field = fields.get(i);
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            gbc.weighty = 0;
            formPanel.add(new JLabel(field.name() + " (" + field.type() + ", " + field.length() + ")"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            if (field.type() == 'M') {
                JTextArea editor = new JTextArea(values.get(i), 5, 40);
                editor.setLineWrap(true);
                editor.setWrapStyleWord(true);
                TextEditSupport.installUndoSupport(editor);
                editors.add(editor);
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weighty = 1.0;
                formPanel.add(new JScrollPane(editor), gbc);
            } else {
                JTextField editor = new JTextField(values.get(i), Math.min(Math.max(field.length(), 12), 40));
                TextEditSupport.installUndoSupport(editor);
                editors.add(editor);
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.weighty = 0;
                formPanel.add(editor, gbc);
            }
        }

        dialog.add(new JScrollPane(formPanel), BorderLayout.CENTER);

        JLabel statusLabel = new JLabel(" ");
        dialog.add(statusLabel, BorderLayout.NORTH);

        JButton okButton = new JButton(localization.text("button.ok"));
        JButton cancelButton = new JButton(localization.text("button.cancel"));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        AtomicReference<List<String>> resultHolder = new AtomicReference<>();

        Runnable validateForm = () -> {
            String error = null;
            for (int i = 0; i < fields.size(); i++) {
                error = DBFEngine.validateValue(fields.get(i), editors.get(i).getText(), charset);
                if (error != null) {
                    error = fields.get(i).name() + ": " + error;
                    break;
                }
            }
            statusLabel.setText(error == null ? " " : error);
            okButton.setEnabled(error == null);
        };

        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateForm.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateForm.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateForm.run();
            }
        };

        for (JTextComponent editor : editors) {
            editor.getDocument().addDocumentListener(listener);
        }

        okButton.addActionListener(e -> {
            List<String> updated = new ArrayList<>(editors.size());
            for (JTextComponent editor : editors) {
                updated.add(editor.getText());
            }
            resultHolder.set(updated);
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        validateForm.run();
        dialog.setSize(650, 450);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return resultHolder.get();
    }
}
