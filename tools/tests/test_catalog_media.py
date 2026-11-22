from base64 import b64decode
from hashlib import sha256
import json
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import unicodedata
import unittest
import warnings
from zipfile import ZipFile, ZipInfo

from tools.catalog_media import resolve_media


# Synthetic 16x16 white JPEG for local preparation tests.
JPEG = b64decode(
    "/9j/4AAQSkZJRgABAgAAAQABAAD//gAQTGF2YzYxLjE5LjEwMQD/2wBDAAgEBAQEBAUFBQUF"
    "BQYGBgYGBgYGBgYGBgYHBwcICAgHBwcGBgcHCAgICAkJCQgICAgJCQoKCgwMCwsODg4RERT/"
    "xABLAAEBAAAAAAAAAAAAAAAAAAAABwEBAAAAAAAAAAAAAAAAAAAAABABAAAAAAAAAAAAAAAA"
    "AAAAABEBAAAAAAAAAAAAAAAAAAAAAP/AABEIABAAEAMBIgACEQADEQD/2gAMAwEAAhEDEQA/"
    "AL+AD//Z"
)


class CatalogMediaTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.output = self.root / "output"

    def test_metadata_scan_resolves_exact_and_unique_normalized_names(self):
        archive_path = self.root / "media.zip"
        with ZipFile(archive_path, "w") as archive:
            archive.writestr("Pierwszy katalog/Łódź.JPEG", JPEG)
            archive.writestr("A/Foto.JPG", JPEG)
            archive.writestr("B/foto.jpg", JPEG)
            archive.writestr("unreferenced.WMV", b"test")
            archive.writestr("../escape.jpg", JPEG)
            symlink = ZipInfo("dir/link.jpg")
            symlink.create_system = 3
            symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(symlink, "../out.jpg")

        łódź_reference = unicodedata.normalize("NFD", "ŁÓDŹ.jpeg")
        paths, report = resolve_media(
            [łódź_reference, "Foto.JPG", "FOTO.jpg", "missing.wmv", "../reference.jpg"],
            [archive_path],
            output_dir=self.output,
        )
        self.assertEqual({}, paths)
        self.assertFalse(self.output.exists())
        matches = {match["originalName"]: match for match in report["matched"]}
        self.assertEqual("exact", matches["Foto.JPG"]["matchType"])
        self.assertEqual("normalized", matches[łódź_reference]["matchType"])
        self.assertIsNone(matches[łódź_reference]["assetPath"])
        self.assertEqual(["missing.wmv"], report["missing"])
        self.assertEqual("FOTO.jpg", report["ambiguous"][0]["originalName"])
        self.assertEqual(2, len(report["ambiguous"][0]["candidates"]))
        self.assertEqual(["unreferenced.WMV"], [file["member"] for file in report["unreferenced"]])
        self.assertEqual(
            {"unsafePath", "notRegularFile"},
            {entry["reason"] for entry in report["rejectedEntries"]},
        )
        self.assertEqual("../reference.jpg", report["rejectedReferences"][0]["originalName"])

    def test_reports_duplicate_exact_entries_and_unreadable_archive(self):
        archive_path = self.root / "duplicates.zip"
        unreadable_path = self.root / "damaged.zip"
        unreadable_path.write_text("DEMO — uszkodzone archiwum")
        with ZipFile(archive_path, "w") as archive:
            archive.writestr("duplicate.jpg", JPEG)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr("duplicate.jpg", JPEG)

        paths, report = resolve_media(["duplicate.jpg"], [archive_path, unreadable_path])
        self.assertEqual({}, paths)
        self.assertEqual("exact", report["ambiguous"][0]["matchType"])
        self.assertEqual([0, 1], [member["entryIndex"] for member in report["ambiguous"][0]["candidates"]])
        self.assertEqual(str(unreadable_path), report["archiveErrors"][0]["archive"])
        self.assertEqual([], report["unreferenced"])

    def test_prepares_only_verified_matches_with_content_addressed_paths(self):
        archive_path = self.root / "media.zip"
        with ZipFile(archive_path, "w") as archive:
            archive.writestr("Folder/DEMO photo.jpeg", JPEG)
            archive.writestr("unused.jpg", JPEG)
            archive.writestr("damaged.jpg", JPEG[:-2])

        with self.assertRaises(ValueError):
            resolve_media(["DEMO photo.jpeg"], [archive_path], output_dir=self.output, prepare=True)
        self.assertFalse(self.output.exists())

        paths, report = resolve_media(
            ["demo photo.JPEG", "DEMO photo.jpeg", "damaged.jpg"],
            [archive_path],
            output_dir=self.output,
            prepare=True,
            rights_confirmed=True,
        )
        path = f"media/{sha256(JPEG).hexdigest()}.jpg"
        self.assertEqual({"demo photo.JPEG": path, "DEMO photo.jpeg": path}, paths)
        self.assertEqual(JPEG, (self.output / path).read_bytes())
        self.assertEqual([Path(path).name], [file.name for file in (self.output / "media").iterdir()])
        self.assertEqual("damaged.jpg", report["failures"][0]["member"])

    def test_missing_encoder_reports_failure_without_output_file(self):
        archive_path = self.root / "media.zip"
        with ZipFile(archive_path, "w") as archive:
            archive.writestr("DEMO.WMV", b"synthetic")
        paths, report = resolve_media(
            ["DEMO.WMV"],
            [archive_path],
            output_dir=self.output,
            prepare=True,
            rights_confirmed=True,
            ffmpeg=str(self.root / "missing-ffmpeg"),
        )
        self.assertEqual({}, paths)
        self.assertEqual("DEMO.WMV", report["failures"][0]["member"])
        self.assertEqual([], list((self.output / "media").iterdir()))

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg and FFprobe required")
    def test_converts_synthetic_wmv_to_android_video_and_audio_codecs(self):
        source = self.root / "demo.wmv"
        subprocess.run([
            "ffmpeg", "-v", "error", "-nostdin",
            "-f", "lavfi", "-i", "color=blue:s=64x48:r=8:d=0.5",
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=0.5",
            "-c:v", "wmv2", "-c:a", "wmav2", "-shortest", str(source),
        ], check=True, timeout=30)
        archive_path = self.root / "media.zip"
        with ZipFile(archive_path, "w") as archive:
            archive.write(source, arcname="Filmy/DEMO wideo.WMV")

        paths, report = resolve_media(
            ["DEMO wideo.WMV"],
            [archive_path],
            output_dir=self.output,
            prepare=True,
            rights_confirmed=True,
        )
        self.assertEqual([], report["failures"])
        result = self.output / paths["DEMO wideo.WMV"]
        self.assertEqual(".mp4", result.suffix)
        self.assertEqual(sha256(result.read_bytes()).hexdigest(), result.stem)
        probe = subprocess.run([
            "ffprobe", "-v", "error", "-show_streams", "-of", "json", str(result),
        ], check=True, capture_output=True, text=True, timeout=30)
        streams = {stream["codec_type"]: stream for stream in json.loads(probe.stdout)["streams"]}
        self.assertEqual("h264", streams["video"]["codec_name"])
        self.assertEqual("yuv420p", streams["video"]["pix_fmt"])
        self.assertEqual("aac", streams["audio"]["codec_name"])
