#!/usr/bin/env python3
"""
verify_linkage.py — memastikan JAR hibrida aman dipakai.

JAR akhir memakai class ASLI dari W2NSMP-1.0.0.jar untuk semua kelas yang tidak disunting,
dan class hasil kompilasi ulang untuk kelas yang disunting. Karena itu dua hal wajib benar:

  1. Setiap member internal (me/w2n/**) yang dirujuk kelas SUNTINGAN harus ada di JAR asli.
  2. Setiap member internal yang dirujuk kelas ASLI dari kelas yang kita SUNTING harus tetap
     ada di hasil kompilasi (kalau tidak, kelas lama yang tidak ikut diganti jadi rusak).

Pemakaian: python3 tools/verify_linkage.py <jar-asli> <dir-class-baru> <Class1> [Class2 ...]
Class ditulis dalam bentuk internal, mis. me/w2n/w2nsmp/stats/StatisticsService
"""
import glob
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_stubs import ClassFile  # noqa: E402

INTERNAL = 'me/w2n/'


def members_of(cf):
    out = {(m['name'], m['desc']) for m in cf.methods}
    out |= {(f['name'], f['desc']) for f in cf.fields}
    return out


def load_dir(root):
    data = {}
    for path in glob.glob(os.path.join(root, '**', '*.class'), recursive=True):
        cf = ClassFile(open(path, 'rb').read())
        data[cf.name] = cf
    return data


def load_jar(path):
    data = {}
    with zipfile.ZipFile(path) as zf:
        for name in zf.namelist():
            if name.endswith('.class'):
                cf = ClassFile(zf.read(name))
                data[cf.name] = cf
    return data


def resolve(classes, owner, mname, mdesc):
    """
    True  = member pasti ada di owner/leluhur internal.
    None  = tidak bisa disimpulkan (hierarki keluar ke kelas eksternal, mis. JavaPlugin,
            Enum.name(), List.add()) -> jangan dianggap masalah.
    False = seluruh hierarki internal sudah dijelajah dan member benar-benar tidak ada.
    """
    seen, queue, external = set(), [owner], False
    while queue:
        cur = queue.pop()
        if cur in seen:
            continue
        seen.add(cur)
        cf = classes.get(cur)
        if cf is None:
            external = True
            continue
        if (mname, mdesc) in members_of(cf):
            return True
        if cf.super_name:
            queue.append(cf.super_name)
        queue.extend(cf.interfaces)
    return None if external else False


def refs_internal(cf):
    return {(owner, name, desc) for _op, owner, name, desc in cf.refs() if owner.startswith(INTERNAL)}


def main():
    jar, newdir = sys.argv[1], sys.argv[2]
    touched = set(sys.argv[3:])
    orig = load_jar(jar)
    new = load_dir(newdir)
    family = lambda n: n.split('$')[0] in touched  # noqa: E731

    problems = 0

    print('== Cek 1: kelas SUNTINGAN memanggil kelas ASLI')
    checked = 0
    for name, cf in sorted(new.items()):
        if not family(name):
            continue
        for owner, mname, mdesc in sorted(refs_internal(cf)):
            if family(owner):
                continue
            checked += 1
            if resolve(orig, owner, mname, mdesc) is False:
                print(f'   !! {name.split("/")[-1]} -> {owner.split("/")[-1]}.{mname}{mdesc} TIDAK ADA di JAR asli')
                problems += 1
    print(f'   {checked} referensi internal diperiksa')

    print('== Cek 2: kelas ASLI memanggil kelas SUNTINGAN')
    checked = 0
    for name, cf in sorted(orig.items()):
        if family(name):
            continue
        for owner, mname, mdesc in sorted(refs_internal(cf)):
            if not family(owner):
                continue
            checked += 1
            if resolve(new, owner, mname, mdesc) is False:
                print(f'   !! {name.split("/")[-1]} -> {owner.split("/")[-1]}.{mname}{mdesc} HILANG di versi baru')
                problems += 1
    print(f'   {checked} referensi internal diperiksa')

    # Cek 3: semua kelas suntingan benar-benar terkompilasi
    print('== Cek 3: kelengkapan kelas suntingan')
    for t in sorted(touched):
        found = [n for n in new if n.split('$')[0] == t]
        if not found:
            print(f'   !! {t} tidak ada di {newdir}')
            problems += 1
        else:
            print(f'   {t.split("/")[-1]}: {len(found)} class ({", ".join(sorted(n.split("/")[-1] for n in found))})')

    print(f'\nHASIL: {"AMAN (0 masalah)" if problems == 0 else str(problems) + " MASALAH"}')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
