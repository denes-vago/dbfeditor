package com.vd.dbfeditor.export;

public enum ExportFormat {
    CSV(".csv", "dialog.export.csv.title", "file_filter.csv"),
    XLSX(".xlsx", "dialog.export.xlsx.title", "file_filter.xlsx"),
    SQL(".sql", "dialog.export.sql.title", "file_filter.sql");

    private final String extension;
    private final String dialogTitleKey;
    private final String filterKey;

    ExportFormat(String extension, String dialogTitleKey, String filterKey) {
        this.extension = extension;
        this.dialogTitleKey = dialogTitleKey;
        this.filterKey = filterKey;
    }

    public String extension() {
        return extension;
    }

    public String dialogTitleKey() {
        return dialogTitleKey;
    }

    public String filterKey() {
        return filterKey;
    }
}
