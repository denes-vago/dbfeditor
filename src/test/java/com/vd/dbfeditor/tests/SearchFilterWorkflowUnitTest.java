package com.vd.dbfeditor.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.junit.jupiter.api.Test;

class SearchFilterWorkflowUnitTest {

    @Test
    // Verifies that find-next follows the visible order of a sorted view.
    void continueSearchFollowsSortedViewOrder() throws Exception {
        Object document = createSearchDocument();
        Object view = createSortedView(document);
        Preferences preferences = Preferences.userNodeForPackage(SearchFilterWorkflowUnitTest.class);
        preferences.put("unit.find", "match");
        preferences.putBoolean("unit.case", false);
        preferences.remove("unit.column");

        Object workflow = createWorkflow(preferences);
        JTable table = (JTable) getFieldValue(view, "table");
        table.setRowSelectionInterval(0, 0);

        Object match = invokeDeclaredMethod(
            workflow,
            "locateMatch",
            new Class<?>[] {document.getClass(), view.getClass(), String.class, boolean.class, int.class, boolean.class},
            document,
            view,
            "match",
            false,
            -1,
            true
        );
        assertNotNull(match);
        assertEquals(0, invokeDeclaredMethod(match, "rowIndex", new Class<?>[0]));
        assertEquals(0, invokeDeclaredMethod(match, "columnIndex", new Class<?>[0]));
    }

    @Test
    // Verifies that find-previous also walks backward in visible view order.
    void previousSearchFollowsSortedViewOrder() throws Exception {
        Object document = createSearchDocument();
        Object view = createSortedView(document);
        Preferences preferences = Preferences.userNodeForPackage(SearchFilterWorkflowUnitTest.class);
        preferences.put("unit.find", "match");
        preferences.putBoolean("unit.case", false);
        preferences.remove("unit.column");

        Object workflow = createWorkflow(preferences);
        JTable table = (JTable) getFieldValue(view, "table");
        table.setRowSelectionInterval(0, 0);

        Object match = invokeDeclaredMethod(
            workflow,
            "locateMatch",
            new Class<?>[] {document.getClass(), view.getClass(), String.class, boolean.class, int.class, boolean.class},
            document,
            view,
            "match",
            false,
            -1,
            false
        );
        assertNotNull(match);
        assertEquals(3, invokeDeclaredMethod(match, "rowIndex", new Class<?>[0]));
        assertEquals(0, invokeDeclaredMethod(match, "columnIndex", new Class<?>[0]));
    }

    @Test
    // Verifies that column filtering targets the selected column and hides deleted rows.
    void columnFilterTargetsSelectedColumnAndHidesDeletedRows() throws Exception {
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

        assertEquals(1, table.getRowCount());
        assertEquals(0, table.convertRowIndexToModel(0));
    }

    @Test
    // Verifies that column-restricted search does not match text in other columns.
    void continueSearchCanBeRestrictedToOneColumn() throws Exception {
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
            "locateMatch",
            new Class<?>[] {document.getClass(), view.getClass(), String.class, boolean.class, int.class, boolean.class},
            document,
            view,
            "match",
            false,
            1,
            true
        );
        assertNull(match);
    }

    private Object createWorkflow(Preferences preferences) throws Exception {
        Class<?> workflowClass = Class.forName("com.vd.dbfeditor.app.SearchFilterWorkflow");
        Constructor<?> constructor = workflowClass.getDeclaredConstructor(
            javax.swing.JFrame.class,
            Localization.class,
            Preferences.class,
            String.class,
            String.class,
            String.class,
            String.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(null, new Localization("en"), preferences, "unit.find", "unit.case", "unit.column", "unit.replace");
    }

    private Object createSearchDocument() throws Exception {
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

    private Object createFilterDocument() throws Exception {
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

    private Object createDocument(String displayName, DBFEngine.DBFFile dbf) throws Exception {
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

    private Object createSortedView(Object document) throws Exception {
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

    private Object createView(JTable table, DBFTableModel tableModel, TableRowSorter<DBFTableModel> rowSorter) throws Exception {
        Class<?> viewClass = Class.forName("com.vd.dbfeditor.app.DocumentView");
        Constructor<?> constructor = viewClass.getDeclaredConstructor(JPanel.class, JTable.class, DBFTableModel.class, TableRowSorter.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new JPanel(), table, tableModel, rowSorter);
    }

    private Object getFieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object invokeDeclaredMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
