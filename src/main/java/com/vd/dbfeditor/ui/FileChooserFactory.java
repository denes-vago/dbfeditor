package com.vd.dbfeditor.ui;

import com.vd.dbfeditor.export.ExportFormat;
import com.vd.dbfeditor.i18n.Localization;
import java.io.File;
import java.nio.file.Path;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

public final class FileChooserFactory {
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FileChooserFactory.class);
    private static final String PREF_LAST_DIRECTORY = "lastDirectory";

    private final Localization localization;
    private final Path baseDirectory;

    public FileChooserFactory(Localization localization) {
        this.localization = localization;
        this.baseDirectory = Path.of("").toAbsolutePath();
    }

    public JFileChooser createDbfChooser(String dialogTitle, boolean multiSelectionEnabled) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSystemView(new DbfOnlyFileSystemView(baseDirectory.toFile()));
        chooser.setCurrentDirectory(initialDirectory());
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
        chooser.setCurrentDirectory(initialDirectory());
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

    public void rememberDirectory(JFileChooser chooser) {
        File selectedFile = chooser.getSelectedFile();
        if (selectedFile != null) {
            File directory = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
            if (directory != null) {
                PREFERENCES.put(PREF_LAST_DIRECTORY, directory.getAbsolutePath());
                return;
            }
        }

        File currentDirectory = chooser.getCurrentDirectory();
        if (currentDirectory != null) {
            PREFERENCES.put(PREF_LAST_DIRECTORY, currentDirectory.getAbsolutePath());
        }
    }

    private File initialDirectory() {
        String stored = PREFERENCES.get(PREF_LAST_DIRECTORY, null);
        if (stored != null && !stored.isBlank()) {
            File directory = new File(stored);
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return baseDirectory.toFile();
    }
}
