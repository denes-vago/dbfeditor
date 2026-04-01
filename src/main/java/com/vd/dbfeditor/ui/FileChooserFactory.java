package com.vd.dbfeditor.ui;

import com.vd.dbfeditor.export.ExportFormat;
import com.vd.dbfeditor.i18n.Localization;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

public final class FileChooserFactory {
    private final Localization localization;
    private final Path baseDirectory;

    public FileChooserFactory(Localization localization) {
        this.localization = localization;
        this.baseDirectory = Path.of("").toAbsolutePath();
    }

    public JFileChooser createDbfChooser(String dialogTitle, boolean multiSelectionEnabled) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSystemView(new DbfOnlyFileSystemView(baseDirectory.toFile()));
        chooser.setCurrentDirectory(baseDirectory.toFile());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
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
        chooser.setMultiSelectionEnabled(multiSelectionEnabled);
        chooser.setDialogTitle(dialogTitle);
        return chooser;
    }

    public JFileChooser createExportChooser(ExportFormat format, String dialogTitle) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(baseDirectory.toFile());
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().toLowerCase().endsWith(format.extension());
            }

            @Override
            public String getDescription() {
                return localization.text(format.filterKey());
            }
        });
        chooser.setFileHidingEnabled(true);
        chooser.setDialogTitle(dialogTitle);
        return chooser;
    }
}
