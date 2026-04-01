package com.vd.dbfeditor.action;

import com.vd.dbfeditor.export.ExportFormat;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

public final class ExportMenuAction extends LocalizedMenuAction {
    private final ExportFormat format;
    private final Consumer<ExportFormat> handler;

    public ExportMenuAction(String textKey, ExportFormat format, Consumer<ExportFormat> handler) {
        super(textKey, () -> { });
        this.format = format;
        this.handler = handler;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        handler.accept(format);
    }
}
