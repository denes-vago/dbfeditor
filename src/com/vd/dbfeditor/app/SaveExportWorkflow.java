package com.vd.dbfeditor.app;

import com.vd.dbfeditor.export.ExportFormat;
import com.vd.dbfeditor.export.SqlDialect;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.FileChooserFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

final class SaveExportWorkflow {
    private final JFrame owner;
    private final Localization localization;
    private final FileChooserFactory fileChooserFactory;

    SaveExportWorkflow(JFrame owner, Localization localization, FileChooserFactory fileChooserFactory) {
        this.owner = owner;
        this.localization = localization;
        this.fileChooserFactory = fileChooserFactory;
    }

    Path chooseSavePath(DocumentModel document, boolean saveAs, Supplier<Boolean> filteredWriteConfirmation) {
        String titleKey = saveAs ? "dialog.save_as.title" : "dialog.save.title";
        if (!filteredWriteConfirmation.get()) {
            return null;
        }

        if (!saveAs && document.path != null) {
            return document.path;
        }

        JFileChooser chooser = fileChooserFactory.createDbfChooser(localization.text("dialog.save_as.title"), false);
        if (document.path != null) {
            chooser.setSelectedFile(document.path.toFile());
        } else if (document.displayName != null && !document.displayName.isBlank()) {
            chooser.setSelectedFile(new File(document.displayName));
        }

        int result = chooser.showSaveDialog(owner);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        Path selectedPath = chooser.getSelectedFile().toPath();
        if (Files.exists(selectedPath)) {
            int overwrite = JOptionPane.showConfirmDialog(
                owner,
                localization.text("dialog.save_as.overwrite_message"),
                localization.text("dialog.save_as.overwrite_title"),
                JOptionPane.YES_NO_OPTION
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return null;
            }
        }

        return selectedPath;
    }

    ExportRequest requestExport(
        DocumentModel document,
        ExportFormat format,
        Supplier<Boolean> filteredWriteConfirmation,
        Supplier<SqlDialect> sqlDialectSupplier,
        Path suggestedPath
    ) {
        if (!filteredWriteConfirmation.get()) {
            return null;
        }

        SqlDialect sqlDialect = null;
        if (format == ExportFormat.SQL) {
            sqlDialect = sqlDialectSupplier.get();
            if (sqlDialect == null) {
                return null;
            }
        }

        JFileChooser chooser = fileChooserFactory.createExportChooser(format, localization.text(format.dialogTitleKey()));
        chooser.setSelectedFile(suggestedPath.toFile());

        int result = chooser.showSaveDialog(owner);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        Path selectedPath = appendExtensionIfMissing(chooser.getSelectedFile().toPath(), format.extension());
        if (Files.exists(selectedPath)) {
            int overwrite = JOptionPane.showConfirmDialog(
                owner,
                localization.text("dialog.save_as.overwrite_message"),
                localization.text("dialog.save_as.overwrite_title"),
                JOptionPane.YES_NO_OPTION
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return null;
            }
        }

        return new ExportRequest(selectedPath, sqlDialect);
    }

    boolean confirmFilteredWrite(int visibleRows, int totalRows, String dialogTitle) {
        int answer = JOptionPane.showConfirmDialog(
            owner,
            localization.text("dialog.filtered_write.message", visibleRows, totalRows),
            dialogTitle,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        return answer == JOptionPane.YES_OPTION;
    }

    private Path appendExtensionIfMissing(Path path, String extension) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(extension)) {
            return path;
        }
        return path.resolveSibling(path.getFileName().toString() + extension);
    }

    record ExportRequest(Path path, SqlDialect sqlDialect) {
    }
}
