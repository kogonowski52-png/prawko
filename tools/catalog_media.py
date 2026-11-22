"""Match catalog media to ZIP members and prepare Android-compatible assets."""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Iterable
from contextlib import ExitStack
from dataclasses import dataclass
from hashlib import file_digest
from pathlib import Path, PurePosixPath
import os
import stat
import subprocess
import tempfile
import unicodedata
from zipfile import BadZipFile, ZipFile, ZipInfo


_MEDIA_EXTENSIONS = {".jpg", ".jpeg", ".wmv"}
_MAX_MEMBER_BYTES = 512 * 1024 * 1024
_MAX_VIDEO_SECONDS = 600
_COPY_CHUNK_BYTES = 1024 * 1024


@dataclass(frozen=True)
class _Member:
    archive_path: Path
    archive: ZipFile
    info: ZipInfo
    index: int

    @property
    def basename(self) -> str:
        return self.info.filename.rsplit("/", 1)[-1]

    def details(self) -> dict:
        return {
            "archive": str(self.archive_path),
            "member": self.info.filename,
            "entryIndex": self.index,
            "originalSizeBytes": self.info.file_size,
        }


def _extension(name: str) -> str:
    return PurePosixPath(name).suffix.casefold()


def _safe_media_name(name: str) -> bool:
    return bool(name) and name == name.rsplit("/", 1)[-1] and _unsafe_path(name) is None


def _unsafe_path(name: str) -> str | None:
    if any(ord(char) < 32 or ord(char) == 127 for char in name):
        return "controlCharacter"
    if "\\" in name or ":" in name:
        return "unsafePath"
    if any(part in {"", ".", ".."} for part in name.split("/")):
        return "unsafePath"
    return None


def _normalized(name: str) -> str:
    return unicodedata.normalize("NFC", unicodedata.normalize("NFC", name).casefold())


def _member_rejection(info: ZipInfo) -> str | None:
    path_issue = _unsafe_path(info.orig_filename)
    if path_issue:
        return path_issue
    if stat.S_IFMT(info.external_attr >> 16) not in {0, stat.S_IFREG}:
        return "notRegularFile"
    if info.flag_bits & 1:
        return "encrypted"
    if not 0 < info.file_size <= _MAX_MEMBER_BYTES:
        return "invalidSize"
    return None


def _index_archives(
    paths: Iterable[Path | str], stack: ExitStack, report: dict
) -> tuple[dict[str, list[_Member]], dict[str, list[_Member]]]:
    exact = defaultdict(list)
    normalized = defaultdict(list)
    seen_paths = set()
    for path in sorted(map(Path, paths)):
        try:
            actual_path = path.resolve()
            if actual_path in seen_paths:
                continue
            seen_paths.add(actual_path)
            archive = stack.enter_context(ZipFile(actual_path))
        except (BadZipFile, OSError, ValueError, RuntimeError) as error:
            report["archiveErrors"].append({"archive": str(path), "message": str(error)})
            continue

        for index, info in enumerate(archive.infolist()):
            if info.is_dir() or _extension(info.filename) not in _MEDIA_EXTENSIONS:
                continue
            member = _Member(path, archive, info, index)
            reason = _member_rejection(info)
            if reason:
                report["rejectedEntries"].append({**member.details(), "reason": reason})
                continue
            exact[member.basename].append(member)
            normalized[_normalized(member.basename)].append(member)
    return exact, normalized


def _copy_member(member: _Member, destination: Path) -> None:
    size = 0
    with member.archive.open(member.info) as source, destination.open("xb") as target:
        while chunk := source.read(_COPY_CHUNK_BYTES):
            size += len(chunk)
            if size > _MAX_MEMBER_BYTES or size > member.info.file_size:
                raise ValueError("Rozpakowany plik przekracza zadeklarowany rozmiar.")
            target.write(chunk)
    if size != member.info.file_size:
        raise ValueError("Rozpakowany plik ma rozmiar inny niż wpis ZIP.")


def _validate_jpeg(path: Path) -> None:
    with path.open("rb") as source:
        if source.read(3) != b"\xff\xd8\xff":
            raise ValueError("Plik JPG/JPEG nie ma nagłówka JPEG.")
        source.seek(-2, os.SEEK_END)
        if source.read(2) != b"\xff\xd9":
            raise ValueError("Plik JPG/JPEG nie ma znacznika końca obrazu.")


def _transcode(source: Path, destination: Path, ffmpeg: str) -> None:
    command = [
        ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-xerror",
        "-protocol_whitelist", "file", "-threads", "2", "-f", "asf", "-i", str(source),
        "-map", "0:v:0", "-map", "0:a:0?", "-map_metadata", "-1", "-map_chapters", "-1",
        "-filter_threads", "2", "-vf",
        "scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2",
        "-c:v", "libx264", "-profile:v", "main", "-threads", "2", "-preset", "medium", "-crf", "21",
        "-pix_fmt", "yuv420p", "-c:a", "aac", "-ac", "2", "-b:a", "128k",
        "-abort_on", "empty_output+empty_output_stream", "-movflags", "+faststart",
        "-fs", str(_MAX_MEMBER_BYTES), "-n", str(destination),
    ]
    completed = subprocess.run(
        command,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=_MAX_VIDEO_SECONDS,
        check=False,
    )
    if completed.returncode:
        raise ValueError(f"FFmpeg zakończył pracę kodem {completed.returncode}. {completed.stderr.strip()[:500]}")
    size = destination.stat().st_size
    if not 0 < size < _MAX_MEMBER_BYTES:
        raise ValueError("Rozmiar pliku MP4 jest nieprawidłowy lub osiągnął limit.")


def _prepare_member(member: _Member, media_dir: Path, ffmpeg: str) -> str:
    is_video = _extension(member.basename) == ".wmv"
    with tempfile.TemporaryDirectory(prefix=".extract-", dir=media_dir) as temporary:
        work_dir = Path(temporary)
        source = work_dir / ("source.wmv" if is_video else "source.jpg")
        _copy_member(member, source)
        asset = source
        if is_video:
            asset = work_dir / "asset.mp4"
            _transcode(source, asset, ffmpeg)
        else:
            _validate_jpeg(source)

        with asset.open("rb") as stream:
            digest = file_digest(stream, "sha256").hexdigest()
        filename = f"{digest}{asset.suffix}"
        os.replace(asset, media_dir / filename)
        return f"media/{filename}"


def resolve_media(
    required_names: Iterable[str],
    archives: Iterable[Path | str],
    *,
    output_dir: Path | str | None = None,
    prepare: bool = False,
    rights_confirmed: bool = False,
    ffmpeg: str = "ffmpeg",
) -> tuple[dict[str, str], dict]:
    """Index supported media and optionally materialize only unambiguous matches.

    The returned paths are relative to output_dir. The report includes ZIP
    provenance even for matches that have not been extracted.
    """
    archive_paths = tuple(archives)
    media_dir = None
    if prepare:
        if not rights_confirmed:
            raise ValueError("Przygotowanie multimediów wymaga potwierdzenia praw do ich przetwarzania.")
        if output_dir is None or not archive_paths:
            raise ValueError("Przygotowanie multimediów wymaga katalogu wyjściowego i archiwum ZIP.")
        media_dir = Path(output_dir) / "media"
        if media_dir.is_symlink():
            raise ValueError("Katalog multimediów nie może być dowiązaniem symbolicznym.")
        media_dir.mkdir(parents=True, exist_ok=True)

    report = {
        "matched": [],
        "missing": [],
        "ambiguous": [],
        "unreferenced": [],
        "rejectedEntries": [],
        "rejectedReferences": [],
        "archiveErrors": [],
        "failures": [],
    }
    asset_paths = {}
    prepared: dict[_Member, str | None] = {}
    referenced = set()
    with ExitStack() as stack:
        exact, normalized = _index_archives(archive_paths, stack, report)
        for name in sorted(set(required_names)):
            if not _safe_media_name(name) or _extension(name) not in _MEDIA_EXTENSIONS:
                report["rejectedReferences"].append({"originalName": name, "reason": "invalidMediaName"})
                continue

            candidates = exact.get(name)
            match_type = "exact"
            if not candidates:
                candidates = normalized.get(_normalized(name))
                match_type = "normalized"
            if not candidates:
                report["missing"].append(name)
                continue

            referenced.update(candidates)
            if len(candidates) > 1:
                report["ambiguous"].append({
                    "originalName": name,
                    "matchType": match_type,
                    "candidates": [member.details() for member in candidates],
                })
                continue

            member = candidates[0]
            if media_dir is not None and member not in prepared:
                try:
                    prepared[member] = _prepare_member(member, media_dir, ffmpeg)
                except (BadZipFile, OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
                    prepared[member] = None
                    report["failures"].append({**member.details(), "message": str(error)})
            asset_path = prepared.get(member)
            if asset_path:
                asset_paths[name] = asset_path
            report["matched"].append({
                **member.details(),
                "originalName": name,
                "matchType": match_type,
                "assetPath": asset_path,
            })

        report["unreferenced"] = [
            member.details()
            for members in exact.values()
            for member in members
            if member not in referenced
        ]
    return asset_paths, report
