package com.vd.dbfeditor.ui;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;

public final class TextEditSupport {
    private static final String UNDO_MANAGER_PROPERTY = "dbfeditor.undoManager";

    private TextEditSupport() {
    }

    public static void installUndoSupport(JTextComponent textComponent) {
        if (textComponent.getClientProperty(UNDO_MANAGER_PROPERTY) instanceof UndoManager) {
            return;
        }
        UndoManager undoManager = new UndoManager();
        textComponent.getDocument().addUndoableEditListener(new UndoableEditListener() {
            @Override
            public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
            }
        });
        textComponent.putClientProperty(UNDO_MANAGER_PROPERTY, undoManager);
    }

    public static void cutFocusedText() {
        JTextComponent textComponent = focusedTextComponent();
        if (textComponent != null) {
            textComponent.cut();
        }
    }

    public static void copyFocusedText() {
        JTextComponent textComponent = focusedTextComponent();
        if (textComponent != null) {
            textComponent.copy();
        }
    }

    public static void pasteFocusedText() {
        JTextComponent textComponent = focusedTextComponent();
        if (textComponent != null) {
            textComponent.paste();
        }
    }

    public static void undoFocusedText() {
        UndoManager undoManager = focusedUndoManager();
        if (undoManager != null && undoManager.canUndo()) {
            try {
                undoManager.undo();
            } catch (CannotUndoException e) {
                // Ignore and keep UI responsive.
            }
        }
    }

    public static void redoFocusedText() {
        UndoManager undoManager = focusedUndoManager();
        if (undoManager != null && undoManager.canRedo()) {
            try {
                undoManager.redo();
            } catch (CannotRedoException e) {
                // Ignore and keep UI responsive.
            }
        }
    }

    private static UndoManager focusedUndoManager() {
        JTextComponent textComponent = focusedTextComponent();
        if (textComponent == null) {
            return null;
        }
        Object clientProperty = textComponent.getClientProperty(UNDO_MANAGER_PROPERTY);
        return clientProperty instanceof UndoManager undoManager ? undoManager : null;
    }

    private static JTextComponent focusedTextComponent() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner instanceof JTextComponent textComponent ? textComponent : null;
    }
}
