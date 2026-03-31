package com.vd.dbfeditor.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JTabbedPane;

final class DocumentController {
    private final JTabbedPane tabbedPane;
    private final List<DocumentModel> documents = new ArrayList<>();
    private final List<DocumentView> documentViews = new ArrayList<>();

    DocumentController(JTabbedPane tabbedPane) {
        this.tabbedPane = tabbedPane;
    }

    List<DocumentModel> documents() {
        return documents;
    }

    DocumentModel currentDocument() {
        int index = tabbedPane.getSelectedIndex();
        if (index < 0 || index >= documents.size()) {
            return null;
        }
        return documents.get(index);
    }

    DocumentView currentView() {
        int index = tabbedPane.getSelectedIndex();
        if (index < 0 || index >= documentViews.size()) {
            return null;
        }
        return documentViews.get(index);
    }

    DocumentView viewOf(DocumentModel document) {
        int index = documents.indexOf(document);
        if (index < 0 || index >= documentViews.size()) {
            return null;
        }
        return documentViews.get(index);
    }

    int findDocumentIndex(Path path) {
        for (int i = 0; i < documents.size(); i++) {
            Path documentPath = documents.get(i).path;
            if (documentPath != null && documentPath.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    void openOrReplaceDocument(
        Path path,
        DocumentModel newDocument,
        DocumentView newView,
        Function<DocumentModel, String> titleBuilder,
        java.util.function.BiConsumer<DocumentModel, DocumentView> existingUpdater
    ) {
        int existingIndex = findDocumentIndex(path);
        if (existingIndex >= 0) {
            DocumentModel existing = documents.get(existingIndex);
            DocumentView existingView = documentViews.get(existingIndex);
            existingUpdater.accept(existing, existingView);
            tabbedPane.setSelectedIndex(existingIndex);
            return;
        }

        documents.add(newDocument);
        documentViews.add(newView);
        tabbedPane.addTab(titleBuilder.apply(newDocument), newView.panel);
        tabbedPane.setTabComponentAt(documents.size() - 1, newView.tabHeader);
        tabbedPane.setSelectedIndex(documents.size() - 1);
    }

    void updateTabTitle(DocumentModel document, Function<DocumentModel, String> titleBuilder) {
        int index = documents.indexOf(document);
        if (index >= 0) {
            String title = titleBuilder.apply(document);
            tabbedPane.setTitleAt(index, title);
            documentViews.get(index).tabHeader.setTitle(title);
        }
    }

    boolean closeDocumentAt(int index, Predicate<DocumentModel> discardChangesChecker) {
        if (index < 0 || index >= documents.size()) {
            return false;
        }

        DocumentModel document = documents.get(index);
        if (!discardChangesChecker.test(document)) {
            return false;
        }

        documents.remove(index);
        documentViews.remove(index);
        tabbedPane.removeTabAt(index);
        return true;
    }
}
