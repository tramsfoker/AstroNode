#!/usr/bin/env python3
"""Metin tablolarından app/src/main/res/values{,-tr}/strings.xml üretir.
Kullanım: python3 tools/gen_strings.py
Mevcut çevrilmeyecek anahtarlar (default_web_client_id vb.) korunur."""
import os, re, sys, importlib.util
from xml.sax.saxutils import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res')

def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(ROOT, 'tools', name + '.py'))
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m.STRINGS

tables = [t for t in ['strings_core', 'strings_features'] if os.path.exists(os.path.join(ROOT, 'tools', t + '.py'))]
rows = [r for t in tables for r in load(t)]
keys = [r[0] for r in rows]
dups = {k for k in keys if keys.count(k) > 1}
if dups: sys.exit(f'Tekrarlanan anahtarlar: {dups}')

def android_escape(s):
    s = escape(s).replace("'", "\\'").replace('"', '\\"')
    if s.startswith('@') or s.startswith('?'): s = '\\' + s
    return s

def needs_formatted_false(s):
    # Biçim belirteci yoksa ama % varsa (ör. "%%") lint uyarısını önle
    return '%' in s and not re.search(r'%\d+\$', s) and '%%' not in s

FIXED = '''    <string name="app_name" translatable="false">AstroNode</string>
    <string name="default_web_client_id" translatable="false">562733677917-o2n934fdtueeugg83ntfrk2hoaa2rgpn.apps.googleusercontent.com</string>
'''

def write(path, idx, header):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- Bu dosya tools/gen_strings.py ile üretilir — elle düzenleme. -->', '<resources>']
    if header: out.append(FIXED.rstrip('\n'))
    for r in rows:
        v = r[idx]
        attr = ' formatted="false"' if needs_formatted_false(v) else ''
        out.append(f'    <string name="{r[0]}"{attr}>{android_escape(v)}</string>')
    out.append('</resources>')
    open(path, 'w', encoding='utf-8').write('\n'.join(out) + '\n')

write(os.path.join(RES, 'values/strings.xml'), 1, True)
write(os.path.join(RES, 'values-tr/strings.xml'), 2, False)
print(f'{len(rows)} metin yazıldı ({", ".join(tables)})')
