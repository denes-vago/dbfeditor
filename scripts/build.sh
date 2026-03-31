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
VERSION=$(sed -n 's/.*VERSION = "\\(.*\\)".*/\\1/p' src/com/vd/dbfeditor/app/AppVersion.java)
MANIFEST_FILE="$BUILD_DIR/MANIFEST.MF"

echo "Starting compilation..."
sources=($(find src -name '*.java' -type f | sort))
javac -encoding UTF-8 -d build "${sources[@]}"
mkdir -p build/com/vd/dbfeditor/i18n
cp src/com/vd/dbfeditor/i18n/*.properties build/com/vd/dbfeditor/i18n/
cat > "$MANIFEST_FILE" <<EOF
Manifest-Version: 1.0
Main-Class: com.vd.dbfeditor.app.DBFEditorUI
Implementation-Version: $VERSION

EOF

echo "Creating runnable JAR..."
jar --create --file "$JAR_PATH" --manifest "$MANIFEST_FILE" -C "$BUILD_DIR" .

echo "Compilation succeeded, created: $JAR_PATH"
echo "Starting application..."
exec java -jar "$JAR_PATH" "${FILE_ARGS[@]}"
