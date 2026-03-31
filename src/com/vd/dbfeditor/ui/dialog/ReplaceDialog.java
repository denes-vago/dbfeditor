package com.vd.dbfeditor.ui.dialog;

import com.vd.dbfeditor.i18n.Localization;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class ReplaceDialog {
    private ReplaceDialog() {
    }

    public static ReplaceRequest show(
        JFrame owner,
        Localization localization,
        String initialSearchText,
        String initialReplaceText,
        boolean initialCaseSensitive
    ) {
        JTextField searchField = new JTextField(initialSearchText, 24);
        JTextField replaceField = new JTextField(initialReplaceText, 24);
        JCheckBox caseSensitiveCheckBox = new JCheckBox(
            localization.text("dialog.replace.case_sensitive"),
            initialCaseSensitive
        );

        JPanel fieldPanel = new JPanel(new GridLayout(0, 1, 0, 8));
        fieldPanel.add(new JLabel(localization.text("dialog.replace.search_message")));
        fieldPanel.add(searchField);
        fieldPanel.add(new JLabel(localization.text("dialog.replace.replace_message")));
        fieldPanel.add(replaceField);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(fieldPanel, BorderLayout.CENTER);
        panel.add(caseSensitiveCheckBox, BorderLayout.SOUTH);

        JOptionPane optionPane = new JOptionPane(
            panel,
            JOptionPane.QUESTION_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION
        );
        optionPane.setInitialSelectionValue(searchField);
        var dialog = optionPane.createDialog(owner, localization.text("dialog.replace.title"));
        dialog.setModal(true);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    optionPane.selectInitialValue();
                    searchField.requestFocusInWindow();
                    searchField.selectAll();
                });
            }
        });
        dialog.setVisible(true);
        dialog.dispose();

        Object selectedValue = optionPane.getValue();
        if (!(selectedValue instanceof Integer answer) || answer != JOptionPane.OK_OPTION) {
            return null;
        }

        return new ReplaceRequest(
            searchField.getText().trim(),
            replaceField.getText(),
            caseSensitiveCheckBox.isSelected()
        );
    }

    public record ReplaceRequest(String searchText, String replaceText, boolean caseSensitive) {
    }
}
