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
import com.vd.dbfeditor.ui.TabHeader;
import com.vd.dbfeditor.ui.TableColumnSizer;
import com.vd.dbfeditor.ui.dialog.RecordEditorDialog;
import com.vd.dbfeditor.ui.dialog.SqlDialectDialog;
import com.vd.dbfeditor.ui.dialog.StructureEditorDialog;
import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class DBFEditorUI extends JFrame {
    private static final String PREF_LANGUAGE = "language";
    private static final String PREF_LOOK_AND_FEEL = "lookAndFeel";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(DBFEditorUI.class);

    private final JComboBox<String> charsetCombo;
    private final JLabel charsetLabel;
    private final JLabel statusBarLabel;
    private final Localization localization;
    private final JTabbedPane tabbedPane;
    private final DocumentController documentController;
    private final EditorMenuBar editorMenuBar;
    private final FileChooserFactory fileChooserFactory;
    private DocumentUiController documentUiController;

    private boolean busy;
    private boolean updatingCharsetCombo;

    public DBFEditorUI() {
        super("");
        localization = new Localization(loadSavedLanguageCode());
        fileChooserFactory = new FileChooserFactory(localization);

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

        charsetCombo = new JComboBox<>(CharsetRegistry.supportedDisplayNames());
        charsetCombo.setEditable(false);
        charsetCombo.addActionListener(e -> {
            if (!updatingCharsetCombo) {
                DocumentState document = currentDocument();
                if (document != null) {
                    Charset charset = selectedCharset();
                    if (charset != null) {
                        applyCharsetToDocument(document, charset);
                    }
                }
            }
        });
        charsetLabel = new JLabel();

        JPanel topPanel = new JPanel();
        topPanel.add(charsetLabel);
        topPanel.add(charsetCombo);
        add(topPanel, BorderLayout.NORTH);

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
        add(tabbedPane, BorderLayout.CENTER);
        documentController = new DocumentController(tabbedPane);

        statusBarLabel = new JLabel();
        statusBarLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusBarLabel, BorderLayout.SOUTH);

        documentUiController = new DocumentUiController(
            this,
            localization,
            charsetCombo,
            statusBarLabel,
            this::currentDocument,
            documentController::documents,
            editorMenuBar.openAction(),
            editorMenuBar.fileBoundActions()
        );

        charsetCombo.setSelectedItem(CharsetRegistry.displayName(DBFEngine.DEFAULT_CHARSET));
        applyLocalization();
        updateViewFromCurrentDocument();
        documentUiController.updateWindowTitle(busy);
    }

    private EditorMenuBar createMenuBar() {
        EditorMenuBar menuBar = new EditorMenuBar(
            this::openDbfFile,
            this::closeCurrentFile,
            this::saveCurrentFile,
            this::saveFileAs,
            this::exitApplication,
            this::exportCurrentDocument,
            this::addNewRecord,
            this::editSelectedRecord,
            this::deleteSelectedRecords,
            this::editDatabaseStructure,
            this::showAboutDialog
        );
        menuBar.rebuildLanguageMenu(localization, this::switchLanguage);
        menuBar.rebuildLookAndFeelMenu(LookAndFeelSupport.availableOptions(), currentLookAndFeelSelectionId(), this::switchLookAndFeel);
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
        charsetLabel.setText(localization.text("label.charset"));
        editorMenuBar.applyLocalization(localization);
        editorMenuBar.syncLanguageMenu(localization);

        documentUiController.updateStatusBar(null);
        documentUiController.updateWindowTitle(busy);
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(
            this,
            localization.text("dialog.about.message"),
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

    private Charset selectedCharset() {
        Object selected = charsetCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, localization.text("error.charset.none"), localization.text("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            return CharsetRegistry.forDisplayName(String.valueOf(selected));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                this,
                localization.text("error.charset.unknown", selected),
                localization.text("dialog.error.title"),
                JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
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

    private void applyCharsetToDocument(DocumentState document, Charset charset) {
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

    private void reloadDocumentWithCharset(DocumentState document, Charset charset) {
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
                    document.charset = charset;
                    document.dbf = DocumentFileService.copyDbf(reloaded);
                    document.modified = false;
                    document.tableModel.setDbf(document.dbf);
                    TableColumnSizer.packColumns(document.table);
                    if (document == currentDocument()) {
                        documentUiController.syncCharsetSelection(document.charset);
                    }
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
        DocumentState document = currentDocument();
        if (document == null || busy) {
            return;
        }

        int viewRow = document.table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }

        int rowIndex = document.table.convertRowIndexToModel(viewRow);
        List<String> currentRow = new ArrayList<>(document.dbf.records().get(rowIndex));
        List<String> editedRow = RecordEditorDialog.show(this, localization, rowIndex, document.dbf.fields(), currentRow, false, document.charset);
        if (editedRow == null) {
            return;
        }

        document.dbf.records().set(rowIndex, editedRow);
        document.tableModel.fireRowUpdated(rowIndex);
        TableColumnSizer.packColumns(document.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);
    }

    private void addNewRecord() {
        DocumentState document = currentDocument();
        if (document == null || busy) {
            return;
        }

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
        document.tableModel.fireRowInserted(rowIndex);
        TableColumnSizer.packColumns(document.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);

        int viewRow = document.table.convertRowIndexToView(rowIndex);
        document.table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
    }

    private void deleteSelectedRecords() {
        DocumentState document = currentDocument();
        if (document == null || busy) {
            return;
        }

        int[] selectedViewRows = document.table.getSelectedRows();
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
            selectedModelRows[i] = document.table.convertRowIndexToModel(selectedViewRows[i]);
        }

        for (int i = selectedModelRows.length - 1; i >= 0; i--) {
            document.dbf.records().remove(selectedModelRows[i]);
        }

        document.table.clearSelection();
        document.tableModel.fireTableDataChanged();
        TableColumnSizer.packColumns(document.table);
        document.modified = true;
        updateTabTitle(document);
        documentUiController.updateWindowTitle(busy);
    }

    private void editDatabaseStructure() {
        DocumentState document = currentDocument();
        if (document == null || busy) {
            return;
        }
        StructureEditorDialog.Result result = StructureEditorDialog.show(this, localization, document.dbf);
        if (result != null) {
            applyStructureEdit(document, result);
        }
    }

    private void applyStructureEdit(DocumentState document, StructureEditorDialog.Result result) {
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
        document.table.clearSelection();
        document.tableModel.setDbf(document.dbf);
        TableColumnSizer.packColumns(document.table);
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

    private void saveCurrentFile() {
        DocumentState document = currentDocument();
        if (busy || document == null || document.path == null) {
            return;
        }
        saveToPath(document, document.path);
    }

    private void closeCurrentFile() {
        closeDocumentAt(tabbedPane.getSelectedIndex());
    }

    private void saveFileAs() {
        DocumentState document = currentDocument();
        if (busy || document == null) {
            return;
        }

        JFileChooser chooser = fileChooserFactory.createDbfChooser(localization.text("dialog.save_as.title"), false);
        if (document.path != null) {
            chooser.setSelectedFile(document.path.toFile());
        }

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selectedPath = chooser.getSelectedFile().toPath();
        if (Files.exists(selectedPath)) {
            int overwrite = JOptionPane.showConfirmDialog(
                this,
                localization.text("dialog.save_as.overwrite_message"),
                localization.text("dialog.save_as.overwrite_title"),
                JOptionPane.YES_NO_OPTION
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        saveToPath(document, selectedPath);
    }

    private void saveToPath(DocumentState document, Path path) {
        setBusy(true, localization.text("status.saving"));
        DBFEngine.DBFFile snapshot = DocumentFileService.copyDbf(document.dbf);
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
                    document.dbf = DocumentFileService.copyDbf(saved);
                    document.path = path;
                    document.modified = false;
                    document.tableModel.setDbf(document.dbf);
                    TableColumnSizer.packColumns(document.table);
                    if (document == currentDocument()) {
                        documentUiController.syncCharsetSelection(document.charset);
                    }
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
        DocumentState document = currentDocument();
        if (busy || document == null) {
            return;
        }

        SqlDialect sqlDialect = null;
        if (format == ExportFormat.SQL) {
            sqlDialect = askSqlDialect();
            if (sqlDialect == null) {
                return;
            }
        }

        JFileChooser chooser = fileChooserFactory.createExportChooser(format, localization.text(format.dialogTitleKey()));
        chooser.setSelectedFile(suggestExportPath(document, format).toFile());

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selectedPath = appendExtensionIfMissing(chooser.getSelectedFile().toPath(), format.extension());
        if (Files.exists(selectedPath)) {
            int overwrite = JOptionPane.showConfirmDialog(
                this,
                localization.text("dialog.save_as.overwrite_message"),
                localization.text("dialog.save_as.overwrite_title"),
                JOptionPane.YES_NO_OPTION
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        exportToPath(document, selectedPath, format, sqlDialect);
    }

    private void exportToPath(DocumentState document, Path path, ExportFormat format, SqlDialect sqlDialect) {
        setBusy(true, localization.text("status.exporting"));
        DBFEngine.DBFFile snapshot = DocumentFileService.copyDbf(document.dbf);

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

    private Path suggestExportPath(DocumentState document, ExportFormat format) {
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

    private String exportTableName(DocumentState document) {
        String baseName = document.path != null ? stripExtension(document.path.getFileName().toString()) : "dbf_export";
        return baseName.isBlank() ? "dbf_export" : baseName;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private boolean confirmDiscardChanges(DocumentState document) {
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
        for (DocumentState document : documentController.documents()) {
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

    private DocumentState currentDocument() {
        return documentController.currentDocument();
    }

    private void openOrReplaceDocument(Path path, Charset charset, DBFEngine.DBFFile dbf) {
        DocumentState document = createDocumentState(path, charset, dbf);
        documentController.openOrReplaceDocument(
            path,
            document,
            this::buildTabTitle,
            existing -> {
                existing.charset = charset;
                existing.dbf = dbf;
                existing.modified = false;
                existing.tableModel.setDbf(existing.dbf);
                TableColumnSizer.packColumns(existing.table);
            }
        );
        updateViewFromCurrentDocument();
        updateTabTitle(currentDocument());
        documentUiController.updateStatusBar(null);
    }

    private void updateViewFromCurrentDocument() {
        updatingCharsetCombo = true;
        documentUiController.refreshCurrentDocumentView(busy);
        updatingCharsetCombo = false;
    }

    private String buildTabTitle(DocumentState document) {
        return buildTabTitle(document.path, document.modified);
    }

    private void updateTabTitle(DocumentState document) {
        documentController.updateTabTitle(document, this::buildTabTitle);
    }

    private DocumentState createDocumentState(Path path, Charset charset, DBFEngine.DBFFile dbf) {
        DBFTableModel model = new DBFTableModel();
        model.setDbf(dbf);

        JTable documentTable = new JTable(model);
        documentTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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

        TabHeader tabHeader = new TabHeader(tabbedPane, this::closeDocumentAt, buildTabTitle(path, false));
        DocumentState document = new DocumentState(path, charset, dbf, false, panel, documentTable, model, tabHeader);
        TableColumnSizer.packColumns(document.table);
        return document;
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

    private String buildTabTitle(Path path, boolean modified) {
        String baseName = path != null ? path.getFileName().toString() : localization.text("app.title");
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
