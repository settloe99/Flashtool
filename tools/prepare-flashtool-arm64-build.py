#!/usr/bin/env python3
"""Prepare verified builder-side inputs for Flashtool's existing ARM64 Gradle build.

Run from the repository root. Does not change Git-tracked files or create a DMG.
The native cache requires byte-for-byte copies of the previously verified tools.
"""

import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile

JDK_VERSION = '17.0.20.1+1'
JDK_ARCHIVE = 'OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.20.1_1.tar.gz'
JDK_RELEASE_URL = ('https://github.com/adoptium/temurin17-binaries/releases/download/'
                   'jdk-17.0.20.1%2B1/' + JDK_ARCHIVE)
NATIVE_FILES = {
    'adb': 0o755,
    'fastboot': 0o755,
    'unyaffs': 0o755,
    'udev/libusbx-1.0.0.dylib': 0o644,
}


def sha256(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def read_jdk_pin(path):
    require(path.is_file(), f'Missing committed JDK checksum manifest: {path}')
    lines = [line.strip() for line in path.read_text().splitlines() if line.strip()]
    require(len(lines) == 1, f'Expected one JDK checksum line in {path}')
    m = re.fullmatch(r'([0-9a-fA-F]{64})\s+([^\s]+)', lines[0])
    require(m is not None and m.group(2) == JDK_ARCHIVE,
            f'Unexpected archive name or invalid checksum in {path}')
    return m.group(1).lower()


def read_native_pins(path):
    require(path.is_file(), f'Missing committed native pin configuration: {path}')
    data = path.read_text()
    matched = re.findall(r"^\s*'([^']+)'\s*:\s*'([0-9a-fA-F]{64})'", data, re.M)
    result = {name: checksum.lower() for name, checksum in matched}
    require(len(matched) == len(result) == len(NATIVE_FILES)
            and set(result) == set(NATIVE_FILES),
            f'Native checksum list differs from expected four entries: {path}')
    return result


def verify_runtime(home):
    release = home / 'release'
    jlink = home / 'bin/jlink'
    require(release.is_file() and jlink.is_file(),
            f'Extracted JDK missing release metadata or jlink: {home}')
    release_text = release.read_text()
    require('JAVA_VERSION="17.0.20.1"' in release_text,
            f'Expected Java 17.0.20.1 in {release}')
    subprocess.run([str(home / 'bin/java'), '-version'], check=True)


def prepare_jdk(cache, expected, offline):
    archive = cache / JDK_ARCHIVE
    extracted = cache / 'extracted'
    home = extracted / ('jdk-' + JDK_VERSION) / 'Contents/Home'
    cache.mkdir(parents=True, exist_ok=True)
    if not archive.is_file():
        require(not offline,
                f'JDK archive not cached: {archive}\nRerun without --offline to download the pinned release.')
        print(f'Downloading official Temurin archive: {JDK_ARCHIVE}', flush=True)
        fd, tmp = tempfile.mkstemp(prefix='.temurin-download-', dir=str(cache))
        os.close(fd)
        try:
            subprocess.run(['curl', '--fail', '--location', '--retry', '3',
                            '--output', tmp, JDK_RELEASE_URL], check=True)
            actual = sha256(Path(tmp))
            require(actual == expected,
                    f'Official Temurin checksum mismatch: expected {expected}, got {actual}')
            Path(tmp).replace(archive)
        finally:
            Path(tmp).unlink(missing_ok=True)
    actual = sha256(archive)
    require(actual == expected,
            f'Cached Temurin SHA-256 mismatch: expected {expected}, got {actual}\n'
            f'Remove the incorrect archive yourself before retrying: {archive}')
    print('PASS  Temurin archive SHA-256 matches the committed manifest', flush=True)

    if not extracted.is_dir():
        tmpdir = Path(tempfile.mkdtemp(prefix='.temurin-extract-', dir=str(cache)))
        try:
            subprocess.run(['tar', '-xzf', str(archive), '-C', str(tmpdir)], check=True)
            verify_runtime(tmpdir / ('jdk-' + JDK_VERSION) / 'Contents/Home')
            tmpdir.rename(extracted)
        finally:
            if tmpdir.exists():
                shutil.rmtree(tmpdir)
    else:
        verify_runtime(home)
    print('PASS  Pinned Temurin runtime is ready at ' + str(home), flush=True)


def prepare_native(cache, pins, source):
    cache.mkdir(parents=True, exist_ok=True)
    missing = []
    for relative, mode in NATIVE_FILES.items():
        dest = cache / relative
        if not dest.is_file():
            missing.append(relative)
            continue
        found = sha256(dest)
        require(found == pins[relative],
                f'Pinned native cache mismatch for {relative}: expected {pins[relative]}, got {found}\n'
                'Do not substitute a newer binary automatically.')
        print('PASS  Native ' + relative, flush=True)

    if not missing:
        return
    require(source is not None,
            'Missing native build inputs: ' + ', '.join(missing) + '\n'
            'Provide --native-source DIR containing the previously verified versions.\n'
            'There is not yet a cleared, versioned download source for these exact binaries.')

    source_root = source.expanduser().resolve()
    # Validate ALL source inputs before populating the cache.
    for relative in missing:
        src = source_root / relative
        require(src.is_file(), f'Missing native source: {src}')
        actual = sha256(src)
        require(actual == pins[relative],
                f'Native source checksum mismatch for {relative}: expected {pins[relative]}, got {actual}')

    for relative in missing:
        src, dest = source_root / relative, cache / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        fd, tmppath = tempfile.mkstemp(prefix='.native-stage-', dir=str(dest.parent))
        os.close(fd)
        try:
            shutil.copyfile(src, tmppath)
            Path(tmppath).chmod(NATIVE_FILES[relative])
            require(sha256(Path(tmppath)) == pins[relative],
                    f'Native changed while staging: {relative}')
            os.replace(tmppath, dest)
        finally:
            Path(tmppath).unlink(missing_ok=True)
        print('PASS  Verified and staged native ' + relative, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-source', type=Path,
                        help='Optional directory containing the four exact verified native files')
    parser.add_argument('--offline', action='store_true',
                        help='Refuse to download the Temurin archive if it is missing')
    args = parser.parse_args()
    if platform.system() != 'Darwin' or platform.machine().lower() not in ('arm64', 'aarch64'):
        parser.error('This build preparation script targets macOS Apple Silicon only.')

    repo = Path.cwd()
    require((repo / 'Flashtool/build.gradle').is_file(),
            'Run from the Flashtool repository root (containing Flashtool/build.gradle).')
    gradle = repo / 'Flashtool/gradle'
    expected = read_jdk_pin(gradle / 'temurin-mac-arm64.sha256')
    pins = read_native_pins(gradle / 'mac-arm64-native.gradle')
    cache = Path.home() / '.cache/flashtool'
    prepare_jdk(cache / ('temurin-' + JDK_VERSION), expected, args.offline)
    prepare_native(cache / 'mac-arm64', pins, args.native_source)
    print('\nBuilder-side ARM64 inputs are verified and ready.', flush=True)
    print('Next: bash ./gradlew :Flashtool:createDMG --rerun-tasks', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as exc:
        print('ERROR: ' + str(exc), file=sys.stderr)
        sys.exit(1)
