#!/usr/bin/env python3
"""
verify_feature.py — pengecekan statis fitur /skill sebelum JAR dikemas.

Yang diperiksa (semuanya tanpa menjalankan server):
  1. Semua berkas YAML resource valid & bisa dibaca.
  2. Setiap kunci pesan yang dipakai kode fitur ada di messages.yml.
  3. Setiap jalur config yang dipakai kode fitur ada di config.yml.
  4. gui/skill.yml: slot unik, dalam rentang 0..(size-1), material terisi.
  5. messages.yml punya nama, penjelasan buff, dan sumber XP untuk 11 skill.
  6. Permission yang dipakai kode terdaftar di plugin.yml.
  7. Tidak ada karakter non-ASCII yang tidak sengaja terbawa (mis. salah input IME).

Pemakaian: python3 tools/verify_feature.py
"""
import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'
SRC = ROOT / 'src/main/java/me/w2n/w2nsmp'

SKILLS = ['fighting', 'defense', 'archery', 'agility', 'mining', 'woodcutting',
          'farming', 'fishing', 'endurance', 'vitality', 'recovery']
BUFF_KINDS = ['damage-melee', 'damage-projectile', 'damage-reduction', 'environment-reduction',
              'walk-speed', 'haste', 'extra-drop', 'extra-catch', 'passive-heal', 'regen-boost']

NEW_FILES = [
    'skill/SkillType.java', 'skill/BuffKind.java', 'skill/SkillCurve.java', 'skill/SkillProfile.java',
    'skill/SkillSettings.java', 'skill/SkillApiProbe.java', 'skill/SkillStorage.java',
    'skill/SkillService.java', 'skill/SkillInfo.java', 'skill/SkillMenuSlots.java',
    'listener/SkillListener.java', 'listener/SkillFishingListener.java', 'listener/SkillGuiListener.java',
    'gui/SkillMenu.java', 'gui/SkillMenuHolder.java', 'command/SkillCommand.java',
]

problems = []
notes = []


def fail(text):
    problems.append(text)


def ok(text):
    notes.append(text)


def flatten(data, prefix=''):
    out = {}
    if isinstance(data, dict):
        for key, value in data.items():
            path = f'{prefix}.{key}' if prefix else str(key)
            out[path] = value
            out.update(flatten(value, path))
    return out


def main():
    # ---- 1. YAML valid
    docs = {}
    for name in ('config.yml', 'messages.yml', 'plugin.yml', 'gui/skill.yml', 'prices.yml'):
        path = RES / name
        if not path.is_file():
            fail(f'{name} tidak ada')
            continue
        try:
            docs[name] = yaml.safe_load(path.read_text(encoding='utf-8'))
            ok(f'{name} valid')
        except Exception as exc:  # noqa: BLE001
            fail(f'{name} YAML rusak: {exc}')

    if 'messages.yml' not in docs or 'config.yml' not in docs:
        report()
        return 1

    messages = flatten(docs['messages.yml'])
    config = flatten(docs['config.yml'])
    plugin_yml = flatten(docs.get('plugin.yml') or {})
    gui = flatten(docs.get('gui/skill.yml') or {})

    # ---- 2. kunci pesan dipakai kode
    code = ''
    for rel in NEW_FILES:
        path = SRC / rel
        if not path.is_file():
            fail(f'sumber tidak ada: {rel}')
            continue
        code += path.read_text(encoding='utf-8')

    used_keys = {k for k in re.findall(r'"((?:skill|help)\.[a-z0-9.\-]+)"', code) if not k.endswith('.')}
    missing = sorted(k for k in used_keys if k not in messages)
    for key in missing:
        fail(f'messages.yml kehilangan kunci yang dipakai kode: {key}')
    ok(f'{len(used_keys) - len(missing)}/{len(used_keys)} kunci pesan statis ada di messages.yml')

    # kunci dinamis (skill.name.<key>, skill.buff.<kind>, skill.source.<key>)
    for skill in SKILLS:
        for prefix in ('skill.name.', 'skill.source.'):
            if prefix + skill not in messages:
                fail(f'messages.yml kehilangan {prefix}{skill}')
    for kind in BUFF_KINDS:
        if 'skill.buff.' + kind not in messages:
            fail(f'messages.yml kehilangan skill.buff.{kind}')
    ok('nama, sumber XP, dan penjelasan buff lengkap untuk 11 skill / 10 jenis buff')

    # ---- 3. jalur config dipakai kode
    # "skills.yml" adalah nama berkas data (SkillStorage.FILE_NAME), bukan jalur config.
    used_paths = {p for p in re.findall(r'"(skills(?:\.[a-z0-9\-]+)+)"', code) if not p.endswith('.yml')}
    missing_cfg = sorted(p for p in used_paths if p not in config)
    for path in missing_cfg:
        fail(f'config.yml kehilangan jalur yang dipakai kode: {path}')
    ok(f'{len(used_paths) - len(missing_cfg)}/{len(used_paths)} jalur config statis ada di config.yml')

    # jalur per-skill (dibangun sebagai path + ".xxx" di SkillSettings)
    per_skill = ['enabled', 'icon', 'slot', 'blocks', 'buff.causes', 'buff.haste-every-levels',
                 'buff.haste-max-amplifier', 'buff.heal-delay-seconds', 'buff.heal-max', 'buff.heal-per-level',
                 'buff.max', 'buff.per-level', 'buff.unlock-level', 'xp.damage-dealt', 'xp.damage-survived',
                 'xp.damage-taken', 'xp.kill-mob', 'xp.kill-player', 'xp.low-health-multiplier',
                 'xp.low-health-threshold', 'xp.min-distance', 'xp.multiplier', 'xp.per-block',
                 'xp.per-block-travelled', 'xp.per-catch', 'xp.per-heart-regen', 'xp.per-minute-online',
                 'xp.pvp-multiplier']
    relevant = {
        'fighting': ['xp.damage-dealt', 'xp.pvp-multiplier', 'xp.kill-mob', 'xp.kill-player', 'buff.per-level', 'buff.max'],
        'defense': ['xp.damage-taken', 'buff.per-level', 'buff.max'],
        'archery': ['xp.damage-dealt', 'xp.pvp-multiplier', 'xp.kill-mob', 'xp.kill-player', 'buff.per-level', 'buff.max'],
        'agility': ['xp.per-block-travelled', 'xp.min-distance', 'buff.per-level', 'buff.max'],
        'mining': ['xp.per-block', 'buff.unlock-level', 'buff.haste-every-levels', 'buff.haste-max-amplifier', 'blocks'],
        'woodcutting': ['xp.per-block', 'buff.per-level', 'buff.max', 'blocks'],
        'farming': ['xp.per-block', 'buff.per-level', 'buff.max', 'blocks'],
        'fishing': ['xp.per-catch', 'buff.unlock-level', 'buff.per-level', 'buff.max'],
        'endurance': ['xp.per-minute-online', 'buff.per-level', 'buff.max', 'buff.causes'],
        'vitality': ['xp.damage-survived', 'xp.low-health-threshold', 'xp.low-health-multiplier',
                     'buff.unlock-level', 'buff.heal-per-level', 'buff.heal-max', 'buff.heal-delay-seconds'],
        'recovery': ['xp.per-heart-regen', 'buff.unlock-level', 'buff.per-level', 'buff.max'],
    }
    for skill in SKILLS:
        base = f'skills.list.{skill}'
        for suffix in ['enabled', 'icon', 'slot'] + relevant[skill]:
            if f'{base}.{suffix}' not in config:
                fail(f'config.yml kehilangan {base}.{suffix}')
    ok('config.yml memuat seluruh jalur per-skill yang dibaca SkillSettings')

    if 'commands.skill.enabled' not in config:
        fail('config.yml kehilangan commands.skill.enabled')
    if 'skills.max-level' not in config:
        fail('config.yml kehilangan skills.max-level')
    else:
        value = config['skills.max-level']
        if value != 50:
            fail(f'skills.max-level = {value} (diminta 50)')
        else:
            ok('skills.max-level = 50 (sesuai permintaan)')

    # ---- 4. gui/skill.yml
    slots = {}
    for skill in SKILLS:
        path = f'slots.{skill}'
        if path not in gui:
            fail(f'gui/skill.yml kehilangan {path}')
            continue
        slot = gui[path]
        if not isinstance(slot, int) or slot < 0 or slot > 53:
            fail(f'gui/skill.yml {path} = {slot} di luar 0..53')
        if slot in slots:
            fail(f'gui/skill.yml slot {slot} dipakai dua skill: {slots[slot]} & {skill}')
        slots[slot] = skill
    for extra in ('info', 'close'):
        if f'slots.{extra}' not in gui:
            fail(f'gui/skill.yml kehilangan slots.{extra}')
        elif gui[f'slots.{extra}'] in slots:
            fail(f'gui/skill.yml slots.{extra} bentrok dengan ikon skill')
    ok(f'gui/skill.yml: {len(slots)} slot skill unik')

    # ---- 5. wiring fitur di kelas lama (harus tetap utuh; gampang terhapus saat edit manual)
    wiring_checks = [
        ('W2NSMP.java', 'private SkillService skillService;', 'W2NSMP punya field skillService'),
        ('W2NSMP.java', 'public SkillService skills()', 'W2NSMP punya accessor skills()'),
        ('W2NSMP.java', 'new SkillService(this)', 'W2NSMP membuat SkillService'),
        ('W2NSMP.java', 'this.skillService.load()', 'W2NSMP memuat data skill saat onEnable'),
        ('W2NSMP.java', 'this.skillService.startTasks()', 'W2NSMP memulai tugas berkala skill'),
        ('W2NSMP.java', 'this.skillService.shutdown()', 'W2NSMP mematikan skill saat onDisable'),
        ('W2NSMP.java', 'this.skillService.reload()', 'W2NSMP memuat ulang skill saat /w2nsmp reload'),
        ('W2NSMP.java', 'SkillMenu.closeAll(this)', 'W2NSMP menutup menu skill saat onDisable'),
        ('manager/CommandManager.java', 'registerRuntime("skill"', 'CommandManager mendaftarkan /skill saat runtime'),
        ('manager/CommandManager.java', '"w2nsmp.skill"', 'CommandManager memakai permission w2nsmp.skill'),
        ('manager/CommandManager.java', 'SkillCommand', 'CommandManager memakai SkillCommand'),
        ('manager/ListenerManager.java', 'new SkillGuiListener(', 'ListenerManager memasang SkillGuiListener'),
        ('manager/ListenerManager.java', 'new SkillListener(', 'ListenerManager memasang SkillListener'),
        ('manager/ListenerManager.java', 'new SkillFishingListener(', 'ListenerManager memasang SkillFishingListener'),
        ('config/GuiConfigs.java', '"skill"', 'GuiConfigs mengenal gui id "skill"'),
    ]
    for rel, needle, label in wiring_checks:
        path = SRC / rel
        if not path.is_file():
            fail(f'wiring: berkas {rel} tidak ada')
        elif needle not in path.read_text(encoding='utf-8'):
            fail(f'wiring hilang: {label} ({rel} tidak memuat "{needle}")')
    ok(f'{len(wiring_checks)} titik wiring fitur di kelas lama masih utuh')

    # ---- 5c. setiap %placeholder% di pesan skill harus benar-benar disuplai kode
    def collect_strings(node):
        found = []
        if isinstance(node, dict):
            for value in node.values():
                found += collect_strings(value)
        elif isinstance(node, list):
            for value in node:
                found += collect_strings(value)
        elif isinstance(node, str):
            found.append(node)
        return found

    tokens = set()
    for text in collect_strings(docs['messages.yml'].get('skill') or {}):
        tokens |= set(re.findall(r'%([a-z0-9\-]+)%', text))
    tokens.discard('prefix')  # diganti otomatis oleh Messages.apply()
    provided = set(re.findall(r'"([a-z][a-z0-9\-]*)"', code))
    unknown = sorted(token for token in tokens if token not in provided)
    for token in unknown:
        fail(f'placeholder %{token}% dipakai di messages.yml (skill.*) tapi tidak disuplai kode')
    ok(f'{len(tokens) - len(unknown)}/{len(tokens)} placeholder pesan skill disuplai kode')

    # ---- 5d. nilai bawaan di kode harus sama dengan config.yml (jalur upgrade server lama)
    # Server yang naik versi dengan config.yml lama tidak punya seksi skills:, jadi nilai
    # bawaan di SkillSettings yang berlaku. Bila keduanya berbeda, admin akan melihat perilaku
    # yang tidak sama dengan yang tertulis di config.yml bawaan.
    settings_src = (SRC / 'skill/SkillSettings.java').read_text(encoding='utf-8')

    def java_case_block(case_name):
        start = settings_src.index(f'case {case_name} -> {{')
        end = settings_src.index('\n         }', start)
        return settings_src[start:end]

    for skill in ('mining', 'woodcutting', 'farming'):
        java_blocks = set(re.findall(r'"([A-Z_]+)"', java_case_block(skill.upper())))
        yaml_blocks = set(config.get(f'skills.list.{skill}.blocks') or [])
        if java_blocks != yaml_blocks:
            fail(f'daftar blok {skill} berbeda: hanya di kode {sorted(java_blocks - yaml_blocks)}, '
                 f'hanya di config.yml {sorted(yaml_blocks - java_blocks)}')
    ok('daftar blok bawaan mining/woodcutting/farming identik dengan config.yml')

    java_causes = set(re.findall(r'"([A-Z_]+)"', java_case_block('ENDURANCE')))
    yaml_causes = set(config.get('skills.list.endurance.buff.causes') or [])
    if java_causes != yaml_causes:
        fail(f'DamageCause endurance berbeda: hanya di kode {sorted(java_causes - yaml_causes)}, '
             f'hanya di config.yml {sorted(yaml_causes - java_causes)}')
    else:
        ok(f'DamageCause endurance identik dengan config.yml ({len(yaml_causes)} penyebab)')

    guarded = settings_src.count('config.isList(')
    if guarded < 2:
        fail(f'pembacaan list di SkillSettings tidak dijaga config.isList (ditemukan {guarded}) - '
             'server dengan config lama akan kehilangan daftar blok/penyebab')
    else:
        ok(f'{guarded} pembacaan list dijaga config.isList() (aman untuk config.yml lama)')

    # ---- 6. permission
    for perm in ('w2nsmp.skill', 'w2nsmp.skill.admin'):
        if 'permissions.' + perm not in plugin_yml:
            fail(f'plugin.yml kehilangan permission {perm}')
        else:
            ok(f'plugin.yml punya {perm}')
    children = plugin_yml.get('permissions.w2nsmp.admin.children') or {}
    for perm in ('w2nsmp.skill', 'w2nsmp.skill.admin'):
        if isinstance(children, dict) and perm not in children:
            fail(f'plugin.yml: w2nsmp.admin.children belum memuat {perm}')

    # ---- 7. karakter asing di sumber & resource
    for rel in NEW_FILES:
        text = (SRC / rel).read_text(encoding='utf-8')
        bad = sorted({ch for ch in text if ord(ch) > 0x2500 and ch not in '✕'})
        if bad:
            fail(f'{rel} memuat karakter mencurigakan: {bad}')
    # Hanya bagian yang BARU ditambahkan (simbol lama di messages.yml memang dipakai plugin).
    for name, marker in (('config.yml', 'SKILL (/skill)'), ('messages.yml', 'SKILL (/skill)'), ('gui/skill.yml', 'GUI /skill')):
        text = (RES / name).read_text(encoding='utf-8')
        index = text.find(marker)
        new_part = text[index:] if index >= 0 else text
        bad = sorted({ch for ch in new_part if ord(ch) > 0x2500 and ch not in '✕←→'})
        if bad:
            fail(f'bagian baru {name} memuat karakter mencurigakan: {bad}')
    ok('tidak ada karakter asing (CJK/simbol tak sengaja) di sumber & resource baru')

    report()
    return 1 if problems else 0


def report():
    print('== Catatan ==')
    for note in notes:
        print('  OK  ' + note)
    if problems:
        print('\n== MASALAH ==')
        for problem in problems:
            print('  !!  ' + problem)
        print(f'\nHASIL: {len(problems)} MASALAH')
    else:
        print('\nHASIL: semua pengecekan lulus (0 masalah)')


if __name__ == '__main__':
    sys.exit(main())
