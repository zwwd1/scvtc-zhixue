"""Publish a previously built APK; never accept or use an Android signing private key."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
META = json.loads((ROOT / 'releases/release.json').read_text())
REPO = os.environ['GITHUB_REPOSITORY']
API = 'https://api.github.com/repos/' + REPO
assert REPO == META['repository']
assert re.fullmatch(r'[0-9a-f]{40}', META['sourceCommit'])
assert re.fullmatch(r'\d+\.\d+\.\d+', META['version'])
HEADERS = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2026-03-10'}

def request(url, data=None, mime='application/json', method=None):
    headers = dict(HEADERS)
    if data is not None: headers['Content-Type'] = mime
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=120) as response:
        return json.load(response)

def digest(data):
    return hashlib.sha256(data).hexdigest()

artifact = META['artifact']
assert Path(artifact['file']).name == artifact['file'] and artifact['file'].endswith('.apk')
expected = {
    'zwwd1/scvtc-kejian': ('cn.scvtc.campus.preview', '5cab58b4d47d14e66143be594d3a4eebb5624be7e8f6a15354126bdab47343b3'),
    'zwwd1/scvtc-zhixue': ('cn.scvtc.campus.next', 'f4eb44ef0231232918e6bfe2bae00506d9bb77c49b6f516869fadaf2dcff7aed'),
}[REPO]
assert (artifact['packageName'], artifact['certificateSha256']) == expected
apk = bytearray()
for part in META['parts']:
    assert re.fullmatch(r'[0-9a-f]{40}', part['blob'])
    blob = request(API + '/git/blobs/' + part['blob'])
    assert blob['encoding'] == 'base64'
    data = base64.b64decode(''.join(blob['content'].split()), validate=True)
    assert len(data) == part['size'] and digest(data) == part['sha256']
    apk.extend(data)
assert len(apk) == artifact['size'] and digest(apk) == artifact['sha256']
with tempfile.TemporaryDirectory() as temporary:
    path = Path(temporary) / artifact['file']
    path.write_bytes(apk)
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        assert META['sourceCommit'].encode() in archive.read('META-INF/version-control-info.textproto')
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools'
    tools = sorted((p for p in sdk.iterdir() if (p / 'apksigner').is_file()), key=lambda p: tuple(map(int, re.findall(r'\d+', p.name))))[-1]
    cert = subprocess.check_output([str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(path)], text=True)
    certificate_digests = re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]{64})', cert)
    assert {value.lower() for value in certificate_digests} == {expected[1]}, cert
    manifest = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(path)], text=True)
    assert "package: name='" + expected[0] + "' versionCode='" + str(artifact['versionCode']) + "' versionName='" + META['version'] + "'" in manifest
    assert 'application-debuggable' not in manifest
    subprocess.run([str(tools/'zipalign'), '-c', '-P', '16', '4', str(path)], check=True)

# All checks complete before creating a draft or a public tag.
tag = 'v' + META['version']
try:
    release = request(API + '/releases/tags/' + tag)
except urllib.error.HTTPError as error:
    if error.code != 404: raise
    drafts = request(API + '/releases?per_page=100')
    release = next((r for r in drafts if r['tag_name'] == tag and r['draft']), None)
    if release is None:
        release = request(API + '/releases', json.dumps({'tag_name': tag, 'target_commitish': META['sourceCommit'], 'name': META['name'] + ' ' + META['version'], 'body': (ROOT/'releases'/ (META['version'] + '.md')).read_text(), 'draft': True, 'prerelease': False}, ensure_ascii=False).encode())
assets = {a['name']: a for a in release['assets']}
checksum = (artifact['sha256'] + '  ' + artifact['file'] + '\n').encode()
provenance = (ROOT / 'BUILD_PROVENANCE.json').read_bytes()
for filename, data, mime in [(artifact['file'], apk, 'application/vnd.android.package-archive'), (artifact['file'] + '.sha256', checksum, 'text/plain'), ('BUILD_PROVENANCE.json', provenance, 'application/json')]:
    if filename in assets:
        assert assets[filename].get('digest') == 'sha256:' + digest(data), 'Existing asset differs; it was not overwritten'
        continue
    upload = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(filename)
    result = request(upload, bytes(data), mime)
    assert result['size'] == len(data) and result.get('digest') == 'sha256:' + digest(data)
if release['draft']:
    release = request(API + '/releases/' + str(release['id']), json.dumps({'draft': False, 'make_latest': 'true'}).encode(), method='PATCH')
print('Published verified release: ' + release['html_url'])
