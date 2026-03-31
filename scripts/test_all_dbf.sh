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

dbf_files=(./*.DBF)
if [[ ! -e "${dbf_files[1]}" ]]; then
  echo "Nem talalhato DBF fajl a mappaban."
  exit 1
fi

success_count=0
failure_count=0

for file in "${dbf_files[@]}"; do
  printf '%s: ' "${file#./}"
  if java -cp "$BUILD_DIR" com.vd.dbfeditor.test.DBFReadSmokeTest "$file" >/tmp/dbf_test_stdout.txt 2>/tmp/dbf_test_stderr.txt; then
    echo "OK"
    ((success_count+=1))
  else
    echo "HIBA"
    cat /tmp/dbf_test_stderr.txt
    ((failure_count+=1))
  fi
done

echo
echo "Osszesites: sikeres=${success_count}, hibas=${failure_count}"

if [[ $failure_count -gt 0 ]]; then
  exit 1
fi
