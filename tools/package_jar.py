#!/usr/bin/env python3
"""package_jar.py — menyusun JAR rilis.

Sumber class:
  * hasil kompilasi ECJ di tools/work/classes  -> HANYA untuk "keluarga" (kelas luar +
    inner/lambda-nya) yang memang diubah/ditambah oleh proyek ini (daftar TOUCHED di
    tools/release.sh). Keluarga lain tetap memakai byte asli dari JAR decompiled supaya
    plugin yang berjalan tidak pernah berbeda dari aslinya.
Sumber resource (config.yml, messages.yml, plugin.yml, prices.yml, gui/*.yml):
  * src/main/resources (sumber kebenaran), termasuk berkas yang baru ditambahkan.

Pemakaian:
  python3 tools/package_jar.py <jar-asli> <dir-kelas> <jar-keluaran> <versi> <touch1> [touch2 ...]
"""
import datetime
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent


def family_of(name: str) -> str:
    """Keluarga class: nama tanpa .class lalu tanpa $inner/lambda.

    Penting: strip ".class" DULU, baru potong pada "$". Bila "$" dipotong lebih dulu
    (mis. "Foo$Bar.class".split("$")[0] -> "Foo"), pemotongan ".class" akan menggerogoti
    nama class luar ("Foo" -> "") dan keluarga inner class tidak pernah cocok.
    """
    base = name[:-len('.class')] if name.endswith('.class') else name
    return base.split('$')[0]


def main() -> int:
    original_jar = pathlib.Path(sys.argv[1])
    classes_dir = pathlib.Path(sys.argv[2])
    out_jar = pathlib.Path(sys.argv[3])
    version = sys.argv[4]
    touched = set(sys.argv[5:])

    classes = {
        p.relative_to(classes_dir).as_posix(): p.read_bytes()
        for p in classes_dir.rglob('*.class')
    }

    resource_root = ROOT / 'src/main/resources'
    resources = {
        p.relative_to(resource_root).as_posix(): p.read_bytes()
        for p in sorted(resource_root.rglob('*')) if p.is_file()
    }

    manifest = (
        'Manifest-Version: 1.0\r\n'
        'Created-By: W2NSMP build (ECJ 3.43)\r\n'
        f'Build-Date: {datetime.date.today().isoformat()}\r\n'
        f'Implementation-Version: {version}\r\n'
        '\r\n'
    ).encode()

    kept = 0
    replaced = 0
    added = 0
    new_resources = 0

    with zipfile.ZipFile(original_jar) as z, zipfile.ZipFile(out_jar, 'w', zipfile.ZIP_DEFLATED) as out:
        names = set(z.namelist())

        for info in z.infolist():
            name = info.filename
            if name.endswith('/'):
                continue
            if name.startswith('META-INF/'):
                continue  # manifest ditulis ulang, tanda tangan lama dibuang

            if name.endswith('.class'):
                family = family_of(name)
                if family in touched and name in classes:
                    out.writestr(name, classes[name])
                    replaced += 1
                else:
                    out.writestr(name, z.read(name))
                    kept += 1
            elif name in resources:
                out.writestr(name, resources[name])
                replaced += 1
            else:
                out.writestr(name, z.read(name))
                kept += 1

        # class baru milik keluarga yang tersentuh (inner/lambda/sibling yang tidak ada di JAR asli)
        for name, blob in sorted(classes.items()):
            if name in names:
                continue
            family = family_of(name)
            if family in touched:
                out.writestr(name, blob)
                added += 1

        # resource baru (mis. gui/skill.yml)
        for name, blob in resources.items():
            if name not in names:
                out.writestr(name, blob)
                new_resources += 1

        out.writestr('META-INF/MANIFEST.MF', manifest)

    print(f'class asli dipertahankan : {kept}')
    print(f'class/resource diganti   : {replaced}')
    print(f'class baru ditambahkan   : {added}')
    print(f'resource baru ditambahkan: {new_resources}')
    print(f'keluarga tersentuh       : {len(touched)}')
    print(f'hasil                    : {out_jar} ({out_jar.stat().st_size} byte)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
