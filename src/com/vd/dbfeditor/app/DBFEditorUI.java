package com.vd.dbfeditor.app;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.export.ExportFormat;
import com.vd.dbfeditor.export.SqlDialect;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.service.DocumentFileService;
import com.vd.dbfeditor.ui.CharsetRegistry;
import com.vd.dbfeditor.ui.DBFTableModel;
import com.vd.dbfeditor.ui.EditorMenuBar;
import com.vd.dbfeditor.ui.FileChooserFactory;
import com.vd.dbfeditor.ui.LookAndFeelOption;
import com.vd.dbfeditor.ui.LookAndFeelSupport;
import com.vd.dbfeditor.ui.TableColumnSizer;
import com.vd.dbfeditor.ui.TextEditSupport;
import com.vd.dbfeditor.ui.dialog.FilterDialog;
import com.vd.dbfeditor.ui.dialog.ReplaceDialog;
import com.vd.dbfeditor.ui.dialog.RecordEditorDialog;
import com.vd.dbfeditor.ui.dialog.SearchDialog;
import com.vd.dbfeditor.ui.dialog.SqlDialectDialog;
import com.vd.dbfeditor.ui.dialog.StructureEditorDialog;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableRowSorter;

public class DBFEditorUI extends JFrame {
    private static final String PREF_LANGUAGE = "language";
    private static final String PREF_LOOK_AND_FEEL = "lookAndFeel";
    private static final String PREF_LAST_FIND_TEXT = "lastFindText";
    private static final String PREF_FIND_CASE_SENSITIVE = "findCaseSensitive";
    private static final String PREF_LAST_REPLACE_TEXT = "lastReplaceText";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(DBFEditorUI.class);

    private final JLabel statusBarLabel;
    private final Localization localization;
    private final JTabbedPane tabbedPane;
    private final DocumentController documentController;
    private final EditorMenuBar editorMenuBar;
    private final FileChooserFactory fileChooserFactory;
    private final NewDatabaseWorkflow newDatabaseWorkflow;
    private final SearchFilterWorkflow searchFilterWorkflow;
    private final SaveExportWorkflow saveExportWorkflow;
    private DocumentUiController documentUiController;

    private boolean busy;
    private int untitledCounter = 1;
    private Charset pendingCharset = DBFEngine.DEFAULT_CHARSET;

    public DBFEditorUI() {
        super("");
        localization = new Localization(loadSavedLanguageCode());
        fileChooserFactory = new FileChooserFactory(localization);
        newDatabaseWorkflow = new NewDatabaseWorkflow(this, localization);
        searchFilterWorkflow = new SearchFilterWorkflow(
            this,
            localization,
            PREFERENCES,
            PREF_LAST_FIND_TEXT,
            PREF_FIND_CASE_SENSITIVE,
            PREF_LAST_REPLACE_TEXT
        );
        saveExportWorkflow = new SaveExportWorkflow(this, localization, fileChooserFactory);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                exitApplication();
            }
        });

        editorMenuBar = createMenuBar();
        setJMenuBar(editorMenuBar.menuBar());

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabPlacement(JTabbedPane.TOP);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        tabbedPane.putClientProperty("JTabbedPane.tabAlignment", "leading");
        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateViewFromCurrentDocument();
            }
        });
        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowTabPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowTabPopup(e);
            }
        });
        add(tabbedPane, BorderLayout.CENTER);
        documentController = new DocumentController(tabbedPane);

        statusBarLabel = new JLabel();
        statusBarLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusBarLabel, BorderLayout.SOUTH);

        documentUiController = new DocumentUiController(
            this,
            localization,
            statusBarLabel,
            this::currentDocument,
            documentController::documents,
            documentController::viewOf,
            () -> pendingCharset,
            editorMenuBar.openAction(),
            editorMenuBar.fileBoundActions(),
            editorMenuBar::setCharsetMenuEnabled,
            charset -> editorMenuBar.syncCharsetMenu(CharsetRegistry.displayName(charset))
        );

        applyLocalization();
        updateViewFromCurrentDocument();
        documentUiController.updateWindowTitle(busy);
    }

    private EditorMenuBar createMenuBar() {
        EditorMenuBar menuBar = new EditorMenuBar(
            this::createNewDatabase,
            this::openDbfFile,
            this::closeCurrentFile,
            () -> closeOtherTabs(tabbedPane.getSelectedIndex()),
            this::closeAllTabs,
            this::cutCurrentSelection,
            this::copyCurrentSelection,
            this::pasteCurrentSelection,
            this::undoCurrentFieldChange,
            this::redoCurrentFieldChange,
            this::showFilterDialog,
            this::clearCurrentFilter,
            this::saveCurrentFile,
            this::saveFileAs,
            this::exitApplication,
            this::exportCurrentDocument,
            this::addNewRecord,
            this::editSelectedRecord,
            this::deleteSelectedRecords,
            this::searchCurrentDocument,
            this::searchNextMatch,
            this::searchPreviousMatch,
            this::replaceInCurrentDocument,
            this::editDatabaseStructure,
            this::showAboutDialog
        );
        menuBar.rebuildLanguageMenu(localization, this::switchLanguage);
        menuBar.rebuildLookAndFeelMenu(LookAndFeelSupport.availableOptions(), currentLookAndFeelSelectionId(), this::switchLookAndFeel);
        menuBar.rebuildCharsetMenu(CharsetRegistry.supportedDisplayNames(), CharsetRegistry.displayName(DBFEngine.DEFAULT_CHARSET), this::applySelectedCharsetName);
        return menuBar;
    }

    private void rebuildLanguageMenu() {
        editorMenuBar.rebuildLanguageMenu(localization, this::switchLanguage);
    }

    private void switchLanguage(String languageCode) {
        localization.setLanguage(languageCode);
        PREFERENCES.put(PREF_LANGUAGE, languageCode);
        applyLocalization();
    }

    private void rebuildLookAndFeelMenu() {
        editorMenuBar.rebuildLookAndFeelMenu(LookAndFeelSupport.availableOptions(), currentLookAndFeelSelectionId(), this::switchLookAndFeel);
    }

    private void switchLookAndFeel(LookAndFeelOption option) {
        try {
            applyLookAndFeelOption(option);
            PREFERENCES.put(PREF_LOOK_AND_FEEL, option.id());
            SwingUtilities.updateComponentTreeUI(this);
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
            rebuildLookAndFeelMenu();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                this,
                localization.text("dialog.look_and_feel.error", option.displayName()),
                localization.text("dialog.error.title"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void applyLocalization() {
        applyDialogLocalization();
        editorMenuBar.applyLocalization(localization);
        editorMenuBar.syncLanguageMenu(localization);

        documentUiController.updateStatusBar(null);
        documentUiController.updateWindowTitle(busy);
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(
            this,
            localization.text("dialog.about.message", AppVersion.VERSION),
            localization.text("dialog.about.title"),
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void applyDialogLocalization() {
        UIManager.put("OptionPane.okButtonText", localization.text("button.ok"));
        UIManager.put("OptionPane.cancelButtonText", localization.text("button.cancel"));
        UIManager.put("OptionPane.yesButtonText", localization.text("button.yes"));
        UIManager.put("OptionPane.noButtonText", localization.text("button.no"));
    }

    private void openDbfFile() {
        if (busy) {
            return;
        }

        JFileChooser chooser = fileChooserFactory.createDbfChooser(localization.text("dialog.open.title"), true);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Charset charset = selectedCharset();
        if (charset == null) {
            return;
        }

        List<Path> selectedPaths = new ArrayList<>();
        File[] selectedFiles = chooser.getSelectedFiles();
        if (selectedFiles != null && selectedFiles.length > 0) {
            for (File selectedFile : selectedFiles) {
                selectedPaths.add(selectedFile.toPath());
            }
        } else if (chooser.getSelectedFile() != null) {
            selectedPaths.add(chooser.getSelectedFile().toPath());
        }
        loadDbfFiles(selectedPaths, charset);
    }

    private void createNewDatabase() {
        if (busy) {
            return;
        }

        Charset charset = selectedCharset();
        if (charset == null) {
            return;
        }

        String defaultName = localization.text("app.untitled") + " " + untitledCounter++;
        NewDatabaseWorkflow.CreationResult creation = newDatabaseWorkflow.create(charset, defaultName);
        if (creation == null) {
            untitledCounter--;
            return;
        }
        openNewDocument(creation.displayName(), creation.charset(), creation.dbf());
    }

    private void cutCurrentSelection() {
        if (copySelectedCellToClipboard()) {
            clearSelectedCellValue();
            return;
        }
        TextEditSupport.cutFocusedText();
    }

    private void copyCurrentSelection() {
        if (copySelectedCellToClipboard()) {
            return;
        }
        TextEditSupport.copyFocusedText();
    }

    private void pasteCurrentSelection() {
        if (pasteClipboardIntoSelectedCell()) {
            return;
        }
        TextEditSupport.pasteFocusedText();
    }

    private Charset charsetFromDisplayName(String displayName) {
        try {
            return CharsetRegistry.forDisplayName(displayName);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                this,
                localization.text("error.charset.unknown", displayName),
                localization.text("dialog.error.title"),
                JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private Charset selectedCharset() {
        DocumentModel document = currentDocument();
        if (document != null && document.charset != null) {
            return document.charset;
        }
        return pendingCharset;
    }

    private void applySelectedCharsetName(String displayName) {
        DocumentModel document = currentDocument();
        Charset charset = charsetFromDisplayName(displayName);
        if (charset == null) {
            return;
        }
        if (document == null) {
            pendingCharset = charset;
            editorMenuBar.syncCharsetMenu(CharsetRegistry.displayName(pendingCharset));
            return;
        }
        applyCharsetToDocument(document, charset);
    }

    private void loadDbfFiles(List<Path> selectedPaths, Charset charset) {
        if (selectedPaths.isEmpty()) {
            return;
        }
        setBusy(true, localization.text("status.loading"));
        new SwingWorker<List<DocumentFileService.LoadedDocument>, Void>() {
            @Override
            protected List<DocumentFileService.LoadedDocument> doInBackground() throws Exception {
                return DocumentFileService.loadDocuments(selectedPaths, charset);
            }

            @Override
            protected void done() {
                try {
                    List<DocumentFileService.LoadedDocument> loadedDocuments = get();
                    for (DocumentFileService.LoadedDocument loadedDocument : loadedDocuments) {
                        openOrReplaceDocument(
                            loadedDocument.path(),
                            loadedDocument.charset(),
                            DocumentFileService.copyDbf(loadedDocument.dbf())
                        );
                    }
                    if (!loadedDocuments.isEmpty()) {
                        int firstIndex = documentController.findDocumentIndex(loadedDocuments.get(0).path());
                        if (firstIndex >= 0) {
                            tabbedPane.setSelectedIndex(firstIndex);
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                        DBFEditorUI.this,
                        localization.text("error.file.read") + "\n" + rootCauseMessage(e),
                        localization.text("dialog.error.title"),
                        JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void applyCharsetToDocument(DocumentModel document, Charset charset) {
        if (document == null || charset == null || document.path == null || busy || charset.equals(document.charset)) {
            return;
        }

        if (document.modified) {
            int answer = JOptionPane.showConfirmDialog(
                this,
                localization.text("dialog.charset_reload.message"),
                localization.text("dialog.charset_reload.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (answer != JOptionPane.YES_OPTION) {
                documentUiController.syncCharsetSelection(document.charset);
                return;
            }
        }

        reloadDocumentWithCharset(document, charset);
    }

    private void reloadDocumentWithCharset(DocumentModel document, Charset charset) {
        setBusy(true, localization.text("status.loading"));
        Path path = document.path;
        new SwingWorker<DBFEngine.DBFFile, Void>() {
            @Override
            protected DBFEngine.DBFFile doInBackground() throws Exception {
                return DocumentFileService.reload(path, charset);
            }

            @Override
            protected void done() {
                try {
                    DBFEngine.DBFFile reloaded = get();
                    DocumentView view = viewFor(document);
                    pendingCharset = charset;
                    document.charset = charset;
                    document.dbf = DocumentFileService.copyDbf(reloaded);
                    document.modified = false;
                    view.tableModel.setDbf(document.dbf);
                    TableColumnSizer.packColumns(view.table);
                    updateTabTitle(document);
                    documentUiController.updateWindowTitle(busy);
                    documentUiController.updateStatusBar(null);
                } catch (Exception e) {
                    documentUiController.syncCharsetSelection(document.charset);
                    JOptionPane.showMessageDialog(
                        DBFEditorUI.this,
                        localization.text("error.file.read") + "\n" + rootCauseMessage(e),
                        localization.text("dialog.error.title"),
                        JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void editSelectedRecord() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        DocumentView view = viewFor(document);

        int viewRow = view.table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }

        int rowIndex = view.table.convertRowIndexToModel(viewRow);
        List<String> currentRow = new ArrayList<>(document.dbf.records().get(rowIndex));
        List<String> editedRow = RecordEditorDialog.show(this, localization, rowIndex, document.dbf.fields(), currentRow, false, document.charset);
        if (editedRow == null) {
            return;
        }

        List<List<String>> beforeRecords = snapshotRecords(document.dbf.records());
        document.dbf.records().set(rowIndex, editedRow);
        List<List<String>> afterRecords = snapshotRecords(document.dbf.records());
        registerFieldContentEdit(document, beforeRecords, afterRecords);
        view.tableModel.fireRowUpdated(rowIndex);
        TableColumnSizer.packColumns(view.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);
    }

    private void addNewRecord() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        DocumentView view = viewFor(document);

        List<String> emptyRow = new ArrayList<>(document.dbf.fields().size());
        for (int i = 0; i < document.dbf.fields().size(); i++) {
            emptyRow.add("");
        }

        int rowIndex = document.dbf.records().size();
        List<String> newRow = RecordEditorDialog.show(this, localization, rowIndex, document.dbf.fields(), emptyRow, true, document.charset);
        if (newRow == null) {
            return;
        }

        document.dbf.records().add(newRow);
        view.tableModel.fireRowInserted(rowIndex);
        TableColumnSizer.packColumns(view.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);

        int viewRow = view.table.convertRowIndexToView(rowIndex);
        view.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
    }

    private void deleteSelectedRecords() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        DocumentView view = viewFor(document);

        int[] selectedViewRows = view.table.getSelectedRows();
        if (selectedViewRows.length == 0) {
            JOptionPane.showMessageDialog(
                this,
                localization.text("dialog.delete.none"),
                localization.text("dialog.delete.title"),
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
            this,
            localization.text("dialog.delete.confirm", selectedViewRows.length),
            localization.text("dialog.delete.confirm_title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        int[] selectedModelRows = new int[selectedViewRows.length];
        for (int i = 0; i < selectedViewRows.length; i++) {
            selectedModelRows[i] = view.table.convertRowIndexToModel(selectedViewRows[i]);
        }

        for (int i = selectedModelRows.length - 1; i >= 0; i--) {
            document.dbf.records().remove(selectedModelRows[i]);
        }

        view.table.clearSelection();
        view.tableModel.fireTableDataChanged();
        TableColumnSizer.packColumns(view.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);
    }

    private void searchCurrentDocument() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        selectMatch(searchFilterWorkflow.searchCurrent(document, viewFor(document)), viewFor(document));
    }

    private void searchNextMatch() {
        continueSearch(true);
    }

    private void searchPreviousMatch() {
        continueSearch(false);
    }

    private void continueSearch(boolean forward) {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        selectMatch(searchFilterWorkflow.continueSearch(document, viewFor(document), forward), viewFor(document));
    }

    private void selectMatch(SearchFilterWorkflow.MatchLocation match, DocumentView view) {
        if (match == null || view == null) {
            return;
        }
        int viewRow = view.table.convertRowIndexToView(match.rowIndex());
        int viewColumn = view.table.convertColumnIndexToView(match.columnIndex());
        view.table.changeSelection(viewRow, viewColumn, false, false);
        view.table.requestFocusInWindow();
    }

    private void replaceInCurrentDocument() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        SearchFilterWorkflow.ReplaceOutcome outcome;
        try {
            outcome = searchFilterWorkflow.replaceInCurrentDocument(document, viewFor(document));
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                this,
                e.getMessage(),
                localization.text("dialog.replace.title"),
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        if (outcome == null) {
            return;
        }

        registerFieldContentEdit(document, outcome.beforeRecords(), outcome.afterRecords());
        DocumentView view = viewFor(document);
        view.tableModel.fireTableDataChanged();
        TableColumnSizer.packColumns(view.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);
    }

    private void showFilterDialog() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        if (!searchFilterWorkflow.showFilterDialog(document)) {
            return;
        }
        applyDocumentFilter(document);
    }

    private void clearCurrentFilter() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        searchFilterWorkflow.clearFilter(document);
        applyDocumentFilter(document);
    }

    private void applyDocumentFilter(DocumentModel document) {
        DocumentView view = viewFor(document);
        if (view != null) {
            applyDocumentFilter(document, view);
        }
    }

    private void applyDocumentFilter(DocumentModel document, DocumentView view) {
        searchFilterWorkflow.applyFilter(document, view, () -> documentUiController.updateStatusBar(null));
    }

    private boolean copySelectedCellToClipboard() {
        SelectedCell selectedCell = selectedCell();
        if (selectedCell == null) {
            return false;
        }

        String value = selectedCell.document.dbf.records().get(selectedCell.rowIndex).get(selectedCell.columnIndex);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
            new StringSelection(value == null ? "" : value),
            null
        );
        return true;
    }

    private void clearSelectedCellValue() {
        SelectedCell selectedCell = selectedCell();
        if (selectedCell == null) {
            return;
        }
        updateSelectedCellValue(selectedCell, "");
    }

    private boolean pasteClipboardIntoSelectedCell() {
        SelectedCell selectedCell = selectedCell();
        if (selectedCell == null) {
            return false;
        }

        try {
            Object clipboardValue = Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .getData(DataFlavor.stringFlavor);
            String pastedText = clipboardValue == null ? "" : clipboardValue.toString();
            updateSelectedCellValue(selectedCell, pastedText);
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                this,
                localization.text("dialog.clipboard.error"),
                localization.text("dialog.error.title"),
                JOptionPane.ERROR_MESSAGE
            );
            return true;
        }
    }

    private void updateSelectedCellValue(SelectedCell selectedCell, String newValue) {
        DBFEngine.FieldDescriptor field = selectedCell.document.dbf.fields().get(selectedCell.columnIndex);
        String validationError = DBFEngine.validateValue(field, newValue, selectedCell.document.charset);
        if (validationError != null) {
            JOptionPane.showMessageDialog(
                this,
                localization.text("dialog.clipboard.invalid_value", field.name(), validationError),
                localization.text("dialog.error.title"),
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        List<List<String>> beforeRecords = snapshotRecords(selectedCell.document.dbf.records());
        selectedCell.document.dbf.records().get(selectedCell.rowIndex).set(selectedCell.columnIndex, newValue);
        List<List<String>> afterRecords = snapshotRecords(selectedCell.document.dbf.records());
        registerFieldContentEdit(selectedCell.document, beforeRecords, afterRecords);
        DocumentView view = viewFor(selectedCell.document);
        view.tableModel.fireRowUpdated(selectedCell.rowIndex);
        TableColumnSizer.packColumns(view.table);
        selectedCell.document.modified = true;
        updateTabTitle(selectedCell.document);
        documentUiController.updateWindowTitle(busy);
        int viewRow = view.table.convertRowIndexToView(selectedCell.rowIndex);
        int viewColumn = view.table.convertColumnIndexToView(selectedCell.columnIndex);
        view.table.changeSelection(viewRow, viewColumn, false, false);
    }

    private SelectedCell selectedCell() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return null;
        }
        DocumentView view = viewFor(document);
        int viewRow = view.table.getSelectedRow();
        int viewColumn = view.table.getSelectedColumn();
        if (viewRow < 0 || viewColumn < 0) {
            return null;
        }
        return new SelectedCell(
            document,
            view.table.convertRowIndexToModel(viewRow),
            view.table.convertColumnIndexToModel(viewColumn)
        );
    }

    private int replaceAllMatches(DocumentModel document, String searchText, String replaceText, boolean caseSensitive) {
        int replacements = 0;
        String effectiveSearch = caseSensitive ? searchText : searchText.toLowerCase();
        int rowCount = document.dbf.records().size();
        int startRow = searchStartRow(document, rowCount, true);

        for (int offset = 0; offset < rowCount; offset++) {
            int rowIndex = (startRow + offset) % rowCount;
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

    private int searchStartRow(DocumentModel document, int rowCount, boolean forward) {
        if (rowCount <= 0) {
            return 0;
        }
        int selectedViewRow = viewFor(document).table.getSelectedRow();
        if (selectedViewRow < 0) {
            return forward ? 0 : rowCount - 1;
        }
        int selectedModelRow = viewFor(document).table.convertRowIndexToModel(selectedViewRow);
        return forward
            ? (selectedModelRow + 1) % rowCount
            : Math.floorMod(selectedModelRow - 1, rowCount);
    }

    private void undoCurrentFieldChange() {
        DocumentModel document = currentDocument();
        if (document == null || busy || document.undoStack.isEmpty()) {
            return;
        }

        DocumentModel.FieldContentEdit edit = document.undoStack.removeLast();
        document.redoStack.addLast(edit);
        applyRecordSnapshot(document, edit.beforeRecords());
    }

    private void redoCurrentFieldChange() {
        DocumentModel document = currentDocument();
        if (document == null || busy || document.redoStack.isEmpty()) {
            return;
        }

        DocumentModel.FieldContentEdit edit = document.redoStack.removeLast();
        document.undoStack.addLast(edit);
        applyRecordSnapshot(document, edit.afterRecords());
    }

    private void registerFieldContentEdit(DocumentModel document, List<List<String>> beforeRecords, List<List<String>> afterRecords) {
        if (beforeRecords.equals(afterRecords)) {
            return;
        }
        document.undoStack.addLast(new DocumentModel.FieldContentEdit(beforeRecords, afterRecords));
        document.redoStack.clear();
    }

    private void applyRecordSnapshot(DocumentModel document, List<List<String>> records) {
        document.dbf.records().clear();
        for (List<String> row : records) {
            document.dbf.records().add(new ArrayList<>(row));
        }
        DocumentView view = viewFor(document);
        view.tableModel.fireTableDataChanged();
        TableColumnSizer.packColumns(view.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);
    }

    private List<List<String>> snapshotRecords(List<List<String>> records) {
        List<List<String>> snapshot = new ArrayList<>(records.size());
        for (List<String> row : records) {
            snapshot.add(new ArrayList<>(row));
        }
        return snapshot;
    }

    private record SelectedCell(DocumentModel document, int rowIndex, int columnIndex) {
    }

    private record MatchLocation(int rowIndex, int columnIndex) {
    }

    private void editDatabaseStructure() {
        DocumentModel document = currentDocument();
        if (document == null || busy) {
            return;
        }
        StructureEditorDialog.Result result = StructureEditorDialog.show(this, localization, document.dbf);
        if (result != null) {
            applyStructureEdit(document, result);
        }
    }

    private void applyStructureEdit(DocumentModel document, StructureEditorDialog.Result result) {
        List<List<String>> newRecords = new ArrayList<>(document.dbf.records().size());
        for (List<String> oldRow : document.dbf.records()) {
            List<String> newRow = new ArrayList<>(result.fields().size());
            for (int i = 0; i < result.fields().size(); i++) {
                int sourceIndex = result.sourceIndexes().get(i);
                String value = sourceIndex >= 0 && sourceIndex < oldRow.size() ? oldRow.get(sourceIndex) : "";
                String validationError = DBFEngine.validateValue(result.fields().get(i), value, document.charset);
                if (validationError != null) {
                    throw new IllegalArgumentException(localization.text("dialog.structure.error.record", i + 1, validationError));
                }
                newRow.add(value);
            }
            newRecords.add(newRow);
        }

        document.dbf = rebuildDbf(document.dbf, result.fields(), newRecords);
        DocumentView view = viewFor(document);
        view.table.clearSelection();
        view.tableModel.setDbf(document.dbf);
        TableColumnSizer.packColumns(view.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);
    }

    private DBFEngine.DBFFile rebuildDbf(DBFEngine.DBFFile source, List<DBFEngine.FieldDescriptor> fields, List<List<String>> records) {
        int headerLength = 32 + fields.size() * 32 + 1;
        int recordLength = 1;
        for (DBFEngine.FieldDescriptor field : fields) {
            recordLength += field.length();
        }

        return new DBFEngine.DBFFile(
            source.version(),
            source.lastUpdate(),
            records.size(),
            headerLength,
            recordLength,
            fields,
            records
        );
    }

    private DBFEngine.DBFFile createDefaultNewDbf() {
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("NEWFIELD", 'C', 20, 0)
        );
        return rebuildDbf(
            new DBFEngine.DBFFile((byte) 0x03, LocalDate.now(), 0, 0, 0, fields, new ArrayList<>()),
            fields,
            new ArrayList<>()
        );
    }

    private DBFEngine.DBFFile snapshotVisibleDbf(DocumentModel document) {
        DocumentView view = viewFor(document);
        List<List<String>> visibleRecords = new ArrayList<>(view.table.getRowCount());
        for (int viewRow = 0; viewRow < view.table.getRowCount(); viewRow++) {
            int modelRow = view.table.convertRowIndexToModel(viewRow);
            visibleRecords.add(new ArrayList<>(document.dbf.records().get(modelRow)));
        }
        return rebuildDbf(document.dbf, document.dbf.fields(), visibleRecords);
    }

    private void saveCurrentFile() {
        DocumentModel document = currentDocument();
        if (busy || document == null) {
            return;
        }
        Path path = saveExportWorkflow.chooseSavePath(
            document,
            document.path == null,
            () -> !hasActiveFilter(document)
                || saveExportWorkflow.confirmFilteredWrite(viewFor(document).table.getRowCount(), document.dbf.records().size(), localization.text(document.path == null ? "dialog.save_as.title" : "dialog.save.title"))
        );
        if (path == null) {
            return;
        }
        saveToPath(document, path);
    }

    private void closeCurrentFile() {
        closeDocumentAt(tabbedPane.getSelectedIndex());
    }

    private void maybeShowTabPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int index = tabbedPane.indexAtLocation(event.getX(), event.getY());
        if (index < 0) {
            return;
        }
        tabbedPane.setSelectedIndex(index);
        showTabPopup(event, index);
    }

    private void showTabPopup(MouseEvent event, int index) {
        showTabPopup(tabbedPane, event.getX(), event.getY(), index);
    }

    private void showTabPopup(Component invoker, int x, int y, int index) {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem closeItem = new JMenuItem(localization.text("menu.file.close"));
        closeItem.addActionListener(e -> closeDocumentAt(index));
        popupMenu.add(closeItem);

        JMenuItem closeOthersItem = new JMenuItem(localization.text("popup.tab.close_others"));
        closeOthersItem.setEnabled(documentController.documents().size() > 1);
        closeOthersItem.addActionListener(e -> closeOtherTabs(index));
        popupMenu.add(closeOthersItem);

        JMenuItem closeAllItem = new JMenuItem(localization.text("popup.tab.close_all"));
        closeAllItem.setEnabled(!documentController.documents().isEmpty());
        closeAllItem.addActionListener(e -> closeAllTabs());
        popupMenu.add(closeAllItem);

        popupMenu.show(invoker, x, y);
    }

    private void saveFileAs() {
        DocumentModel document = currentDocument();
        if (busy || document == null) {
            return;
        }
        Path path = saveExportWorkflow.chooseSavePath(
            document,
            true,
            () -> !hasActiveFilter(document)
                || saveExportWorkflow.confirmFilteredWrite(viewFor(document).table.getRowCount(), document.dbf.records().size(), localization.text("dialog.save_as.title"))
        );
        if (path != null) {
            saveToPath(document, path);
        }
    }

    private void saveToPath(DocumentModel document, Path path) {
        setBusy(true, localization.text("status.saving"));
        DBFEngine.DBFFile snapshot = snapshotVisibleDbf(document);
        Charset charset = document.charset;

        new SwingWorker<DBFEngine.DBFFile, Void>() {
            @Override
            protected DBFEngine.DBFFile doInBackground() throws Exception {
                return DocumentFileService.save(path, charset, snapshot);
            }

            @Override
            protected void done() {
                try {
                    DBFEngine.DBFFile saved = get();
                    DocumentView view = viewFor(document);
                    document.dbf = DocumentFileService.copyDbf(saved);
                    document.path = path;
                    document.displayName = path.getFileName().toString();
                    document.modified = false;
                    document.undoStack.clear();
                    document.redoStack.clear();
                    view.tableModel.setDbf(document.dbf);
                    applyDocumentFilter(document);
                    TableColumnSizer.packColumns(view.table);
                    updateTabTitle(document);
                    documentUiController.updateWindowTitle(busy);
                    JOptionPane.showMessageDialog(
                        DBFEditorUI.this,
                        localization.text("dialog.save.success"),
                        localization.text("dialog.save.title"),
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                        DBFEditorUI.this,
                        localization.text("dialog.save.error") + "\n" + rootCauseMessage(e),
                        localization.text("dialog.error.title"),
                        JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void exportCurrentDocument(ExportFormat format) {
        DocumentModel document = currentDocument();
        if (busy || document == null) {
            return;
        }
        SaveExportWorkflow.ExportRequest request = saveExportWorkflow.requestExport(
            document,
            format,
            () -> !hasActiveFilter(document)
                || saveExportWorkflow.confirmFilteredWrite(viewFor(document).table.getRowCount(), document.dbf.records().size(), localization.text("dialog.export.title")),
            this::askSqlDialect,
            suggestExportPath(document, format)
        );
        if (request != null) {
            exportToPath(document, request.path(), format, request.sqlDialect());
        }
    }

    private boolean hasActiveFilter(DocumentModel document) {
        return document != null
            && document.filterText != null
            && !document.filterText.trim().isEmpty()
            && viewFor(document).table.getRowCount() != document.dbf.records().size();
    }

    private void exportToPath(DocumentModel document, Path path, ExportFormat format, SqlDialect sqlDialect) {
        setBusy(true, localization.text("status.exporting"));
        DBFEngine.DBFFile snapshot = snapshotVisibleDbf(document);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                DocumentFileService.export(path, snapshot, format, exportTableName(document), sqlDialect);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(
                        DBFEditorUI.this,
                        localization.text("dialog.export.success"),
                        localization.text("dialog.export.title"),
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                        DBFEditorUI.this,
                        localization.text("dialog.export.error") + "\n" + rootCauseMessage(e),
                        localization.text("dialog.error.title"),
                        JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private SqlDialect askSqlDialect() {
        return SqlDialectDialog.show(this, localization);
    }

    private Path suggestExportPath(DocumentModel document, ExportFormat format) {
        String baseName = document.path != null ? stripExtension(document.path.getFileName().toString()) : "export";
        Path parent = document.path != null && document.path.getParent() != null
            ? document.path.getParent()
            : Path.of("").toAbsolutePath();
        return parent.resolve(baseName + format.extension());
    }

    private Path appendExtensionIfMissing(Path path, String extension) {
        String fileName = path.getFileName() != null ? path.getFileName().toString().toLowerCase() : "";
        if (fileName.endsWith(extension)) {
            return path;
        }
        return path.resolveSibling((path.getFileName() != null ? path.getFileName().toString() : "export") + extension);
    }

    private String exportTableName(DocumentModel document) {
        String baseName = document.path != null ? stripExtension(document.path.getFileName().toString()) : "dbf_export";
        return baseName.isBlank() ? "dbf_export" : baseName;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private boolean confirmDiscardChanges(DocumentModel document) {
        if (!document.modified) {
            return true;
        }

        int answer = JOptionPane.showConfirmDialog(
            this,
            localization.text("dialog.unsaved.message"),
            localization.text("dialog.unsaved.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        return answer == JOptionPane.YES_OPTION;
    }

    private void exitApplication() {
        if (busy) {
            return;
        }
        for (DocumentModel document : documentController.documents()) {
            if (!confirmDiscardChanges(document)) {
                return;
            }
        }
        if (!busy) {
            dispose();
            System.exit(0);
        }
    }

    private void setBusy(boolean busyState, String message) {
        busy = busyState;
        documentUiController.applyBusyState(busyState, message);
    }

    private static String loadSavedLanguageCode() {
        return PREFERENCES.get(PREF_LANGUAGE, "hu");
    }

    private static String loadSavedLookAndFeelId() {
        return PREFERENCES.get(PREF_LOOK_AND_FEEL, "javax.swing.plaf.metal.MetalLookAndFeel");
    }

    private String rootCauseMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    private String currentLookAndFeelSelectionId() {
        return PREFERENCES.get(PREF_LOOK_AND_FEEL, currentLookAndFeelClassName());
    }

    private String currentLookAndFeelClassName() {
        return LookAndFeelSupport.currentClassName();
    }

    private static void applyLookAndFeelOption(LookAndFeelOption option) throws Exception {
        LookAndFeelSupport.apply(option);
    }

    private DocumentModel currentDocument() {
        return documentController.currentDocument();
    }

    private DocumentView currentDocumentView() {
        return documentController.currentView();
    }

    private DocumentView viewFor(DocumentModel document) {
        return documentController.viewOf(document);
    }

    private void openOrReplaceDocument(Path path, Charset charset, DBFEngine.DBFFile dbf) {
        DocumentModel document = createDocumentModel(path != null ? path.getFileName().toString() : localization.text("app.untitled"), path, charset, dbf, false);
        DocumentView view = createDocumentView(document);
        documentController.openOrReplaceDocument(
            path,
            document,
            view,
            this::buildTabTitle,
            (existing, existingView) -> {
                existing.displayName = path != null ? path.getFileName().toString() : existing.displayName;
                existing.charset = charset;
                existing.dbf = dbf;
                existing.modified = false;
                existingView.tableModel.setDbf(existing.dbf);
                applyDocumentFilter(existing);
                TableColumnSizer.packColumns(existingView.table);
            }
        );
        updateViewFromCurrentDocument();
        updateTabTitle(currentDocument());
        documentUiController.updateStatusBar(null);
    }

    private void openNewDocument(String displayName, Charset charset, DBFEngine.DBFFile dbf) {
        DocumentModel document = createDocumentModel(displayName, null, charset, dbf, true);
        DocumentView view = createDocumentView(document);
        documentController.openOrReplaceDocument(null, document, view, this::buildTabTitle, (existing, existingView) -> { });
        updateViewFromCurrentDocument();
        updateTabTitle(currentDocument());
        documentUiController.updateStatusBar(null);
        documentUiController.updateWindowTitle(busy);
    }

    private void updateViewFromCurrentDocument() {
        documentUiController.refreshCurrentDocumentView(busy);
    }

    private String buildTabTitle(DocumentModel document) {
        return buildTabTitle(document.displayName, document.path, document.modified);
    }

    private void updateTabTitle(DocumentModel document) {
        documentController.updateTabTitle(document, this::buildTabTitle);
    }

    private DocumentModel createDocumentModel(String displayName, Path path, Charset charset, DBFEngine.DBFFile dbf, boolean modified) {
        return new DocumentModel(displayName, path, charset, dbf, modified);
    }

    private DocumentView createDocumentView(DocumentModel document) {
        DBFTableModel model = new DBFTableModel();
        model.setDbf(document.dbf);

        TableRowSorter<DBFTableModel> rowSorter = new TableRowSorter<>(model);
        JTable documentTable = new JTable(model);
        documentTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        documentTable.setRowSorter(rowSorter);
        documentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!busy && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    editSelectedRecord();
                }
            }
        });

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JScrollPane(documentTable), BorderLayout.CENTER);

        DocumentView documentView = new DocumentView(panel, documentTable, model, rowSorter);
        applyDocumentFilter(document, documentView);
        TableColumnSizer.packColumns(documentView.table);
        return documentView;
    }

    private void closeDocumentAt(int index) {
        if (busy) {
            return;
        }
        if (documentController.closeDocumentAt(index, this::confirmDiscardChanges)) {
            updateViewFromCurrentDocument();
            setBusy(false, null);
        }
    }

    private void closeOtherTabs(int keepIndex) {
        if (busy || keepIndex < 0 || keepIndex >= documentController.documents().size()) {
            return;
        }
        DocumentModel keepDocument = documentController.documents().get(keepIndex);
        for (int index = documentController.documents().size() - 1; index >= 0; index--) {
            if (documentController.documents().get(index) == keepDocument) {
                continue;
            }
            int beforeSize = documentController.documents().size();
            closeDocumentAt(index);
            if (documentController.documents().size() == beforeSize) {
                return;
            }
        }
    }

    private void closeAllTabs() {
        if (busy) {
            return;
        }
        for (int index = documentController.documents().size() - 1; index >= 0; index--) {
            int beforeSize = documentController.documents().size();
            closeDocumentAt(index);
            if (documentController.documents().size() == beforeSize) {
                return;
            }
        }
    }

    private String buildTabTitle(String displayName, Path path, boolean modified) {
        String baseName = path != null ? path.getFileName().toString() : displayName;
        return modified ? baseName + " *" : baseName;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                LookAndFeelSupport.applyStartupLookAndFeel();

                DBFEditorUI viewer = new DBFEditorUI();
                viewer.setVisible(true);
                viewer.restoreSavedLookAndFeel();
                viewer.openFilesFromArguments(args);
            } catch (Exception e) {
                e.printStackTrace(System.err);
                JOptionPane.showMessageDialog(
                    null,
                    "A felhasználói felület indítása sikertelen:\n" + e,
                    "Indítási hiba",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private void openFilesFromArguments(String[] args) {
        if (args == null || args.length == 0) {
            return;
        }

        Charset charset = selectedCharset();
        if (charset == null) {
            return;
        }

        List<Path> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            paths.add(Path.of(arg));
        }
        loadDbfFiles(paths, charset);
    }

    private void restoreSavedLookAndFeel() {
        String preferredId = loadSavedLookAndFeelId();
        if (preferredId == null || preferredId.isBlank()) {
            return;
        }
        LookAndFeelOption preferred = LookAndFeelSupport.findOption(preferredId);
        if (preferred == null) {
            return;
        }
        String currentClassName = currentLookAndFeelClassName();
        boolean alreadyApplied = preferred.className().equals(currentClassName);
        if (!alreadyApplied) {
            switchLookAndFeel(preferred);
        }
    }

}
