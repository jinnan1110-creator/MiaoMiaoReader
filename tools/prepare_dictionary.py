#!/usr/bin/env python3
"""Optional command-line equivalent of the Gradle prepareDictionary task."""
from pathlib import Path
import gzip
import hashlib
import json
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def prepare():
    directory = ROOT / 'dictionary'
    manifest = json.loads((directory / 'manifest.json').read_text())
    output = ROOT / 'app/build/generated/dictionaryAssets/general_dictionary.sqlite'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryFile() as combined:
        for part in manifest['parts']:
            data = (directory / part['file']).read_bytes()
            if len(data) != part['size'] or hashlib.sha256(data).hexdigest() != part['sha256']:
                raise ValueError('Dictionary part failed verification: ' + part['file'])
            combined.write(data)
        combined.seek(0)
        temporary = output.with_suffix('.tmp')
        try:
            with gzip.GzipFile(fileobj=combined) as source, temporary.open('wb') as target:
                shutil.copyfileobj(source, target)
            if temporary.stat().st_size != manifest['database_size'] or hashlib.sha256(temporary.read_bytes()).hexdigest() != manifest['database_sha256']:
                raise ValueError('Dictionary database failed verification')
            temporary.replace(output)
        finally:
            if temporary.exists():
                temporary.unlink()
    print('Verified and prepared general dictionary.')
    return output

if __name__ == '__main__':
    prepare()
