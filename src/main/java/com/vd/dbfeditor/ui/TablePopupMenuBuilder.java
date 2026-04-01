package com.vd.dbfeditor.ui;

import com.vd.dbfeditor.i18n.Localization;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public final class TablePopupMenuBuilder {
    private TablePopupMenuBuilder() {
    }

    public static JPopupMenu build(
        Localization localization,
        boolean restoreEnabled,
        Runnable cutHandler,
        Runnable copyHandler,
        Runnable pasteHandler,
        Runnable editRecordHandler,
        Runnable deleteRecordHandler,
        Runnable restoreRecordHandler,
        Runnable searchHandler,
        Runnable searchNextHandler,
        Runnable searchPreviousHandler,
        Runnable replaceHandler
    ) {
        JPopupMenu popupMenu = new JPopupMenu();

        popupMenu.add(menuItem(localization.text("menu.edit.cut"), cutHandler));
        popupMenu.add(menuItem(localization.text("menu.edit.copy"), copyHandler));
        popupMenu.add(menuItem(localization.text("menu.edit.paste"), pasteHandler));
        popupMenu.addSeparator();

        popupMenu.add(menuItem(localization.text("menu.database.edit_record"), editRecordHandler));
        popupMenu.add(menuItem(localization.text("menu.database.delete_record"), deleteRecordHandler));

        JMenuItem restoreItem = menuItem(localization.text("menu.database.restore_record"), restoreRecordHandler);
        restoreItem.setEnabled(restoreEnabled);
        popupMenu.add(restoreItem);
        popupMenu.addSeparator();

        popupMenu.add(menuItem(localization.text("menu.edit.search"), searchHandler));
        popupMenu.add(menuItem(localization.text("menu.edit.search_next"), searchNextHandler));
        popupMenu.add(menuItem(localization.text("menu.edit.search_previous"), searchPreviousHandler));
        popupMenu.add(menuItem(localization.text("menu.edit.replace"), replaceHandler));

        return popupMenu;
    }

    private static JMenuItem menuItem(String text, Runnable handler) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> handler.run());
        return item;
    }
}
