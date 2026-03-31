package com.vd.dbfeditor.action;

import java.awt.event.ActionEvent;
import java.util.function.Function;
import javax.swing.AbstractAction;

public class LocalizedMenuAction extends AbstractAction {
    private final String textKey;
    private final Runnable handler;

    public LocalizedMenuAction(String textKey, Runnable handler) {
        this.textKey = textKey;
        this.handler = handler;
    }

    public void updateText(Function<String, String> textResolver) {
        putValue(NAME, textResolver.apply(textKey));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        handler.run();
    }
}
