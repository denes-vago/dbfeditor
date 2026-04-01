package com.vd.dbfeditor.dbf;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

final class DBFWriter {
    private DBFWriter() {
    }

    static void write(Path path, Charset charset, DBFEngine.DBFFile dbf) throws IOException {
        DBFValidator.validateDbfForWrite(dbf, charset);
        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath();
        }
        Files.createDirectories(parent);
        String fileName = absolutePath.getFileName() != null ? absolutePath.getFileName().toString() : "dbf";
        Path tempFile = Files.createTempFile(parent, fileName + ".", ".tmp");
        Path tempMemoFile = hasMemoFields(dbf) ? Files.createTempFile(parent, fileName + ".", ".dbt.tmp") : null;
        try {
            writeDirect(tempFile, tempMemoFile, charset, dbf);
            moveAtomically(tempFile, absolutePath);
            if (tempMemoFile != null) {
                moveAtomically(tempMemoFile, DBFIOUtil.memoPathFor(absolutePath));
            }
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            if (tempMemoFile != null) {
                Files.deleteIfExists(tempMemoFile);
            }
            throw e;
        }
    }

    private static void writeDirect(Path path, Path memoPath, Charset charset, DBFEngine.DBFFile dbf) throws IOException {
        int headerLength = calculateHeaderLength(dbf.fields());
        int recordLength = calculateRecordLength(dbf.fields());
        MemoWriter memoWriter = memoPath != null ? new MemoWriter(memoPath, charset) : null;
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path))) {
            LocalDate updateDate = LocalDate.now();
            DBFIOUtil.writeUnsignedByte(output, dbf.version());
            DBFIOUtil.writeUnsignedByte(output, updateDate.getYear() - 1900);
            DBFIOUtil.writeUnsignedByte(output, updateDate.getMonthValue());
            DBFIOUtil.writeUnsignedByte(output, updateDate.getDayOfMonth());
            DBFIOUtil.writeLittleEndianInt(output, dbf.records().size());
            DBFIOUtil.writeLittleEndianShort(output, headerLength);
            DBFIOUtil.writeLittleEndianShort(output, recordLength);
            for (int i = 0; i < DBFIOUtil.RESERVED_HEADER_BYTES; i++) {
                DBFIOUtil.writeUnsignedByte(output, 0);
            }
            for (DBFEngine.FieldDescriptor field : dbf.fields()) {
                writeFieldDescriptor(output, field, charset);
            }
            DBFIOUtil.writeUnsignedByte(output, 0x0D);
            for (int rowIndex = 0; rowIndex < dbf.records().size(); rowIndex++) {
                List<String> row = dbf.records().get(rowIndex);
                boolean deleted = rowIndex < dbf.deletedFlags().size() && Boolean.TRUE.equals(dbf.deletedFlags().get(rowIndex));
                DBFIOUtil.writeUnsignedByte(output, deleted ? '*' : ' ');
                for (int i = 0; i < dbf.fields().size(); i++) {
                    DBFEngine.FieldDescriptor field = dbf.fields().get(i);
                    String value = i < row.size() ? row.get(i) : "";
                    writeFieldValue(output, field, value, charset, memoWriter);
                }
            }
            DBFIOUtil.writeUnsignedByte(output, 0x1A);
            if (memoWriter != null) {
                memoWriter.finish();
            }
        }
    }

    private static int calculateHeaderLength(List<DBFEngine.FieldDescriptor> fields) {
        return 32 + fields.size() * DBFIOUtil.FIELD_DESCRIPTOR_LENGTH + DBFIOUtil.FIELD_TERMINATOR_LENGTH;
    }

    private static int calculateRecordLength(List<DBFEngine.FieldDescriptor> fields) {
        int recordLength = 1;
        for (DBFEngine.FieldDescriptor field : fields) {
            recordLength += field.length();
        }
        return recordLength;
    }

    private static void moveAtomically(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeFieldDescriptor(OutputStream output, DBFEngine.FieldDescriptor field, Charset charset) throws IOException {
        byte[] nameBytes = field.name().getBytes(charset);
        for (int i = 0; i < 11; i++) {
            output.write(i < nameBytes.length ? nameBytes[i] : 0);
        }
        DBFIOUtil.writeUnsignedByte(output, field.type());
        for (int i = 0; i < 4; i++) {
            DBFIOUtil.writeUnsignedByte(output, 0);
        }
        DBFIOUtil.writeUnsignedByte(output, field.length());
        DBFIOUtil.writeUnsignedByte(output, field.decimalCount());
        for (int i = 0; i < 14; i++) {
            DBFIOUtil.writeUnsignedByte(output, 0);
        }
    }

    private static void writeFieldValue(OutputStream output, DBFEngine.FieldDescriptor field, String value, Charset charset, MemoWriter memoWriter)
        throws IOException {
        String normalized = field.type() == 'M'
            ? (memoWriter != null ? memoWriter.store(value == null ? "" : value) : "")
            : DBFValueCodec.normalizeForStorage(field, value);
        if (normalized == null) {
            throw new IOException("A(z) " + field.name() + " mező értéke érvénytelen.");
        }
        byte[] encoded = normalized.getBytes(charset);
        if (encoded.length > field.length()) {
            throw new IOException("A(z) " + field.name() + " mező értéke túl hosszú.");
        }
        int padSize = field.length() - encoded.length;
        if (field.type() == 'N' || field.type() == 'F') {
            for (int i = 0; i < padSize; i++) {
                output.write(' ');
            }
            output.write(encoded);
        } else {
            output.write(encoded);
            for (int i = 0; i < padSize; i++) {
                output.write(' ');
            }
        }
    }

    private static boolean hasMemoFields(DBFEngine.DBFFile dbf) {
        for (DBFEngine.FieldDescriptor field : dbf.fields()) {
            if (field.type() == 'M') {
                return true;
            }
        }
        return false;
    }

    private static final class MemoWriter {
        private final OutputStream output;
        private final Charset charset;
        private int nextBlock = 1;

        private MemoWriter(Path path, Charset charset) throws IOException {
            this.output = new BufferedOutputStream(Files.newOutputStream(path));
            this.charset = charset;
            byte[] header = new byte[DBFIOUtil.DBT_BLOCK_SIZE];
            DBFIOUtil.writeLittleEndianInt(header, 0, nextBlock);
            output.write(header);
        }

        String store(String value) throws IOException {
            String safeValue = value == null ? "" : value;
            if (safeValue.isEmpty()) {
                return "";
            }
            int startBlock = nextBlock;
            byte[] data = safeValue.replace("\n", System.lineSeparator()).getBytes(charset);
            byte[] terminated = new byte[data.length + 1];
            System.arraycopy(data, 0, terminated, 0, data.length);
            terminated[data.length] = 0x1A;
            int blocks = Math.max(1, (terminated.length + DBFIOUtil.DBT_BLOCK_SIZE - 1) / DBFIOUtil.DBT_BLOCK_SIZE);
            byte[] padded = new byte[blocks * DBFIOUtil.DBT_BLOCK_SIZE];
            System.arraycopy(terminated, 0, padded, 0, terminated.length);
            output.write(padded);
            nextBlock += blocks;
            return String.format(Locale.ROOT, "%10d", startBlock);
        }

        void finish() throws IOException {
            output.flush();
            output.close();
        }
    }
}
