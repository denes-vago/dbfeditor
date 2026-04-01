package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.DBFTableModel;
import com.vd.dbfeditor.ui.dialog.FilterDialog;
import com.vd.dbfeditor.ui.dialog.ReplaceDialog;
import com.vd.dbfeditor.ui.dialog.SearchDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.RowFilter;

final class SearchFilterWorkflow {
    private final JFrame owner;
    private final Localization localization;
    private final Preferences preferences;
    private final String findTextKey;
    private final String findCaseSensitiveKey;
    private final String replaceTextKey;

    SearchFilterWorkflow(
        JFrame owner,
        Localization localization,
        Preferences preferences,
        String findTextKey,
        String findCaseSensitiveKey,
        String replaceTextKey
    ) {
        this.owner = owner;
        this.localization = localization;
        this.preferences = preferences;
        this.findTextKey = findTextKey;
        this.findCaseSensitiveKey = findCaseSensitiveKey;
        this.replaceTextKey = replaceTextKey;
    }

    MatchLocation searchCurrent(DocumentModel document, DocumentView view) {
        SearchDialog.SearchRequest request = SearchDialog.show(
            owner,
            localization,
            preferences.get(findTextKey, ""),
            preferences.getBoolean(findCaseSensitiveKey, false)
        );
        if (request == null) {
            return null;
        }

        String normalizedQuery = request.text();
        if (normalizedQuery.isEmpty()) {
            JOptionPane.showMessageDialog(
                owner,
                localization.text("dialog.search.empty"),
                localization.text("dialog.search.title"),
                JOptionPane.INFORMATION_MESSAGE
            );
            return null;
        }

        preferences.put(findTextKey, normalizedQuery);
        preferences.putBoolean(findCaseSensitiveKey, request.caseSensitive());
        return findMatch(document, view, normalizedQuery, request.caseSensitive(), true);
    }

    MatchLocation continueSearch(DocumentModel document, DocumentView view, boolean forward) {
        String searchText = preferences.get(findTextKey, "").trim();
        if (searchText.isEmpty()) {
            return searchCurrent(document, view);
        }
        return findMatch(document, view, searchText, preferences.getBoolean(findCaseSensitiveKey, false), forward);
    }

    ReplaceOutcome replaceInCurrentDocument(DocumentModel document, DocumentView view) {
        ReplaceDialog.ReplaceRequest request = ReplaceDialog.show(
            owner,
            localization,
            preferences.get(findTextKey, ""),
            preferences.get(replaceTextKey, ""),
            preferences.getBoolean(findCaseSensitiveKey, false)
        );
        if (request == null) {
            return null;
        }

        String searchText = request.searchText();
        if (searchText.isEmpty()) {
            JOptionPane.showMessageDialog(
                owner,
                localization.text("dialog.search.empty"),
                localization.text("dialog.replace.title"),
                JOptionPane.INFORMATION_MESSAGE
            );
            return null;
        }

        preferences.put(findTextKey, searchText);
        preferences.put(replaceTextKey, request.replaceText());
        preferences.putBoolean(findCaseSensitiveKey, request.caseSensitive());

        List<List<String>> beforeRecords = snapshotRecords(document.dbf.records());
        int replacements = replaceAllMatches(document, view, searchText, request.replaceText(), request.caseSensitive());
        if (replacements == 0) {
            JOptionPane.showMessageDialog(
                owner,
                localization.text("dialog.search.not_found", searchText),
                localization.text("dialog.replace.title"),
                JOptionPane.INFORMATION_MESSAGE
            );
            return null;
        }

        List<List<String>> afterRecords = snapshotRecords(document.dbf.records());
        JOptionPane.showMessageDialog(
            owner,
            localization.text("dialog.replace.success", replacements),
            localization.text("dialog.replace.title"),
            JOptionPane.INFORMATION_MESSAGE
        );
        return new ReplaceOutcome(beforeRecords, afterRecords, replacements);
    }

    boolean showFilterDialog(DocumentModel document) {
        FilterDialog.FilterRequest request = FilterDialog.show(
            owner,
            localization,
            document.filterText,
            document.filterCaseSensitive,
            buildFilterColumnOptions(document),
            document.filterColumnIndex
        );
        if (request == null) {
            return false;
        }

        document.filterText = request.text();
        document.filterCaseSensitive = request.caseSensitive();
        document.filterColumnIndex = request.columnIndex();
        return true;
    }

    void clearFilter(DocumentModel document) {
        document.filterText = "";
        document.filterCaseSensitive = false;
        document.filterColumnIndex = -1;
    }

    void applyFilter(DocumentModel document, DocumentView view, Runnable statusUpdater) {
        String filterText = document.filterText == null ? "" : document.filterText.trim();
        boolean hasTextFilter = !filterText.isEmpty();
        boolean hideDeleted = !document.showDeletedRecords;
        if (!hasTextFilter && !hideDeleted) {
            view.rowSorter.setRowFilter(null);
            statusUpdater.run();
            return;
        }

        String effectiveFilter = document.filterCaseSensitive ? filterText : filterText.toLowerCase();
        view.rowSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DBFTableModel, ? extends Integer> entry) {
                if (hideDeleted && entry.getModel().isDeletedRow(entry.getIdentifier())) {
                    return false;
                }
                if (!hasTextFilter) {
                    return true;
                }
                int startColumn = document.filterColumnIndex >= 0 ? document.filterColumnIndex : 0;
                int endColumn = document.filterColumnIndex >= 0 ? document.filterColumnIndex + 1 : entry.getValueCount();
                for (int columnIndex = startColumn; columnIndex < endColumn; columnIndex++) {
                    String value = entry.getStringValue(columnIndex);
                    if (value == null) {
                        continue;
                    }
                    String effectiveValue = document.filterCaseSensitive ? value : value.toLowerCase();
                    if (effectiveValue.contains(effectiveFilter)) {
                        return true;
                    }
                }
                return false;
            }
        });
        statusUpdater.run();
    }

    void setShowDeletedRecords(DocumentModel document, boolean showDeletedRecords) {
        document.showDeletedRecords = showDeletedRecords;
    }

    private MatchLocation findMatch(DocumentModel document, DocumentView view, String query, boolean caseSensitive, boolean forward) {
        MatchLocation match = locateMatch(document, view, query, caseSensitive, forward);
        if (match == null) {
            JOptionPane.showMessageDialog(
                owner,
                localization.text("dialog.search.not_found", query),
                localization.text("dialog.search.title"),
                JOptionPane.INFORMATION_MESSAGE
            );
        }
        return match;
    }

    private MatchLocation locateMatch(DocumentModel document, DocumentView view, String query, boolean caseSensitive, boolean forward) {
        String effectiveQuery = caseSensitive ? query : query.toLowerCase();
        int rowCount = view.table.getRowCount();
        if (rowCount == 0) {
            return null;
        }

        int startRow = searchStartRow(view, rowCount, forward);
        for (int offset = 0; offset < rowCount; offset++) {
            int viewRowIndex = forward
                ? (startRow + offset) % rowCount
                : Math.floorMod(startRow - offset, rowCount);
            int rowIndex = view.table.convertRowIndexToModel(viewRowIndex);
            List<String> row = document.dbf.records().get(rowIndex);
            if (forward) {
                for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                    if (matches(row.get(columnIndex), effectiveQuery, caseSensitive)) {
                        return new MatchLocation(rowIndex, columnIndex);
                    }
                }
            } else {
                for (int columnIndex = row.size() - 1; columnIndex >= 0; columnIndex--) {
                    if (matches(row.get(columnIndex), effectiveQuery, caseSensitive)) {
                        return new MatchLocation(rowIndex, columnIndex);
                    }
                }
            }
        }
        return null;
    }

    private int replaceAllMatches(DocumentModel document, DocumentView view, String searchText, String replaceText, boolean caseSensitive) {
        int replacements = 0;
        String effectiveSearch = caseSensitive ? searchText : searchText.toLowerCase();
        int rowCount = view.table.getRowCount();
        int startRow = searchStartRow(view, rowCount, true);

        for (int offset = 0; offset < rowCount; offset++) {
            int rowIndex = view.table.convertRowIndexToModel((startRow + offset) % rowCount);
            List<String> row = document.dbf.records().get(rowIndex);
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                String currentValue = row.get(columnIndex);
                if (currentValue == null || currentValue.isEmpty()) {
                    continue;
                }

                String replacedValue = replaceText(currentValue, searchText, replaceText, caseSensitive, effectiveSearch);
                if (replacedValue.equals(currentValue)) {
                    continue;
                }

                DBFEngine.FieldDescriptor field = document.dbf.fields().get(columnIndex);
                String validationError = DBFEngine.validateValue(field, replacedValue, document.charset);
                if (validationError != null) {
                    throw new IllegalArgumentException(
                        localization.text("dialog.replace.invalid_value", field.name(), validationError)
                    );
                }

                row.set(columnIndex, replacedValue);
                replacements++;
            }
        }
        return replacements;
    }

    private int searchStartRow(DocumentView view, int rowCount, boolean forward) {
        if (rowCount <= 0) {
            return 0;
        }
        int selectedViewRow = view.table.getSelectedRow();
        if (selectedViewRow < 0) {
            return forward ? 0 : rowCount - 1;
        }
        return forward
            ? (selectedViewRow + 1) % rowCount
            : Math.floorMod(selectedViewRow - 1, rowCount);
    }

    private boolean matches(String value, String effectiveQuery, boolean caseSensitive) {
        if (value == null) {
            return false;
        }
        String effectiveValue = caseSensitive ? value : value.toLowerCase();
        return effectiveValue.contains(effectiveQuery);
    }

    private String replaceText(String input, String searchText, String replaceText, boolean caseSensitive, String effectiveSearch) {
        if (caseSensitive) {
            return input.replace(searchText, replaceText);
        }

        String effectiveInput = input.toLowerCase();
        StringBuilder result = new StringBuilder(input.length());
        int start = 0;
        int matchIndex;
        while ((matchIndex = effectiveInput.indexOf(effectiveSearch, start)) >= 0) {
            result.append(input, start, matchIndex);
            result.append(replaceText);
            start = matchIndex + searchText.length();
        }
        result.append(input.substring(start));
        return result.toString();
    }

    private List<List<String>> snapshotRecords(List<List<String>> records) {
        List<List<String>> snapshot = new ArrayList<>(records.size());
        for (List<String> row : records) {
            snapshot.add(new ArrayList<>(row));
        }
        return snapshot;
    }

    private String[] buildFilterColumnOptions(DocumentModel document) {
        List<String> options = new ArrayList<>();
        options.add(localization.text("dialog.filter.column_all"));
        for (DBFEngine.FieldDescriptor field : document.dbf.fields()) {
            options.add(field.name());
        }
        return options.toArray(String[]::new);
    }

    record MatchLocation(int rowIndex, int columnIndex) {
    }

    record ReplaceOutcome(List<List<String>> beforeRecords, List<List<String>> afterRecords, int replacements) {
    }
}
