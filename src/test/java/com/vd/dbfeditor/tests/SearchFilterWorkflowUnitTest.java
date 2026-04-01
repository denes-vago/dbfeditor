package com.vd.dbfeditor.tests;

import com.vd.dbfeditor.dbf.DBFEngine;
import com.vd.dbfeditor.i18n.Localization;
import com.vd.dbfeditor.ui.DBFTableModel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;

public class SearchFilterWorkflowUnitTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        testContinueSearchFollowsSortedViewOrder();
        testPreviousSearchFollowsSortedViewOrder();
        testColumnFilterTargetsSelectedColumnAndHidesDeletedRows();
        System.out.println("OK - assertions=" + assertions);
    }

    // The next-match search must follow the current sorted JTable view order, not the model order.
    private static void testContinueSearchFollowsSortedViewOrder() throws Exception {
        Object document = createSearchDocument();
        Object view = createSortedView(document);
        Preferences preferences = Preferences.userNodeForPackage(SearchFilterWorkflowUnitTest.class);
        preferences.put("unit.find", "match");
        preferences.putBoolean("unit.case", false);

        Object workflow = createWorkflow(preferences);
        JTable table = (JTable) getFieldValue(view, "table");
        table.setRowSelectionInterval(0, 0);

        Object match = invokeDeclaredMethod(
            workflow,
            "continueSearch",
            new Class<?>[] {document.getClass(), view.getClass(), boolean.class},
            document,
            view,
            true
        );
        assertNotNull(match, "Forward search should find a next match.");
        assertEquals(0, invokeDeclaredMethod(match, "rowIndex", new Class<?>[0]), "Forward search should return the next match in view order.");
        assertEquals(0, invokeDeclaredMethod(match, "columnIndex", new Class<?>[0]), "The match should be found in the first column.");
    }

    // The previous-match search must also follow the current sorted JTable view order in reverse.
    private static void testPreviousSearchFollowsSortedViewOrder() throws Exception {
        Object document = createSearchDocument();
        Object view = createSortedView(document);
        Preferences preferences = Preferences.userNodeForPackage(SearchFilterWorkflowUnitTest.class);
        preferences.put("unit.find", "match");
        preferences.putBoolean("unit.case", false);

        Object workflow = createWorkflow(preferences);
        JTable table = (JTable) getFieldValue(view, "table");
        table.setRowSelectionInterval(0, 0);

        Object match = invokeDeclaredMethod(
            workflow,
            "continueSearch",
            new Class<?>[] {document.getClass(), view.getClass(), boolean.class},
            document,
            view,
            false
        );
        assertNotNull(match, "Backward search should find a previous match.");
        assertEquals(3, invokeDeclaredMethod(match, "rowIndex", new Class<?>[0]), "Backward search should wrap in view order, not model order.");
        assertEquals(0, invokeDeclaredMethod(match, "columnIndex", new Class<?>[0]), "The match should be found in the first column.");
    }

    // Column-specific filtering must only inspect the selected column and still respect the deleted-row visibility setting.
    private static void testColumnFilterTargetsSelectedColumnAndHidesDeletedRows() throws Exception {
        Object document = createFilterDocument();
        setFieldValue(document, "filterText", "target");
        setFieldValue(document, "filterCaseSensitive", false);
        setFieldValue(document, "filterColumnIndex", 1);
        setFieldValue(document, "showDeletedRecords", false);

        DBFEngine.DBFFile dbf = (DBFEngine.DBFFile) getFieldValue(document, "dbf");
        DBFTableModel tableModel = new DBFTableModel();
        tableModel.setDbf(dbf);
        JTable table = new JTable(tableModel);
        TableRowSorter<DBFTableModel> rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);
        Object view = createView(table, tableModel, rowSorter);

        Object workflow = createWorkflow(Preferences.userNodeForPackage(SearchFilterWorkflowUnitTest.class));
        invokeDeclaredMethod(
            workflow,
            "applyFilter",
            new Class<?>[] {document.getClass(), view.getClass(), Runnable.class},
            document,
            view,
            (Runnable) () -> {}
        );

        assertEquals(1, table.getRowCount(), "Only non-deleted rows matching the selected column should remain visible.");
        int visibleModelRow = table.convertRowIndexToModel(0);
        assertEquals(0, visibleModelRow, "Filtering should keep the expected model row.");
    }

    private static Object createWorkflow(Preferences preferences) throws Exception {
        Class<?> workflowClass = Class.forName("com.vd.dbfeditor.app.SearchFilterWorkflow");
        Constructor<?> constructor = workflowClass.getDeclaredConstructor(
            javax.swing.JFrame.class,
            Localization.class,
            Preferences.class,
            String.class,
            String.class,
            String.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
            null,
            new Localization("en"),
            preferences,
            "unit.find",
            "unit.case",
            "unit.replace"
        );
    }

    private static Object createSearchDocument() throws Exception {
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("NAME", 'C', 20, 0),
            new DBFEngine.FieldDescriptor("ORD", 'N', 3, 0)
        );
        List<List<String>> records = new ArrayList<>();
        records.add(new ArrayList<>(List.of("match-one", "2")));
        records.add(new ArrayList<>(List.of("plain", "3")));
        records.add(new ArrayList<>(List.of("selected", "1")));
        records.add(new ArrayList<>(List.of("match-two", "4")));

        DBFEngine.DBFFile dbf = new DBFEngine.DBFFile(
            0x03,
            LocalDate.of(2024, 1, 1),
            records.size(),
            32 + fields.size() * 32 + 1,
            1 + 20 + 3,
            fields,
            records,
            new ArrayList<>(List.of(false, false, false, false)),
            new ArrayList<>()
        );
        return createDocument("test", dbf);
    }

    private static Object createFilterDocument() throws Exception {
        List<DBFEngine.FieldDescriptor> fields = List.of(
            new DBFEngine.FieldDescriptor("NAME", 'C', 20, 0),
            new DBFEngine.FieldDescriptor("CITY", 'C', 20, 0)
        );
        List<List<String>> records = new ArrayList<>();
        records.add(new ArrayList<>(List.of("alpha target", "target city")));
        records.add(new ArrayList<>(List.of("target hidden", "other city")));
        records.add(new ArrayList<>(List.of("deleted", "target city")));

        DBFEngine.DBFFile dbf = new DBFEngine.DBFFile(
            0x03,
            LocalDate.of(2024, 1, 1),
            records.size(),
            32 + fields.size() * 32 + 1,
            1 + 20 + 20,
            fields,
            records,
            new ArrayList<>(List.of(false, false, true)),
            new ArrayList<>()
        );
        return createDocument("filter", dbf);
    }

    private static Object createDocument(String displayName, DBFEngine.DBFFile dbf) throws Exception {
        Class<?> documentClass = Class.forName("com.vd.dbfeditor.app.DocumentModel");
        Constructor<?> constructor = documentClass.getDeclaredConstructor(
            String.class,
            java.nio.file.Path.class,
            Charset.class,
            DBFEngine.DBFFile.class,
            boolean.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(displayName, null, Charset.forName("IBM437"), dbf, false);
    }

    private static Object createSortedView(Object document) throws Exception {
        DBFEngine.DBFFile dbf = (DBFEngine.DBFFile) getFieldValue(document, "dbf");
        DBFTableModel tableModel = new DBFTableModel();
        tableModel.setDbf(dbf);
        JTable table = new JTable(tableModel);
        TableRowSorter<DBFTableModel> rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);
        rowSorter.setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING)));
        rowSorter.sort();
        return createView(table, tableModel, rowSorter);
    }

    private static Object createView(JTable table, DBFTableModel tableModel, TableRowSorter<DBFTableModel> rowSorter) throws Exception {
        Class<?> viewClass = Class.forName("com.vd.dbfeditor.app.DocumentView");
        Constructor<?> constructor = viewClass.getDeclaredConstructor(JPanel.class, JTable.class, DBFTableModel.class, TableRowSorter.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new JPanel(), table, tableModel, rowSorter);
    }

    private static Object getFieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invokeDeclaredMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void assertNotNull(Object value, String message) {
        assertions++;
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " Expected: " + expected + ", actual: " + actual);
        }
    }
}
