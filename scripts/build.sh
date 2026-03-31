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
VERSION=$(sed -n 's/.*VERSION = "\(.*\)".*/\1/p' src/com/vd/dbfeditor/app/AppVersion.java)
MAVEN_CMD=()

if [[ -x "$PROJECT_DIR/mvnw" ]]; then
  MAVEN_CMD=("$PROJECT_DIR/mvnw")
elif command -v mvn >/dev/null 2>&1; then
  MAVEN_CMD=("mvn")
else
  echo "Maven is required to build this project."
  echo "Install Maven or add a Maven wrapper to the repository."
  exit 1
fi

echo "Starting Maven build..."
rm -rf target
"${MAVEN_CMD[@]}" -q -Drevision="$VERSION" clean package
cp "$PROJECT_DIR/target/dbfeditor.jar" "$JAR_PATH"

echo "Compilation succeeded, created: $JAR_PATH"
echo "Starting application..."
exec java -jar "$JAR_PATH" "${FILE_ARGS[@]}"
