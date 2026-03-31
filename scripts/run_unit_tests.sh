#!/bin/zsh

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
PROJECT_DIR="${SCRIPT_DIR:h}"

cd "$PROJECT_DIR"
mkdir -p build
find build -type f -delete
BUILD_DIR="$PROJECT_DIR/build"

echo "Forditas indul..."
sources=($(find src -name '*.java' -type f | sort))
javac -encoding UTF-8 -d build "${sources[@]}"
mkdir -p build/com/vd/dbfeditor/i18n
cp src/com/vd/dbfeditor/i18n/*.properties build/com/vd/dbfeditor/i18n/

echo "Unit tesztek futasa..."
java -cp "$BUILD_DIR" com.vd.dbfeditor.test.DBFEngineUnitTest
