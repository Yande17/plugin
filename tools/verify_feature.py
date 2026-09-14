#!/usr/bin/env python3
"""
verify_feature.py — pengecekan statis fitur /skill sebelum JAR dikemas.

Yang diperiksa (semuanya tanpa menjalankan server):
  1. Semua berkas YAML resource valid & bisa dibaca.
  2. Setiap kunci pesan yang dipakai kode fitur ada di messages.yml.
  3. Setiap jalur config yang dipakai kode fitur ada di config.yml.
  4. gui/skill.yml: slot unik, dalam rentang 0..(size-1), material terisi (termasuk menu progres).
  5. messages.yml punya nama, penjelasan buff, dan sumber XP untuk 11 skill / 16 jenis buff.
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
BUFF_KINDS = ['damage-melee', 'damage-projectile', 'crit-chance', 'damage-reduction', 'block-chance',
              'environment-reduction', 'walk-speed', 'haste', 'potion', 'extra-drop', 'extra-catch',
              'mob-loot', 'vanilla-xp', 'double-xp', 'passive-heal', 'regen-boost']

NEW_FILES = [
    'skill/SkillType.java', 'skill/BuffKind.java', 'skill/SkillCurve.java', 'skill/SkillProfile.java',
    'skill/SkillSettings.java', 'skill/SkillApiProbe.java', 'skill/SkillStorage.java',
    'skill/SkillService.java', 'skill/SkillInfo.java', 'skill/SkillMenuSlots.java',
    'skill/SkillDiagnostics.java',
    'listener/SkillListener.java', 'listener/SkillFishingListener.java', 'listener/SkillGuiListener.java',
    'gui/SkillMenu.java', 'gui/SkillMenuHolder.java', 'command/SkillCommand.java',
    'skill/SkillBuff.java', 'skill/SkillTop.java',
    'gui/SkillProgressMenu.java', 'gui/SkillProgressMenuHolder.java',
]

# Kunci efek potion yang dipakai buff bawaan (skill.potion-name.<kunci> harus ada).
POTIONS = ['haste', 'slow_falling', 'fire_resistance', 'absorption', 'health_boost']

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
        if 'skill.buff-short.' + kind not in messages:
            fail(f'messages.yml kehilangan skill.buff-short.{kind} (nama pendek untuk lore GUI)')
    for potion in POTIONS:
        if 'skill.potion-name.' + potion not in messages:
            fail(f'messages.yml kehilangan skill.potion-name.{potion}')
    ok(f'nama, sumber XP, penjelasan, dan nama pendek lengkap untuk 11 skill / {len(BUFF_KINDS)} jenis buff')

    # ---- 3. jalur config dipakai kode
    # "skills.yml" adalah nama berkas data (SkillStorage.FILE_NAME), bukan jalur config.
    used_paths = {p for p in re.findall(r'"(skills(?:\.[a-z0-9\-]+)+)"', code) if not p.endswith('.yml')}
    missing_cfg = sorted(p for p in used_paths if p not in config)
    for path in missing_cfg:
        fail(f'config.yml kehilangan jalur yang dipakai kode: {path}')
    ok(f'{len(used_paths) - len(missing_cfg)}/{len(used_paths)} jalur config statis ada di config.yml')

    # jalur per-skill (dibangun sebagai path + ".xxx" di SkillSettings)
    per_skill = ['enabled', 'icon', 'slot', 'blocks',
                 'buffs.1.kind', 'buffs.1.unlock-level',
                 'xp.damage-dealt', 'xp.damage-survived',
                 'xp.damage-taken', 'xp.kill-mob', 'xp.kill-player', 'xp.low-health-multiplier',
                 'xp.low-health-threshold', 'xp.min-distance', 'xp.multiplier', 'xp.per-block',
                 'xp.per-block-travelled', 'xp.per-catch', 'xp.per-heart-regen', 'xp.per-minute-online',
                 'xp.pvp-multiplier']
    relevant = {
        'fighting': ['xp.damage-dealt', 'xp.pvp-multiplier', 'xp.kill-mob', 'xp.kill-player',
                     'buffs.1.per-level', 'buffs.1.max', 'buffs.2.power', 'buffs.3.kind'],
        'defense': ['xp.damage-taken', 'buffs.1.per-level', 'buffs.1.max', 'buffs.2.power', 'buffs.3.unlock-level'],
        'archery': ['xp.damage-dealt', 'xp.pvp-multiplier', 'xp.kill-mob', 'xp.kill-player',
                    'buffs.1.per-level', 'buffs.1.max', 'buffs.2.power'],
        'agility': ['xp.per-block-travelled', 'xp.min-distance', 'buffs.1.per-level', 'buffs.1.max',
                    'buffs.2.causes', 'buffs.3.potion', 'buffs.3.amplifier-max'],
        'mining': ['xp.per-block', 'blocks', 'buffs.1.potion', 'buffs.1.amplifier-every-levels',
                   'buffs.1.amplifier-max', 'buffs.2.kind', 'buffs.3.kind'],
        'woodcutting': ['xp.per-block', 'blocks', 'buffs.1.per-level', 'buffs.2.unlock-level', 'buffs.3.kind'],
        'farming': ['xp.per-block', 'blocks', 'buffs.1.per-level', 'buffs.2.unlock-level', 'buffs.3.kind'],
        'fishing': ['xp.per-catch', 'buffs.1.per-level', 'buffs.2.unlock-level', 'buffs.3.kind'],
        'endurance': ['xp.per-minute-online', 'buffs.1.causes', 'buffs.2.power', 'buffs.3.potion'],
        'vitality': ['xp.damage-survived', 'xp.low-health-threshold', 'xp.low-health-multiplier',
                     'buffs.1.delay-seconds', 'buffs.2.potion', 'buffs.2.amplifier-max', 'buffs.3.kind'],
        'recovery': ['xp.per-heart-regen', 'buffs.1.per-level', 'buffs.2.delay-seconds', 'buffs.3.potion'],
    }
    for skill in SKILLS:
        base = f'skills.list.{skill}'
        for suffix in ['enabled', 'icon', 'slot'] + relevant[skill]:
            if f'{base}.{suffix}' not in config:
                fail(f'config.yml kehilangan {base}.{suffix}')
        # tiap skill harus punya lebih dari satu buff (permintaan v1.3.0)
        buffs = (docs['config.yml'].get('skills', {}).get('list', {}).get(skill, {}) or {}).get('buffs') or {}
        if len(buffs) < 2:
            fail(f'config.yml {base}.buffs hanya punya {len(buffs)} buff - satu skill harus punya beberapa buff')
        for number, buff in buffs.items():
            if not isinstance(buff, dict) or 'kind' not in buff or 'unlock-level' not in buff:
                fail(f'config.yml {base}.buffs.{number} tidak punya kind/unlock-level')
            elif buff['kind'] not in BUFF_KINDS:
                fail(f"config.yml {base}.buffs.{number}.kind = '{buff['kind']}' bukan jenis buff yang dikenal")
    ok('config.yml memuat seluruh jalur per-skill (multi-buff) yang dibaca SkillSettings')

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
    for extra in ('top', 'info', 'close'):
        if f'slots.{extra}' not in gui:
            fail(f'gui/skill.yml kehilangan slots.{extra}')
        elif gui[f'slots.{extra}'] in slots:
            fail(f'gui/skill.yml slots.{extra} bentrok dengan ikon skill')
        else:
            slots[gui[f'slots.{extra}']] = extra
    ok(f'gui/skill.yml: {len(slots)} slot unik (11 skill + top/info/close)')

    # menu progres skill (v1.3.0)
    progress_size = gui.get('progress.size', 54)
    if not isinstance(progress_size, int) or progress_size < 27 or progress_size > 54:
        fail(f'gui/skill.yml progress.size = {progress_size} di luar 27..54')
    progress_slots = {}
    for name, fallback in (('icon', 4), ('buffs', 10), ('next-level', 28), ('next-buff', 30),
                           ('max-level', 32), ('rank', 34), ('back', 45), ('detail', 49), ('close', 53)):
        path = f'progress.slots.{name}'
        if path not in gui:
            fail(f'gui/skill.yml kehilangan {path}')
            continue
        value = gui[path]
        if not isinstance(value, int) or value < 0 or value >= progress_size:
            fail(f'gui/skill.yml {path} = {value} di luar 0..{progress_size - 1}')
        elif value in progress_slots:
            fail(f'gui/skill.yml {path} bentrok dengan {progress_slots[value]}')
        else:
            progress_slots[value] = name
    for path in ('progress.bar-start', 'progress.bar-length', 'progress.bar-filled', 'progress.bar-empty',
                 'progress.buff-active-material', 'progress.buff-locked-material'):
        if path not in gui:
            fail(f'gui/skill.yml kehilangan {path}')
    bar_start = gui.get('progress.bar-start', 0)
    bar_length = gui.get('progress.bar-length', 0)
    if isinstance(bar_start, int) and isinstance(bar_length, int):
        if bar_start < 0 or bar_length < 1 or bar_start + bar_length > progress_size:
            fail(f'gui/skill.yml bar progres {bar_start}+{bar_length} keluar dari inventory ({progress_size})')
        for offset in range(bar_length):
            if bar_start + offset in progress_slots:
                fail(f'gui/skill.yml bar progres menimpa slot {progress_slots[bar_start + offset]}')
    # kartu buff (1-3 buff dipasang mengitari slots.buffs + 3 dengan jarak 2) tidak boleh
    # menimpa bar kemajuan atau kartu milestone
    buff_start = gui.get('progress.slots.buffs')
    other_slots = {slot: name for slot, name in progress_slots.items() if name != 'buffs'}
    if isinstance(buff_start, int):
        for count in (1, 2, 3, 5, 7):
            if count <= 3:
                center = buff_start + 3
                positions = [center - (count - 1 - i) * 2 for i in range(count)]
            else:
                positions = [buff_start + i for i in range(count)]
            for position in positions:
                if not 0 <= position < progress_size:
                    fail(f'gui/skill.yml: kartu buff ke-{count} jatuh di slot {position} (di luar inventory)')
                elif position in other_slots:
                    fail(f'gui/skill.yml: kartu buff ({count} buff) menimpa {other_slots[position]} di slot {position}')
                elif isinstance(bar_start, int) and bar_start <= position < bar_start + bar_length:
                    fail(f'gui/skill.yml: kartu buff ({count} buff) menimpa bar kemajuan di slot {position}')
    ok(f'gui/skill.yml: menu progres punya {len(progress_slots)} slot unik + bar {bar_length} petak '
       '(kartu buff tidak bertabrakan)')

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
        ('manager/ListenerManager.java', 'registerSkillListeners()', 'ListenerManager memanggil pendaftaran listener skill'),
        ('skill/SkillService.java', 'new SkillGuiListener(', 'SkillService membuat SkillGuiListener'),
        ('skill/SkillService.java', 'new SkillListener(', 'SkillService membuat SkillListener'),
        ('skill/SkillService.java', 'new SkillFishingListener(', 'SkillService membuat SkillFishingListener'),
        ('skill/SkillService.java', 'manager.registerEvents(listener, this.plugin)', 'SkillService mendaftarkan listener ke server'),
        ('skill/SkillService.java', 'public synchronized void ensureListeners()', 'SkillService punya watchdog pemasangan ulang listener'),
        ('skill/SkillDiagnostics.java', 'getRegisteredListeners', 'SkillDiagnostics memeriksa HandlerList server'),
        ('listener/SkillGuiListener.java', 'EventPriority.LOWEST', 'SkillGuiListener membatalkan klik paling awal'),
        ('listener/SkillGuiListener.java', 'event.setCancelled(true)', 'SkillGuiListener membatalkan klik di menu'),
        ('gui/SkillMenu.java', 'OPEN.put(player.getUniqueId(), inventory)', 'SkillMenu mencatat menu yang terbuka'),
        ('gui/SkillProgressMenu.java', 'OPEN.put(player.getUniqueId(), inventory)', 'SkillProgressMenu mencatat menu yang terbuka'),
        ('listener/SkillGuiListener.java', 'SkillProgressMenu.open(', 'Klik ikon skill membuka menu progres'),
        ('listener/SkillGuiListener.java', 'SkillProgressMenuHolder', 'SkillGuiListener mengenal holder menu progres'),
        ('skill/SkillService.java', 'public double buffValue(Player player, SkillType type, BuffKind kind)',
         'SkillService bisa menghitung nilai buff per jenis'),
        ('skill/SkillService.java', 'private Map<String, Integer> activePotions(', 'SkillService memasang semua buff potion'),
        ('listener/SkillListener.java', 'BuffKind.MOB_LOOT', 'SkillListener menerapkan buff loot mob'),
        ('listener/SkillListener.java', 'BuffKind.VANILLA_XP', 'SkillListener menerapkan buff XP vanilla'),
        ('command/SkillCommand.java', 'case "top":', 'SkillCommand punya sub-perintah top'),
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

    def java_case_block(case_name, method='applyDefaults'):
        anchor = ('private void applyDefaults(' if method == 'applyDefaults'
                  else 'private static List<SkillBuff> defaultBuffs(')
        method_start = settings_src.index(anchor)
        start = settings_src.index(f'case {case_name} -> {{', method_start)
        end = settings_src.index('\n         }', start)
        return settings_src[start:end]

    for skill in ('mining', 'woodcutting', 'farming'):
        java_blocks = set(re.findall(r'"([A-Z_]+)"', java_case_block(skill.upper())))
        yaml_blocks = set(config.get(f'skills.list.{skill}.blocks') or [])
        if java_blocks != yaml_blocks:
            fail(f'daftar blok {skill} berbeda: hanya di kode {sorted(java_blocks - yaml_blocks)}, '
                 f'hanya di config.yml {sorted(yaml_blocks - java_blocks)}')
    ok('daftar blok bawaan mining/woodcutting/farming identik dengan config.yml')

    java_causes = set(re.findall(r'"([A-Z_]+)"', java_case_block('ENDURANCE', 'defaultBuffs')))
    endurance_buffs = ((docs['config.yml'].get('skills', {}).get('list', {}).get('endurance', {}) or {})
                       .get('buffs') or {})
    yaml_causes = set()
    for buff in endurance_buffs.values():
        yaml_causes |= set(buff.get('causes') or [])
    if java_causes != yaml_causes:
        fail(f'DamageCause endurance berbeda: hanya di kode {sorted(java_causes - yaml_causes)}, '
             f'hanya di config.yml {sorted(yaml_causes - java_causes)}')
    else:
        ok(f'DamageCause endurance identik dengan config.yml ({len(yaml_causes)} penyebab)')

    # ---- 5e. buff bawaan di kode HARUS sama dengan skills.list.<key>.buffs di config.yml
    # Server yang naik versi dengan config.yml lama memakai angka di kode; bila keduanya berbeda,
    # keseimbangan yang tertulis di config.yml tidak sama dengan yang benar-benar berjalan.
    kind_keys = dict(re.findall(r'^\s+([A-Z_]+)\("([a-z0-9\-]+)"\)', 
                                (SRC / 'skill/BuffKind.java').read_text(encoding='utf-8'), re.M))
    if len(kind_keys) != len(BUFF_KINDS):
        fail(f'BuffKind.java punya {len(kind_keys)} jenis, diharapkan {len(BUFF_KINDS)}')

    def java_buffs(skill):
        block = java_case_block(skill.upper(), 'defaultBuffs')
        matches = list(re.finditer(r'SkillBuff\.(of|potion)\(', block))
        found = []
        for position, match in enumerate(matches):
            open_index = match.end() - 1
            depth = 0
            close_index = len(block) - 1
            for index in range(open_index, len(block)):
                if block[index] == '(':
                    depth += 1
                elif block[index] == ')':
                    depth -= 1
                    if depth == 0:
                        close_index = index
                        break
            # ekor = rantai .power()/.delay()/.causes() milik buff INI saja (sampai buff berikutnya)
            next_start = matches[position + 1].start() if position + 1 < len(matches) else len(block)
            found.append({'factory': match.group(1),
                          'args': block[open_index + 1:close_index],
                          'tail': block[close_index + 1:next_start]})
        return found

    def java_values(entry):
        kind = re.search(r'BuffKind\.([A-Z_]+)', entry['args'])
        kind = kind.group(1) if kind else '?'
        numbers = [float(value) for value in re.findall(r'(?<![\w."])(-?\d+(?:\.\d+)?)D?(?![\w.])', 
                                                         re.sub(r'"[^"]*"', '', entry['args']))]
        strings = re.findall(r'"([a-z_]+)"', entry['args'])
        power = re.search(r'\.power\((\d+(?:\.\d+)?)D?\)', entry['tail'])
        delay = re.search(r'\.delay\((\d+)\)', entry['tail'])
        causes = re.search(r'\.causes\(Set\.of\((.*?)\)\)', entry['tail'], re.S)
        out = {'kind': kind_keys.get(kind, kind.lower().replace('_', '-')),
               'power': float(power.group(1)) if power else None,
               'delay-seconds': int(delay.group(1)) if delay else None,
               'causes': set(re.findall(r'"([A-Z_]+)"', causes.group(1))) if causes else None}
        if entry['factory'] == 'potion':
            out['potion'] = strings[0] if strings else ''
            out['unlock-level'] = int(numbers[0]) if numbers else 1
            out['amplifier-every-levels'] = int(numbers[1]) if len(numbers) > 1 else 15
            out['amplifier-max'] = int(numbers[2]) if len(numbers) > 2 else 0
        else:
            out['unlock-level'] = int(numbers[0]) if numbers else 1
            out['per-level'] = numbers[1] if len(numbers) > 1 else 0.0
            out['max'] = numbers[2] if len(numbers) > 2 else 0.0
        return out

    compared = 0
    for skill in SKILLS:
        yaml_buffs = ((docs['config.yml'].get('skills', {}).get('list', {}).get(skill, {}) or {}).get('buffs') or {})
        code_buffs = [java_values(entry) for entry in java_buffs(skill)]
        if len(code_buffs) != len(yaml_buffs):
            fail(f'jumlah buff {skill} berbeda: kode {len(code_buffs)}, config.yml {len(yaml_buffs)}')
            continue
        ordered = [yaml_buffs[key] for key in sorted(yaml_buffs, key=lambda item: int(item))]
        for index, (code_buff, yaml_buff) in enumerate(zip(code_buffs, ordered), start=1):
            where = f'skills.list.{skill}.buffs.{index}'
            if str(yaml_buff.get('kind', '')).lower() != code_buff['kind']:
                fail(f"{where}.kind = '{yaml_buff.get('kind')}' tapi kode memakai '{code_buff['kind']}'")
            for key in ('unlock-level', 'per-level', 'max', 'power', 'potion',
                        'amplifier-every-levels', 'amplifier-max', 'delay-seconds'):
                code_value = code_buff.get(key)
                yaml_value = yaml_buff.get(key)
                if code_value is None and yaml_value is None:
                    continue
                if isinstance(code_value, float) or isinstance(yaml_value, float):
                    if code_value is None or yaml_value is None or abs(float(code_value) - float(yaml_value)) > 1e-9:
                        fail(f'{where}.{key}: kode {code_value}, config.yml {yaml_value}')
                elif code_value != yaml_value:
                    fail(f'{where}.{key}: kode {code_value}, config.yml {yaml_value}')
            code_causes = code_buff.get('causes')
            yaml_causes = set(yaml_buff.get('causes') or [])
            if code_causes is not None and code_causes != yaml_causes:
                fail(f'{where}.causes berbeda: kode {sorted(code_causes)}, config.yml {sorted(yaml_causes)}')
            compared += 1
    ok(f'{compared} buff bawaan di SkillSettings identik dengan config.yml (jenis, unlock, per-level, max, power, potion)')

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

    # ---- 6b. stub anotasi @EventHandler WAJIB retention RUNTIME
    # Bukkit menemukan handler lewat refleksi. Stub tanpa @Retention(RUNTIME) membuat Java memakai
    # RetentionPolicy.CLASS: class tetap terkompilasi rapi, tapi saat runtime Bukkit melihat nol
    # handler sehingga listener "tuli" (GUI tidak membatalkan klik, XP tidak pernah masuk).
    stub_annotation = ROOT / 'tools/stubs/generated/src/org/bukkit/event/EventHandler.java'
    if not stub_annotation.is_file():
        fail('stub org/bukkit/event/EventHandler.java tidak ada - jalankan tools/build.sh dulu')
    else:
        stub_text = stub_annotation.read_text(encoding='utf-8')
        if 'RetentionPolicy.RUNTIME' not in stub_text:
            fail('stub @EventHandler tidak punya @Retention(RUNTIME) -> semua listener hasil kompilasi '
                 'akan tuli di server (bug yang pernah terjadi di v1.2.0)')
        else:
            ok('stub @EventHandler memakai @Retention(RUNTIME)')

    # setiap listener baru harus punya handler dan mencatat kegagalannya
    for rel in ('listener/SkillListener.java', 'listener/SkillGuiListener.java', 'listener/SkillFishingListener.java'):
        text = (SRC / rel).read_text(encoding='utf-8')
        handlers = text.count('@EventHandler')
        if handlers == 0:
            fail(f'{rel} tidak punya @EventHandler sama sekali')
        if 'catch (Throwable' not in text:
            fail(f'{rel} tidak membungkus handler dengan catch (Throwable) - kegagalan akan hilang diam-diam')
    ok('3 listener skill punya handler + penjaga catch (Throwable)')

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
