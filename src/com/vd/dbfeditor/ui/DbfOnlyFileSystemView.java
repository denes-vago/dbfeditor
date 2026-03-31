package com.vd.dbfeditor.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.filechooser.FileSystemView;

public final class DbfOnlyFileSystemView extends FileSystemView {
    private final FileSystemView delegate = FileSystemView.getFileSystemView();
    private final File rootDirectory;

    public DbfOnlyFileSystemView(File rootDirectory) {
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
