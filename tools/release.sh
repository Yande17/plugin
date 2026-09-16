#!/usr/bin/env bash
# Rilis JAR yang bisa langsung dipakai: W2NSMP-<versi>.jar di root repo.
#
# Urutan (gagal di langkah mana pun = rilis dibatalkan):
#   1. bangkitkan stub API + kompilasi SELURUH proyek dengan ECJ
#   2. uji logika Java: kurva level/profil XP, anotasi listener, buff bawaan terkompilasi,
#      peringkat (/skill top) & kunci slot menu progres
#   3. verifikasi linkage bytecode (kelas tersentuh <-> kelas asli)
#   4. verifikasi konsistensi config/messages/plugin.yml/gui dengan kode
#   5. kemas JAR (class asli dipertahankan, hanya keluarga tersentuh yang diganti)
#   6. verifikasi ISI jar rilis byte-per-byte (gerbang terakhir; gagal = jar dihapus)
#
# Pemakaian: bash tools/release.sh [versi]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-1.4.0}"
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
  me/w2n/w2nsmp/gui/SkillProgressMenu
  me/w2n/w2nsmp/gui/SkillProgressMenuHolder
  me/w2n/w2nsmp/listener/SkillListener
  me/w2n/w2nsmp/listener/SkillFishingListener
  me/w2n/w2nsmp/listener/SkillGuiListener
  me/w2n/w2nsmp/skill/SkillType
  me/w2n/w2nsmp/skill/BuffKind
  me/w2n/w2nsmp/skill/SkillBuff
  me/w2n/w2nsmp/skill/SkillTop
  me/w2n/w2nsmp/skill/SkillCurve
  me/w2n/w2nsmp/skill/SkillProfile
  me/w2n/w2nsmp/skill/SkillSettings
  me/w2n/w2nsmp/skill/SkillApiProbe
  me/w2n/w2nsmp/skill/SkillStorage
  me/w2n/w2nsmp/skill/SkillService
  me/w2n/w2nsmp/skill/SkillInfo
  me/w2n/w2nsmp/skill/SkillMenuSlots
  me/w2n/w2nsmp/skill/SkillDiagnostics
  # v1.4.0 - jalur progres skill, gear history, custom fishing, rod, kategori /setting:
  me/w2n/w2nsmp/skill/SkillPath
  me/w2n/w2nsmp/gui/SkillPathMenu
  me/w2n/w2nsmp/gui/SkillPathMenuHolder
  me/w2n/w2nsmp/utility/ItemTags
  me/w2n/w2nsmp/gear/GearKeys
  me/w2n/w2nsmp/gear/GearPerk
  me/w2n/w2nsmp/gear/GearClass
  me/w2n/w2nsmp/gear/GearService
  me/w2n/w2nsmp/listener/GearListener
  me/w2n/w2nsmp/fishing/FishingKeys
  me/w2n/w2nsmp/fishing/FishRarity
  me/w2n/w2nsmp/fishing/CustomFish
  me/w2n/w2nsmp/fishing/FishingItem
  me/w2n/w2nsmp/fishing/FishingService
  me/w2n/w2nsmp/listener/FishingListener
  me/w2n/w2nsmp/gui/RodMenu
  me/w2n/w2nsmp/gui/RodMenuHolder
  me/w2n/w2nsmp/command/RodCommand
  me/w2n/w2nsmp/listener/RodGuiListener
  me/w2n/w2nsmp/gui/SettingsMenu
  me/w2n/w2nsmp/gui/SettingsMenuHolder
  me/w2n/w2nsmp/listener/SettingsGuiListener
  me/w2n/w2nsmp/sell/SellManager
)

echo "== [1/6] stub + kompilasi =="
bash tools/build.sh

echo "== [2/6] uji runtime: logika murni + handler listener =="
rm -rf tools/work/selftest-classes && mkdir -p tools/work/selftest-classes

# 2a. logika murni Java (kurva level, profil XP, kunci skill) - tanpa Bukkit
if [ -f tools/selftest/SkillSelfTest.java ]; then
  "$JAVA" -jar "$ECJ_JAR" -25 -nowarn -encoding UTF-8 -proc:none -d tools/work/selftest-classes \
    src/main/java/me/w2n/w2nsmp/skill/SkillCurve.java \
    src/main/java/me/w2n/w2nsmp/skill/SkillProfile.java \
    src/main/java/me/w2n/w2nsmp/skill/SkillType.java \
    src/main/java/me/w2n/w2nsmp/skill/BuffKind.java \
    src/main/java/me/w2n/w2nsmp/skill/SkillBuff.java \
    tools/selftest/SkillSelfTest.java
  "$JAVA" -cp tools/work/selftest-classes SkillSelfTest | tail -3
else
  echo "  (tools/selftest/SkillSelfTest.java tidak ada - dilewati)"
fi

# 2b. handler listener harus TERLIHAT oleh refleksi, persis seperti cara Bukkit menemukannya.
# Tanpa ini, listener terdaftar tapi tuli (bug v1.2.0: stub @EventHandler kurang @Retention(RUNTIME)).
if [ -f tools/selftest/ListenerAnnotationTest.java ]; then
  "$JAVA" -jar "$ECJ_JAR" -25 -nowarn -encoding UTF-8 -proc:none \
    -cp tools/work/stubs-classes:tools/work/classes -d tools/work/selftest-classes \
    tools/selftest/ListenerAnnotationTest.java
  "$JAVA" -cp tools/work/stubs-classes:tools/work/classes:tools/work/selftest-classes \
    ListenerAnnotationTest | grep -E "handler [0-9]+:|HASIL|GAGAL" | tail -16
else
  echo "  (tools/selftest/ListenerAnnotationTest.java tidak ada - dilewati)"
fi

# 2c. buff bawaan yang TERKOMPILASI harus waras: beberapa buff per skill, milestone bertahap,
# angka tetap "awal game" di level 50, dan tidak ada pemain kebal karena penumpukan reduksi.
if [ -f tools/selftest/BuffDefaultsTest.java ]; then
  "$JAVA" -jar "$ECJ_JAR" -25 -nowarn -encoding UTF-8 -proc:none \
    -cp tools/work/stubs-classes:tools/work/classes -d tools/work/selftest-classes \
    tools/selftest/BuffDefaultsTest.java
  "$JAVA" -cp tools/work/stubs-classes:tools/work/classes:tools/work/selftest-classes \
    BuffDefaultsTest | grep -E "GAGAL|HASIL|total pengurangan|setiap skill" | tail -12
else
  echo "  (tools/selftest/BuffDefaultsTest.java tidak ada - dilewati)"
fi

# 2d. peringkat (/skill top) & kunci slot menu progres: urutan, pemecah seri, paging, penguraian kunci.
# Dipakai class hasil kompilasi (sama dengan yang dikemas), stub Bukkit hanya agar kelas termuat.
if [ -f tools/selftest/SkillTopTest.java ]; then
  "$JAVA" -jar "$ECJ_JAR" -25 -nowarn -encoding UTF-8 -proc:none \
    -cp tools/work/stubs-classes:tools/work/classes -d tools/work/selftest-classes \
    tools/selftest/SkillTopTest.java
  "$JAVA" -cp tools/work/stubs-classes:tools/work/classes:tools/work/selftest-classes \
    SkillTopTest | grep -E "GAGAL|HASIL|MAX_LIMIT|seri level|offset" | tail -12
else
  echo "  (tools/selftest/SkillTopTest.java tidak ada - dilewati)"
fi

# 2e. v1.4.0: matematika jalur progres (snake path) + rarity & syarat lingkungan ikan custom.
if [ -f tools/selftest/SkillPathTest.java ]; then
  "$JAVA" -jar "$ECJ_JAR" -25 -nowarn -encoding UTF-8 -proc:none \
    -cp tools/work/stubs-classes:tools/work/classes -d tools/work/selftest-classes \
    tools/selftest/SkillPathTest.java
  "$JAVA" -cp tools/work/stubs-classes:tools/work/classes:tools/work/selftest-classes \
    SkillPathTest | grep -E "GAGAL|HASIL" | tail -4
else
  echo "  (tools/selftest/SkillPathTest.java tidak ada - dilewati)"
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
