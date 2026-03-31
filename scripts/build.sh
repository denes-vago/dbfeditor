#!/bin/zsh

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
PROJECT_DIR="${SCRIPT_DIR:h}"
LAUNCH_DIR="$PWD"
JAR_PATH="$PROJECT_DIR/dbfeditor.jar"
typeset -a FILE_ARGS

for arg in "$@"; do
  if [[ -n "$arg" ]]; then
    if [[ "$arg" = /* ]]; then
      FILE_ARGS+=("$arg")
    else
      FILE_ARGS+=("${LAUNCH_DIR}/${arg}")
    fi
  fi
done

cd "$PROJECT_DIR"
mkdir -p build
find build -type f -delete
BUILD_DIR="$PROJECT_DIR/build"

echo "Starting compilation..."
sources=($(find src -name '*.java' -type f | sort))
javac -encoding UTF-8 -d build "${sources[@]}"
mkdir -p build/com/vd/dbfeditor/i18n
cp src/com/vd/dbfeditor/i18n/*.properties build/com/vd/dbfeditor/i18n/

echo "Creating runnable JAR..."
jar --create --file "$JAR_PATH" --main-class com.vd.dbfeditor.DBFEditorUI -C "$BUILD_DIR" .

echo "Compilation succeeded, created: $JAR_PATH"
echo "Starting application..."
exec java -jar "$JAR_PATH" "${FILE_ARGS[@]}"
