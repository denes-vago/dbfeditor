package com.vd.dbfeditor.export;

import com.vd.dbfeditor.dbf.DBFEngine;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    private XlsxExporter() {
    }

    public static void export(DBFEngine.DBFFile dbf, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeEntry(zip, "[Content_Types].xml", contentTypesXml());
            writeEntry(zip, "_rels/.rels", rootRelsXml());
            writeEntry(zip, "xl/workbook.xml", workbookXml());
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            writeEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(dbf));
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypesXml() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
            """;
    }

    private static String rootRelsXml() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
            """;
    }

    private static String workbookXml() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
            """;
    }

    private static String workbookRelsXml() {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
            """;
    }

    private static String worksheetXml(DBFEngine.DBFFile dbf) {
        StringBuilder xml = new StringBuilder();
        xml.append("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
            """);

        int rowNumber = 1;
        xml.append("    <row r=\"").append(rowNumber).append("\">").append(System.lineSeparator());
        for (int columnIndex = 0; columnIndex < dbf.fields().size(); columnIndex++) {
            appendInlineStringCell(xml, rowNumber, columnIndex, dbf.fields().get(columnIndex).name());
        }
        xml.append("    </row>").append(System.lineSeparator());

        for (List<String> record : dbf.records()) {
            rowNumber++;
            xml.append("    <row r=\"").append(rowNumber).append("\">").append(System.lineSeparator());
            for (int columnIndex = 0; columnIndex < dbf.fields().size(); columnIndex++) {
                String value = columnIndex < record.size() ? record.get(columnIndex) : "";
                appendInlineStringCell(xml, rowNumber, columnIndex, value);
            }
            xml.append("    </row>").append(System.lineSeparator());
        }

        xml.append("""
              </sheetData>
            </worksheet>
            """);
        return xml.toString();
    }

    private static void appendInlineStringCell(StringBuilder xml, int rowNumber, int columnIndex, String value) {
        xml.append("      <c r=\"")
            .append(columnReference(columnIndex))
            .append(rowNumber)
            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
            .append(escapeXml(value == null ? "" : value))
            .append("</t></is></c>")
            .append(System.lineSeparator());
    }

    private static String columnReference(int columnIndex) {
        StringBuilder result = new StringBuilder();
        int index = columnIndex;
        do {
            result.insert(0, (char) ('A' + (index % 26)));
            index = (index / 26) - 1;
        } while (index >= 0);
        return result.toString();
    }

    private static String escapeXml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> {
                    if (ch >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
