"""Sign an already-built release with the established external Stroke Input Method identity.

Windows Python 3; JDK 21 and Android SDK build-tools 37.0.0. No private signing
material is kept in this source tree. Restore the SAME key/certificate on a new
machine and restore the shared password through secure local entry before use.
"""
import argparse
import base64
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import subprocess

EXPECTED_CERT = '61dcd13391bcdd7025083cabd3095c85638c02cacb56a72653c6bc09acec8cb9'
APPLICATION_ID = 'io.github.yawnoc.strokeinput.haoke'

def unseal(blob):
    class Blob(ctypes.Structure):
        _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]
    raw = base64.b64decode(blob, validate=True)
    buf = (ctypes.c_ubyte * len(raw)).from_buffer_copy(raw)
    src, dst = Blob(len(raw), buf), Blob()
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    crypt.CryptUnprotectData.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p,
        ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    if not crypt.CryptUnprotectData(ctypes.byref(src), None, None, None, None, 1, ctypes.byref(dst)):
        raise RuntimeError('Protected credential unavailable; restore it with the original Windows user')
    try:
        return json.loads(ctypes.string_at(dst.data, dst.size))
    finally:
        kernel.LocalFree(dst.data)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--unsigned', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    if os.name != 'nt': raise RuntimeError('This signing entry requires Windows protected credentials')
    signing = Path(os.environ['LOCALAPPDATA']) / 'Haoke/AndroidSigning/strokeinput-release'
    meta = json.loads((signing/'signing-identity.json').read_text('utf-8-sig'))
    assert meta['applicationId'] == APPLICATION_ID and meta['certificateSha256'].lower() == EXPECTED_CERT
    ref_path = Path(meta['credentialsReference'])
    ref = json.loads(ref_path.read_text('utf-8-sig'))
    cred = unseal((ref_path.parent/ref['credentialsFile']).read_bytes())
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk')))
    jdk = Path(os.environ['JAVA_HOME'])
    build_tools = sdk/'build-tools/37.0.0'
    env = os.environ.copy()
    env['STROKE_STORE_PASS'] = cred['store_pass']
    env['STROKE_KEY_PASS'] = cred['key_pass']
    def run(args):
        result = subprocess.run([str(x) for x in args], env=env, capture_output=True, timeout=180)
        if result.returncode:
            raise RuntimeError('Signing/verification failed; credential-bearing process output withheld')
        return result.stdout
    try:
        cert = run([jdk/'bin/keytool.exe','-exportcert','-keystore',signing/meta['keystoreFile'],
                    '-alias',meta['alias'],'-storepass:env','STROKE_STORE_PASS'])
        assert hashlib.sha256(cert).hexdigest() == EXPECTED_CERT, 'Certificate mismatch; never replace the identity'
        aligned = a.unsigned.with_name('app-release-aligned.apk')
        run([build_tools/'zipalign.exe','-P','16','-f','4',a.unsigned,aligned])
        a.out.parent.mkdir(parents=True, exist_ok=True)
        run([jdk/'bin/java.exe','-jar',build_tools/'lib/apksigner.jar','sign',
             '--ks',signing/meta['keystoreFile'],'--ks-key-alias',meta['alias'],
             '--ks-pass','env:STROKE_STORE_PASS','--key-pass','env:STROKE_KEY_PASS',
             '--v4-signing-enabled','false','--out',a.out,aligned])
        verification = run([jdk/'bin/java.exe','-jar',build_tools/'lib/apksigner.jar',
                            'verify','--verbose','--print-certs',a.out]).decode('utf8','replace')
        assert EXPECTED_CERT in verification.lower()
        run([build_tools/'zipalign.exe','-c','-P','16','4',a.out])
        print(json.dumps({'apk':str(a.out.resolve()),'sizeBytes':a.out.stat().st_size,
              'sha256':hashlib.file_digest(a.out.open('rb'),'sha256').hexdigest(),
              'certificateSha256':EXPECTED_CERT},ensure_ascii=False))
    finally:
        env.pop('STROKE_STORE_PASS',None);env.pop('STROKE_KEY_PASS',None);cred.clear()

if __name__ == '__main__':
    main()
