package com.vd.dbfeditor;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class DBFEditorUI extends JFrame {
    private static final String[] SUPPORTED_CHARSETS = {"Cp852", "ISO-8859-1", "windows-1250", "UTF-8"};
    private static final String VALID_FIELD_TYPES = "CDFLMN";
    private static final String PREF_LANGUAGE = "language";
    private static final String PREF_LOOK_AND_FEEL = "lookAndFeel";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(DBFEditorUI.class);

    private final JComboBox<String> charsetCombo;
    private final JLabel charsetLabel;
    private final JLabel statusBarLabel;
    private final Localization localization;
    private final Map<String, JRadioButtonMenuItem> languageMenuItems = new LinkedHashMap<>();
    private final Map<String, JRadioButtonMenuItem> lookAndFeelMenuItems = new LinkedHashMap<>();
    private final JTabbedPane tabbedPane;
    private final List<DocumentState> documents = new ArrayList<>();

    private JMenu fileMenu;
    private JMenu databaseMenu;
    private JMenu settingsMenu;
    private JMenu languageMenu;
    private JMenu lookAndFeelMenu;
    private JMenuItem openMenuItem;
    private JMenuItem closeMenuItem;
    private JMenuItem saveMenuItem;
    private JMenuItem saveAsMenuItem;
    private JMenuItem exitMenuItem;
    private JMenuItem addRecordMenuItem;
    private JMenuItem editRecordMenuItem;
    private JMenuItem deleteRecordMenuItem;
    private JMenuItem editStructureMenuItem;

    private boolean busy;
    private boolean updatingCharsetCombo;

    private record LoadedDocument(Path path, Charset charset, DBFEngine.DBFFile dbf) {
    }

    private record LookAndFeelOption(String id, String displayName, String className) {
    }

    private static final class DocumentState {
        private Path path;
        private Charset charset;
        private DBFEngine.DBFFile dbf;
        private boolean modified;
        private final JPanel panel;
        private final JTable table;
        private final DBFTableModel tableModel;
        private final TabHeader tabHeader;

        private DocumentState(Path path, Charset charset, DBFEngine.DBFFile dbf, boolean modified, JPanel panel, JTable table,
                DBFTableModel tableModel, TabHeader tabHeader) {
            this.path = path;
            this.charset = charset;
            this.dbf = dbf;
            this.modified = modified;
            this.panel = panel;
            this.table = table;
            this.tableModel = tableModel;
            this.tabHeader = tabHeader;
        }
    }

    public DBFEditorUI() {
        super("");
        localization = new Localization(loadSavedLanguageCode());

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

        setJMenuBar(createMenuBar());

        charsetCombo = new JComboBox<>(SUPPORTED_CHARSETS);
        charsetCombo.setEditable(false);
        charsetCombo.addActionListener(e -> {
            if (!updatingCharsetCombo) {
                DocumentState document = currentDocument();
                if (document != null) {
                    Charset charset = selectedCharset();
                    if (charset != null) {
                        document.charset = charset;
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

        statusBarLabel = new JLabel();
        statusBarLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusBarLabel, BorderLayout.SOUTH);

        charsetCombo.setSelectedItem(DBFEngine.DEFAULT_CHARSET.name());
        applyLocalization();
        updateViewFromCurrentDocument();
        updateWindowTitle();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        fileMenu = new JMenu();
        databaseMenu = new JMenu();
        settingsMenu = new JMenu();
        languageMenu = new JMenu();
        lookAndFeelMenu = new JMenu();

        openMenuItem = new JMenuItem();
        openMenuItem.addActionListener(e -> openDbfFile());
        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcutMask));

        closeMenuItem = new JMenuItem();
        closeMenuItem.addActionListener(e -> closeCurrentFile());
        closeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask));
        closeMenuItem.setEnabled(false);

        saveMenuItem = new JMenuItem();
        saveMenuItem.addActionListener(e -> saveCurrentFile());
        saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask));
        saveMenuItem.setEnabled(false);

        saveAsMenuItem = new JMenuItem();
        saveAsMenuItem.addActionListener(e -> saveFileAs());
        saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        saveAsMenuItem.setEnabled(false);

        exitMenuItem = new JMenuItem();
        exitMenuItem.addActionListener(e -> exitApplication());
        exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcutMask));

        addRecordMenuItem = new JMenuItem();
        addRecordMenuItem.addActionListener(e -> addNewRecord());
        addRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask));
        addRecordMenuItem.setEnabled(false);

        editRecordMenuItem = new JMenuItem();
        editRecordMenuItem.addActionListener(e -> editSelectedRecord());
        editRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutMask));
        editRecordMenuItem.setEnabled(false);

        deleteRecordMenuItem = new JMenuItem();
        deleteRecordMenuItem.addActionListener(e -> deleteSelectedRecords());
        deleteRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteRecordMenuItem.setEnabled(false);

        editStructureMenuItem = new JMenuItem();
        editStructureMenuItem.addActionListener(e -> editDatabaseStructure());
        editStructureMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        editStructureMenuItem.setEnabled(false);

        fileMenu.add(openMenuItem);
        fileMenu.add(closeMenuItem);
        fileMenu.add(saveMenuItem);
        fileMenu.add(saveAsMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);

        databaseMenu.add(addRecordMenuItem);
        databaseMenu.add(editRecordMenuItem);
        databaseMenu.add(deleteRecordMenuItem);
        databaseMenu.addSeparator();
        databaseMenu.add(editStructureMenuItem);
        menuBar.add(databaseMenu);

        settingsMenu.add(languageMenu);
        settingsMenu.add(lookAndFeelMenu);
        menuBar.add(settingsMenu);
        rebuildLanguageMenu();
        rebuildLookAndFeelMenu();
        return menuBar;
    }

    private void rebuildLanguageMenu() {
        languageMenu.removeAll();
        languageMenuItems.clear();

        ButtonGroup buttonGroup = new ButtonGroup();
        for (Localization.LanguageOption option : localization.availableLanguages()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.displayName());
            item.addActionListener(e -> switchLanguage(option.code()));
            item.setSelected(option.code().equals(localization.currentLanguageCode()));
            buttonGroup.add(item);
            languageMenu.add(item);
            languageMenuItems.put(option.code(), item);
        }
    }

    private void switchLanguage(String languageCode) {
        localization.setLanguage(languageCode);
        PREFERENCES.put(PREF_LANGUAGE, languageCode);
        applyLocalization();
    }

    private void rebuildLookAndFeelMenu() {
        lookAndFeelMenu.removeAll();
        lookAndFeelMenuItems.clear();

        ButtonGroup buttonGroup = new ButtonGroup();
        String currentSelection = currentLookAndFeelSelectionId();
        for (LookAndFeelOption option : availableLookAndFeelOptions()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.displayName());
            item.addActionListener(e -> switchLookAndFeel(option));
            item.setSelected(option.id().equals(currentSelection));
            buttonGroup.add(item);
            lookAndFeelMenu.add(item);
            lookAndFeelMenuItems.put(option.id(), item);
        }
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
        fileMenu.setText(localization.text("menu.file"));
        databaseMenu.setText(localization.text("menu.database"));
        settingsMenu.setText(localization.text("menu.settings"));
        languageMenu.setText(localization.text("menu.settings.language"));
        lookAndFeelMenu.setText(localization.text("menu.settings.look_and_feel"));
        openMenuItem.setText(localization.text("menu.file.open"));
        closeMenuItem.setText(localization.text("menu.file.close"));
        saveMenuItem.setText(localization.text("menu.file.save"));
        saveAsMenuItem.setText(localization.text("menu.file.save_as"));
        exitMenuItem.setText(localization.text("menu.file.exit"));
        addRecordMenuItem.setText(localization.text("menu.database.add_record"));
        editRecordMenuItem.setText(localization.text("menu.database.edit_record"));
        deleteRecordMenuItem.setText(localization.text("menu.database.delete_record"));
        editStructureMenuItem.setText(localization.text("menu.database.edit_structure"));

        for (Localization.LanguageOption option : localization.availableLanguages()) {
            JRadioButtonMenuItem item = languageMenuItems.get(option.code());
            if (item != null) {
                item.setText(option.displayName());
                item.setSelected(option.code().equals(localization.currentLanguageCode()));
            }
        }

        updateStatusBar(null);
        updateWindowTitle();
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

        JFileChooser chooser = new JFileChooser();
        configureDbfFileChooser(chooser);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(localization.text("dialog.open.title"));
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
            return Charset.forName(String.valueOf(selected));
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
        new SwingWorker<List<LoadedDocument>, Void>() {
            @Override
            protected List<LoadedDocument> doInBackground() throws Exception {
                List<LoadedDocument> loaded = new ArrayList<>();
                for (Path path : selectedPaths) {
                    loaded.add(new LoadedDocument(path, charset, DBFEngine.read(path, charset)));
                }
                return loaded;
            }

            @Override
            protected void done() {
                try {
                    List<LoadedDocument> loadedDocuments = get();
                    for (LoadedDocument loadedDocument : loadedDocuments) {
                        openOrReplaceDocument(loadedDocument.path(), loadedDocument.charset(), copyDbf(loadedDocument.dbf()));
                    }
                    if (!loadedDocuments.isEmpty()) {
                        int firstIndex = findDocumentIndex(loadedDocuments.get(0).path());
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
        List<String> editedRow = showRecordEditor(rowIndex, document.dbf.fields(), currentRow, false, document.charset);
        if (editedRow == null) {
            return;
        }

        document.dbf.records().set(rowIndex, editedRow);
        document.tableModel.fireRowUpdated(rowIndex);
        packColumns(document.table);
        document.modified = true;
        updateTabTitle(document);
        updateWindowTitle();
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
        List<String> newRow = showRecordEditor(rowIndex, document.dbf.fields(), emptyRow, true, document.charset);
        if (newRow == null) {
            return;
        }

        document.dbf.records().add(newRow);
        document.tableModel.fireRowInserted(rowIndex);
        packColumns(document.table);
        document.modified = true;
        updateTabTitle(document);
        updateWindowTitle();

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
        packColumns(document.table);
        document.modified = true;
        updateTabTitle(document);
        updateWindowTitle();
    }

    private void editDatabaseStructure() {
        DocumentState document = currentDocument();
        if (document == null || busy) {
            return;
        }

        JDialog dialog = new JDialog(this, localization.text("dialog.structure.title"), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        DefaultTableModel model = new DefaultTableModel(
            new Object[] {
                localization.text("dialog.structure.column.name"),
                localization.text("dialog.structure.column.type"),
                localization.text("dialog.structure.column.length"),
                localization.text("dialog.structure.column.decimals")
            },
            0
        );
        List<Integer> sourceIndexes = new ArrayList<>();
        for (int i = 0; i < document.dbf.fields().size(); i++) {
            DBFEngine.FieldDescriptor field = document.dbf.fields().get(i);
            model.addRow(new Object[] {field.name(), String.valueOf(field.type()), field.length(), field.decimalCount()});
            sourceIndexes.add(i);
        }

        JTable structureTable = new JTable(model);
        JTextField nameEditorField = new JTextField();
        ((AbstractDocument) nameEditorField.getDocument()).setDocumentFilter(new MaxLengthDocumentFilter(11));
        structureTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(nameEditorField));
        JTextField typeEditorField = new JTextField();
        ((AbstractDocument) typeEditorField.getDocument()).setDocumentFilter(new FieldTypeDocumentFilter());
        structureTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeEditorField));
        dialog.add(new JScrollPane(structureTable), BorderLayout.CENTER);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        dialog.add(statusLabel, BorderLayout.NORTH);

        JButton addFieldButton = new JButton(localization.text("dialog.structure.add_field"));
        JButton removeFieldButton = new JButton(localization.text("dialog.structure.remove_field"));
        JButton okButton = new JButton(localization.text("button.ok"));
        JButton cancelButton = new JButton(localization.text("button.cancel"));

        addFieldButton.addActionListener(e -> {
            model.addRow(new Object[] {"NEWFIELD", "C", 20, 0});
            sourceIndexes.add(-1);
            int newRow = model.getRowCount() - 1;
            structureTable.getSelectionModel().setSelectionInterval(newRow, newRow);
        });

        removeFieldButton.addActionListener(e -> {
            int selectedRow = structureTable.getSelectedRow();
            if (selectedRow >= 0) {
                model.removeRow(selectedRow);
                sourceIndexes.remove(selectedRow);
            }
        });

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(addFieldButton);
        leftButtons.add(removeFieldButton);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightButtons.add(okButton);
        rightButtons.add(cancelButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(leftButtons, BorderLayout.WEST);
        bottomPanel.add(rightButtons, BorderLayout.EAST);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        okButton.addActionListener(e -> {
            try {
                if (structureTable.isEditing()) {
                    structureTable.getCellEditor().stopCellEditing();
                }
                StructureEditResult result = collectStructureEdits(model, sourceIndexes);
                applyStructureEdit(document, result);
                dialog.dispose();
            } catch (IllegalArgumentException ex) {
                statusLabel.setText(ex.getMessage());
            }
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.setSize(760, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private StructureEditResult collectStructureEdits(DefaultTableModel model, List<Integer> sourceIndexes) {
        if (model.getRowCount() == 0) {
            throw new IllegalArgumentException(localization.text("dialog.structure.error.empty"));
        }

        List<DBFEngine.FieldDescriptor> fields = new ArrayList<>();
        List<Integer> mappings = new ArrayList<>();
        List<String> usedNames = new ArrayList<>();

        for (int row = 0; row < model.getRowCount(); row++) {
            String name = String.valueOf(model.getValueAt(row, 0)).trim();
            String typeText = String.valueOf(model.getValueAt(row, 1)).trim().toUpperCase();
            String lengthText = String.valueOf(model.getValueAt(row, 2)).trim();
            String decimalsText = String.valueOf(model.getValueAt(row, 3)).trim();

            int length;
            int decimals;
            try {
                length = Integer.parseInt(lengthText);
                decimals = Integer.parseInt(decimalsText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(localization.text("dialog.structure.error.number", row + 1));
            }

            char type = typeText.isEmpty() ? ' ' : typeText.charAt(0);
            DBFEngine.FieldDescriptor field = new DBFEngine.FieldDescriptor(name, type, length, decimals);
            String fieldError = DBFEngine.validateFieldDefinition(field);
            if (fieldError != null) {
                throw new IllegalArgumentException(localization.text("dialog.structure.error.field", row + 1, fieldError));
            }
            if (usedNames.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
                throw new IllegalArgumentException(localization.text("dialog.structure.error.duplicate", name));
            }

            usedNames.add(name);
            fields.add(field);
            mappings.add(sourceIndexes.get(row));
        }

        return new StructureEditResult(fields, mappings);
    }

    private void applyStructureEdit(DocumentState document, StructureEditResult result) {
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
        packColumns(document.table);
        document.modified = true;
        updateTabTitle(document);
        updateWindowTitle();
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

    private List<String> showRecordEditor(int rowIndex, List<DBFEngine.FieldDescriptor> fields, List<String> values, boolean isNewRecord, Charset charset) {
        String titleKey = isNewRecord ? "dialog.editor.new_title" : "dialog.editor.edit_title";
        JDialog dialog = new JDialog(
            this,
            localization.text(titleKey, rowIndex + 1),
            Dialog.ModalityType.APPLICATION_MODAL
        );
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel formPanel = new JPanel(new GridBagLayout());
        List<JTextComponent> editors = new ArrayList<>();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        for (int i = 0; i < fields.size(); i++) {
            DBFEngine.FieldDescriptor field = fields.get(i);
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            gbc.weighty = 0;
            formPanel.add(new JLabel(field.name() + " (" + field.type() + ", " + field.length() + ")"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            if (field.type() == 'M') {
                JTextArea editor = new JTextArea(values.get(i), 5, 40);
                editor.setLineWrap(true);
                editor.setWrapStyleWord(true);
                editors.add(editor);
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weighty = 1.0;
                formPanel.add(new JScrollPane(editor), gbc);
            } else {
                JTextField editor = new JTextField(values.get(i), Math.min(Math.max(field.length(), 12), 40));
                editors.add(editor);
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.weighty = 0;
                formPanel.add(editor, gbc);
            }
        }

        dialog.add(new JScrollPane(formPanel), BorderLayout.CENTER);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        dialog.add(statusLabel, BorderLayout.NORTH);

        JButton okButton = new JButton(localization.text("button.ok"));
        JButton cancelButton = new JButton(localization.text("button.cancel"));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        AtomicReference<List<String>> resultHolder = new AtomicReference<>();

        Runnable validateForm = () -> {
            String error = null;
            for (int i = 0; i < fields.size(); i++) {
                error = DBFEngine.validateValue(fields.get(i), editors.get(i).getText(), charset);
                if (error != null) {
                    error = fields.get(i).name() + ": " + error;
                    break;
                }
            }
            statusLabel.setText(error == null ? " " : error);
            okButton.setEnabled(error == null);
        };

        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateForm.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateForm.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateForm.run();
            }
        };

        for (JTextComponent editor : editors) {
            editor.getDocument().addDocumentListener(listener);
        }

        okButton.addActionListener(e -> {
            List<String> updated = new ArrayList<>(editors.size());
            for (JTextComponent editor : editors) {
                updated.add(editor.getText());
            }
            resultHolder.set(updated);
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        validateForm.run();
        dialog.setSize(650, 450);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return resultHolder.get();
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

        JFileChooser chooser = new JFileChooser();
        configureDbfFileChooser(chooser);
        chooser.setDialogTitle(localization.text("dialog.save_as.title"));
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

    private void configureDbfFileChooser(JFileChooser chooser) {
        Path baseDirectory = Path.of("").toAbsolutePath();
        chooser.setFileSystemView(new DbfOnlyFileSystemView(baseDirectory.toFile()));
        chooser.setCurrentDirectory(baseDirectory.toFile());
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().toLowerCase().endsWith(".dbf");
            }

            @Override
            public String getDescription() {
                return localization.text("file_filter.dbf");
            }
        });
        chooser.setFileHidingEnabled(true);
    }

    private void saveToPath(DocumentState document, Path path) {
        setBusy(true, localization.text("status.saving"));
        DBFEngine.DBFFile snapshot = copyDbf(document.dbf);
        Charset charset = document.charset;

        new SwingWorker<DBFEngine.DBFFile, Void>() {
            @Override
            protected DBFEngine.DBFFile doInBackground() throws Exception {
                DBFEngine.write(path, charset, snapshot);
                return DBFEngine.read(path, charset);
            }

            @Override
            protected void done() {
                try {
                    DBFEngine.DBFFile saved = get();
                    document.dbf = copyDbf(saved);
                    document.path = path;
                    document.modified = false;
                    document.tableModel.setDbf(document.dbf);
                    packColumns(document.table);
                    if (document == currentDocument()) {
                        syncCharsetCombo(document.charset);
                    }
                    updateTabTitle(document);
                    updateWindowTitle();
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
        for (DocumentState document : documents) {
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
        boolean hasFile = currentDocument() != null;
        openMenuItem.setEnabled(!busyState);
        closeMenuItem.setEnabled(!busyState && hasFile);
        charsetCombo.setEnabled(!busyState);
        saveMenuItem.setEnabled(!busyState && hasFile);
        saveAsMenuItem.setEnabled(!busyState && hasFile);
        addRecordMenuItem.setEnabled(!busyState && hasFile);
        editRecordMenuItem.setEnabled(!busyState && hasFile);
        deleteRecordMenuItem.setEnabled(!busyState && hasFile);
        editStructureMenuItem.setEnabled(!busyState && hasFile);

        for (DocumentState document : documents) {
            document.table.setEnabled(!busyState);
        }
        updateStatusBar(busyState ? message : null);
        updateWindowTitle();
    }

    private void updateWindowTitle() {
        StringBuilder title = new StringBuilder(localization.text("app.title"));
        DocumentState document = currentDocument();
        if (document != null && document.path != null) {
            title.append(" - ").append(document.path.getFileName());
        }
        if (busy) {
            title.append(localization.text("app.title.busy_suffix"));
        } else if (document != null && document.modified) {
            title.append(" *");
        }
        setTitle(title.toString());
    }

    private void updateStatusBar(String overrideMessage) {
        if (overrideMessage != null && !overrideMessage.isBlank()) {
            statusBarLabel.setText(overrideMessage);
            return;
        }

        DocumentState document = currentDocument();
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

    private static String loadSavedLanguageCode() {
        return PREFERENCES.get(PREF_LANGUAGE, "hu");
    }

    private static String loadSavedLookAndFeelId() {
        return PREFERENCES.get(PREF_LOOK_AND_FEEL, MetalLookAndFeel.class.getName());
    }

    private DBFEngine.DBFFile copyDbf(DBFEngine.DBFFile source) {
        List<DBFEngine.FieldDescriptor> fields = new ArrayList<>(source.fields());
        List<List<String>> records = new ArrayList<>(source.records().size());
        for (List<String> row : source.records()) {
            records.add(new ArrayList<>(row));
        }
        return new DBFEngine.DBFFile(
            source.version(),
            source.lastUpdate(),
            source.recordCount(),
            source.headerLength(),
            source.recordLength(),
            fields,
            records
        );
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
        LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
        return lookAndFeel != null ? lookAndFeel.getClass().getName() : "";
    }

    private static List<LookAndFeelOption> availableLookAndFeelOptions() {
        List<LookAndFeelOption> options = new ArrayList<>();
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            options.add(new LookAndFeelOption(info.getClassName(), info.getName(), info.getClassName()));
        }
        return options;
    }

    private static void applyLookAndFeelOption(LookAndFeelOption option) throws Exception {
        UIManager.setLookAndFeel(option.className());
    }

    private static LookAndFeelOption findLookAndFeelOption(String selectionId) {
        List<LookAndFeelOption> options = availableLookAndFeelOptions();
        for (LookAndFeelOption option : options) {
            if (option.id().equals(selectionId) || option.className().equals(selectionId)) {
                return option;
            }
        }
        return null;
    }

    private DocumentState currentDocument() {
        int index = tabbedPane.getSelectedIndex();
        if (index < 0 || index >= documents.size()) {
            return null;
        }
        return documents.get(index);
    }

    private void openOrReplaceDocument(Path path, Charset charset, DBFEngine.DBFFile dbf) {
        int existingIndex = findDocumentIndex(path);
        if (existingIndex >= 0) {
            DocumentState document = documents.get(existingIndex);
            document.charset = charset;
            document.dbf = dbf;
            document.modified = false;
            document.tableModel.setDbf(document.dbf);
            packColumns(document.table);
            tabbedPane.setSelectedIndex(existingIndex);
            updateViewFromCurrentDocument();
            updateTabTitle(document);
            updateStatusBar(null);
            return;
        }

        DocumentState document = createDocumentState(path, charset, dbf);
        documents.add(document);
        tabbedPane.addTab(buildTabTitle(document), document.panel);
        tabbedPane.setTabComponentAt(documents.size() - 1, document.tabHeader);
        tabbedPane.setSelectedIndex(documents.size() - 1);
        updateViewFromCurrentDocument();
    }

    private int findDocumentIndex(Path path) {
        for (int i = 0; i < documents.size(); i++) {
            Path documentPath = documents.get(i).path;
            if (documentPath != null && documentPath.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private void updateViewFromCurrentDocument() {
        DocumentState document = currentDocument();
        if (document == null) {
            syncCharsetCombo(DBFEngine.DEFAULT_CHARSET);
        } else {
            syncCharsetCombo(document.charset);
        }
        updateStatusBar(null);
        setBusy(busy, null);
    }

    private void syncCharsetCombo(Charset charset) {
        updatingCharsetCombo = true;
        charsetCombo.setSelectedItem(charset.name());
        updatingCharsetCombo = false;
    }

    private String buildTabTitle(DocumentState document) {
        return buildTabTitle(document.path, document.modified);
    }

    private void updateTabTitle(DocumentState document) {
        int index = documents.indexOf(document);
        if (index >= 0) {
            tabbedPane.setTitleAt(index, buildTabTitle(document));
            document.tabHeader.setTitle(buildTabTitle(document));
        }
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

        TabHeader tabHeader = new TabHeader(this, buildTabTitle(path, false));
        DocumentState document = new DocumentState(path, charset, dbf, false, panel, documentTable, model, tabHeader);
        packColumns(document.table);
        return document;
    }

    private void closeDocumentAt(int index) {
        if (busy || index < 0 || index >= documents.size()) {
            return;
        }

        DocumentState document = documents.get(index);
        if (!confirmDiscardChanges(document)) {
            return;
        }

        documents.remove(index);
        tabbedPane.removeTabAt(index);
        updateViewFromCurrentDocument();
        setBusy(false, null);
    }

    private String buildTabTitle(Path path, boolean modified) {
        String baseName = path != null ? path.getFileName().toString() : localization.text("app.title");
        return modified ? baseName + " *" : baseName;
    }

    private void packColumns(JTable table) {
        int margin = 16;
        for (int columnIndex = 0; columnIndex < table.getColumnCount(); columnIndex++) {
            TableColumn column = table.getColumnModel().getColumn(columnIndex);
            int preferredWidth = preferredHeaderWidth(table, columnIndex);
            for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
                TableCellRenderer renderer = table.getCellRenderer(rowIndex, columnIndex);
                var component = table.prepareRenderer(renderer, rowIndex, columnIndex);
                preferredWidth = Math.max(preferredWidth, component.getPreferredSize().width);
            }
            column.setPreferredWidth(preferredWidth + margin);
        }
    }

    private int preferredHeaderWidth(JTable table, int columnIndex) {
        TableColumn column = table.getColumnModel().getColumn(columnIndex);
        TableCellRenderer renderer = column.getHeaderRenderer();
        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        var component = renderer.getTableCellRendererComponent(
            table,
            column.getHeaderValue(),
            false,
            false,
            -1,
            columnIndex
        );
        return component.getPreferredSize().width;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                applyStartupLookAndFeel();

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
        LookAndFeelOption preferred = findLookAndFeelOption(preferredId);
        if (preferred == null) {
            return;
        }
        String currentClassName = currentLookAndFeelClassName();
        boolean alreadyApplied = preferred.className().equals(currentClassName);
        if (!alreadyApplied) {
            switchLookAndFeel(preferred);
        }
    }

    private static void applyStartupLookAndFeel() {
        List<LookAndFeelOption> candidates = List.of(
            new LookAndFeelOption(UIManager.getSystemLookAndFeelClassName(), "System", UIManager.getSystemLookAndFeelClassName()),
            new LookAndFeelOption(MetalLookAndFeel.class.getName(), "Metal", MetalLookAndFeel.class.getName())
        );

        for (LookAndFeelOption option : candidates) {
            if (option.className() == null || option.className().isBlank()) {
                continue;
            }
            try {
                applyLookAndFeelOption(option);
                return;
            } catch (Exception e) {
                // Try the next available look and feel.
            }
        }
    }

    private static final class DBFTableModel extends AbstractTableModel {
        private DBFEngine.DBFFile dbf;

        void setDbf(DBFEngine.DBFFile dbf) {
            this.dbf = dbf;
            fireTableStructureChanged();
        }

        void fireRowUpdated(int rowIndex) {
            fireTableRowsUpdated(rowIndex, rowIndex);
        }

        void fireRowInserted(int rowIndex) {
            fireTableRowsInserted(rowIndex, rowIndex);
        }

        @Override
        public int getRowCount() {
            return dbf == null ? 0 : dbf.records().size();
        }

        @Override
        public int getColumnCount() {
            return dbf == null ? 0 : dbf.fields().size();
        }

        @Override
        public String getColumnName(int column) {
            return dbf == null ? "" : dbf.fields().get(column).name();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (dbf == null) {
                return "";
            }
            return dbf.records().get(rowIndex).get(columnIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private record StructureEditResult(List<DBFEngine.FieldDescriptor> fields, List<Integer> sourceIndexes) {
    }

    private static final class MaxLengthDocumentFilter extends DocumentFilter {
        private final int maxLength;

        MaxLengthDocumentFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String incoming = text == null ? "" : text;
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            StringBuilder builder = new StringBuilder(current);
            builder.replace(offset, offset + length, incoming);
            String limited = builder.length() > maxLength ? builder.substring(0, maxLength) : builder.toString();
            fb.replace(0, fb.getDocument().getLength(), limited, attrs);
        }
    }

    private static final class FieldTypeDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String incoming = text == null ? "" : text.toUpperCase();
            StringBuilder builder = new StringBuilder(fb.getDocument().getText(0, fb.getDocument().getLength()));
            builder.replace(offset, offset + length, incoming);

            String filtered = filterValidType(builder.toString());
            fb.replace(0, fb.getDocument().getLength(), filtered, attrs);
        }

        private String filterValidType(String value) {
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (VALID_FIELD_TYPES.indexOf(ch) >= 0) {
                    return String.valueOf(ch);
                }
            }
            return "";
        }
    }

    private static final class DbfOnlyFileSystemView extends FileSystemView {
        private final FileSystemView delegate = FileSystemView.getFileSystemView();
        private final File rootDirectory;

        DbfOnlyFileSystemView(File rootDirectory) {
            this.rootDirectory = rootDirectory;
        }

        @Override
        public File createNewFolder(File containingDir) throws IOException {
            return delegate.createNewFolder(containingDir);
        }

        @Override
        public File getDefaultDirectory() {
            return rootDirectory;
        }

        @Override
        public File getHomeDirectory() {
            return rootDirectory;
        }

        @Override
        public File[] getFiles(File dir, boolean useFileHiding) {
            File[] files = delegate.getFiles(dir, useFileHiding);
            List<File> visibleFiles = new ArrayList<>(files.length);
            for (File file : files) {
                if (file.isDirectory() || file.getName().toLowerCase().endsWith(".dbf")) {
                    visibleFiles.add(file);
                }
            }
            return visibleFiles.toArray(File[]::new);
        }

        @Override
        public Boolean isTraversable(File file) {
            return file.isDirectory();
        }
    }

    private static final class TabHeader extends JPanel {
        private final JLabel titleLabel;
        private final JButton closeButton;

        TabHeader(DBFEditorUI owner, String title) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setOpaque(false);

            titleLabel = new JLabel(title + " ");
            closeButton = new JButton("x");
            closeButton.setFocusable(false);
            closeButton.setOpaque(false);
            closeButton.setContentAreaFilled(false);
            closeButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            closeButton.setPreferredSize(new Dimension(18, 18));
            closeButton.addActionListener(e -> {
                int index = owner.tabbedPane.indexOfTabComponent(TabHeader.this);
                owner.closeDocumentAt(index);
            });

            add(titleLabel);
            add(closeButton);
        }

        void setTitle(String title) {
            titleLabel.setText(title + " ");
        }
    }

}
