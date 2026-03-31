# DBF Editor

A desktop Java application for opening, editing, and saving classic DBF files with a Swing user interface.

The project currently focuses on:

- reading and writing DBF files safely
- editing records and table structure
- working with multiple open files in tabs
- localized UI strings
- lightweight smoke and unit tests for the DBF engine

## Project Structure

```text
src/com/vd/dbfeditor/
  DBFEditorUI.java          Swing application
  DBFEngine.java            DBF read/write/validation engine
  Localization.java         Language bundle loader
  i18n/                     UI translations
  test/                     Test classes

build.sh                    Build + run entry point
scripts/build.sh            Actual build/run script used by the wrapper
dbfeditor.jar               Generated runnable artifact after build
```

## Requirements

- macOS or another system with Java installed
- a JDK that provides `javac`
- `zsh` for the provided build script

## Build And Run

From the project root:

```bash
./build.sh
```

To open one or more DBF files immediately on startup:

```bash
./build.sh file1.dbf file2.dbf
```

The script:

1. compiles the Java sources into `build/`
2. copies the localization files
3. creates a runnable `dbfeditor.jar`
4. starts the Swing application from the JAR

## Manual Compilation

If you want to compile manually:

```bash
javac -encoding UTF-8 -d build $(find src -name '*.java' -type f | sort)
mkdir -p build/com/vd/dbfeditor/i18n
cp src/com/vd/dbfeditor/i18n/*.properties build/com/vd/dbfeditor/i18n/
jar --create --file dbfeditor.jar --main-class com.vd.dbfeditor.DBFEditorUI -C build .
```

Run the UI manually from the generated JAR:

```bash
java -jar dbfeditor.jar
```

You can also pass one or more DBF files on startup:

```bash
java -jar dbfeditor.jar file1.dbf file2.dbf
```

## Tests

The repository includes small executable test classes under `com.vd.dbfeditor.test`.

Compile first:

```bash
javac -encoding UTF-8 -d build $(find src -name '*.java' -type f | sort)
mkdir -p build/com/vd/dbfeditor/i18n
cp src/com/vd/dbfeditor/i18n/*.properties build/com/vd/dbfeditor/i18n/
```

Run the unit tests:

```bash
java -cp build com.vd.dbfeditor.test.DBFEngineUnitTest
```

Run a DBF read smoke test for a specific file:

```bash
java -cp build com.vd.dbfeditor.test.DBFReadSmokeTest path/to/file.dbf
```

Run the DBF write smoke test:

```bash
java -cp build com.vd.dbfeditor.test.DBFWriteSmokeTest
```

## Notes

- DBF and DBT data files are ignored by git.
- `dbfeditor.jar` is generated during the build process and is ignored by git.
- The application default character encoding is `Cp852`.
- The UI supports multiple languages via property files in `src/com/vd/dbfeditor/i18n`.
