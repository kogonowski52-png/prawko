#!/usr/bin/env python3
"""Split prepared catalog media into versioned ZIPs for installation from Android settings."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
from zipfile import ZIP_STORED, ZipFile


MIB = 1024 * 1024
MAX_CATALOG_BYTES = 32 * MIB
MAX_FILE_BYTES = 64 * MIB
MAX_PACK_BYTES = 128 * MIB
MAX_FILES = 128
ZIP_OVERHEAD_BYTES = 128 * 1024
VERSION = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
ASSET = re.compile(r"media/([a-f0-9]{64})\.(jpg|mp4)")


def catalog_media(catalog_dir):
    with (catalog_dir / "questions.json").open("rb") as stream:
        data = stream.read(MAX_CATALOG_BYTES + 1)
    if len(data) > MAX_CATALOG_BYTES:
        raise ValueError("Pakiet pytań przekracza 32 MiB.")
    catalog = json.loads(data)
    if not isinstance(catalog, dict) or catalog.get("schemaVersion") != 1:
        raise ValueError("Oczekiwano questions.json w formacie 1.")
    version = catalog.get("datasetVersion")
    if not isinstance(version, str) or not VERSION.fullmatch(version):
        raise ValueError("Nieprawidłowy identyfikator wersji katalogu.")
    questions = catalog.get("questions")
    if not isinstance(questions, list):
        raise ValueError("Brak listy pytań w katalogu.")

    paths = set()
    for question in questions:
        media = question.get("media") if isinstance(question, dict) else None
        if not isinstance(media, list):
            raise ValueError("Nieprawidłowa lista mediów pytania.")
        for reference in media:
            if not isinstance(reference, dict):
                raise ValueError("Nieprawidłowe odwołanie do mediów.")
            path = reference.get("assetPath")
            if path is not None:
                if not isinstance(path, str) or not ASSET.fullmatch(path):
                    raise ValueError("Ścieżki mediów muszą wskazywać pliki JPG lub MP4 nazwane sumą SHA-256.")
                paths.add(path)
    if not paths:
        raise ValueError("Katalog nie ma gotowych ścieżek mediów. Użyj importera z --prepare-media.")

    files = []
    for path in sorted(paths):
        source = catalog_dir / path
        if source.parent.is_symlink() or source.is_symlink() or not source.is_file():
            raise ValueError(f"Brak regularnego pliku {path}.")
        size = source.stat().st_size
        if not 0 < size <= MAX_FILE_BYTES:
            raise ValueError(f"Nieprawidłowy rozmiar pliku {path}.")
        files.append((path, size))
    return version, files


def pack_groups(files, byte_limit):
    group = []
    total = ZIP_OVERHEAD_BYTES
    for path, size in files:
        if size + ZIP_OVERHEAD_BYTES > byte_limit:
            raise ValueError(f"Plik {path} przekracza limit wybranej paczki. Zwiększ --max-pack-mib.")
        if total + size > byte_limit or len(group) == MAX_FILES:
            yield group
            group = []
            total = ZIP_OVERHEAD_BYTES
        group.append((path, size))
        total += size
    if group:
        yield group


def write_pack(target, catalog_dir, version, pack_id, files, byte_limit):
    manifest = {
        "schemaVersion": 1,
        "datasetVersion": version,
        "packId": pack_id,
        "files": [{"path": path, "sizeBytes": size} for path, size in files],
    }
    with ZipFile(target, "w", compression=ZIP_STORED) as archive:
        archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, separators=(",", ":")))
        for path, size in files:
            digest = hashlib.sha256()
            copied = 0
            with (catalog_dir / path).open("rb") as source, archive.open(path, "w") as destination:
                while chunk := source.read(MIB):
                    copied += len(chunk)
                    if copied > size:
                        raise ValueError(f"Plik {path} zmienił rozmiar podczas tworzenia paczki.")
                    digest.update(chunk)
                    destination.write(chunk)
            if copied != size or digest.hexdigest() != Path(path).stem:
                raise ValueError(f"Zawartość {path} nie zgadza się z nazwą SHA-256 lub rozmiarem.")
    if target.stat().st_size > byte_limit:
        raise ValueError(f"Paczka {target.name} przekroczyła limit rozmiaru.")
    with target.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def build_packs(catalog_dir, output, max_pack_mib):
    if not 1 <= max_pack_mib <= MAX_PACK_BYTES // MIB:
        raise ValueError("--max-pack-mib musi zawierać się w przedziale 1–128.")
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError("Katalog wyjściowy musi być nowy lub pusty.")
    version, files = catalog_media(catalog_dir)
    limit = max_pack_mib * MIB
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix=f".{output.name}.", dir=output.parent))
    try:
        checksums = []
        for index, group in enumerate(pack_groups(files, limit), start=1):
            pack_id = f"pack-{index:03d}"
            name = f"{version}-{pack_id}.zip"
            digest = write_pack(stage / name, catalog_dir, version, pack_id, group, limit)
            checksums.append(f"{digest}  {name}\n")
        (stage / "SHA256SUMS").write_text("".join(checksums), encoding="utf-8")
        os.replace(stage, output)
    finally:
        if stage.exists():
            shutil.rmtree(stage)
    return len(checksums), len(files)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--max-pack-mib", type=int, default=32)
    args = parser.parse_args()
    try:
        packs, files = build_packs(args.catalog_dir, args.output_dir, args.max_pack_mib)
    except (OSError, ValueError) as error:
        parser.exit(2, f"{error}\n")
    print(f"Paczki: {packs}. Pliki: {files}. Sumy SHA-256: {args.output_dir / 'SHA256SUMS'}.")


if __name__ == "__main__":
    main()
