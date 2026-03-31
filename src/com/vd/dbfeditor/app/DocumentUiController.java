package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.CharsetRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;

final class DocumentUiController {
    private final JFrame frame;
    private final Localization localization;
    private final JComboBox<String> charsetCombo;
    private final JLabel statusBarLabel;
    private final Supplier<DocumentState> currentDocumentSupplier;
    private final Supplier<List<DocumentState>> documentsSupplier;
    private final Action openAction;
    private final List<Action> fileBoundActions;

    DocumentUiController(
        JFrame frame,
        Localization localization,
        JComboBox<String> charsetCombo,
        JLabel statusBarLabel,
        Supplier<DocumentState> currentDocumentSupplier,
        Supplier<List<DocumentState>> documentsSupplier,
        Action openAction,
        List<Action> fileBoundActions
    ) {
        this.frame = frame;
        this.localization = localization;
        this.charsetCombo = charsetCombo;
        this.statusBarLabel = statusBarLabel;
        this.currentDocumentSupplier = currentDocumentSupplier;
        this.documentsSupplier = documentsSupplier;
        this.openAction = openAction;
        this.fileBoundActions = fileBoundActions;
    }

    void refreshCurrentDocumentView(boolean busyState) {
        DocumentState document = currentDocumentSupplier.get();
        if (document == null) {
            syncCharsetCombo(DBFEngine.DEFAULT_CHARSET);
        } else {
            syncCharsetCombo(document.charset);
        }
        applyBusyState(busyState, null);
    }

    void syncCharsetSelection(java.nio.charset.Charset charset) {
        syncCharsetCombo(charset);
    }

    void applyBusyState(boolean busyState, String message) {
        boolean hasFile = currentDocumentSupplier.get() != null;
        openAction.setEnabled(!busyState);
        charsetCombo.setEnabled(!busyState);
        for (Action action : fileBoundActions) {
            action.setEnabled(!busyState && hasFile);
        }
        for (DocumentState document : documentsSupplier.get()) {
            document.table.setEnabled(!busyState);
        }
        updateStatusBar(message);
        updateWindowTitle(busyState);
    }

    void updateWindowTitle(boolean busyState) {
        StringBuilder title = new StringBuilder(localization.text("app.title"));
        DocumentState document = currentDocumentSupplier.get();
        if (document != null && document.path != null) {
            title.append(" - ").append(document.path.getFileName());
        }
        if (busyState) {
            title.append(localization.text("app.title.busy_suffix"));
        } else if (document != null && document.modified) {
            title.append(" *");
        }
        frame.setTitle(title.toString());
    }

    void updateStatusBar(String overrideMessage) {
        if (overrideMessage != null && !overrideMessage.isBlank()) {
            statusBarLabel.setText(overrideMessage);
            return;
        }

        DocumentState document = currentDocumentSupplier.get();
        if (document == null || document.path == null || document.dbf == null) {
            statusBarLabel.setText(localization.text("status.no_file"));
            return;
        }

        long fileSize = 0L;
        try {
            if (Files.exists(document.path)) {
                fileSize = Files.size(document.path);
            }
        } catch (IOException e) {
            fileSize = 0L;
        }

        statusBarLabel.setText(
            localization.text(
                "status.summary",
                String.format("%02X", document.dbf.version()),
                fileSize,
                document.dbf.records().size()
            )
        );
    }

    private void syncCharsetCombo(java.nio.charset.Charset charset) {
        ensureCharsetAvailable(charset);
        charsetCombo.setSelectedItem(CharsetRegistry.displayName(charset));
    }

    private void ensureCharsetAvailable(java.nio.charset.Charset charset) {
        if (charset == null) {
            return;
        }

        String charsetName = CharsetRegistry.displayName(charset);
        for (int i = 0; i < charsetCombo.getItemCount(); i++) {
            if (charsetName.equals(charsetCombo.getItemAt(i))) {
                return;
            }
        }
        charsetCombo.addItem(charsetName);
    }
}
