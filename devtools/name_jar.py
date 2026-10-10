"""Rename SRG member names (m_123_, f_123_) to dev names inside a Forge 1.20.1 mod jar, nested jars
included, so the jar can sit in a dev run's mods folder. Usage: name_jar.py <srg file> <in.jar> <out.jar>"""
import io
import re
import struct
import sys
import zipfile

SRG = re.compile(r'[mf]_\d+_')


def load(srg_path):
    names = {}
    for line in open(srg_path, encoding='utf-8'):
        p = line.split()
        if p and p[0] == 'MD:':
            names[p[1].rsplit('/', 1)[1]] = p[3].rsplit('/', 1)[1]
        elif p and p[0] == 'FD:':
            names[p[1].rsplit('/', 1)[1]] = p[2].rsplit('/', 1)[1]
    return names


def rename_class(data, names):
    count = struct.unpack_from('>H', data, 8)[0]
    out = bytearray(data[:10])
    pos, i = 10, 1
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack_from('>H', data, pos + 1)[0]
            raw = data[pos + 3:pos + 3 + n]
            new = names.get(raw.decode('latin-1'), None)
            if new is not None:
                raw = new.encode('ascii')
            out += bytes([1]) + struct.pack('>H', len(raw)) + raw
            pos += 3 + n
        else:
            size = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5,
                    15: 4, 16: 3, 17: 5, 18: 5, 19: 3, 20: 3}[tag]
            out += data[pos:pos + size]
            pos += size
            if tag in (5, 6):
                i += 1
        i += 1
    return bytes(out) + data[pos:]


def rename_jar(data, names):
    out = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as zin, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            body = zin.read(info.filename)
            name = info.filename
            if re.search(r'META-INF/[^/]+\.(SF|RSA|DSA)$', name):
                continue
            if name.endswith('.class'):
                body = rename_class(body, names)
            elif name.endswith('.jar'):
                body = rename_jar(body, names)
            elif name.endswith('accesstransformer.cfg'):
                body = SRG.sub(lambda m: names.get(m.group(0), m.group(0)), body.decode('utf-8')).encode('utf-8')
            # Nested jars must be stored, not compressed, for the loader to open them.
            zout.writestr(name, body, zipfile.ZIP_STORED if name.endswith('.jar') else zipfile.ZIP_DEFLATED)
    return out.getvalue()


if __name__ == '__main__':
    names = load(sys.argv[1])
    assert names['m_21211_'] == 'getUseItem', 'mapping file did not parse'
    open(sys.argv[3], 'wb').write(rename_jar(open(sys.argv[2], 'rb').read(), names))
    print('named', len(names), 'members ->', sys.argv[3])
