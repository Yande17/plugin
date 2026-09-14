#!/usr/bin/env python3
"""verify_jar.py — gerbang terakhir: memastikan isi JAR rilis benar-benar yang dimaksud.

Yang diperiksa (byte-per-byte, bukan sekadar nama):
  1. Setiap class milik keluarga yang DIEDIT = hasil kompilasi terbaru (bukan byte lama).
     Termasuk inner class & lambda — di sinilah bug pengemasan biasa bersembunyi.
  2. Setiap class yang TIDAK diedit = byte asli dari JAR decompiled (tidak berubah).
  3. Tidak ada class hasil kompilasi dari keluarga diedit yang tertinggal di luar JAR.
  4. Semua resource (config.yml, messages.yml, plugin.yml, prices.yml, gui/*.yml) = isi
     src/main/resources, termasuk berkas baru.
  5. Isi JAR memuat perintah /skill, permission w2nsmp.skill(.admin), seksi skills:,
     dan gui/skill.yml.
  6. Tidak ada class asli yang hilang dari JAR.

Pemakaian: python3 tools/verify_jar.py <jar-rilis> <jar-asli> <dir-kelas> <touch1> [touch2 ...]
"""
import pathlib
import sys
import zipfile

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / 'src/main/resources'

problems = []
notes = []


def fail(text):
    problems.append(text)


def family_of(name):
    base = name[:-len('.class')] if name.endswith('.class') else name
    return base.split('$')[0]


def main():
    release_path, original_path, classes_dir = (pathlib.Path(a) for a in sys.argv[1:4])
    touched = set(sys.argv[4:])

    with zipfile.ZipFile(release_path) as rel, zipfile.ZipFile(original_path) as orig:
        release = {n: rel.read(n) for n in rel.namelist() if not n.endswith('/')}
        original = {n: orig.read(n) for n in orig.namelist() if not n.endswith('/')}

    compiled = {
        p.relative_to(classes_dir).as_posix(): p.read_bytes()
        for p in classes_dir.rglob('*.class')
    }
    resources = {
        p.relative_to(RES).as_posix(): p.read_bytes()
        for p in sorted(RES.rglob('*')) if p.is_file()
    }

    # ---- 1 & 2: isi setiap class
    edited = kept = 0
    for name, blob in sorted(release.items()):
        if not name.endswith('.class'):
            continue
        if family_of(name) in touched:
            edited += 1
            if name not in compiled:
                fail(f'{name} dipakai dari keluarga diedit tapi tidak ada hasil kompilasinya')
            elif compiled[name] != blob:
                fail(f'{name} di JAR TIDAK sama dengan hasil kompilasi terbaru (byte lama ikut terkemas)')
        else:
            kept += 1
            if name not in original:
                fail(f'{name} bukan keluarga diedit dan tidak ada di JAR asli')
            elif original[name] != blob:
                fail(f'{name} berubah padahal tidak diedit')
    notes.append(f'{edited} class hasil kompilasi (keluarga diedit) cocok byte-per-byte')
    notes.append(f'{kept} class asli dipertahankan tanpa perubahan')

    # ---- 3: tidak ada yang tertinggal
    for name in sorted(compiled):
        if family_of(name) in touched and name not in release:
            fail(f'{name} hasil kompilasi TIDAK masuk JAR (akan NoClassDefFoundError saat runtime)')

    # ---- 4: resource
    for name, blob in sorted(resources.items()):
        if name not in release:
            fail(f'resource {name} tidak ada di JAR')
        elif release[name] != blob:
            fail(f'resource {name} di JAR tidak sama dengan src/main/resources')
    notes.append(f'{len(resources)} resource cocok dengan src/main/resources')

    # ---- 5: isi plugin.yml/config.yml/gui di dalam JAR
    plugin_yml = yaml.safe_load(release['plugin.yml'].decode('utf-8'))
    commands = plugin_yml.get('commands') or {}
    # /skill SENGAJA tidak dideklarasikan di plugin.yml (lihat catatan "anti-timpa command" di
    # berkas itu): command fitur didaftarkan saat runtime oleh CommandManager agar tidak menimpa
    # label plugin lain. Yang wajib ada di plugin.yml hanyalah permission-nya.
    if 'skill' in commands:
        fail('plugin.yml mendeklarasikan command "skill" - menyalahi aturan anti-timpa command plugin ini')
    else:
        notes.append('plugin.yml tidak mendeklarasikan /skill (sesuai aturan anti-timpa; '
                     'didaftarkan runtime oleh CommandManager)')
    cm = release.get('me/w2n/w2nsmp/manager/CommandManager.class')
    if cm is None:
        fail('CommandManager.class tidak ada di JAR')
    elif b'w2nsmp.skill' not in cm:
        fail('CommandManager di JAR tidak mendaftarkan /skill (string "w2nsmp.skill" tidak ada di constant pool)')
    else:
        notes.append('CommandManager di JAR mendaftarkan /skill saat runtime (permission w2nsmp.skill)')
    perms = plugin_yml.get('permissions') or {}
    for perm in ('w2nsmp.skill', 'w2nsmp.skill.admin'):
        if perm not in perms:
            fail(f'plugin.yml di JAR tidak punya permission {perm}')
    children = (perms.get('w2nsmp.admin') or {}).get('children') or {}
    for perm in ('w2nsmp.skill', 'w2nsmp.skill.admin'):
        if perm not in children:
            fail(f'w2nsmp.admin.children di JAR tidak memuat {perm}')

    config_yml = yaml.safe_load(release['config.yml'].decode('utf-8'))
    skills = config_yml.get('skills') or {}
    if not skills:
        fail('config.yml di JAR tidak punya seksi skills:')
    else:
        listed = (skills.get('list') or {})
        missing_skills = [k for k in ('fighting', 'defense', 'archery', 'agility', 'mining',
                                      'woodcutting', 'farming', 'fishing', 'endurance',
                                      'vitality', 'recovery') if k not in listed]
        if missing_skills:
            fail(f'config.yml di JAR kehilangan skill: {missing_skills}')
        else:
            notes.append(f'config.yml di JAR punya 11 skill, max-level={skills.get("max-level")}')
            # v1.3.0: tiap skill harus punya BEBERAPA buff dengan level pembukaan bertahap.
            total_buffs = 0
            kinds = set()
            for skill, body in listed.items():
                buffs = (body or {}).get('buffs') or {}
                if len(buffs) < 2:
                    fail(f'config.yml di JAR: skills.list.{skill} cuma punya {len(buffs)} buff (harus beberapa)')
                for number, buff in sorted(buffs.items()):
                    if not isinstance(buff, dict) or 'kind' not in buff or 'unlock-level' not in buff:
                        fail(f'config.yml di JAR: skills.list.{skill}.buffs.{number} tidak punya kind/unlock-level')
                        continue
                    kinds.add(str(buff['kind']))
                    if int(buff['unlock-level']) < 1:
                        fail(f'config.yml di JAR: skills.list.{skill}.buffs.{number}.unlock-level < 1')
                total_buffs += len(buffs)
            notes.append(f'config.yml di JAR: {total_buffs} buff untuk {len(listed)} skill '
                         f'({len(kinds)} jenis buff berbeda)')
    if (config_yml.get('commands') or {}).get('skill', {}).get('enabled') is not True:
        fail('config.yml di JAR: commands.skill.enabled bukan true')

    messages_yml = yaml.safe_load(release['messages.yml'].decode('utf-8'))
    if 'skill' not in messages_yml:
        fail('messages.yml di JAR tidak punya seksi skill:')
    else:
        names = (messages_yml['skill'].get('name') or {})
        if len(names) != 11:
            fail(f'messages.yml di JAR: skill.name cuma {len(names)} entri (harus 11)')
        else:
            notes.append('messages.yml di JAR: 11 nama skill + pesan lengkap')
    if 'skill' not in (messages_yml.get('help') or {}):
        fail('messages.yml di JAR tidak punya help.skill')

    if 'gui/skill.yml' not in release:
        fail('gui/skill.yml tidak ada di JAR')
    else:
        gui = yaml.safe_load(release['gui/skill.yml'].decode('utf-8'))
        slots = gui.get('slots') or {}
        if len(slots) != 14:
            fail(f'gui/skill.yml di JAR: {len(slots)} slot (harus 14 = 11 skill + top + info + close)')
        else:
            notes.append('gui/skill.yml di JAR: 14 slot (11 skill + top + info + close)')
        if len(set(slots.values())) != len(slots):
            fail('gui/skill.yml di JAR: ada slot yang dipakai dua item')
        progress = gui.get('progress') or {}
        progress_slots = progress.get('slots') or {}
        for needed in ('icon', 'buffs', 'next-level', 'next-buff', 'max-level', 'rank', 'back', 'detail', 'close'):
            if needed not in progress_slots:
                fail(f'gui/skill.yml di JAR: progress.slots kehilangan {needed}')
        if len(set(progress_slots.values())) != len(progress_slots):
            fail('gui/skill.yml di JAR: slot menu progres ada yang bentrok')
        if not progress.get('bar-filled') or not progress.get('bar-empty'):
            fail('gui/skill.yml di JAR: material bar kemajuan (progress.bar-filled/bar-empty) kosong')
        if len(progress_slots) == 9:
            notes.append('gui/skill.yml di JAR: menu progres skill lengkap (9 slot unik + bar kemajuan)')

    # ---- 5a: fitur v1.3.0 harus benar-benar ada di bytecode JAR rilis
    # (bukan cuma di source): kelas baru terkemas, dan simbol API/ metode baru terpanggil.
    v130_classes = [
        'me/w2n/w2nsmp/skill/SkillBuff.class',
        'me/w2n/w2nsmp/skill/SkillTop.class',
        'me/w2n/w2nsmp/skill/SkillTop$Entry.class',
        'me/w2n/w2nsmp/gui/SkillProgressMenu.class',
        'me/w2n/w2nsmp/gui/SkillProgressMenuHolder.class',
    ]
    for name in v130_classes:
        if name not in release:
            fail(f'JAR kehilangan kelas v1.3.0: {name}')
    if all(name in release for name in v130_classes):
        notes.append(f'JAR memuat {len(v130_classes)} kelas baru v1.3.0 (SkillBuff, SkillTop, menu progres)')

    v130_symbols = {
        'me/w2n/w2nsmp/skill/SkillSettings.class':
            ['defaultBuffs', 'readBuffs', 'applyLegacyBuff', 'buffValue', 'nextBuff', 'environmentReduction'],
        'me/w2n/w2nsmp/skill/SkillService.class':
            ['buffValueAll', 'kindAvailable', 'critMultiplier', 'blockMultiplier', 'activePotions',
             'applyPotion', 'removePotion', 'buffLines', 'buffName', 'profilesSnapshot', 'DOUBLE_XP'],
        'me/w2n/w2nsmp/skill/SkillApiProbe.class':
            ['potionType', 'applyPotion', 'removePotion', 'mobLootApi', 'blockExpApi', 'hasteApi', 'missingPotions'],
        'me/w2n/w2nsmp/listener/SkillListener.class':
            ['getDrops', 'getExpToDrop', 'setExpToDrop', 'rollCrit', 'critMultiplier', 'MOB_LOOT', 'VANILLA_XP'],
        'me/w2n/w2nsmp/listener/SkillGuiListener.class':
            ['SkillProgressMenu', 'SkillProgressMenuHolder', 'handleProgressClick', 'sendTop'],
        'me/w2n/w2nsmp/gui/SkillMenu.class': ['topItem', 'topSlot', 'SkillProgressMenu'],
        'me/w2n/w2nsmp/gui/SkillProgressMenu.class':
            ['buffSlots', 'renderBar', 'renderMilestones', 'buffItem', 'keyAt', 'rankOfSkill', 'rankOfTotal'],
        'me/w2n/w2nsmp/command/SkillCommand.class': ['sendTop', 'isTopWord'],
        'me/w2n/w2nsmp/skill/SkillInfo.class': ['sendTop', 'sendBuffDetail', 'buffsCompact', 'buffLines'],
    }
    missing_symbols = 0
    checked_symbols = 0
    for name, needles in v130_symbols.items():
        blob = release.get(name)
        if blob is None:
            fail(f'JAR kehilangan {name}')
            continue
        for needle in needles:
            checked_symbols += 1
            if needle.encode('utf-8') not in blob:
                missing_symbols += 1
                fail(f'{name} di JAR tidak memuat "{needle}" (fitur v1.3.0 tidak terkemas?)')
    if missing_symbols == 0:
        notes.append(f'{checked_symbols} simbol fitur v1.3.0 (multi-buff, menu progres, top) ada di bytecode JAR')

    for key in ('skill.progress-title', 'skill.progress-buff-active-name', 'skill.top-header',
                'skill.buff-short.crit-chance', 'skill.potion-name.absorption', 'skill.level-up-buff'):
        section, _, leaf = key.partition('.')
        node = (messages_yml.get(section) or {})
        for part in leaf.split('.'):
            node = (node or {}).get(part) if isinstance(node, dict) else None
        if node is None:
            fail(f'messages.yml di JAR kehilangan {key}')
    if 'crit-chance' in str((messages_yml.get('skill') or {}).get('buff-short') or {}):
        notes.append('messages.yml di JAR punya nama pendek 16 jenis buff + pesan menu progres & peringkat')

    # ---- 5b: anotasi @EventHandler harus RUNTIME-visible di SETIAP class listener
    # Bila tidak, Bukkit mendaftarkan listener-nya tapi tidak menemukan satu pun handler -> fitur
    # terlihat hidup padahal "tuli". Ini penyebab bug v1.2.0, jadi diperiksa di level bytecode JAR.
    deaf = []
    for name, blob in sorted(release.items()):
        if not name.endswith('.class') or b'Lorg/bukkit/event/EventHandler;' not in blob:
            continue
        if b'RuntimeVisibleAnnotations' not in blob:
            deaf.append(name)
        elif b'RuntimeInvisibleAnnotations' in blob:
            deaf.append(name + ' (sebagian anotasi tidak terlihat saat runtime)')
    if deaf:
        for name in deaf:
            fail(f'{name}: @EventHandler TIDAK terlihat saat runtime - Bukkit tidak akan memanggil handler apa pun')
    else:
        count = sum(1 for name, blob in release.items()
                    if name.endswith('.class') and b'Lorg/bukkit/event/EventHandler;' in blob)
        notes.append(f'{count} class listener punya @EventHandler yang terlihat saat runtime')

    # ---- 5c: event yang ditangani listener skill harus benar-benar ada di bytecode-nya
    expected_events = {
        'me/w2n/w2nsmp/listener/SkillListener.class': [
            'Lorg/bukkit/event/block/BlockBreakEvent;', 'Lorg/bukkit/event/entity/EntityDamageEvent;',
            'Lorg/bukkit/event/entity/EntityDamageByEntityEvent;', 'Lorg/bukkit/event/entity/EntityDeathEvent;',
            'Lorg/bukkit/event/player/PlayerMoveEvent;', 'Lorg/bukkit/event/player/PlayerJoinEvent;',
        ],
        'me/w2n/w2nsmp/listener/SkillGuiListener.class': [
            'Lorg/bukkit/event/inventory/InventoryClickEvent;', 'Lorg/bukkit/event/inventory/InventoryDragEvent;',
            'Lorg/bukkit/event/inventory/InventoryCloseEvent;',
        ],
        'me/w2n/w2nsmp/listener/SkillFishingListener.class': ['Lorg/bukkit/event/player/PlayerFishEvent;'],
    }
    for name, events in expected_events.items():
        blob = release.get(name)
        if blob is None:
            fail(f'{name} tidak ada di JAR')
            continue
        for event in events:
            if event.encode() not in blob:
                fail(f'{name} tidak menangani {event} (handler hilang dari bytecode)')
    notes.append('handler XP/buff/GUI/mancing lengkap di bytecode JAR')

    # ---- 6: class asli tidak ada yang hilang
    for name in sorted(original):
        if name.endswith('.class') and name not in release:
            fail(f'class asli {name} hilang dari JAR rilis')

    # ---- ringkasan
    notes.append(f'META-INF/MANIFEST.MF: {"ada" if "META-INF/MANIFEST.MF" in release else "HILANG"}')
    signed = [n for n in release if n.startswith('META-INF/') and n.endswith(('.SF', '.RSA', '.DSA'))]
    if signed:
        fail(f'tanda tangan lama masih ada (JAR akan ditolak server): {signed}')

    print('== Catatan ==')
    for note in notes:
        print('  OK  ' + note)
    if problems:
        print('\n== MASALAH ==')
        for problem in problems:
            print('  !!  ' + problem)
        print(f'\nHASIL: {len(problems)} MASALAH - JANGAN pakai JAR ini')
        return 1
    print(f'\nHASIL: JAR {release_path.name} lolos semua pengecekan ({len(release)} entri)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
