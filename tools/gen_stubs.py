#!/usr/bin/env python3
"""
gen_stubs.py — membangkitkan stub API (Paper/Bukkit/Adventure/Vault) dari bytecode JAR asli.

Kenapa perlu: sandbox ini tidak bisa mengakses repo Maven (repo.papermc.io, Maven Central),
sehingga `paper-api` tidak dapat diunduh. Sebagai gantinya kita bangkitkan "API tiruan" yang:
  * berisi tepat tipe & member yang benar-benar dirujuk bytecode plugin (dari constant pool),
  * punya deskriptor (erased signature) IDENTIK dengan API asli -> bytecode hasil kompilasi
    tetap memanggil simbol yang sama saat runtime di server Paper sungguhan,
  * hierarki & generics ditulis manual (tabel SPEC) agar source hasil decompile bisa
    dikompilasi tanpa error tipe.

Stub ini HANYA untuk kompilasi (scope: provided). Tidak pernah ikut dikemas ke JAR plugin.

Pemakaian:
    python3 tools/gen_stubs.py <jar-atau-dir-class> <outdir-src>
"""
import glob
import os
import re
import struct
import sys
from collections import defaultdict

# --------------------------------------------------------------------------- #
# 1. Parser class file (constant pool + header + method/field + Code attribute)
# --------------------------------------------------------------------------- #

# jumlah byte operand per opcode (tanpa byte opcode itu sendiri)
OP_LEN = {}
for op in list(range(0x1A, 0x36)) + list(range(0x3B, 0x4F)) + list(range(0x4F, 0x57)) + \
         list(range(0x57, 0x60)) + list(range(0x60, 0x84)) + list(range(0x85, 0x94)) + \
         [0xAC, 0xAD, 0xAE, 0xAF, 0xB0, 0xB1, 0xBE, 0xBF, 0xC2, 0xC3]:
    OP_LEN[op] = 0
for op in [0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3A, 0xA9, 0xBC]:
    OP_LEN[op] = 1
for op in [0x10, 0xBB, 0xBD, 0xC0, 0xC1] + list(range(0x99, 0xA9)) + [0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xC6, 0xC7]:
    OP_LEN[op] = 2
for op in [0x11, 0x84, 0x9F]:
    OP_LEN[op] = 2
OP_LEN[0x11] = 2   # sipush
OP_LEN[0x84] = 2   # iinc
OP_LEN[0x13] = 2   # ldc_w
OP_LEN[0x14] = 2   # ldc2_w
OP_LEN[0x12] = 1   # ldc
OP_LEN[0x10] = 1   # bipush
OP_LEN[0x11] = 2   # sipush
for op in [0xB9, 0xBA]:
    OP_LEN[op] = 4
OP_LEN[0xC5] = 3
OP_LEN[0xC8] = 4
OP_LEN[0xC9] = 4


class ClassFile:
    def __init__(self, data):
        assert data[:4] == b'\xca\xfe\xba\xbe', 'bukan class file'
        count = struct.unpack_from('>H', data, 8)[0]
        i, n = 10, 1
        cp, utf = {}, {}
        while n < count:
            tag = data[i]
            i += 1
            if tag == 1:
                ln = struct.unpack_from('>H', data, i)[0]
                i += 2
                utf[n] = data[i:i + ln].decode('utf-8', 'replace')
                i += ln
            elif tag in (7, 8, 16, 19, 20):
                cp[n] = (tag, struct.unpack_from('>H', data, i)[0])
                i += 2
            elif tag == 15:
                i += 3
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                cp[n] = (tag, struct.unpack_from('>HH', data, i))
                i += 4
            elif tag in (5, 6):
                i += 8
                n += 1
            else:
                raise ValueError(f'tag tak dikenal {tag}')
            n += 1
        self.cp, self.utf = cp, utf
        self.access, this_i, super_i = struct.unpack_from('>HHH', data, i)
        self.name = utf[cp[this_i][1]]
        self.super_name = utf[cp[super_i][1]] if super_i else None
        i += 6
        ifc = struct.unpack_from('>H', data, i)[0]
        i += 2
        self.interfaces = [utf[cp[struct.unpack_from('>H', data, i + 2 * k)[0]][1]] for k in range(ifc)]
        i += 2 * ifc
        self.fields, self.methods = [], []
        for bucket in (self.fields, self.methods):
            cnt = struct.unpack_from('>H', data, i)[0]
            i += 2
            for _ in range(cnt):
                acc, ni, di, atc = struct.unpack_from('>HHHH', data, i)
                i += 8
                attrs = {}
                for _a in range(atc):
                    an_i, ln = struct.unpack_from('>HI', data, i)
                    i += 6
                    attrs[utf[an_i]] = data[i:i + ln]
                    i += ln
                bucket.append({'access': acc, 'name': utf[ni], 'desc': utf[di], 'attrs': attrs})
        self.data = data

    def cname(self, idx):
        return self.utf[self.cp[idx][1]]

    def refs(self):
        """Semua member-ref yang dipakai bytecode, lengkap dengan opcode pemanggilnya."""
        out = []
        for m in self.methods:
            code = m['attrs'].get('Code')
            if not code:
                continue
            clen = struct.unpack_from('>I', code, 4)[0]
            body, pos = code[8:8 + clen], 0
            while pos < len(body):
                op = body[pos]
                if op in (0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBB, 0xC0, 0xC1, 0xBD, 0x12, 0x13):
                    if op in (0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9):
                        idx = struct.unpack_from('>H', body, pos + 1)[0]
                        if idx in self.cp and self.cp[idx][0] in (9, 10, 11):
                            cls_i, nt = self.cp[idx][1]
                            owner = self.cname(cls_i)
                            mname = self.utf[self.cp[nt][1][0]]
                            mdesc = self.utf[self.cp[nt][1][1]]
                            out.append((op, owner, mname, mdesc))
                if op == 0xAA:  # tableswitch
                    pad = (4 - ((pos + 1) % 4)) % 4
                    base = pos + 1 + pad
                    lo, hi = struct.unpack_from('>ii', body, base + 4)
                    pos = base + 12 + (hi - lo + 1) * 4
                    continue
                if op == 0xAB:  # lookupswitch
                    pad = (4 - ((pos + 1) % 4)) % 4
                    base = pos + 1 + pad
                    npairs = struct.unpack_from('>i', body, base + 4)[0]
                    pos = base + 8 + npairs * 8
                    continue
                if op == 0xC4:  # wide
                    op2 = body[pos + 1]
                    pos += 6 if op2 == 0x84 else 4
                    continue
                pos += 1 + OP_LEN.get(op, 0)
        return out

    def annotation_types(self):
        """Tipe anotasi (mis. org/bukkit/event/EventHandler) dari semua method."""
        found = set()
        self.enum_consts = []
        for m in self.methods:
            for key in ('RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations'):
                blob = m['attrs'].get(key)
                if not blob:
                    continue
                cnt = struct.unpack_from('>H', blob, 0)[0]
                pos = 2
                for _ in range(cnt):
                    ti = struct.unpack_from('>H', blob, pos)[0]
                    pos += 2
                    found.add(self.utf[ti][1:-1].replace('.', '/'))
                    npairs = struct.unpack_from('>H', blob, pos)[0]
                    pos += 2
                    for _p in range(npairs):
                        pos += 2
                        tag = chr(blob[pos])
                        pos += 1
                        if tag in 'BCDFIJSZs':
                            pos += 2
                        elif tag in 'et':
                            if tag == 'e':
                                ti3, ci3 = struct.unpack_from('>HH', blob, pos)
                                found.add(self.utf[ti3][1:-1].replace('.', '/'))
                                self.enum_consts.append((self.utf[ti3][1:-1].replace('.', '/'), self.utf[ci3]))
                                pos += 4
                            else:
                                ti2 = struct.unpack_from('>H', blob, pos)[0]
                                found.add(self.utf[ti2][1:-1].replace('.', '/'))
                                pos += 2
                        elif tag == 'c':
                            pos += 2
                        elif tag == '@':
                            pos += 2
                        elif tag == '[':
                            n = struct.unpack_from('>H', blob, pos)[0]
                            pos += 2 + n * 3
        return found

    def overridden(self):
        """Method ber-@Override (perlu ada di supertipe stub)."""
        out = []
        for m in self.methods:
            for key in ('RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations'):
                blob = m['attrs'].get(key)
                if not blob:
                    continue
                if b'Lorg/bukkit' in blob or b'Override' in blob:
                    cnt = struct.unpack_from('>H', blob, 0)[0]
                    pos = 2
                    for _ in range(cnt):
                        ti = struct.unpack_from('>H', blob, pos)[0]
                        pos += 2
                        aname = self.utf[ti]
                        npairs = struct.unpack_from('>H', blob, pos)[0]
                        pos += 2
                        for _p in range(npairs):
                            pos += 2
                            tag = chr(blob[pos])
                            pos += 1
                            if tag in 'BCDFIJSZs':
                                pos += 2
                            elif tag == 'e':
                                pos += 4
                            elif tag == 'c':
                                pos += 2
                            elif tag == '@':
                                pos += 2
                            elif tag == '[':
                                n = struct.unpack_from('>H', blob, pos)[0]
                                pos += 2 + n * 3
                        if aname == 'Ljava/lang/Override;':
                            out.append((m['name'], m['desc']))
        return out


# --------------------------------------------------------------------------- #
# 2. Descriptor -> tipe Java
# --------------------------------------------------------------------------- #

PRIM = {'B': 'byte', 'C': 'char', 'D': 'double', 'F': 'float', 'I': 'int',
        'J': 'long', 'S': 'short', 'Z': 'boolean', 'V': 'void'}


def split_params(desc):
    inner = desc[1:desc.index(')')]
    out, i = [], 0
    while i < len(inner):
        c = inner[i]
        if c == '[':
            j = i
            while inner[j] == '[':
                j += 1
            if inner[j] == 'L':
                end = inner.index(';', j)
                out.append(inner[i:end + 1])
                i = end + 1
            else:
                out.append(inner[i:j + 1])
                i = j + 1
        elif c == 'L':
            end = inner.index(';', i)
            out.append(inner[i:end + 1])
            i = end + 1
        else:
            out.append(c)
            i += 1
    ret = desc[desc.index(')') + 1:]
    return out, ret


def types_in(desc):
    params, ret = split_params(desc)
    out = []
    for t in params + [ret]:
        for m in re.finditer(r'L([A-Za-z0-9_$/]+);', t):
            out.append(m.group(1))
    return out


if __name__ == '__main__':
    print(__doc__)


# --------------------------------------------------------------------------- #
# 3. SPEC: pengetahuan API asli (hierarki, generics, static, throws)
# --------------------------------------------------------------------------- #

SKIP_OWNERS = ('java/', 'javax/', 'jdk/', 'sun/', 'me/w2n/', '[')
OBJECT_MEMBERS = {'equals', 'hashCode', 'toString', 'getClass', 'notify', 'notifyAll', 'wait', 'clone'}
OBJECT_DESCS = {
    'equals': '(Ljava/lang/Object;)Z', 'hashCode': '()I', 'toString': '()Ljava/lang/String;',
    'getClass': '()Ljava/lang/Class;', 'clone': '()Ljava/lang/Object;', 'notify': '()V', 'notifyAll': '()V',
}


def is_object_member(name, desc):
    """Object.equals/hashCode/... dilewati, tapi clone() dengan return tipe khusus (ItemStack,
    Location) TIDAK boleh dilewati karena itu override kovarian dari API asli."""
    return name in OBJECT_MEMBERS and OBJECT_DESCS.get(name, desc) == desc

INTERFACES = {
    'io/papermc/paper/plugin/configuration/PluginMeta', 'net/kyori/adventure/key/Key',
    'net/kyori/adventure/sound/Sound', 'net/kyori/adventure/text/Component',
    'net/kyori/adventure/text/TextComponent', 'net/kyori/adventure/text/format/TextDecoration',
    'net/kyori/adventure/text/serializer/legacy/LegacyComponentSerializer',
    'net/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer',
    'net/milkbowl/vault/economy/Economy', 'org/bukkit/Chunk', 'org/bukkit/OfflinePlayer',
    'org/bukkit/Server', 'org/bukkit/World', 'org/bukkit/block/Block',
    'org/bukkit/command/CommandExecutor', 'org/bukkit/command/CommandMap',
    'org/bukkit/command/CommandSender', 'org/bukkit/command/TabCompleter',
    'org/bukkit/command/TabExecutor', 'org/bukkit/configuration/ConfigurationSection',
    'org/bukkit/entity/Entity', 'org/bukkit/entity/HumanEntity', 'org/bukkit/entity/Item',
    'org/bukkit/entity/LivingEntity', 'org/bukkit/entity/Player', 'org/bukkit/entity/Projectile',
    'org/bukkit/entity/FishHook', 'org/bukkit/block/Biome',
    'org/bukkit/event/Listener', 'org/bukkit/inventory/Inventory',
    'org/bukkit/inventory/InventoryHolder', 'org/bukkit/inventory/InventoryView',
    'org/bukkit/inventory/PlayerInventory', 'org/bukkit/inventory/meta/ItemMeta',
    'org/bukkit/inventory/meta/SkullMeta', 'org/bukkit/persistence/PersistentDataContainer',
    'org/bukkit/persistence/PersistentDataType', 'org/bukkit/plugin/Plugin',
    'org/bukkit/plugin/PluginManager', 'org/bukkit/plugin/ServicesManager',
    'org/bukkit/projectiles/ProjectileSource', 'org/bukkit/scheduler/BukkitScheduler',
    'org/bukkit/scheduler/BukkitTask', 'org/bukkit/scoreboard/Objective',
    'org/bukkit/scoreboard/Score', 'org/bukkit/scoreboard/Scoreboard',
    'org/bukkit/scoreboard/ScoreboardManager', 'org/bukkit/scoreboard/Team',
}

ENUMS = {
    'org/bukkit/Material', 'org/bukkit/GameMode', 'org/bukkit/World$Environment',
    'org/bukkit/event/entity/EntityDamageEvent$DamageCause',
    'org/bukkit/event/inventory/ClickType', 'org/bukkit/event/inventory/InventoryAction',
    'org/bukkit/event/inventory/InventoryType', 'org/bukkit/scoreboard/DisplaySlot',
    'net/kyori/adventure/sound/Sound$Source', 'org/bukkit/event/EventPriority',
}

ABSTRACT_CLASSES = {'org/bukkit/plugin/java/JavaPlugin'}

# Tipe bersarang yang di API aslinya public. Stub default membuat tipe bersarang jadi
# package-private, sehingga tidak bisa disebut dari paket plugin (mis. EntityDamageEvent.DamageCause).
PUBLIC_NESTED = {'org/bukkit/event/entity/EntityDamageEvent$DamageCause',
                 'org/bukkit/event/player/PlayerFishEvent$State'}

# supertipe antar-stub: name -> (extends, [implements])
HIER = {
    'net/kyori/adventure/text/TextComponent': ('net/kyori/adventure/text/Component', []),
    'org/bukkit/entity/LivingEntity': ('org/bukkit/entity/Entity', []),
    'org/bukkit/entity/HumanEntity': ('org/bukkit/entity/LivingEntity', []),
    'org/bukkit/entity/Player': ('org/bukkit/entity/HumanEntity',
                                 ['org/bukkit/command/CommandSender', 'org/bukkit/OfflinePlayer']),
    'org/bukkit/entity/Item': ('org/bukkit/entity/Entity', []),
    'org/bukkit/entity/Projectile': ('org/bukkit/entity/Entity', []),
    'org/bukkit/entity/FishHook': ('org/bukkit/entity/Projectile', []),
    'org/bukkit/command/TabExecutor': (None, ['org/bukkit/command/CommandExecutor',
                                              'org/bukkit/command/TabCompleter']),
    'org/bukkit/command/PluginCommand': ('org/bukkit/command/Command', []),
    'org/bukkit/configuration/file/YamlConfiguration': ('org/bukkit/configuration/file/FileConfiguration', []),
    'org/bukkit/configuration/file/FileConfiguration': (None, ['org/bukkit/configuration/ConfigurationSection']),
    'org/bukkit/configuration/InvalidConfigurationException': ('java/lang/Exception', []),
    'org/bukkit/inventory/PlayerInventory': ('org/bukkit/inventory/Inventory', []),
    'org/bukkit/inventory/meta/SkullMeta': ('org/bukkit/inventory/meta/ItemMeta', []),
    'org/bukkit/event/entity/EntityDamageByEntityEvent': ('org/bukkit/event/entity/EntityDamageEvent', []),
    'org/bukkit/event/entity/PlayerDeathEvent': ('org/bukkit/event/entity/EntityDeathEvent', []),
    'org/bukkit/plugin/java/JavaPlugin': (None, ['org/bukkit/plugin/Plugin']),
}

# metode static (dipanggil sebagai Tipe.metode(...))
STATIC_METHODS = {
    ('org/bukkit/Bukkit', '*'),
    ('net/kyori/adventure/key/Key', 'key'), ('net/kyori/adventure/sound/Sound', 'sound'),
    ('net/kyori/adventure/text/Component', 'empty'), ('net/kyori/adventure/text/Component', 'text'),
    ('net/kyori/adventure/text/serializer/legacy/LegacyComponentSerializer', 'legacyAmpersand'),
    ('net/kyori/adventure/text/serializer/legacy/LegacyComponentSerializer', 'legacySection'),
    ('net/kyori/adventure/text/serializer/plain/PlainTextComponentSerializer', 'plainText'),
    ('org/bukkit/Material', 'matchMaterial'), ('org/bukkit/configuration/file/YamlConfiguration', 'loadConfiguration'),
    ('org/bukkit/inventory/ItemStack', 'deserializeBytes'),
}

# generics: (owner, name, desc-prefix) -> (return, [params]) dalam bentuk teks Java
GENERICS = {
    ('org/bukkit/configuration/ConfigurationSection', 'getKeys'): ('java.util.Set<java.lang.String>', None),
    ('org/bukkit/configuration/ConfigurationSection', 'getStringList'): ('java.util.List<java.lang.String>', None),
    ('org/bukkit/configuration/file/FileConfiguration', 'getKeys'): ('java.util.Set<java.lang.String>', None),
    ('org/bukkit/configuration/file/FileConfiguration', 'getStringList'): ('java.util.List<java.lang.String>', None),
    ('org/bukkit/configuration/file/FileConfiguration', 'getList'): ('java.util.List<?>', ['java.lang.String', 'java.util.List<?>']),
    ('org/bukkit/configuration/file/YamlConfiguration', 'getKeys'): ('java.util.Set<java.lang.String>', None),
    ('org/bukkit/configuration/file/YamlConfiguration', 'getStringList'): ('java.util.List<java.lang.String>', None),
    ('org/bukkit/configuration/file/YamlConfigurationOptions', 'setHeader'): (
        'org/bukkit/configuration/file/YamlConfigurationOptions', ['java.util.List<java.lang.String>']),
    ('org/bukkit/Bukkit', 'getOnlinePlayers'): ('java.util.Collection<? extends org/bukkit/entity/Player>', None),
    ('org/bukkit/block/Block', 'getDrops'): ('java.util.Collection<org/bukkit/inventory/ItemStack>', None),
    ('org/bukkit/command/CommandMap', 'getKnownCommands'): ('java.util.Map<java.lang.String, org/bukkit/command/Command>', None),
    ('org/bukkit/inventory/PlayerInventory', 'addItem'): ('java.util.HashMap<java.lang.Integer, org/bukkit/inventory/ItemStack>', None),
    ('org/bukkit/inventory/meta/ItemMeta', 'lore'): (
        'java.util.List<net/kyori/adventure/text/Component>', ['java.util.List<net/kyori/adventure/text/Component>']),
    ('org/bukkit/event/entity/PlayerDeathEvent', 'getDrops'): ('java.util.List<org/bukkit/inventory/ItemStack>', None),
    ('org/bukkit/event/inventory/InventoryDragEvent', 'getRawSlots'): ('java.util.Set<java.lang.Integer>', None),
    ('org/bukkit/scoreboard/Team', 'getEntries'): ('java.util.Set<java.lang.String>', None),
    ('org/bukkit/plugin/ServicesManager', 'getRegistration'): ('<T> org/bukkit/plugin/RegisteredServiceProvider<T>', ['java.lang.Class<T>']),
    ('org/bukkit/plugin/RegisteredServiceProvider', 'getProvider'): ('T', None),
    ('org/bukkit/World', 'getChunkAtAsync'): ('java.util.concurrent.CompletableFuture<org/bukkit/Chunk>', None),
    ('org/bukkit/entity/Player', 'teleportAsync'): ('java.util.concurrent.CompletableFuture<java.lang.Boolean>', None),
    ('org/bukkit/command/Command', '<init>'): (None, ['java.lang.String', 'java.lang.String', 'java.lang.String', 'java.util.List<java.lang.String>']),
}

# override yang berbeda per overload (owner, name, desc) -> (return, params)
GENERICS_BY_DESC = {
    ('org/bukkit/inventory/meta/ItemMeta', 'lore', '()Ljava/util/List;'): (
        'java.util.List<net/kyori/adventure/text/Component>', None),
    ('org/bukkit/inventory/meta/ItemMeta', 'lore', '(Ljava/util/List;)V'): (
        'void', ['java.util.List<net/kyori/adventure/text/Component>']),
}

VARARGS = {('org/bukkit/inventory/PlayerInventory', 'addItem')}

THROWS = {
    ('org/bukkit/configuration/file/YamlConfiguration', 'load'): ['java.io.IOException', 'org.bukkit.configuration.InvalidConfigurationException'],
    ('org/bukkit/configuration/file/YamlConfiguration', 'save'): ['java.io.IOException'],
    ('org/bukkit/configuration/file/FileConfiguration', 'save'): ['java.io.IOException'],
}

# member tambahan yang dibutuhkan source (dipakai plugin lewat @Override / pewarisan)
EXTRA_RAW_MEMBERS = {
    'org/bukkit/command/Command': ['public Command() {}'],
    'net/kyori/adventure/text/Component': ['static net.kyori.adventure.text.TextComponent text(String content) { return null; }'],
}

EXTRA_MEMBERS = {
    'org/bukkit/inventory/InventoryHolder': ['Inventory getInventory();'],
    'org/bukkit/command/Command': [
        'public boolean execute(CommandSender sender, String commandLabel, String[] args) { return false; }',
        'public String getLabel() { return null; }',
        'public String getPermission() { return null; }',
        'public void setPermission(String permission) {}',
        'public String getPermissionMessage() { return null; }',
        'public void setPermissionMessage(String message) {}',
        'public java.util.List<String> getAliases() { return null; }',
        'public String getDescription() { return null; }',
        'public String getUsage() { return null; }',
    ],
    'org/bukkit/command/CommandExecutor': ['boolean onCommand(CommandSender sender, Command command, String label, String[] args);'],
    'org/bukkit/command/TabCompleter': ['java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);'],
    'org/bukkit/plugin/java/JavaPlugin': [
        'public void onEnable() {}', 'public void onDisable() {}', 'public void onLoad() {}',
        'public boolean isEnabled() { return false; }',
        'public org.bukkit.configuration.file.FileConfiguration getConfig() { return null; }',
        'public void saveConfig() {}', 'public void reloadConfig() {}', 'public void saveDefaultConfig() {}',
        'public org.bukkit.Server getServer() { return null; }',
        'public String getName() { return null; }',
        'public io.papermc.paper.plugin.configuration.PluginMeta getPluginMeta() { return null; }',
        'public org.bukkit.command.PluginCommand getCommand(String name) { return null; }',
    ],
    'org/bukkit/plugin/Plugin': [
        'java.io.File getDataFolder();', 'java.util.logging.Logger getLogger();',
        'java.io.InputStream getResource(String filename);', 'void saveResource(String resourcePath, boolean replace);',
        'boolean isEnabled();', 'org.bukkit.Server getServer();', 'String getName();',
        'io.papermc.paper.plugin.configuration.PluginMeta getPluginMeta();',
    ],
    'net/kyori/adventure/text/Component': [
    ],
    'org/bukkit/entity/Entity': ['java.util.UUID getUniqueId();', 'String getName();'],
    'org/bukkit/command/CommandSender': ['org.bukkit.Server getServer();'],
    'org/bukkit/scoreboard/Team': ['void setDisplayName(String name);'],
}

EXTRA_ENUM_CONSTANTS = {
    'org/bukkit/GameMode': ['SURVIVAL', 'ADVENTURE', 'SPECTATOR'],
    'org/bukkit/World$Environment': ['NORMAL', 'THE_END'],
    'org/bukkit/event/inventory/InventoryType': ['PLAYER', 'CRAFTING', 'FURNACE', 'DISPENSER', 'HOPPER', 'BARREL', 'SHULKER_BOX'],
    'org/bukkit/event/inventory/InventoryAction': ['NOTHING', 'PICKUP_ALL', 'PLACE_ALL', 'MOVE_TO_OTHER_INVENTORY', 'DROP_ALL_SLOT', 'DROP_ONE_SLOT', 'HOTBAR_SWAP', 'UNKNOWN'],
    'org/bukkit/event/inventory/ClickType': ['SHIFT_LEFT', 'SHIFT_RIGHT', 'WINDOW_BORDER_LEFT', 'WINDOW_BORDER_RIGHT', 'NUMBER_KEY', 'OFFHAND', 'UNKNOWN'],
    'org/bukkit/scoreboard/DisplaySlot': ['BELOW_NAME', 'PLAYER_LIST', 'SIDEBAR_TEAM_RED'],
    'org/bukkit/event/EventPriority': ['LOWEST', 'LOW', 'NORMAL', 'HIGH', 'HIGHEST'],
    'net/kyori/adventure/text/format/TextDecoration': ['BOLD', 'UNDERLINED', 'STRIKETHROUGH', 'OBFUSCATED'],
    'net/kyori/adventure/sound/Sound$Source': ['AMBIENT', 'BLOCK', 'HOSTILE', 'MUSIC', 'NEUTRAL', 'PLAYER', 'RECORD', 'VOICE', 'WEATHER'],
    'org/bukkit/persistence/PersistentDataType': ['BYTE', 'BYTE_ARRAY', 'DOUBLE', 'FLOAT', 'INTEGER', 'INTEGER_ARRAY', 'LONG_ARRAY', 'TAG_CONTAINER', 'TAG_CONTAINER_ARRAY'],
}


# --------------------------------------------------------------------------- #
# 3b. API untuk FITUR BARU (tidak dirujuk JAR asli, jadi tidak muncul dari scan)
#
#     Setiap member di bawah menjadi referensi bytecode sungguhan di JAR rilis,
#     jadi hanya API Bukkit yang stabil sejak lama yang boleh masuk daftar ini.
#     Kode fitur baru juga melakukan "probe" saat enable (lihat SkillApiProbe)
#     dan menonaktifkan buff/sumber XP yang API-nya tidak ada di server, sehingga
#     ketidakcocokan versi tidak pernah berujung NoSuchMethodError.
# --------------------------------------------------------------------------- #

FORCED_TYPES = {
    # event memancing: hanya dipakai SkillFishingListener yang didaftarkan bersyarat
    'org/bukkit/event/player/PlayerFishEvent',
    # penyebab damage (dibaca lewat getCause().name(), TIDAK pernah merujuk konstanta)
    'org/bukkit/event/entity/EntityDamageEvent$DamageCause',
    # v1.4.0 - custom fishing: kail (bonus kecepatan rod) & biome (syarat ikan custom).
    # Biome dibaca HANYA lewat String.valueOf(...) supaya aman baik saat Biome berupa enum
    # (API lama) maupun interface registry (API baru) - konstanta tidak pernah dirujuk.
    'org/bukkit/entity/FishHook',
    'org/bukkit/block/Biome',
    # status memancing (BITE/CAUGHT_FISH/...) - dipakai FishingListener v1.4.0
    'org/bukkit/event/player/PlayerFishEvent$State',
}

FORCED_MEMBERS = {
    'org/bukkit/event/player/PlayerFishEvent': [
        ('getPlayer', '()Lorg/bukkit/entity/Player;', False),
        ('getCaught', '()Lorg/bukkit/entity/Entity;', False),
        # v1.4.0 - rod: bonus kecepatan gigitan (dibungkus try/catch di pemanggil)
        ('getHook', '()Lorg/bukkit/entity/FishHook;', False),
        ('getState', '()Lorg/bukkit/event/player/PlayerFishEvent$State;', False),
    ],
    'org/bukkit/entity/FishHook': [
        # ada sejak 1.16; selalu dipanggil dalam try/catch
        ('setWaitTime', '(I)V', False),
    ],
    'org/bukkit/event/inventory/InventoryClickEvent': [
        # menu /rod: kurangi item attachment yang diklik di inventory pemain
        ('setCurrentItem', '(Lorg/bukkit/inventory/ItemStack;)V', False),
    ],
    # v1.4.0 - gear history & custom fishing (semua API stabil sejak Bukkit lama):
    'org/bukkit/World': [
        ('getTime', '()J', False),          # syarat waktu ikan custom (siang/malam)
        ('hasStorm', '()Z', False),         # syarat cuaca ikan custom
        ('isThundering', '()Z', False),
    ],
    'org/bukkit/event/block/BlockPlaceEvent': [
        # anti-abuse skill: blok yang baru dipasang pemain tidak memberi XP saat dipecah
        ('getBlock', '()Lorg/bukkit/block/Block;', False),
    ],
    'org/bukkit/inventory/PlayerInventory': [
        # bonus set armor (gear): hitung potongan armor yang cocok
        ('getArmorContents', '()[Lorg/bukkit/inventory/ItemStack;', False),
        # getItemInMainHand/getArmorContents mengembalikan SALINAN di CraftBukkit -
        # perubahan PDC gear wajib ditulis balik lewat setter ini
        ('setArmorContents', '([Lorg/bukkit/inventory/ItemStack;)V', False),
    ],
    'org/bukkit/event/entity/EntityDamageEvent': [
        ('getDamage', '()D', False),
        ('setDamage', '(D)V', False),
        ('getCause', '()Lorg/bukkit/event/entity/EntityDamageEvent$DamageCause;', False),
    ],
    'org/bukkit/entity/LivingEntity': [
        ('getHealth', '()D', False),
        ('setHealth', '(D)V', False),
        # perk gear "bleeding": damage berkala pada korban (lewat pipeline damage normal)
        ('damage', '(D)V', False),
    ],
    'org/bukkit/entity/Player': [
        ('getWalkSpeed', '()F', False),
        ('setWalkSpeed', '(F)V', False),
    ],
    'org/bukkit/block/Block': [
        ('getLocation', '()Lorg/bukkit/Location;', False),
        ('getWorld', '()Lorg/bukkit/World;', False),
        ('getDrops', '()Ljava/util/Collection;', False),
        # v1.4.0 - custom fishing: syarat biome (nama dibaca via String.valueOf, aman
        # untuk Biome enum lama maupun interface registry baru)
        ('getBiome', '()Lorg/bukkit/block/Biome;', False),
    ],
    'org/bukkit/event/block/BlockBreakEvent': [
        ('getBlock', '()Lorg/bukkit/block/Block;', False),
        # buff VANILLA_XP (mining): XP vanilla dari ore dibaca lalu dikalikan
        ('getExpToDrop', '()I', False),
        ('setExpToDrop', '(I)V', False),
    ],
    'org/bukkit/event/entity/EntityDeathEvent': [
        ('getEntity', '()Lorg/bukkit/entity/LivingEntity;', False),
        # buff MOB_LOOT (fighting/archery): satu drop mob diduplikasi bila peluang terpenuhi
        ('getDrops', '()Ljava/util/List;', False),
    ],
    'org/bukkit/entity/Item': [
        ('getItemStack', '()Lorg/bukkit/inventory/ItemStack;', False),
        # custom fishing: tangkapan vanilla diganti ikan custom sebelum masuk inventory
        ('setItemStack', '(Lorg/bukkit/inventory/ItemStack;)V', False),
    ],
}

# Konstanta Material tambahan untuk fitur v1.4.0. Semuanya menjadi getstatic sungguhan di
# bytecode rilis, jadi HANYA material yang pasti ada di Paper 26.2 (semuanya sudah ada sejak
# MC 1.13/1.14) yang boleh masuk daftar ini. Material lain dibaca dari config lewat
# Material.matchMaterial sehingga tidak pernah membuat plugin gagal dimuat.
FORCED_MATERIALS = [
    'FISHING_ROD', 'COD', 'SALMON', 'TROPICAL_FISH', 'PUFFERFISH',
    'LIME_STAINED_GLASS_PANE', 'YELLOW_STAINED_GLASS_PANE', 'ORANGE_STAINED_GLASS_PANE',
    'PURPLE_STAINED_GLASS_PANE', 'MAGENTA_STAINED_GLASS_PANE', 'LIGHT_BLUE_STAINED_GLASS_PANE',
    'PRISMARINE_SHARD', 'PRISMARINE_CRYSTALS', 'HEART_OF_THE_SEA', 'NAUTILUS_SHELL',
    'TRIPWIRE_HOOK', 'STRING', 'LEAD', 'ANVIL', 'EXPERIENCE_BOTTLE', 'AMETHYST_SHARD',
    'YELLOW_DYE', 'GLOWSTONE_DUST', 'FIREWORK_STAR', 'CONDUIT', 'CHAIN',
    'FILLED_MAP', 'PURPLE_STAINED_GLASS_PANE', 'RED_STAINED_GLASS_PANE',
]

# konstanta enum penyebab damage - hanya untuk dokumentasi stub; kode fitur membaca
# getCause().name() dan membandingkannya dengan daftar di config.yml, jadi tidak ada
# referensi getstatic ke konstanta ini di bytecode.
FORCED_ENUM_CONSTANTS = {
    'org/bukkit/event/player/PlayerFishEvent$State': [
        'FISHING', 'CAUGHT_FISH', 'CAUGHT_ENTITY', 'IN_GROUND', 'FAILED_ATTEMPT', 'REEL_IN',
        'BITE', 'LURED',
    ],
    'org/bukkit/event/entity/EntityDamageEvent$DamageCause': [
        'BLOCK_EXPLOSION', 'CONTACT', 'CRAMMING', 'CUSTOM', 'DRYOUT', 'DROWNING', 'ENTITY_ATTACK',
        'ENTITY_EXPLOSION', 'ENTITY_SWEEP_ATTACK', 'FALL', 'FALLING_BLOCK', 'FIRE', 'FIRE_TICK',
        'FLY_INTO_WALL', 'FREEZE', 'HOT_FLOOR', 'KILL', 'LAVA', 'LIGHTNING', 'MAGIC', 'MELTING',
        'POISON', 'PROJECTILE', 'STARVATION', 'SUFFOCATION', 'SUICIDE', 'THORNS', 'VOID', 'WITHER',
    ],
}


# --------------------------------------------------------------------------- #
# 4. Pemetaan nama tipe & pembangkitan source stub
# --------------------------------------------------------------------------- #

def java_type(tok):
    """Token descriptor ('I', '[Ljava/lang/String;', 'Lorg/bukkit/World;') -> nama Java FQN."""
    dims = 0
    while tok.startswith('['):
        dims += 1
        tok = tok[1:]
    if tok in PRIM:
        base = PRIM[tok]
    elif tok.startswith('L') and tok.endswith(';'):
        base = tok[1:-1].replace('$', '.').replace('/', '.')
    else:
        base = tok.replace('$', '.').replace('/', '.')
    return base + '[]' * dims


def default_value(jtype):
    t = jtype.strip()
    if t == 'void':
        return None
    if t == 'boolean':
        return 'false'
    if t in ('int', 'short', 'byte'):
        return '0'
    if t == 'long':
        return '0L'
    if t == 'float':
        return '0F'
    if t == 'double':
        return '0D'
    if t == 'char':
        return "'\\0'"
    return 'null'


def is_static(owner, name):
    return (owner, '*') in STATIC_METHODS or (owner, name) in STATIC_METHODS


def scan(root):
    files = sorted(glob.glob(os.path.join(root, '**', '*.class'), recursive=True))
    ext_methods, ext_fields, ext_types = defaultdict(set), defaultdict(set), set()
    plugin = {}
    parsed = []
    for path in files:
        cf = ClassFile(open(path, 'rb').read())
        parsed.append(cf)
        plugin[cf.name] = cf
        for raw in [cf.super_name] + cf.interfaces:
            if raw and not raw.startswith(SKIP_OWNERS):
                ext_types.add(raw)
        for f in cf.fields:
            for t in types_in('(' + f['desc'] + ')V') + types_in('()' + f['desc']):
                if not t.startswith(SKIP_OWNERS):
                    ext_types.add(t)
        for m in cf.methods:
            for t in types_in(m['desc']):
                if not t.startswith(SKIP_OWNERS):
                    ext_types.add(t)
        for t in cf.annotation_types():
            if not t.startswith(SKIP_OWNERS):
                ext_types.add(t)
        for (etype, const) in getattr(cf, 'enum_consts', []):
            if not etype.startswith(SKIP_OWNERS):
                ext_types.add(etype)
                ext_fields[etype].add((const, 'L' + etype + ';', True))
        for idx, entry in cf.cp.items():
            if entry[0] == 7:
                raw = cf.utf[entry[1]]
                if not raw.startswith(SKIP_OWNERS):
                    ext_types.add(raw)
        for op, owner, name, desc in cf.refs():
            for t in [owner] + (types_in(desc) if ')' in desc else re.findall(r'L([A-Za-z0-9_$/]+);', desc)):
                if not t.startswith(SKIP_OWNERS):
                    ext_types.add(t)
            if owner.startswith(SKIP_OWNERS):
                continue
            static = op in (0xB8, 0xB2, 0xB3)
            if op in (0xB2, 0xB3):
                ext_fields[owner].add((name, desc, static))
            elif op in (0xB4, 0xB5):
                ext_fields[owner].add((name, desc, False))
            else:
                ext_methods[owner].add((name, desc, static))
    # scan B: member dipanggil lewat tipe plugin tapi tidak dideklarasikan plugin -> warisan API
    declared = {}
    for cf in parsed:
        keys = {(m['name'], m['desc']) for m in cf.methods} | {(f['name'], f['desc']) for f in cf.fields}
        declared[cf.name] = keys

    def external_ancestor(name):
        seen = set()
        cur = name
        while cur and cur.startswith('me/w2n/') and cur not in seen:
            seen.add(cur)
            cf = plugin.get(cur)
            if cf is None:
                return None
            if cf.super_name and not cf.super_name.startswith('me/w2n/'):
                return cf.super_name
            cur = cf.super_name
        return None

    inherited = defaultdict(set)
    for cf in parsed:
        for op, owner, name, desc in cf.refs():
            if not owner.startswith('me/w2n/'):
                continue
            if is_object_member(name, desc) or name == '<init>':
                continue
            cur, found = owner, False
            while cur and cur.startswith('me/w2n/'):
                if (name, desc) in declared.get(cur, set()):
                    found = True
                    break
                cur = plugin[cur].super_name if cur in plugin else None
            if found:
                continue
            anc = external_ancestor(owner)
            if anc and not anc.startswith(SKIP_OWNERS):
                inherited[anc].add((name, desc, op in (0xB8, 0xB2, 0xB3)))
    # scan C: @Override pada class plugin -> harus ada di supertipe eksternal
    for cf in parsed:
        for name, desc in cf.overridden():
            if is_object_member(name, desc):
                continue
            targets = set()
            sup = cf.super_name
            if sup and not sup.startswith(SKIP_OWNERS):
                targets.add(sup)
            for ifc in cf.interfaces:
                if not ifc.startswith(SKIP_OWNERS):
                    targets.add(ifc)
            if not targets:
                anc = external_ancestor(cf.name)
                if anc and not anc.startswith(SKIP_OWNERS):
                    targets.add(anc)
            for t in targets:
                inherited[t].add((name, desc, False))
    for owner, members in inherited.items():
        for (name, desc, static) in members:
            if is_object_member(name, desc):
                continue
            ext_methods[owner].add((name, desc, static))
            ext_types.add(owner)
    return ext_types, ext_methods, ext_fields


QUALIFY = {}


def qualify(src):
    def rep(m):
        word = m.group(0)
        return QUALIFY.get(word, word)
    return re.sub(r'(?<![.\w$])[A-Z][A-Za-z0-9_]*\b', rep, src)


def kind_of(name, methods, fields):
    if name == 'org/bukkit/event/EventHandler':
        return 'annotation'
    if name in ENUMS:
        return 'enum'
    if name in INTERFACES:
        return 'interface'
    if name in ABSTRACT_CLASSES:
        return 'abstract'
    names = {n for (n, d, s) in methods}
    if 'values' in names and any(d.startswith('()[L') and n == 'values' for (n, d, s) in methods):
        return 'enum'
    if fields and all(s for (n, d, s) in fields) and any(d == 'L' + name + ';' for (n, d, s) in fields):
        return 'enum'
    return 'class'


def emit(types, methods, fields, outdir):
    global QUALIFY
    simple = defaultdict(list)
    for t in types:
        simple[t.split('/')[-1].split('$')[-1]].append(t)
    QUALIFY = {k: v[0].replace('$', '.').replace('/', '.') for k, v in simple.items() if len(v) == 1}

    outers = defaultdict(list)
    for t in sorted(types):
        outers[t.split('$')[0]].append(t)

    written = 0
    for outer, group in sorted(outers.items()):
        pkg = outer.rsplit('/', 1)[0].replace('/', '.') if '/' in outer else ''
        lines = ['// Stub API dibangkitkan otomatis oleh tools/gen_stubs.py - HANYA untuk kompilasi.',
                 '// Jangan dipakai saat runtime: server menyediakan kelas asli Paper/Adventure/Vault.',
                 f'package {pkg};', '',
                 'import java.io.File;', 'import java.io.InputStream;', 'import java.io.IOException;',
                 'import java.util.Collection;', 'import java.util.HashMap;', 'import java.util.List;',
                 'import java.util.Map;', 'import java.util.Set;', 'import java.util.UUID;',
                 'import java.util.concurrent.CompletableFuture;', 'import java.util.logging.Logger;', '']
        outer_lines = render(outer, methods, fields, nested=False)
        nested_lines = []
        for name in group:
            if name != outer:
                nested_lines.extend(render(name, methods, fields, nested=True))
        lines.extend(outer_lines[:-1] + nested_lines + outer_lines[-1:])
        path = os.path.join(outdir, outer + '.java')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as fh:
            fh.write('\n'.join(lines) + '\n')
        written += 1
    return written


def render(name, methods, fields, nested=False):
    kind = kind_of(name, methods.get(name, set()), fields.get(name, set()))
    short = name.rsplit('/', 1)[-1].split('$')[-1]
    pad = '   ' if nested else ''
    mod = 'public ' if (not nested or name in PUBLIC_NESTED) else ''
    out = []
    if kind == 'annotation':
        # RETENTION RUNTIME ITU WAJIB. Bukkit mendaftarkan handler lewat refleksi
        # (method.isAnnotationPresent(EventHandler.class)); tanpa @Retention(RUNTIME), Java
        # memakai RetentionPolicy.CLASS sehingga anotasi TIDAK terlihat saat runtime dan
        # seluruh @EventHandler di kelas hasil kompilasi ulang diam-diam tidak pernah
        # dipanggil (listener terdaftar tapi "tuli": GUI tidak dibatalkan, XP tidak masuk).
        out.append(f'{pad}@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)')
        out.append(f'{pad}@java.lang.annotation.Target({{java.lang.annotation.ElementType.METHOD}})')
        out.append(f'{pad}{mod}@interface {short} {{')
        out.append(f'{pad}   org.bukkit.event.EventPriority priority() default org.bukkit.event.EventPriority.NORMAL;')
        out.append(f'{pad}   boolean ignoreCancelled() default false;')
        out.append(f'{pad}}}')
        return out
    head = f'{pad}{mod}'
    if kind == 'enum':
        head += f'enum {short}'
    elif kind == 'interface':
        head += f'interface {short}'
    elif kind == 'abstract':
        head += f'abstract class {short}'
    else:
        head += f'class {short}'
    extends, implements = HIER.get(name, (None, []))
    # generics pada deklarasi kelas
    if name == 'org/bukkit/plugin/RegisteredServiceProvider':
        head += '<T>'
    parts = []
    dn = lambda t: t.replace('$', '.').replace('/', '.')
    if kind == 'interface':
        parents = ([extends] if extends else []) + list(implements)
        if parents:
            parts.append('extends ' + ', '.join(dn(p) for p in parents))
    elif kind != 'enum':
        if extends:
            parts.append('extends ' + dn(extends))
        if implements:
            parts.append('implements ' + ', '.join(dn(i) for i in implements))
    if parts:
        head += ' ' + ' '.join(parts)
    out.append(head + ' {')
    body = []
    if kind == 'enum':
        consts = sorted(n for (n, d, s) in fields.get(name, set()))
        for extra in EXTRA_ENUM_CONSTANTS.get(name, []):
            if extra not in consts:
                consts.append(extra)
        consts = [c for c in consts if c.isupper() or '_' in c]
        if consts:
            body.append(pad + '   ' + ', '.join(consts) + ';')
        else:
            body.append(pad + '   ;')
    else:
        for (fname, fdesc, fstatic) in sorted(fields.get(name, set())):
            if is_object_member(fname, fdesc):
                continue
            jt = java_type(fdesc)
            if kind == 'interface':
                body.append(f'{pad}   {jt} {fname} = null;')
            else:
                body.append(f'{pad}   public static final {jt} {fname} = {default_value(jt) or "null"};')
        for extra in EXTRA_ENUM_CONSTANTS.get(name, []):
            if kind == 'interface':
                body.append(f'{pad}   {short} {extra} = null;')
            else:
                body.append(f'{pad}   public static final {short} {extra} = null;')
    for (mname, mdesc, mstatic) in sorted(methods.get(name, set())):
        if kind == 'enum' and mname in ('values', 'valueOf', 'name', 'ordinal'):
            continue
        if mname == 'getClass':
            continue
        if is_object_member(mname, mdesc):
            if kind != 'interface':
                continue
            if mname in ('notify', 'notifyAll', 'wait'):
                continue
            body.append(f'{pad}   boolean equals(java.lang.Object p0);' if mname == 'equals'
                        else (f'{pad}   int hashCode();' if mname == 'hashCode' else f'{pad}   java.lang.String toString();'))
            continue
        params, ret = split_params(mdesc)
        g = GENERICS_BY_DESC.get((name, mname, mdesc)) or GENERICS.get((name, mname))
        if g and g[0]:
            ret_j = qualify_generic(g[0])
        else:
            ret_j = java_type(ret)
        if g and g[1] and len(g[1]) == len(params):
            param_j = [qualify_generic(p) for p in g[1]]
        else:
            param_j = [java_type(p) for p in params]
        if (name, mname) in VARARGS and param_j:
            last = param_j[-1]
            if last.endswith('[]'):
                param_j[-1] = last[:-2] + '...'
        args = ', '.join(f'{t} p{i}' for i, t in enumerate(param_j))
        prefix = 'static ' if mstatic else ''
        throws = THROWS.get((name, mname))
        thr = (' throws ' + ', '.join(throws)) if throws else ''
        if mname == '<init>':
            body.append(pad + '   public ' + short + '(' + args + ') {}')
            continue
        if kind == 'interface':
            if mstatic:
                dv0 = default_value(ret_j)
                impl0 = ' {}' if dv0 is None else f' {{ return {dv0}; }}'
                body.append(f'{pad}   {prefix}{ret_j} {mname}({args}){thr}{impl0}')
            else:
                dv1 = default_value(ret_j)
                impl1 = ' {}' if dv1 is None else f' {{ return {dv1}; }}'
                body.append(f'{pad}   default {ret_j} {mname}({args}){thr}{impl1}')
        else:
            dv = default_value(ret_j)
            impl = ' {}' if dv is None else f' {{ return {dv}; }}'
            body.append(f'{pad}   public {prefix}{ret_j} {mname}({args}){thr}{impl}')
    emitted = set()
    for line in body:
        m = re.search(r'([A-Za-z0-9_$]+)\s*\(', line)
        if m and m.group(1) != short:   # konstruktor tidak ikut dedup
            emitted.add(m.group(1))
    for extra in EXTRA_RAW_MEMBERS.get(name, []) + EXTRA_MEMBERS.get(name, []):
        text = extra if extra in EXTRA_RAW_MEMBERS.get(name, []) else qualify(extra)
        m = re.search(r'([A-Za-z0-9_$]+)\s*\(', text)
        if m and m.group(1) != short and m.group(1) in emitted:
            continue
        if m:
            emitted.add(m.group(1))
        body.append(pad + '   ' + text)
    out.extend(body if body else [pad + '   '])
    out.append(f'{pad}}}')
    return out


def qualify_generic(text):
    return text.replace('$', '.').replace('/', '.')


def main():
    src, outdir = sys.argv[1], sys.argv[2]
    types, methods, fields = scan(src)
    types |= FORCED_TYPES
    for owner, members in FORCED_MEMBERS.items():
        types.add(owner)
        methods[owner].update(members)
    EXTRA_ENUM_CONSTANTS.setdefault('org/bukkit/Material', [])
    for const in FORCED_MATERIALS:
        if const not in EXTRA_ENUM_CONSTANTS['org/bukkit/Material']:
            EXTRA_ENUM_CONSTANTS['org/bukkit/Material'].append(const)
    for owner, consts in FORCED_ENUM_CONSTANTS.items():
        types.add(owner)
        EXTRA_ENUM_CONSTANTS.setdefault(owner, [])
        for const in consts:
            if const not in EXTRA_ENUM_CONSTANTS[owner]:
                EXTRA_ENUM_CONSTANTS[owner].append(const)
    n = emit(types, methods, fields, outdir)
    total_m = sum(len(v) for v in methods.values())
    total_f = sum(len(v) for v in fields.values())
    print(f'tipe eksternal : {len(types)}')
    print(f'member dirujuk : {total_m} method, {total_f} field')
    print(f'file stub      : {n} -> {outdir}')


if __name__ == '__main__':
    main()
