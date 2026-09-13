#!/usr/bin/env bash
# ============================================================================
# tools/build.sh — rakit ulang seluruh pipeline build di sandbox ini.
#
# Sandbox tidak punya Maven/javac dan repo Maven diblokir, jadi build memakai:
#   * ECJ (Eclipse Compiler for Java) 3.46 dari wheel PyPI `karellen-jdtls`
#   * JRE 25 dari wheel PyPI `jdk4py`
#   * stub API Bukkit/Paper/Vault yang DIHASILKAN dari constant pool JAR asli
#     (tools/gen_stubs.py) — bukan dependency sungguhan, hanya untuk kompilasi.
#
# Hasil: tools/work/classes (145 class) yang sudah diverifikasi setara dengan
# class asli (tools/work/verify.py) dan aman ditautkan (tools/verify_linkage.py).
#
# Pemakaian:  bash tools/build.sh
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="/home/user/.cache/w2ntools"
J="$CACHE/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"
ECJ="$CACHE/ecj.jar"
ORIG_JAR="$ROOT/W2NSMP-1.0.0.jar"
JARX=/tmp/jarx

cd "$ROOT"

# --- 0. toolchain (unduh ulang bila sandbox di-reset) -----------------------
if [ ! -x "$J" ] || [ ! -f "$ECJ" ]; then
  echo "== menyiapkan toolchain (jdk4py + karellen-jdtls) =="
  mkdir -p "$CACHE"
  [ -x "$CACHE/venv/bin/pip" ] || python3 -m venv "$CACHE/venv"
  "$CACHE/venv/bin/pip" install -q --disable-pip-version-check jdk4py karellen-jdtls
  cp "$CACHE"/venv/lib/karellen-jdtls-kotlin/plugins/org.eclipse.jdt.core.compiler.batch_*.jar "$ECJ"
fi
"$J" -jar "$ECJ" -version

# --- 1. ekstrak JAR asli (sumber kebenaran API) -----------------------------
echo "== ekstrak JAR asli ke $JARX =="
rm -rf "$JARX" && mkdir -p "$JARX"
python3 -c "import zipfile;zipfile.ZipFile('$ORIG_JAR').extractall('$JARX')"

# --- 2. bangkitkan stub API -------------------------------------------------
echo "== bangkitkan stub API =="
rm -rf tools/stubs/generated/src && mkdir -p tools/stubs/generated/src
python3 tools/gen_stubs.py "$JARX" tools/stubs/generated/src

# --- 3. kompilasi stub lalu seluruh sumber plugin ---------------------------
mkdir -p tools/work/stubs-classes tools/work/classes
rm -rf tools/work/stubs-classes/* tools/work/classes/*
find tools/stubs/generated/src -name '*.java' | sort > tools/work/stubfiles.txt
find src/main/java -name '*.java' | sort > tools/work/srcfiles.txt

echo "== kompilasi $(wc -l < tools/work/stubfiles.txt) file stub =="
"$J" -jar "$ECJ" -25 -nowarn -encoding UTF-8 -proc:none \
  -d tools/work/stubs-classes @tools/work/stubfiles.txt

echo "== kompilasi $(wc -l < tools/work/srcfiles.txt) file sumber plugin =="
"$J" -jar "$ECJ" -25 -nowarn -encoding UTF-8 -proc:none \
  -cp tools/work/stubs-classes -d tools/work/classes @tools/work/srcfiles.txt

echo "== hasil: $(find tools/work/classes -name '*.class' | wc -l) class =="
