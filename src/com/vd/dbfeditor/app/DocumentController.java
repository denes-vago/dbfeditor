package com.vd.dbfeditor.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JTabbedPane;

final class DocumentController {
    private final JTabbedPane tabbedPane;
    private final List<DocumentState> documents = new ArrayList<>();

    DocumentController(JTabbedPane tabbedPane) {
        this.tabbedPane = tabbedPane;
    }

    List<DocumentState> documents() {
        return documents;
    }

    DocumentState currentDocument() {
        int index = tabbedPane.getSelectedIndex();
        if (index < 0 || index >= documents.size()) {
            return null;
        }
        return documents.get(index);
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
        DocumentState newDocument,
        Function<DocumentState, String> titleBuilder,
        java.util.function.Consumer<DocumentState> existingUpdater
    ) {
        int existingIndex = findDocumentIndex(path);
        if (existingIndex >= 0) {
            DocumentState existing = documents.get(existingIndex);
            existingUpdater.accept(existing);
            tabbedPane.setSelectedIndex(existingIndex);
            return;
        }

        documents.add(newDocument);
        tabbedPane.addTab(titleBuilder.apply(newDocument), newDocument.panel);
        tabbedPane.setTabComponentAt(documents.size() - 1, newDocument.tabHeader);
        tabbedPane.setSelectedIndex(documents.size() - 1);
    }

    void updateTabTitle(DocumentState document, Function<DocumentState, String> titleBuilder) {
        int index = documents.indexOf(document);
        if (index >= 0) {
            String title = titleBuilder.apply(document);
            tabbedPane.setTitleAt(index, title);
            document.tabHeader.setTitle(title);
        }
    }

    boolean closeDocumentAt(int index, Predicate<DocumentState> discardChangesChecker) {
        if (index < 0 || index >= documents.size()) {
            return false;
        }

        DocumentState document = documents.get(index);
        if (!discardChangesChecker.test(document)) {
            return false;
        }

        documents.remove(index);
        tabbedPane.removeTabAt(index);
        return true;
    }
}
