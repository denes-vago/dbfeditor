package com.vd.dbfeditor.ui.dialog;

import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.TextEditSupport;
import java.awt.BorderLayout;
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

    public static FilterRequest show(JFrame owner, Localization localization, String initialText, boolean initialCaseSensitive) {
        JTextField filterField = new JTextField(initialText, 24);
        TextEditSupport.installUndoSupport(filterField);
        JCheckBox caseSensitiveCheckBox = new JCheckBox(
            localization.text("dialog.filter.case_sensitive"),
            initialCaseSensitive
        );

        JPanel filterPanel = new JPanel(new BorderLayout(0, 8));
        filterPanel.add(new JLabel(localization.text("dialog.filter.message")), BorderLayout.NORTH);
        filterPanel.add(filterField, BorderLayout.CENTER);
        filterPanel.add(caseSensitiveCheckBox, BorderLayout.SOUTH);

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

        return new FilterRequest(filterField.getText().trim(), caseSensitiveCheckBox.isSelected());
    }

    public record FilterRequest(String text, boolean caseSensitive) {
    }
}
