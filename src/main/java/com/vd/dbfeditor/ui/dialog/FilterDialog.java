package com.vd.dbfeditor.ui.dialog;

import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.TextEditSupport;
import java.awt.BorderLayout;
import javax.swing.JComboBox;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class FilterDialog {
    private FilterDialog() {
    }

    public static FilterRequest show(
        JFrame owner,
        Localization localization,
        String initialText,
        boolean initialCaseSensitive,
        String[] columnOptions,
        int initialColumnIndex
    ) {
        JTextField filterField = new JTextField(initialText, 24);
        TextEditSupport.installUndoSupport(filterField);
        JComboBox<String> columnComboBox = new JComboBox<>(columnOptions);
        columnComboBox.setSelectedIndex(Math.max(0, Math.min(initialColumnIndex + 1, columnOptions.length - 1)));
        JCheckBox caseSensitiveCheckBox = new JCheckBox(
            localization.text("dialog.filter.case_sensitive"),
            initialCaseSensitive
        );

        JPanel filterPanel = new JPanel(new BorderLayout(0, 8));
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.add(new JLabel(localization.text("dialog.filter.message")), BorderLayout.NORTH);
        topPanel.add(filterField, BorderLayout.CENTER);

        JPanel optionsPanel = new JPanel(new BorderLayout(0, 8));
        optionsPanel.add(new JLabel(localization.text("dialog.filter.column")), BorderLayout.NORTH);
        optionsPanel.add(columnComboBox, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.add(optionsPanel, BorderLayout.NORTH);
        bottomPanel.add(caseSensitiveCheckBox, BorderLayout.SOUTH);

        filterPanel.add(topPanel, BorderLayout.NORTH);
        filterPanel.add(bottomPanel, BorderLayout.CENTER);

        JOptionPane optionPane = new JOptionPane(
            filterPanel,
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION
        );
        optionPane.setInitialSelectionValue(filterField);
        JDialog dialog = optionPane.createDialog(owner, localization.text("dialog.filter.title"));
        dialog.setModal(true);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    optionPane.selectInitialValue();
                    filterField.requestFocusInWindow();
                    filterField.selectAll();
                });
            }
        });

        dialog.setVisible(true);
        dialog.dispose();

        Object selectedValue = optionPane.getValue();
        if (!(selectedValue instanceof Integer answer) || answer != JOptionPane.OK_OPTION) {
            return null;
        }

        return new FilterRequest(filterField.getText().trim(), caseSensitiveCheckBox.isSelected(), columnComboBox.getSelectedIndex() - 1);
    }

    public record FilterRequest(String text, boolean caseSensitive, int columnIndex) {
    }
}
