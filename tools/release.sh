#!/usr/bin/env bash
# Rilis JAR yang bisa langsung dipakai: W2NSMP-<versi>.jar di root repo.
#
# Urutan (gagal di langkah mana pun = rilis dibatalkan):
#   1. bangkitkan stub API + kompilasi SELURUH proyek dengan ECJ
#   2. uji logika murni Java (kurva level, profil XP, kunci skill)
#   3. verifikasi linkage bytecode (kelas tersentuh <-> kelas asli)
#   4. verifikasi konsistensi config/messages/plugin.yml/gui dengan kode
#   5. kemas JAR (class asli dipertahankan, hanya keluarga tersentuh yang diganti)
#   6. verifikasi ISI jar rilis byte-per-byte (gerbang terakhir; gagal = jar dihapus)
#
# Pemakaian: bash tools/release.sh [versi]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-1.2.0}"
JAVA_HOME="${JAVA_HOME:-/home/user/.cache/w2ntools/venv/lib/python3.11/site-packages/jdk4py/java-runtime}"
ECJ_JAR="${ECJ_JAR:-/home/user/.cache/w2ntools/ecj.jar}"
VENV_PY="${VENV_PY:-/home/user/.cache/w2ntools/venv/bin/python}"
JAVA="$JAVA_HOME/bin/java"

# Keluarga class (kelas luar + inner/lambda-nya) yang diubah/ditambah proyek ini.
# Sisanya diambil apa adanya dari W2NSMP-1.0.0.jar.
TOUCHED=(
  me/w2n/w2nsmp/W2NSMP
  me/w2n/w2nsmp/stats/StatisticsService
  me/w2n/w2nsmp/stats/PlayerStats
  me/w2n/w2nsmp/config/ConfigManager
  me/w2n/w2nsmp/config/GuiConfigs
  me/w2n/w2nsmp/scoreboard/ScoreboardService
  me/w2n/w2nsmp/nametag/NametagService
  me/w2n/w2nsmp/command/TopCommand
  me/w2n/w2nsmp/command/W2NSMPCommand
  me/w2n/w2nsmp/command/SkillCommand
  me/w2n/w2nsmp/manager/CommandManager
  me/w2n/w2nsmp/manager/ListenerManager
  me/w2n/w2nsmp/gui/SkillMenu
  me/w2n/w2nsmp/gui/SkillMenuHolder
  me/w2n/w2nsmp/listener/SkillListener
  me/w2n/w2nsmp/listener/SkillFishingListener
  me/w2n/w2nsmp/listener/SkillGuiListener
  me/w2n/w2nsmp/skill/SkillType
  me/w2n/w2nsmp/skill/BuffKind
  me/w2n/w2nsmp/skill/SkillCurve
  me/w2n/w2nsmp/skill/SkillProfile
  me/w2n/w2nsmp/skill/SkillSettings
  me/w2n/w2nsmp/skill/SkillApiProbe
  me/w2n/w2nsmp/skill/SkillStorage
  me/w2n/w2nsmp/skill/SkillService
  me/w2n/w2nsmp/skill/SkillInfo
  me/w2n/w2nsmp/skill/SkillMenuSlots
)

echo "== [1/6] stub + kompilasi =="
bash tools/build.sh

echo "== [2/6] uji logika murni (kurva level & profil XP) =="
if [ -f tools/selftest/SkillSelfTest.java ]; then
  rm -rf tools/work/selftest-classes && mkdir -p tools/work/selftest-classes
  "$JAVA" -jar "$ECJ_JAR" -25 -nowarn -encoding UTF-8 -proc:none -d tools/work/selftest-classes \
    src/main/java/me/w2n/w2nsmp/skill/SkillCurve.java \
    src/main/java/me/w2n/w2nsmp/skill/SkillProfile.java \
    src/main/java/me/w2n/w2nsmp/skill/SkillType.java \
    src/main/java/me/w2n/w2nsmp/skill/BuffKind.java \
    tools/selftest/SkillSelfTest.java
  "$JAVA" -cp tools/work/selftest-classes SkillSelfTest | tail -4
else
  echo "  (tools/selftest tidak ada - dilewati)"
fi

echo "== [3/6] verifikasi linkage =="
PY=python3
if [ -x "$VENV_PY" ]; then PY="$VENV_PY"; fi
"$PY" tools/verify_linkage.py W2NSMP-1.0.0.jar tools/work/classes "${TOUCHED[@]}"

echo "== [4/6] verifikasi config/messages/gui vs kode =="
if "$PY" -c 'import yaml' 2>/dev/null; then
  "$PY" tools/verify_feature.py
else
  echo "  PyYAML tidak tersedia - lewati (jalankan: pip install pyyaml)"
fi

echo "== [5/6] kemas JAR =="
OUT="W2NSMP-${VERSION}.jar"
rm -f "$OUT"
python3 tools/package_jar.py W2NSMP-1.0.0.jar tools/work/classes "$OUT" "$VERSION" "${TOUCHED[@]}"

echo "== [6/6] verifikasi isi JAR rilis =="
if ! "$PY" tools/verify_jar.py "$OUT" W2NSMP-1.0.0.jar tools/work/classes "${TOUCHED[@]}"; then
  rm -f "$OUT"
  echo "!! JAR dihapus karena tidak lolos verifikasi." >&2
  exit 1
fi

echo
echo "Rilis siap: $OUT"
unzip -l "$OUT" | tail -3
