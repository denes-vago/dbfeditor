package com.vd.dbfeditor.dbf;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class DBFReader {
    private DBFReader() {
    }

    static DBFEngine.DBFFile read(Path path, Charset charset) throws IOException {
        long fileSize = Files.size(path);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            int version = DBFIOUtil.readUnsignedByte(file);
            int year = 1900 + DBFIOUtil.readUnsignedByte(file);
            int month = DBFIOUtil.readUnsignedByte(file);
            int day = DBFIOUtil.readUnsignedByte(file);
            long recordCount = DBFIOUtil.readLittleEndianInt(file) & 0xFFFFFFFFL;
            int headerLength = DBFIOUtil.readLittleEndianShort(file);
            int recordLength = DBFIOUtil.readLittleEndianShort(file);

            DBFValidator.validateHeader(path, fileSize, recordCount, headerLength, recordLength);
            file.skipBytes(DBFIOUtil.RESERVED_HEADER_BYTES);

            List<DBFEngine.FieldDescriptor> fields = new ArrayList<>();
            int totalFieldLength = 0;

            long descriptorSectionEnd = headerLength - DBFIOUtil.FIELD_TERMINATOR_LENGTH;
            boolean terminatorFound = false;
            while (file.getFilePointer() < descriptorSectionEnd) {
                int marker = DBFIOUtil.readUnsignedByte(file);
                if (marker == 0x0D) {
                    terminatorFound = true;
                    break;
                }
                byte[] descriptor = new byte[31];
                file.readFully(descriptor);
                byte[] nameBytes = new byte[11];
                nameBytes[0] = (byte) marker;
                System.arraycopy(descriptor, 0, nameBytes, 1, 10);
                String name = DBFIOUtil.decodeZeroTerminated(nameBytes, charset).trim();
                char type = Character.toUpperCase((char) (descriptor[10] & 0xFF));
                int length = descriptor[15] & 0xFF;
                int decimalCount = descriptor[16] & 0xFF;
                DBFValidator.validateFieldDescriptor(path, fields.size(), name, type, length, decimalCount);
                fields.add(new DBFEngine.FieldDescriptor(name, type, length, decimalCount));
                totalFieldLength += length;
                if (fields.size() > DBFValidator.MAX_FIELD_COUNT) {
                    throw new IOException("Túl sok mező van a DBF fejlécben: " + fields.size());
                }
            }

            if (!terminatorFound) {
                long pointer = file.getFilePointer();
                if (pointer < headerLength) {
                    int terminator = DBFIOUtil.readUnsignedByte(file);
                    if (terminator == 0x0D) {
                        terminatorFound = true;
                    } else {
                        file.seek(pointer);
                    }
                }
            }
            if (fields.isEmpty()) {
                throw new IOException("Hibás DBF fejléc: nem található érvényes mezőleíró.");
            }
            if (1 + totalFieldLength != recordLength) {
                throw new IOException("Hibás DBF fejléc: a mezőhosszak összege nem egyezik a rekordhosszal.");
            }
            if (!terminatorFound) {
                file.seek(headerLength);
            }

            DBFValidator.ensureRecordCountFits(path, fileSize, headerLength, recordLength, recordCount);
            byte[] memoData = readMemoData(path);
            List<String> memoWarnings = new ArrayList<>();
            if (memoData == null && containsMemoField(fields)) {
                Path memoPath = DBFIOUtil.memoPathFor(path);
                memoWarnings.add("A memo mezőkhöz tartozó DBT fájl hiányzik: " + memoPath.getFileName());
            }

            file.seek(headerLength);
            int initialCapacity = (int) Math.min(recordCount, 10_000L);
            List<List<String>> records = new ArrayList<>(initialCapacity);
            List<Boolean> deletedFlags = new ArrayList<>(initialCapacity);
            byte[] recordBuffer = new byte[recordLength - 1];

            for (long i = 0; i < recordCount; i++) {
                int flag;
                try {
                    flag = DBFIOUtil.readUnsignedByte(file);
                } catch (EOFException eof) {
                    throw new IOException("A DBF rekordterülete csonka: a fájl váratlanul véget ért.", eof);
                }
                file.readFully(recordBuffer);
                List<String> row = new ArrayList<>(fields.size());
                int offset = 0;
                for (DBFEngine.FieldDescriptor field : fields) {
                    DBFValueCodec.ValueReadResult result = DBFValueCodec.formatValue(field, recordBuffer, offset, charset, memoData, records.size());
                    row.add(result.value());
                    if (result.warning() != null && !memoWarnings.contains(result.warning())) {
                        memoWarnings.add(result.warning());
                    }
                    offset += field.length();
                }
                records.add(row);
                deletedFlags.add(flag == '*');
            }

            LocalDate lastUpdate = DBFIOUtil.safeDate(year, month, day);
            return new DBFEngine.DBFFile(version, lastUpdate, recordCount, headerLength, recordLength, fields, records, deletedFlags, memoWarnings);
        }
    }

    private static boolean containsMemoField(List<DBFEngine.FieldDescriptor> fields) {
        for (DBFEngine.FieldDescriptor field : fields) {
            if (field.type() == 'M') {
                return true;
            }
        }
        return false;
    }

    private static byte[] readMemoData(Path dbfPath) throws IOException {
        Path memoPath = DBFIOUtil.memoPathFor(dbfPath);
        return Files.exists(memoPath) ? Files.readAllBytes(memoPath) : null;
    }
}
