package com.vd.dbfeditor.app;

import com.vd.dbfeditor.i18n.Localization;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JLabel;

final class DocumentUiController {
    private final JFrame frame;
    private final Localization localization;
    private final JLabel statusBarLabel;
    private final Supplier<DocumentModel> currentDocumentSupplier;
    private final Supplier<List<DocumentModel>> documentsSupplier;
    private final java.util.function.Function<DocumentModel, DocumentView> viewLookup;
    private final Supplier<Charset> fallbackCharsetSupplier;
    private final Action openAction;
    private final List<Action> fileBoundActions;
    private final Consumer<Boolean> exportMenuEnabledSetter;
    private final Consumer<Boolean> charsetMenuEnabledSetter;
    private final Consumer<Boolean> showDeletedMenuEnabledSetter;
    private final Consumer<Charset> charsetSelectionSyncer;

    DocumentUiController(
        JFrame frame,
        Localization localization,
        JLabel statusBarLabel,
        Supplier<DocumentModel> currentDocumentSupplier,
        Supplier<List<DocumentModel>> documentsSupplier,
        java.util.function.Function<DocumentModel, DocumentView> viewLookup,
        Supplier<Charset> fallbackCharsetSupplier,
        Action openAction,
        List<Action> fileBoundActions,
        Consumer<Boolean> exportMenuEnabledSetter,
        Consumer<Boolean> charsetMenuEnabledSetter,
        Consumer<Boolean> showDeletedMenuEnabledSetter,
        Consumer<Charset> charsetSelectionSyncer
    ) {
        this.frame = frame;
        this.localization = localization;
        this.statusBarLabel = statusBarLabel;
        this.currentDocumentSupplier = currentDocumentSupplier;
        this.documentsSupplier = documentsSupplier;
        this.viewLookup = viewLookup;
        this.fallbackCharsetSupplier = fallbackCharsetSupplier;
        this.openAction = openAction;
        this.fileBoundActions = fileBoundActions;
        this.exportMenuEnabledSetter = exportMenuEnabledSetter;
        this.charsetMenuEnabledSetter = charsetMenuEnabledSetter;
        this.showDeletedMenuEnabledSetter = showDeletedMenuEnabledSetter;
        this.charsetSelectionSyncer = charsetSelectionSyncer;
    }

    void refreshCurrentDocumentView(boolean busyState) {
        DocumentModel document = currentDocumentSupplier.get();
        if (document == null) {
            charsetSelectionSyncer.accept(fallbackCharsetSupplier.get());
        } else {
            charsetSelectionSyncer.accept(document.charset);
        }
        applyBusyState(busyState, null);
    }

    void syncCharsetSelection(java.nio.charset.Charset charset) {
        charsetSelectionSyncer.accept(charset);
    }

    void applyBusyState(boolean busyState, String message) {
        boolean hasFile = currentDocumentSupplier.get() != null;
        openAction.setEnabled(!busyState);
        exportMenuEnabledSetter.accept(!busyState && hasFile);
        charsetMenuEnabledSetter.accept(!busyState);
        showDeletedMenuEnabledSetter.accept(!busyState && hasFile);
        for (Action action : fileBoundActions) {
            action.setEnabled(!busyState && hasFile);
        }
        for (DocumentModel document : documentsSupplier.get()) {
            DocumentView view = viewLookup.apply(document);
            if (view != null) {
                view.table.setEnabled(!busyState);
            }
        }
        updateStatusBar(message);
        updateWindowTitle(busyState);
    }

    void updateWindowTitle(boolean busyState) {
        StringBuilder title = new StringBuilder(localization.text("app.title"));
        DocumentModel document = currentDocumentSupplier.get();
        if (document != null) {
            String documentName = document.path != null
                ? document.path.getFileName().toString()
                : document.displayName;
            if (documentName != null && !documentName.isBlank()) {
                title.append(" - ").append(documentName);
            }
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

        DocumentModel document = currentDocumentSupplier.get();
        if (document == null || document.dbf == null) {
            statusBarLabel.setText(localization.text("status.no_file"));
            return;
        }
        DocumentView view = viewLookup.apply(document);
        if (view == null) {
            statusBarLabel.setText(localization.text("status.no_file"));
            return;
        }

        long fileSize = 0L;
        try {
            if (document.path != null && Files.exists(document.path)) {
                fileSize = Files.size(document.path);
            }
        } catch (IOException e) {
            fileSize = 0L;
        }

        statusBarLabel.setText(StatusBarFormatter.format(localization, document, view, fileSize));
    }
}
