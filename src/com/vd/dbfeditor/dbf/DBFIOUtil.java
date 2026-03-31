package com.vd.dbfeditor.dbf;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;

final class DBFIOUtil {
    static final int RESERVED_HEADER_BYTES = 20;
    static final int FIELD_DESCRIPTOR_LENGTH = 32;
    static final int FIELD_TERMINATOR_LENGTH = 1;
    static final int DBT_BLOCK_SIZE = 512;

    private DBFIOUtil() {
    }

    static String decodeZeroTerminated(byte[] bytes, Charset charset) {
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, charset);
    }

    static String trimRight(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    static int readUnsignedByte(RandomAccessFile file) throws IOException {
        int value = file.read();
        if (value == -1) {
            throw new EOFException("Váratlan fájlvége.");
        }
        return value;
    }

    static int readLittleEndianShort(RandomAccessFile file) throws IOException {
        int b1 = readUnsignedByte(file);
        int b2 = readUnsignedByte(file);
        return b1 | (b2 << 8);
    }

    static int readLittleEndianInt(RandomAccessFile file) throws IOException {
        int b1 = readUnsignedByte(file);
        int b2 = readUnsignedByte(file);
        int b3 = readUnsignedByte(file);
        int b4 = readUnsignedByte(file);
        return b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
    }

    static void writeUnsignedByte(OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
    }

    static void writeLittleEndianShort(OutputStream output, int value) throws IOException {
        writeUnsignedByte(output, value);
        writeUnsignedByte(output, value >>> 8);
    }

    static void writeLittleEndianInt(OutputStream output, int value) throws IOException {
        writeUnsignedByte(output, value);
        writeUnsignedByte(output, value >>> 8);
        writeUnsignedByte(output, value >>> 16);
        writeUnsignedByte(output, value >>> 24);
    }

    static void writeLittleEndianInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    static Path memoPathFor(Path dbfPath) {
        String fileName = dbfPath.getFileName() != null ? dbfPath.getFileName().toString() : "memo";
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return dbfPath.resolveSibling(baseName + ".DBT");
    }

    static int memoEnd(byte[] data, int offset, int length) {
        for (int i = 0; i < length; i++) {
            if (data[offset + i] == 0x1A) {
                return i;
            }
        }
        return -1;
    }

    static String readMemo(byte[] data, Charset charset, String pointerText) {
        if (data == null) {
            return pointerText;
        }
        try {
            int blockNumber = Integer.parseInt(pointerText.trim());
            if (blockNumber <= 0) {
                return "";
            }
            long start = (long) blockNumber * DBT_BLOCK_SIZE;
            if (start >= data.length) {
                return pointerText;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int position = (int) start;
            while (position < data.length) {
                int read = Math.min(DBT_BLOCK_SIZE, data.length - position);
                int end = memoEnd(data, position, read);
                if (end >= 0) {
                    buffer.write(data, position, end);
                    break;
                }
                buffer.write(data, position, read);
                if (read < DBT_BLOCK_SIZE) {
                    break;
                }
                position += read;
            }
            return trimRight(new String(buffer.toByteArray(), charset)).replace("\r\n", "\n");
        } catch (Exception e) {
            return pointerText;
        }
    }
}
