import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from zipfile import ZipFile


SCRIPT = Path(__file__).resolve().parents[1] / "generate_demo_media.py"


class DemoMediaGeneratorTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="prawko media ")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.output = self.root / "media demo"

    def run_generator(self, *args: str):
        return subprocess.run(
            [sys.executable, "-X", "utf8", str(SCRIPT), "--output-dir", str(self.output), *args],
            capture_output=True, text=True, encoding="utf-8", timeout=150,
        )

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "Wymagane ffmpeg i ffprobe")
    def test_creates_matching_catalog_pack_and_checksums_with_playable_media(self):
        result = self.run_generator()
        self.assertEqual(0, result.returncode, result.stderr)
        catalog = json.loads((self.output / "questions.json").read_bytes())
        self.assertEqual("DEMO", catalog["source"])
        self.assertEqual("B", catalog["category"])
        self.assertEqual(2, len(catalog["questions"]))
        for question in catalog["questions"]:
            self.assertTrue(question["prompt"].startswith("DEMO"))
            self.assertEqual("QUESTION", question["media"][0]["role"])
        photo, video = (question["media"][0]["assetPath"] for question in catalog["questions"])
        self.assertTrue(photo.endswith(".jpg"))
        self.assertTrue(video.endswith(".mp4"))
        archive_name = f"{catalog['datasetVersion']}-pack-001.zip"

        with ZipFile(self.output / archive_name) as archive:
            self.assertEqual(["manifest.json", *sorted([photo, video])], archive.namelist())
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual(catalog["datasetVersion"], manifest["datasetVersion"])
            self.assertEqual("pack-001", manifest["packId"])
            for entry in manifest["files"]:
                content = archive.read(entry["path"])
                self.assertEqual(content, (self.output / entry["path"]).read_bytes())
                self.assertEqual(entry["sizeBytes"], len(content))
                self.assertEqual(Path(entry["path"]).stem, hashlib.sha256(content).hexdigest())

        sums = (self.output / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
        expected_names = [archive_name, "questions.json", photo, video]
        expected_sums = [
            f"{hashlib.sha256((self.output / name).read_bytes()).hexdigest()}  {name}"
            for name in expected_names
        ]
        self.assertEqual(expected_sums, sums)
        self.assertIn(expected_sums[0].split()[0], result.stdout)

        photo_stream, = self.probe(photo)["streams"]
        self.assertEqual("mjpeg", photo_stream["codec_name"])
        self.assertEqual((640, 360), (photo_stream["width"], photo_stream["height"]))
        clip = self.probe(video)
        self.assertEqual({"video": "h264", "audio": "aac"}, {
            stream["codec_type"]: stream["codec_name"] for stream in clip["streams"]
        })
        self.assertAlmostEqual(8.0, float(clip["format"]["duration"]), places=1)

    def probe(self, name: str):
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(self.output / name)],
            check=True, capture_output=True, timeout=15,
        )
        return json.loads(result.stdout)

    def test_keeps_existing_output(self):
        self.output.mkdir()
        existing = self.output / "questions.json"
        existing.write_text("DEMO — existing data", encoding="utf-8")
        result = self.run_generator()
        self.assertEqual(2, result.returncode)
        self.assertIn("już istnieje", result.stderr)
        self.assertEqual("DEMO — existing data", existing.read_text(encoding="utf-8"))

    def test_cleans_up_if_ffmpeg_cannot_start(self):
        result = self.run_generator("--ffmpeg", str(self.root / "missing-ffmpeg"))
        self.assertEqual(2, result.returncode)
        self.assertIn("Nie znaleziono FFmpeg", result.stderr)
        self.assertEqual([], list(self.root.iterdir()))
