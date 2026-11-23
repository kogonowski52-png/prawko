import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

from tools.build_media_packs import build_packs


class MediaPacksTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.catalog = Path(temporary.name) / "catalog"
        (self.catalog / "media").mkdir(parents=True)
        self.output = Path(temporary.name) / "packs"

    def add_file(self, data, suffix):
        name = f"media/{hashlib.sha256(data).hexdigest()}.{suffix}"
        (self.catalog / name).write_bytes(data)
        return name

    def write_catalog(self, paths):
        catalog = {
            "schemaVersion": 1,
            "source": "DEMO",
            "datasetVersion": "DEMO-packs",
            "questions": [{"media": [{"assetPath": path} for path in paths]}],
        }
        (self.catalog / "questions.json").write_text(json.dumps(catalog), encoding="utf-8")

    def test_pack_contains_only_referenced_files_and_checksums(self):
        photo = self.add_file(b"DEMO photo for archive test", "jpg")
        film = self.add_file(b"DEMO film for archive test", "mp4")
        self.add_file(b"unreferenced DEMO", "jpg")
        self.write_catalog([photo, film, photo, None])
        self.assertEqual((1, 2), build_packs(self.catalog, self.output, 1))

        path = self.output / "DEMO-packs-pack-001.zip"
        with ZipFile(path) as archive:
            self.assertEqual(["manifest.json", *sorted([photo, film])], archive.namelist())
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual("DEMO-packs", manifest["datasetVersion"])
            self.assertEqual("pack-001", manifest["packId"])
            for entry in manifest["files"]:
                media = archive.read(entry["path"])
                self.assertEqual(len(media), entry["sizeBytes"])
                self.assertEqual(Path(entry["path"]).stem, hashlib.sha256(media).hexdigest())
        checksum = hashlib.sha256(path.read_bytes()).hexdigest()
        self.assertEqual(f"{checksum}  {path.name}\n", (self.output / "SHA256SUMS").read_text())

    def test_splits_at_device_file_limit(self):
        files = [self.add_file(f"DEMO {i}".encode(), "jpg") for i in range(129)]
        self.write_catalog(files)
        self.assertEqual((2, 129), build_packs(self.catalog, self.output, 1))
        for index, count in ((1, 128), (2, 1)):
            with ZipFile(self.output / f"DEMO-packs-pack-{index:03d}.zip") as archive:
                self.assertEqual(count + 1, len(archive.namelist()))

    def test_rejects_corrupt_file_without_publishing_partial_packs(self):
        path = self.add_file(b"DEMO expected", "mp4")
        (self.catalog / path).write_bytes(b"DEMO changed")
        self.write_catalog([path])
        with self.assertRaisesRegex(ValueError, "nie zgadza"):
            build_packs(self.catalog, self.output, 1)
        self.assertFalse(self.output.exists())
