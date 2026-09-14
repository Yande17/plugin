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
        if len(slots) != 13:
            fail(f'gui/skill.yml di JAR: {len(slots)} slot (harus 13 = 11 skill + info + close)')
        else:
            notes.append('gui/skill.yml di JAR: 13 slot (11 skill + info + close)')

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
