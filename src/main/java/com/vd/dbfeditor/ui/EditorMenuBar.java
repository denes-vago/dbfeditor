package com.vd.dbfeditor.ui;

import com.vd.dbfeditor.action.ExportMenuAction;
import com.vd.dbfeditor.action.LocalizedMenuAction;
import com.vd.dbfeditor.export.ExportFormat;
import com.vd.dbfeditor.i18n.Localization;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

public final class EditorMenuBar {
    private final JMenuBar menuBar;
    private final JMenu fileMenu;
    private final JMenu editMenu;
    private final JMenu databaseMenu;
    private final JMenu charsetMenu;
    private final JMenu settingsMenu;
    private final JMenu helpMenu;
    private final JMenu languageMenu;
    private final JMenu lookAndFeelMenu;
    private final JMenu exportMenu;

    private final LocalizedMenuAction newDatabaseAction;
    private final LocalizedMenuAction openAction;
    private final LocalizedMenuAction closeAction;
    private final LocalizedMenuAction closeOthersAction;
    private final LocalizedMenuAction closeAllAction;
    private final LocalizedMenuAction cutAction;
    private final LocalizedMenuAction copyAction;
    private final LocalizedMenuAction pasteAction;
    private final LocalizedMenuAction undoAction;
    private final LocalizedMenuAction redoAction;
    private final LocalizedMenuAction filterAction;
    private final LocalizedMenuAction clearFilterAction;
    private final LocalizedMenuAction saveAction;
    private final LocalizedMenuAction saveAsAction;
    private final LocalizedMenuAction exitAction;
    private final ExportMenuAction exportCsvAction;
    private final ExportMenuAction exportXlsxAction;
    private final ExportMenuAction exportSqlAction;
    private final LocalizedMenuAction addRecordAction;
    private final LocalizedMenuAction editRecordAction;
    private final LocalizedMenuAction deleteRecordAction;
    private final LocalizedMenuAction restoreRecordAction;
    private final LocalizedMenuAction purgeDeletedRecordsAction;
    private final JCheckBoxMenuItem showDeletedRecordsMenuItem;
    private final JCheckBoxMenuItem showOnlyDeletedRecordsMenuItem;
    private final LocalizedMenuAction searchAction;
    private final LocalizedMenuAction searchNextAction;
    private final LocalizedMenuAction searchPreviousAction;
    private final LocalizedMenuAction replaceAction;
    private final LocalizedMenuAction editStructureAction;
    private final LocalizedMenuAction aboutAction;

    private final Map<String, JRadioButtonMenuItem> languageMenuItems = new LinkedHashMap<>();
    private final Map<String, JRadioButtonMenuItem> lookAndFeelMenuItems = new LinkedHashMap<>();
    private final Map<String, JRadioButtonMenuItem> charsetMenuItems = new LinkedHashMap<>();

    public EditorMenuBar(
        Runnable newDatabaseHandler,
        Runnable openHandler,
        Runnable closeHandler,
        Runnable closeOthersHandler,
        Runnable closeAllHandler,
        Runnable cutHandler,
        Runnable copyHandler,
        Runnable pasteHandler,
        Runnable undoHandler,
        Runnable redoHandler,
        Runnable filterHandler,
        Runnable clearFilterHandler,
        Runnable saveHandler,
        Runnable saveAsHandler,
        Runnable exitHandler,
        Consumer<ExportFormat> exportHandler,
        Runnable addRecordHandler,
        Runnable editRecordHandler,
        Runnable deleteRecordHandler,
        Runnable restoreRecordHandler,
        Runnable purgeDeletedRecordsHandler,
        Consumer<Boolean> showDeletedHandler,
        Consumer<Boolean> showOnlyDeletedHandler,
        Runnable searchHandler,
        Runnable searchNextHandler,
        Runnable searchPreviousHandler,
        Runnable replaceHandler,
        Runnable editStructureHandler,
        Runnable aboutHandler
    ) {
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        menuBar = new JMenuBar();
        fileMenu = new JMenu();
        editMenu = new JMenu();
        databaseMenu = new JMenu();
        charsetMenu = new JMenu();
        settingsMenu = new JMenu();
        helpMenu = new JMenu();
        languageMenu = new JMenu();
        lookAndFeelMenu = new JMenu();
        exportMenu = new JMenu();
        exportMenu.setEnabled(false);

        newDatabaseAction = new LocalizedMenuAction("menu.file.new", newDatabaseHandler);
        JMenuItem newDatabaseMenuItem = new JMenuItem(newDatabaseAction);
        newDatabaseMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask));

        openAction = new LocalizedMenuAction("menu.file.open", openHandler);
        JMenuItem openMenuItem = new JMenuItem(openAction);
        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcutMask));

        closeAction = new LocalizedMenuAction("menu.file.close", closeHandler);
        JMenuItem closeMenuItem = new JMenuItem(closeAction);
        closeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask));
        closeAction.setEnabled(false);

        closeOthersAction = new LocalizedMenuAction("popup.tab.close_others", closeOthersHandler);
        JMenuItem closeOthersMenuItem = new JMenuItem(closeOthersAction);
        closeOthersAction.setEnabled(false);

        closeAllAction = new LocalizedMenuAction("popup.tab.close_all", closeAllHandler);
        JMenuItem closeAllMenuItem = new JMenuItem(closeAllAction);
        closeAllAction.setEnabled(false);

        undoAction = new LocalizedMenuAction("menu.edit.undo", undoHandler);
        JMenuItem undoMenuItem = new JMenuItem(undoAction);
        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask));
        undoAction.setEnabled(false);

        redoAction = new LocalizedMenuAction("menu.edit.redo", redoHandler);
        JMenuItem redoMenuItem = new JMenuItem(redoAction);
        redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        redoAction.setEnabled(false);

        filterAction = new LocalizedMenuAction("menu.edit.filter", filterHandler);
        JMenuItem filterMenuItem = new JMenuItem(filterAction);
        filterMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, shortcutMask));

        clearFilterAction = new LocalizedMenuAction("menu.edit.clear_filter", clearFilterHandler);
        JMenuItem clearFilterMenuItem = new JMenuItem(clearFilterAction);
        clearFilterMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, shortcutMask | InputEvent.SHIFT_DOWN_MASK));

        cutAction = new LocalizedMenuAction("menu.edit.cut", cutHandler);
        JMenuItem cutMenuItem = new JMenuItem(cutAction);
        cutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutMask));
        cutAction.setEnabled(false);

        copyAction = new LocalizedMenuAction("menu.edit.copy", copyHandler);
        JMenuItem copyMenuItem = new JMenuItem(copyAction);
        copyMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutMask));
        copyAction.setEnabled(false);

        pasteAction = new LocalizedMenuAction("menu.edit.paste", pasteHandler);
        JMenuItem pasteMenuItem = new JMenuItem(pasteAction);
        pasteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutMask));
        pasteAction.setEnabled(false);

        saveAction = new LocalizedMenuAction("menu.file.save", saveHandler);
        JMenuItem saveMenuItem = new JMenuItem(saveAction);
        saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask));
        saveAction.setEnabled(false);

        saveAsAction = new LocalizedMenuAction("menu.file.save_as", saveAsHandler);
        JMenuItem saveAsMenuItem = new JMenuItem(saveAsAction);
        saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        saveAsAction.setEnabled(false);

        exitAction = new LocalizedMenuAction("menu.file.exit", exitHandler);
        JMenuItem exitMenuItem = new JMenuItem(exitAction);
        exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcutMask));

        exportCsvAction = new ExportMenuAction("menu.file.export.csv", ExportFormat.CSV, exportHandler);
        JMenuItem exportCsvMenuItem = new JMenuItem(exportCsvAction);
        exportCsvAction.setEnabled(false);

        exportXlsxAction = new ExportMenuAction("menu.file.export.xlsx", ExportFormat.XLSX, exportHandler);
        JMenuItem exportXlsxMenuItem = new JMenuItem(exportXlsxAction);
        exportXlsxAction.setEnabled(false);

        exportSqlAction = new ExportMenuAction("menu.file.export.sql", ExportFormat.SQL, exportHandler);
        JMenuItem exportSqlMenuItem = new JMenuItem(exportSqlAction);
        exportSqlAction.setEnabled(false);

        addRecordAction = new LocalizedMenuAction("menu.database.add_record", addRecordHandler);
        JMenuItem addRecordMenuItem = new JMenuItem(addRecordAction);
        addRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        addRecordAction.setEnabled(false);

        editRecordAction = new LocalizedMenuAction("menu.database.edit_record", editRecordHandler);
        JMenuItem editRecordMenuItem = new JMenuItem(editRecordAction);
        editRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutMask));
        editRecordAction.setEnabled(false);

        deleteRecordAction = new LocalizedMenuAction("menu.database.delete_record", deleteRecordHandler);
        JMenuItem deleteRecordMenuItem = new JMenuItem(deleteRecordAction);
        deleteRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteRecordAction.setEnabled(false);

        restoreRecordAction = new LocalizedMenuAction("menu.database.restore_record", restoreRecordHandler);
        JMenuItem restoreRecordMenuItem = new JMenuItem(restoreRecordAction);
        restoreRecordAction.setEnabled(false);

        purgeDeletedRecordsAction = new LocalizedMenuAction("menu.database.purge_deleted", purgeDeletedRecordsHandler);
        JMenuItem purgeDeletedRecordsMenuItem = new JMenuItem(purgeDeletedRecordsAction);
        purgeDeletedRecordsAction.setEnabled(false);

        showDeletedRecordsMenuItem = new JCheckBoxMenuItem();
        showDeletedRecordsMenuItem.addActionListener(e -> showDeletedHandler.accept(showDeletedRecordsMenuItem.isSelected()));
        showDeletedRecordsMenuItem.setEnabled(false);

        showOnlyDeletedRecordsMenuItem = new JCheckBoxMenuItem();
        showOnlyDeletedRecordsMenuItem.addActionListener(e -> showOnlyDeletedHandler.accept(showOnlyDeletedRecordsMenuItem.isSelected()));
        showOnlyDeletedRecordsMenuItem.setEnabled(false);

        searchAction = new LocalizedMenuAction("menu.edit.search", searchHandler);
        JMenuItem searchMenuItem = new JMenuItem(searchAction);
        searchMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask));
        searchAction.setEnabled(false);

        searchNextAction = new LocalizedMenuAction("menu.edit.search_next", searchNextHandler);
        JMenuItem searchNextMenuItem = new JMenuItem(searchNextAction);
        searchNextMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, shortcutMask));
        searchNextAction.setEnabled(false);

        searchPreviousAction = new LocalizedMenuAction("menu.edit.search_previous", searchPreviousHandler);
        JMenuItem searchPreviousMenuItem = new JMenuItem(searchPreviousAction);
        searchPreviousMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        searchPreviousAction.setEnabled(false);

        replaceAction = new LocalizedMenuAction("menu.edit.replace", replaceHandler);
        JMenuItem replaceMenuItem = new JMenuItem(replaceAction);
        replaceMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcutMask));
        replaceAction.setEnabled(false);

        editStructureAction = new LocalizedMenuAction("menu.database.edit_structure", editStructureHandler);
        JMenuItem editStructureMenuItem = new JMenuItem(editStructureAction);
        editStructureMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        editStructureAction.setEnabled(false);

        aboutAction = new LocalizedMenuAction("menu.help.about", aboutHandler);
        JMenuItem aboutMenuItem = new JMenuItem(aboutAction);

        fileMenu.add(newDatabaseMenuItem);
        fileMenu.add(openMenuItem);
        fileMenu.add(closeMenuItem);
        fileMenu.add(closeOthersMenuItem);
        fileMenu.add(closeAllMenuItem);
        fileMenu.add(saveMenuItem);
        fileMenu.add(saveAsMenuItem);
        fileMenu.add(exportMenu);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);

        editMenu.add(undoMenuItem);
        editMenu.add(redoMenuItem);
        editMenu.addSeparator();
        editMenu.add(cutMenuItem);
        editMenu.add(copyMenuItem);
        editMenu.add(pasteMenuItem);
        editMenu.addSeparator();
        editMenu.add(filterMenuItem);
        editMenu.add(clearFilterMenuItem);
        editMenu.addSeparator();
        editMenu.add(searchMenuItem);
        editMenu.add(searchNextMenuItem);
        editMenu.add(searchPreviousMenuItem);
        editMenu.add(replaceMenuItem);
        menuBar.add(editMenu);

        exportMenu.add(exportCsvMenuItem);
        exportMenu.add(exportXlsxMenuItem);
        exportMenu.add(exportSqlMenuItem);

        databaseMenu.add(addRecordMenuItem);
        databaseMenu.add(editRecordMenuItem);
        databaseMenu.add(deleteRecordMenuItem);
        databaseMenu.add(restoreRecordMenuItem);
        databaseMenu.add(purgeDeletedRecordsMenuItem);
        databaseMenu.add(showDeletedRecordsMenuItem);
        databaseMenu.add(showOnlyDeletedRecordsMenuItem);
        databaseMenu.addSeparator();
        databaseMenu.add(charsetMenu);
        databaseMenu.addSeparator();
        databaseMenu.add(editStructureMenuItem);
        menuBar.add(databaseMenu);

        settingsMenu.add(languageMenu);
        settingsMenu.add(lookAndFeelMenu);
        menuBar.add(settingsMenu);

        helpMenu.add(aboutMenuItem);
        menuBar.add(helpMenu);
    }

    public JMenuBar menuBar() {
        return menuBar;
    }

    public Action openAction() {
        return openAction;
    }

    public List<Action> fileBoundActions() {
        return List.of(
            closeAction,
            closeOthersAction,
            closeAllAction,
            undoAction,
            redoAction,
            cutAction,
            copyAction,
            pasteAction,
            saveAction,
            saveAsAction,
            exportCsvAction,
            exportXlsxAction,
            exportSqlAction,
            addRecordAction,
            editRecordAction,
            deleteRecordAction,
            restoreRecordAction,
            purgeDeletedRecordsAction,
            searchAction,
            searchNextAction,
            searchPreviousAction,
            replaceAction,
            filterAction,
            clearFilterAction,
            editStructureAction
        );
    }

    public void applyLocalization(Localization localization) {
        fileMenu.setText(localization.text("menu.file"));
        editMenu.setText(localization.text("menu.edit"));
        databaseMenu.setText(localization.text("menu.database"));
        charsetMenu.setText(localization.text("menu.database.charset"));
        settingsMenu.setText(localization.text("menu.settings"));
        helpMenu.setText(localization.text("menu.help"));
        languageMenu.setText(localization.text("menu.settings.language"));
        lookAndFeelMenu.setText(localization.text("menu.settings.look_and_feel"));
        exportMenu.setText(localization.text("menu.file.export"));
        newDatabaseAction.updateText(localization::text);
        openAction.updateText(localization::text);
        closeAction.updateText(localization::text);
        closeOthersAction.updateText(localization::text);
        closeAllAction.updateText(localization::text);
        undoAction.updateText(localization::text);
        redoAction.updateText(localization::text);
        filterAction.updateText(localization::text);
        clearFilterAction.updateText(localization::text);
        cutAction.updateText(localization::text);
        copyAction.updateText(localization::text);
        pasteAction.updateText(localization::text);
        saveAction.updateText(localization::text);
        saveAsAction.updateText(localization::text);
        exitAction.updateText(localization::text);
        exportCsvAction.updateText(localization::text);
        exportXlsxAction.updateText(localization::text);
        exportSqlAction.updateText(localization::text);
        addRecordAction.updateText(localization::text);
        editRecordAction.updateText(localization::text);
        deleteRecordAction.updateText(localization::text);
        restoreRecordAction.updateText(localization::text);
        purgeDeletedRecordsAction.updateText(localization::text);
        showDeletedRecordsMenuItem.setText(localization.text("menu.database.show_deleted"));
        showOnlyDeletedRecordsMenuItem.setText(localization.text("menu.database.show_only_deleted"));
        searchAction.updateText(localization::text);
        searchNextAction.updateText(localization::text);
        searchPreviousAction.updateText(localization::text);
        replaceAction.updateText(localization::text);
        editStructureAction.updateText(localization::text);
        aboutAction.updateText(localization::text);
    }

    public void rebuildCharsetMenu(String[] supportedDisplayNames, String currentSelection, Consumer<String> switchHandler) {
        charsetMenu.removeAll();
        charsetMenuItems.clear();

        ButtonGroup buttonGroup = new ButtonGroup();
        for (String displayName : supportedDisplayNames) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(displayName);
            item.addActionListener(e -> switchHandler.accept(displayName));
            item.setSelected(displayName.equals(currentSelection));
            buttonGroup.add(item);
            charsetMenu.add(item);
            charsetMenuItems.put(displayName, item);
        }
    }

    public void syncCharsetMenu(String currentSelection) {
        for (Map.Entry<String, JRadioButtonMenuItem> entry : charsetMenuItems.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(currentSelection));
        }
    }

    public void setCharsetMenuEnabled(boolean enabled) {
        charsetMenu.setEnabled(enabled);
        for (AbstractButton item : charsetMenuItems.values()) {
            item.setEnabled(enabled);
        }
    }

    public void setExportMenuEnabled(boolean enabled) {
        exportMenu.setEnabled(enabled);
    }

    public void syncShowDeletedMenu(boolean selected) {
        showDeletedRecordsMenuItem.setSelected(selected);
    }

    public void syncShowOnlyDeletedMenu(boolean selected) {
        showOnlyDeletedRecordsMenuItem.setSelected(selected);
    }

    public void setShowDeletedMenuEnabled(boolean enabled) {
        showDeletedRecordsMenuItem.setEnabled(enabled);
        showOnlyDeletedRecordsMenuItem.setEnabled(enabled);
    }

    public void rebuildLanguageMenu(Localization localization, Consumer<String> switchHandler) {
        languageMenu.removeAll();
        languageMenuItems.clear();

        ButtonGroup buttonGroup = new ButtonGroup();
        for (Localization.LanguageOption option : localization.availableLanguages()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.displayName());
            item.addActionListener(e -> switchHandler.accept(option.code()));
            item.setSelected(option.code().equals(localization.currentLanguageCode()));
            buttonGroup.add(item);
            languageMenu.add(item);
            languageMenuItems.put(option.code(), item);
        }
    }

    public void syncLanguageMenu(Localization localization) {
        for (Localization.LanguageOption option : localization.availableLanguages()) {
            JRadioButtonMenuItem item = languageMenuItems.get(option.code());
            if (item != null) {
                item.setText(option.displayName());
                item.setSelected(option.code().equals(localization.currentLanguageCode()));
            }
        }
    }

    public void rebuildLookAndFeelMenu(
        List<LookAndFeelOption> options,
        String currentSelection,
        Consumer<LookAndFeelOption> switchHandler
    ) {
        lookAndFeelMenu.removeAll();
        lookAndFeelMenuItems.clear();

        ButtonGroup buttonGroup = new ButtonGroup();
        for (LookAndFeelOption option : options) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(option.displayName());
            item.addActionListener(e -> switchHandler.accept(option));
            item.setSelected(option.id().equals(currentSelection));
            buttonGroup.add(item);
            lookAndFeelMenu.add(item);
            lookAndFeelMenuItems.put(option.id(), item);
        }
    }
}
