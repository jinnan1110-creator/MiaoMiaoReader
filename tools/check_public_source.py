#!/usr/bin/env python3
"""Check the publication tree, original demo, dictionary hashes, and upgrade SQL."""
from pathlib import Path
import hashlib
import json
import re
import sqlite3
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def check():
    forbidden_names = {'local.properties', 'ielts_vocab.db', 'reader.sqlite'}
    forbidden_extensions = {'.keystore', '.jks', '.p12', '.pfx', '.pem', '.key', '.apk', '.aab', '.idsig', '.zip', '.log'}
    for file in ROOT.rglob('*'):
        if not file.is_file() or any(p in {'.git', 'build', '.gradle', '__pycache__'} for p in file.relative_to(ROOT).parts):
            continue
        if file.name in forbidden_names or file.suffix.lower() in forbidden_extensions:
            raise AssertionError('Private/generated file in public tree: ' + str(file.relative_to(ROOT)))
        if file.stat().st_size > 25 * 1024 * 1024:
            raise AssertionError('File exceeds web-upload size: ' + str(file.relative_to(ROOT)))
        if file.suffix in {'.java', '.gradle', '.html', '.md', '.yml', '.txt', '.properties', '.py'}:
            text = file.read_text()
            if re.search(r'/(?:Users|home)/[A-Za-z0-9_.-]+/', text) or re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----', text):
                raise AssertionError('Private path or key in ' + str(file.relative_to(ROOT)))
    for file in (ROOT / 'app/src').rglob('*.xml'):
        ET.parse(file)
    manifest = json.loads((ROOT / 'dictionary/manifest.json').read_text())
    for part in manifest['parts']:
        data = (ROOT / 'dictionary' / part['file']).read_bytes()
        assert len(data) == part['size'] and hashlib.sha256(data).hexdigest() == part['sha256']
    pages = json.loads((ROOT / 'app/src/main/assets/sample_pages.json').read_text())
    assert len(pages) == 3
    for page in pages:
        tokens = [t for b in page['blocks'] for t in b['tokens']]
        assert len(tokens) > 100
        assert [t['i'] for t in tokens] == list(range(len(tokens)))
        assert all(0 <= t['x'] < 1 and 0 <= t['y'] < 1 for t in tokens)
    store = (ROOT / 'app/src/main/java/com/jinnan/miaomiaoreader/ReaderStore.java').read_text()
    with sqlite3.connect(':memory:') as database:
        database.execute('CREATE TABLE annotations(id INTEGER PRIMARY KEY,doc TEXT,page INTEGER,start_token INTEGER,end_token INTEGER,quote TEXT,translation TEXT,style TEXT,visible INTEGER)')
        database.execute("INSERT INTO annotations VALUES(7,'old',0,2,2,'policy','政策','underline',0)")
        for sql in re.findall(r'db.execSQL\("(ALTER TABLE[^\"]+)"\)', store):
            database.execute(sql)
        assert database.execute('SELECT quote,translation,style,visible,color,sentence FROM annotations').fetchone() == ('policy','政策','underline',0,'honey','')
    assert 'dictionary=seed.exists()?' in store and 'if(dictionary!=null)' in store
    assert 'getAssets().open("ielts_vocab.db")' not in store
    assert 'MM_READER_KEYSTORE' in (ROOT / 'app/build.gradle').read_text()
    print('PASS: public tree, dictionary checksums, original demo, XML and annotation migration.')

if __name__ == '__main__':
    check()
