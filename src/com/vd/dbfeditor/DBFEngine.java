package com.vd.dbfeditor;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DBFEngine {
    public static final Charset DEFAULT_CHARSET = Charset.forName("Cp852");
    private static final int RESERVED_HEADER_BYTES = 20;
    private static final int FIELD_DESCRIPTOR_LENGTH = 32;
    private static final int FIELD_TERMINATOR_LENGTH = 1;
    private static final int DBT_BLOCK_SIZE = 512;
    private static final int MIN_HEADER_LENGTH = 32 + FIELD_TERMINATOR_LENGTH;
    private static final int MAX_FIELD_COUNT = 1024;
    private static final int MAX_RECORD_LENGTH = 64 * 1024;
    private static final long MAX_RECORD_COUNT = 2_000_000L;
    private static final DateTimeFormatter DBF_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<Character> SUPPORTED_FIELD_TYPES = Set.of('C', 'D', 'F', 'L', 'M', 'N');

    private DBFEngine() {
    }

    public static DBFFile read(Path path, Charset charset) throws IOException {
        long fileSize = Files.size(path);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            int version = readUnsignedByte(file);
            int year = 1900 + readUnsignedByte(file);
            int month = readUnsignedByte(file);
            int day = readUnsignedByte(file);
            long recordCount = readLittleEndianInt(file) & 0xFFFFFFFFL;
            int headerLength = readLittleEndianShort(file);
            int recordLength = readLittleEndianShort(file);

            validateHeader(path, fileSize, recordCount, headerLength, recordLength);
            file.skipBytes(RESERVED_HEADER_BYTES);

            List<FieldDescriptor> fields = new ArrayList<>();
            int totalFieldLength = 0;

            long descriptorSectionEnd = headerLength - FIELD_TERMINATOR_LENGTH;
            boolean terminatorFound = false;
            while (file.getFilePointer() < descriptorSectionEnd) {
                int marker = readUnsignedByte(file);
                if (marker == 0x0D) {
                    terminatorFound = true;
                    break;
                }
                byte[] descriptor = new byte[31];
                file.readFully(descriptor);

                byte[] nameBytes = new byte[11];
                nameBytes[0] = (byte) marker;
                System.arraycopy(descriptor, 0, nameBytes, 1, 10);

                String name = decodeZeroTerminated(nameBytes, charset).trim();
                char type = Character.toUpperCase((char) (descriptor[10] & 0xFF));
                int length = descriptor[15] & 0xFF;
                int decimalCount = descriptor[16] & 0xFF;
                validateFieldDescriptor(path, fields.size(), name, type, length, decimalCount);
                fields.add(new FieldDescriptor(name, type, length, decimalCount));
                totalFieldLength += length;
                if (fields.size() > MAX_FIELD_COUNT) {
                    throw new IOException("Túl sok mező van a DBF fejlécben: " + fields.size());
                }
            }

            if (!terminatorFound) {
                long pointer = file.getFilePointer();
                if (pointer < headerLength) {
                    int terminator = readUnsignedByte(file);
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

            ensureRecordCountFits(path, fileSize, headerLength, recordLength, recordCount);
            MemoReader memoReader = MemoReader.open(path, charset);

            file.seek(headerLength);
            int initialCapacity = (int) Math.min(recordCount, 10_000L);
            List<List<String>> records = new ArrayList<>(initialCapacity);
            byte[] recordBuffer = new byte[recordLength - 1];

            for (long i = 0; i < recordCount; i++) {
                int flag;
                try {
                    flag = readUnsignedByte(file);
                } catch (EOFException eof) {
                    throw new IOException("A DBF rekordterülete csonka: a fájl váratlanul véget ért.", eof);
                }

                file.readFully(recordBuffer);
                if (flag == '*') {
                    continue;
                }

                List<String> row = new ArrayList<>(fields.size());
                int offset = 0;
                for (FieldDescriptor field : fields) {
                    row.add(formatValue(field, recordBuffer, offset, charset, memoReader));
                    offset += field.length();
                }
                records.add(row);
            }

            LocalDate lastUpdate = safeDate(year, month, day);
            return new DBFFile(version, lastUpdate, recordCount, headerLength, recordLength, fields, records);
        }
    }

    public static void write(Path path, Charset charset, DBFFile dbf) throws IOException {
        validateDbfForWrite(dbf, charset);

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
                moveAtomically(tempMemoFile, memoPathFor(absolutePath));
            }
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            if (tempMemoFile != null) {
                Files.deleteIfExists(tempMemoFile);
            }
            throw e;
        }
    }

    private static void writeDirect(Path path, Path memoPath, Charset charset, DBFFile dbf) throws IOException {
        int headerLength = calculateHeaderLength(dbf.fields());
        int recordLength = calculateRecordLength(dbf.fields());
        MemoWriter memoWriter = memoPath != null ? new MemoWriter(memoPath, charset) : null;
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path))) {
            LocalDate updateDate = LocalDate.now();
            writeUnsignedByte(output, dbf.version());
            writeUnsignedByte(output, updateDate.getYear() - 1900);
            writeUnsignedByte(output, updateDate.getMonthValue());
            writeUnsignedByte(output, updateDate.getDayOfMonth());
            writeLittleEndianInt(output, dbf.records().size());
            writeLittleEndianShort(output, headerLength);
            writeLittleEndianShort(output, recordLength);

            for (int i = 0; i < RESERVED_HEADER_BYTES; i++) {
                writeUnsignedByte(output, 0);
            }

            for (FieldDescriptor field : dbf.fields()) {
                writeFieldDescriptor(output, field, charset);
            }
            writeUnsignedByte(output, 0x0D);

            for (List<String> row : dbf.records()) {
                writeUnsignedByte(output, ' ');
                for (int i = 0; i < dbf.fields().size(); i++) {
                    FieldDescriptor field = dbf.fields().get(i);
                    String value = i < row.size() ? row.get(i) : "";
                    writeFieldValue(output, field, value, charset, memoWriter);
                }
            }

            writeUnsignedByte(output, 0x1A);
            if (memoWriter != null) {
                memoWriter.finish();
            }
        }
    }

    private static void validateHeader(Path path, long fileSize, long recordCount, int headerLength, int recordLength) throws IOException {
        if (fileSize < MIN_HEADER_LENGTH) {
            throw new IOException("A fájl túl kicsi ahhoz, hogy érvényes DBF legyen: " + path.getFileName());
        }
        if (headerLength < MIN_HEADER_LENGTH || headerLength > fileSize) {
            throw new IOException("Érvénytelen DBF fejléc hossza: " + headerLength);
        }
        if (recordLength <= 0 || recordLength > MAX_RECORD_LENGTH) {
            throw new IOException("Érvénytelen DBF rekordhossz: " + recordLength);
        }
        if (recordCount < 0 || recordCount > MAX_RECORD_COUNT) {
            throw new IOException("A DBF fájlban túl sok rekord szerepel a fejléc szerint: " + recordCount);
        }
    }

    private static void validateFieldDescriptor(Path path, int index, String name, char type, int length, int decimalCount)
        throws IOException {
        if (name.isBlank()) {
            throw new IOException("A DBF fejlécben üres mezőnév szerepel a(z) " + (index + 1) + ". mezőhelyen.");
        }
        if (!SUPPORTED_FIELD_TYPES.contains(type)) {
            throw new IOException("Nem támogatott mezőtípus a DBF fejlécben: " + type);
        }
        if (length <= 0 || length > 255) {
            throw new IOException("Érvénytelen mezőhossz a fejlécben: " + length);
        }
        if ((type == 'D' || type == 'L') && length != 1 && length != 8) {
            throw new IOException("Érvénytelen fix hosszú mező a fejlécben: " + name);
        }
        if (type == 'L' && length != 1) {
            throw new IOException("A logikai mező hossza csak 1 lehet: " + name);
        }
        if (type == 'D' && length != 8) {
            throw new IOException("A dátum mező hossza csak 8 lehet: " + name);
        }
        if ((type == 'N' || type == 'F') && decimalCount > length) {
            throw new IOException("Érvénytelen tizedes pontosság a fejlécben: " + name);
        }
        if (name.length() > 11) {
            throw new IOException("Túlságosan hosszú mezőnév a fejlécben a fájlban: " + path.getFileName());
        }
    }

    private static void ensureRecordCountFits(Path path, long fileSize, int headerLength, int recordLength, long declaredCount)
        throws IOException {
        long dataBytes = Math.max(0L, fileSize - headerLength);
        long maxRecordsBySize = dataBytes / recordLength;
        long remainder = dataBytes % recordLength;
        if (remainder > 1) {
            throw new IOException("A DBF rekordblokk sérült vagy idegen adatot tartalmaz: " + path.getFileName());
        }
        if (declaredCount > maxRecordsBySize) {
            throw new IOException("A fejléc szerinti rekordszám nagyobb, mint ami a fájlméretből következik.");
        }
    }

    private static void validateDbfForWrite(DBFFile dbf, Charset charset) throws IOException {
        if (dbf == null) {
            throw new IOException("Nincs menthető DBF tartalom.");
        }
        if (dbf.fields().isEmpty()) {
            throw new IOException("Nincs menthető mezőleíró a DBF fájlban.");
        }
        for (FieldDescriptor field : dbf.fields()) {
            validateFieldDescriptor(Path.of("<memory>"), 0, field.name(), field.type(), field.length(), field.decimalCount());
        }
        if (dbf.records().size() > MAX_RECORD_COUNT) {
            throw new IOException("Túl sok rekordot próbálsz menteni.");
        }

        for (List<String> row : dbf.records()) {
            for (int i = 0; i < dbf.fields().size(); i++) {
                FieldDescriptor field = dbf.fields().get(i);
                String value = i < row.size() ? row.get(i) : "";
                String error = validateValue(field, value, charset);
                if (error != null) {
                    throw new IOException(field.name() + ": " + error);
                }
            }
        }
    }

    private static int calculateHeaderLength(List<FieldDescriptor> fields) {
        return 32 + fields.size() * FIELD_DESCRIPTOR_LENGTH + FIELD_TERMINATOR_LENGTH;
    }

    private static int calculateRecordLength(List<FieldDescriptor> fields) {
        int recordLength = 1;
        for (FieldDescriptor field : fields) {
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

    private static String formatValue(FieldDescriptor field, byte[] bytes, int offset, Charset charset, MemoReader memoReader) {
        String raw = new String(bytes, offset, field.length(), charset);
        String trimmed = trimRight(raw).trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        return switch (field.type()) {
            case 'D' -> formatDate(trimmed);
            case 'L' -> formatLogical(trimmed);
            case 'M' -> memoReader != null ? memoReader.read(trimmed) : trimRight(raw);
            default -> trimRight(raw);
        };
    }

    private static String formatDate(String value) {
        if (value.length() == 8) {
            return value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8);
        }
        return value;
    }

    private static String formatLogical(String value) {
        char flag = Character.toUpperCase(value.charAt(0));
        return switch (flag) {
            case 'T', 'Y' -> "true";
            case 'F', 'N' -> "false";
            default -> "?";
        };
    }

    public static String validateValue(FieldDescriptor field, String value, Charset charset) {
        String safeValue = value == null ? "" : value;

        return switch (field.type()) {
            case 'D' -> validateDateValue(field, safeValue.trim());
            case 'L' -> validateLogicalValue(safeValue.trim());
            case 'M' -> null;
            case 'N', 'F' -> validateNumericValue(field, safeValue.trim(), charset);
            default -> validateCharacterValue(field, safeValue, charset);
        };
    }

    public static String validateFieldDefinition(FieldDescriptor field) {
        try {
            validateFieldDescriptor(Path.of("<memory>"), 0, field.name(), field.type(), field.length(), field.decimalCount());
            return null;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private static String validateDateValue(FieldDescriptor field, String value) {
        if (value.isEmpty()) {
            return null;
        }
        String normalized = normalizeDateForStorage(value);
        if (normalized == null || normalized.length() != field.length()) {
            return "A dátum mezőt yyyy-MM-dd vagy yyyyMMdd alakban add meg.";
        }
        try {
            LocalDate.parse(normalized, DBF_DATE);
        } catch (DateTimeParseException e) {
            return "A dátum érvénytelen naptári dátum.";
        }
        return null;
    }

    private static String validateLogicalValue(String value) {
        if (value.isEmpty()) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (
            upper.equals("T") || upper.equals("F") ||
            upper.equals("Y") || upper.equals("N") ||
            upper.equals("TRUE") || upper.equals("FALSE")
        ) {
            return null;
        }
        return "A logikai mezőben true/false, t/f vagy y/n érték adható meg.";
    }

    private static String validateNumericValue(FieldDescriptor field, String value, Charset charset) {
        if (value.isEmpty()) {
            return null;
        }

        String normalized = value.replace(',', '.');
        BigDecimal number;
        try {
            number = new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return "A numerikus mezőben csak szám lehet.";
        }

        number = number.stripTrailingZeros();
        int scale = Math.max(0, number.scale());
        if (scale > field.decimalCount()) {
            return "A mezőben legfeljebb " + field.decimalCount() + " tizedesjegy lehet.";
        }

        String encodedValue = formatNumericForStorage(field, normalized);
        byte[] encoded = encodedValue.getBytes(charset);
        if (encoded.length > field.length()) {
            return "A numerikus érték nem fér bele a " + field.length() + " karakteres mezőbe.";
        }
        return null;
    }

    private static String validateCharacterValue(FieldDescriptor field, String value, Charset charset) {
        byte[] encoded = value.getBytes(charset);
        if (encoded.length > field.length()) {
            return "A mezőbe legfeljebb " + field.length() + " byte fér.";
        }
        return null;
    }

    private static void writeFieldDescriptor(OutputStream output, FieldDescriptor field, Charset charset) throws IOException {
        byte[] nameBytes = field.name().getBytes(charset);
        for (int i = 0; i < 11; i++) {
            output.write(i < nameBytes.length ? nameBytes[i] : 0);
        }
        writeUnsignedByte(output, field.type());
        for (int i = 0; i < 4; i++) {
            writeUnsignedByte(output, 0);
        }
        writeUnsignedByte(output, field.length());
        writeUnsignedByte(output, field.decimalCount());
        for (int i = 0; i < 14; i++) {
            writeUnsignedByte(output, 0);
        }
    }

    private static void writeFieldValue(OutputStream output, FieldDescriptor field, String value, Charset charset, MemoWriter memoWriter) throws IOException {
        String normalized = field.type() == 'M'
            ? (memoWriter != null ? memoWriter.store(value == null ? "" : value) : "")
            : normalizeForStorage(field, value);
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

    private static String normalizeForStorage(FieldDescriptor field, String value) {
        String safeValue = value == null ? "" : value;
        return switch (field.type()) {
            case 'D' -> safeValue.trim().isEmpty() ? "" : normalizeDateForStorage(safeValue.trim());
            case 'L' -> normalizeLogicalForStorage(safeValue.trim());
            case 'M' -> safeValue;
            case 'N', 'F' -> formatNumericForStorage(field, safeValue.trim());
            default -> safeValue;
        };
    }

    private static boolean hasMemoFields(DBFFile dbf) {
        for (FieldDescriptor field : dbf.fields()) {
            if (field.type() == 'M') {
                return true;
            }
        }
        return false;
    }

    private static Path memoPathFor(Path dbfPath) {
        String fileName = dbfPath.getFileName() != null ? dbfPath.getFileName().toString() : "memo";
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return dbfPath.resolveSibling(baseName + ".DBT");
    }

    private static final class MemoReader {
        private final byte[] data;
        private final Charset charset;

        private MemoReader(byte[] data, Charset charset) {
            this.data = data;
            this.charset = charset;
        }

        static MemoReader open(Path dbfPath, Charset charset) throws IOException {
            Path memoPath = memoPathFor(dbfPath);
            if (!Files.exists(memoPath)) {
                return null;
            }
            return new MemoReader(Files.readAllBytes(memoPath), charset);
        }

        String read(String pointerText) {
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

        private int memoEnd(byte[] data, int offset, int length) {
            for (int i = 0; i < length; i++) {
                if (data[offset + i] == 0x1A) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static final class MemoWriter {
        private final OutputStream output;
        private final Charset charset;
        private int nextBlock = 1;

        private MemoWriter(Path path, Charset charset) throws IOException {
            this.output = new BufferedOutputStream(Files.newOutputStream(path));
            this.charset = charset;
            byte[] header = new byte[DBT_BLOCK_SIZE];
            writeLittleEndianInt(header, 0, nextBlock);
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

            int blocks = Math.max(1, (terminated.length + DBT_BLOCK_SIZE - 1) / DBT_BLOCK_SIZE);
            byte[] padded = new byte[blocks * DBT_BLOCK_SIZE];
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

    private static void writeLittleEndianInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static String normalizeDateForStorage(String value) {
        if (value.length() == 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            value = value.substring(0, 4) + value.substring(5, 7) + value.substring(8, 10);
        }
        if (value.length() != 8) {
            return null;
        }

        try {
            LocalDate.parse(value, DBF_DATE);
            return value;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normalizeLogicalForStorage(String value) {
        if (value.isEmpty()) {
            return "";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "TRUE", "T", "Y" -> "T";
            case "FALSE", "F", "N" -> "F";
            default -> null;
        };
    }

    private static String formatNumericForStorage(FieldDescriptor field, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            BigDecimal number = new BigDecimal(value.replace(',', '.'));
            if (field.decimalCount() == 0) {
                number = number.setScale(0, RoundingMode.UNNECESSARY);
            } else {
                number = number.setScale(field.decimalCount(), RoundingMode.UNNECESSARY);
            }
            return number.toPlainString();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static String decodeZeroTerminated(byte[] bytes, Charset charset) {
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, charset);
    }

    private static String trimRight(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static int readUnsignedByte(RandomAccessFile file) throws IOException {
        int value = file.read();
        if (value == -1) {
            throw new EOFException("Váratlan fájlvége.");
        }
        return value;
    }

    private static int readLittleEndianShort(RandomAccessFile file) throws IOException {
        int b1 = readUnsignedByte(file);
        int b2 = readUnsignedByte(file);
        return b1 | (b2 << 8);
    }

    private static int readLittleEndianInt(RandomAccessFile file) throws IOException {
        int b1 = readUnsignedByte(file);
        int b2 = readUnsignedByte(file);
        int b3 = readUnsignedByte(file);
        int b4 = readUnsignedByte(file);
        return b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
    }

    private static void writeUnsignedByte(OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
    }

    private static void writeLittleEndianShort(OutputStream output, int value) throws IOException {
        writeUnsignedByte(output, value);
        writeUnsignedByte(output, value >>> 8);
    }

    private static void writeLittleEndianInt(OutputStream output, int value) throws IOException {
        writeUnsignedByte(output, value);
        writeUnsignedByte(output, value >>> 8);
        writeUnsignedByte(output, value >>> 16);
        writeUnsignedByte(output, value >>> 24);
    }

    public record FieldDescriptor(String name, char type, int length, int decimalCount) {
    }

    public record DBFFile(
        int version,
        LocalDate lastUpdate,
        long recordCount,
        int headerLength,
        int recordLength,
        List<FieldDescriptor> fields,
        List<List<String>> records
    ) {
    }
}
