#!/usr/bin/env python3
"""Wygeneruj syntetyczny katalog i paczkę multimediów dla Prawko Katalog TEST."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

from build_media_packs import build_packs


def file_sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def encode_json(value: dict) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def run_ffmpeg(ffmpeg: str, output: Path, *args: str) -> None:
    command = [ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-n", *args, str(output)]
    try:
        subprocess.run(command, check=True, capture_output=True, encoding="utf-8", errors="replace", timeout=60)
    except FileNotFoundError as error:
        raise ValueError("Nie znaleziono FFmpeg. Dodaj go do PATH lub podaj --ffmpeg.") from error
    except subprocess.CalledProcessError as error:
        raise ValueError(f"FFmpeg nie utworzył pliku {output.name}. {error.stderr.strip()[:500]}") from error
    except subprocess.TimeoutExpired as error:
        raise ValueError(f"Przekroczono czas generowania pliku {output.name}.") from error


def store_media(source: Path, catalog_dir: Path) -> dict:
    path = f"media/{file_sha256(source)}{source.suffix}"
    source.rename(catalog_dir / path)
    return {"role": "QUESTION", "originalName": source.name, "assetPath": path}


def demo_catalog(photo: dict, video: dict) -> dict:
    catalog = {
        "schemaVersion": 1,
        "source": "DEMO",
        "sourceReference": "DEMO · Syntetyczny test zdjęcia i filmu",
        "category": "B",
        "questions": [
            {
                "id": "DEMO-MEDIA-01",
                "prompt": "DEMO · Jaki wzór widać u góry zdjęcia?",
                "type": "SPECIALIST",
                "points": 1,
                "correctAnswer": "A",
                "optionA": "Pionowe kolorowe pasy",
                "optionB": "Czarno-biała kratka",
                "optionC": "Jednolite niebieskie tło",
                "categories": ["B"],
                "translations": [],
                "media": [photo],
            },
            {
                "id": "DEMO-MEDIA-02",
                "prompt": "DEMO · Czy wzór testowy zmienia się w trakcie filmu?",
                "type": "BASIC",
                "points": 1,
                "correctAnswer": "YES",
                "optionA": None,
                "optionB": None,
                "optionC": None,
                "categories": ["B"],
                "translations": [],
                "media": [video],
            },
        ],
    }
    # Room treats catalog versions as immutable. A change to any record or asset gets a new version.
    digest = hashlib.sha256(encode_json(catalog)).hexdigest()
    return {**catalog, "sourceSha256": digest, "datasetVersion": f"DEMO-media-{digest[:16]}"}


def generate(output: Path, ffmpeg: str) -> tuple[str, str, str]:
    if output.exists() or output.is_symlink():
        raise ValueError("Katalog wyjściowy już istnieje. Wskaż nowy katalog przez --output-dir.")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".demo-media-", dir=output.parent) as temporary:
        work = Path(temporary)
        catalog_dir = work / "catalog"
        (catalog_dir / "media").mkdir(parents=True)

        photo = work / "DEMO-pasy.jpg"
        run_ffmpeg(
            ffmpeg, photo, "-f", "lavfi", "-i", "smptebars=size=640x360",
            "-frames:v", "1", "-c:v", "mjpeg", "-q:v", "3", "-pix_fmt", "yuvj420p", "-update", "1",
        )
        video = work / "DEMO-wzor.mp4"
        run_ffmpeg(
            ffmpeg, video, "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=8",
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=8",
            "-c:v", "libx264", "-profile:v", "baseline", "-pix_fmt", "yuv420p", "-crf", "22",
            "-c:a", "aac", "-b:a", "96k", "-af", "volume=0.25", "-shortest", "-movflags", "+faststart",
        )

        photo_ref = store_media(photo, catalog_dir)
        video_ref = store_media(video, catalog_dir)
        catalog = demo_catalog(photo_ref, video_ref)
        (catalog_dir / "questions.json").write_bytes(encode_json(catalog))

        packs_dir = work / "packs"
        build_packs(catalog_dir, packs_dir, max_pack_mib=32)
        (archive,) = packs_dir.glob("*.zip")
        archive.rename(catalog_dir / archive.name)
        archive_sha256 = file_sha256(catalog_dir / archive.name)
        checksums = [f"{archive_sha256}  {archive.name}\n"]
        for name in ("questions.json", photo_ref["assetPath"], video_ref["assetPath"]):
            checksums.append(f"{file_sha256(catalog_dir / name)}  {name}\n")
        (catalog_dir / "SHA256SUMS").write_text("".join(checksums), encoding="utf-8", newline="\n")

        catalog_dir.rename(output)
    return catalog["datasetVersion"], archive.name, archive_sha256


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=Path("local-data/media-demo"))
    parser.add_argument("--ffmpeg", default="ffmpeg", help="Ścieżka do FFmpeg obsługującego libx264 i AAC")
    args = parser.parse_args()
    try:
        version, archive, checksum = generate(args.output_dir, args.ffmpeg)
    except (OSError, ValueError) as error:
        parser.exit(2, f"{error}\n")
    print(f"Wersja katalogu {version}")
    print(f"Katalog pytań {args.output_dir / 'questions.json'}")
    print(f"Paczka mediów {args.output_dir / archive}")
    print(f"SHA-256 paczki ZIP {checksum}")
    print(f"Sumy kontrolne plików {args.output_dir / 'SHA256SUMS'}")


if __name__ == "__main__":
    main()
