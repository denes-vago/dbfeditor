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
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

public final class EditorMenuBar {
    private final JMenuBar menuBar;
    private final JMenu fileMenu;
    private final JMenu databaseMenu;
    private final JMenu settingsMenu;
    private final JMenu helpMenu;
    private final JMenu languageMenu;
    private final JMenu lookAndFeelMenu;
    private final JMenu exportMenu;

    private final LocalizedMenuAction openAction;
    private final LocalizedMenuAction closeAction;
    private final LocalizedMenuAction saveAction;
    private final LocalizedMenuAction saveAsAction;
    private final LocalizedMenuAction exitAction;
    private final ExportMenuAction exportCsvAction;
    private final ExportMenuAction exportSqlAction;
    private final LocalizedMenuAction addRecordAction;
    private final LocalizedMenuAction editRecordAction;
    private final LocalizedMenuAction deleteRecordAction;
    private final LocalizedMenuAction editStructureAction;
    private final LocalizedMenuAction aboutAction;

    private final Map<String, JRadioButtonMenuItem> languageMenuItems = new LinkedHashMap<>();
    private final Map<String, JRadioButtonMenuItem> lookAndFeelMenuItems = new LinkedHashMap<>();

    public EditorMenuBar(
        Runnable openHandler,
        Runnable closeHandler,
        Runnable saveHandler,
        Runnable saveAsHandler,
        Runnable exitHandler,
        Consumer<ExportFormat> exportHandler,
        Runnable addRecordHandler,
        Runnable editRecordHandler,
        Runnable deleteRecordHandler,
        Runnable editStructureHandler,
        Runnable aboutHandler
    ) {
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        menuBar = new JMenuBar();
        fileMenu = new JMenu();
        databaseMenu = new JMenu();
        settingsMenu = new JMenu();
        helpMenu = new JMenu();
        languageMenu = new JMenu();
        lookAndFeelMenu = new JMenu();
        exportMenu = new JMenu();

        openAction = new LocalizedMenuAction("menu.file.open", openHandler);
        JMenuItem openMenuItem = new JMenuItem(openAction);
        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcutMask));

        closeAction = new LocalizedMenuAction("menu.file.close", closeHandler);
        JMenuItem closeMenuItem = new JMenuItem(closeAction);
        closeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutMask));
        closeAction.setEnabled(false);

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

        exportSqlAction = new ExportMenuAction("menu.file.export.sql", ExportFormat.SQL, exportHandler);
        JMenuItem exportSqlMenuItem = new JMenuItem(exportSqlAction);
        exportSqlAction.setEnabled(false);

        addRecordAction = new LocalizedMenuAction("menu.database.add_record", addRecordHandler);
        JMenuItem addRecordMenuItem = new JMenuItem(addRecordAction);
        addRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutMask));
        addRecordAction.setEnabled(false);

        editRecordAction = new LocalizedMenuAction("menu.database.edit_record", editRecordHandler);
        JMenuItem editRecordMenuItem = new JMenuItem(editRecordAction);
        editRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutMask));
        editRecordAction.setEnabled(false);

        deleteRecordAction = new LocalizedMenuAction("menu.database.delete_record", deleteRecordHandler);
        JMenuItem deleteRecordMenuItem = new JMenuItem(deleteRecordAction);
        deleteRecordMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteRecordAction.setEnabled(false);

        editStructureAction = new LocalizedMenuAction("menu.database.edit_structure", editStructureHandler);
        JMenuItem editStructureMenuItem = new JMenuItem(editStructureAction);
        editStructureMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutMask | InputEvent.SHIFT_DOWN_MASK));
        editStructureAction.setEnabled(false);

        aboutAction = new LocalizedMenuAction("menu.help.about", aboutHandler);
        JMenuItem aboutMenuItem = new JMenuItem(aboutAction);

        fileMenu.add(openMenuItem);
        fileMenu.add(closeMenuItem);
        fileMenu.add(saveMenuItem);
        fileMenu.add(saveAsMenuItem);
        fileMenu.add(exportMenu);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);

        exportMenu.add(exportCsvMenuItem);
        exportMenu.add(exportSqlMenuItem);

        databaseMenu.add(addRecordMenuItem);
        databaseMenu.add(editRecordMenuItem);
        databaseMenu.add(deleteRecordMenuItem);
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
            saveAction,
            saveAsAction,
            exportCsvAction,
            exportSqlAction,
            addRecordAction,
            editRecordAction,
            deleteRecordAction,
            editStructureAction
        );
    }

    public void applyLocalization(Localization localization) {
        fileMenu.setText(localization.text("menu.file"));
        databaseMenu.setText(localization.text("menu.database"));
        settingsMenu.setText(localization.text("menu.settings"));
        helpMenu.setText(localization.text("menu.help"));
        languageMenu.setText(localization.text("menu.settings.language"));
        lookAndFeelMenu.setText(localization.text("menu.settings.look_and_feel"));
        exportMenu.setText(localization.text("menu.file.export"));
        openAction.updateText(localization::text);
        closeAction.updateText(localization::text);
        saveAction.updateText(localization::text);
        saveAsAction.updateText(localization::text);
        exitAction.updateText(localization::text);
        exportCsvAction.updateText(localization::text);
        exportSqlAction.updateText(localization::text);
        addRecordAction.updateText(localization::text);
        editRecordAction.updateText(localization::text);
        deleteRecordAction.updateText(localization::text);
        editStructureAction.updateText(localization::text);
        aboutAction.updateText(localization::text);
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
