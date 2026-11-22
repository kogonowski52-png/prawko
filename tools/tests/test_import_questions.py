"""Validate the XLSX importer with synthetic DEMO workbooks."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from xml.etree import ElementTree as XML
from zipfile import ZipFile

SCRIPT = Path(__file__).resolve().parents[1] / "import_questions.py"
LOCAL_CATALOG = SCRIPT.parents[1] / "local-data" / "KATALOG_dla_kandydatów_na_kierowców_072026.xlsx"
DOCUMENTED_CONFLICTS = {"3759", "3761", "4600", "4602", "7462", "11087", "11088", "11229", "13560", "13684"}
MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
OFFICE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
HEADERS = (
    "Lp", "Numer pytania", "Pytanie", "Odpowiedź A", "Odpowiedź B", "Odpowiedź C", "Poprawna odp",
    "Media", "Zakres struktury", "Liczba punktów", "Kategorie",
    "Nazwa media tłumaczenie migowe (PJM) treść pyt", "Nazwa media PJM odp. A",
    "Nazwa media PJM odp. B", "Nazwa media PJM odp. C",
    "Treść pytania (angielski)", "Odpowiedź A (ang.)", "Odpowiedź B EN", "Odpowiedź C EN",
    "Pytanie po niemiecku", "Odpowiedź A DE", "Odpowiedź B DE", "Odpowiedź C DE",
    "Pytanie (ukraiński)", "Odpowiedź A UA", "Odpowiedź B UA", "Odpowiedź C UA",
)


def question(identifier, category="B", **overrides):
    result = {
        1: 1, 2: identifier, 3: "DEMO – pytanie", 7: "T",
        9: " Podstawowy ", 10: 3, 11: category,
    }
    result.update({int(column.removeprefix("c")): value for column, value in overrides.items()})
    return result


def column_name(index):
    characters = []
    while index:
        index, position = divmod(index - 1, 26)
        characters.append(chr(ord("A") + position))
    return "".join(reversed(characters))


def worksheet(rows):
    root = XML.Element("worksheet", xmlns=MAIN)
    data = XML.SubElement(root, "sheetData")
    for number, row_data in enumerate(rows, start=1):
        row = XML.SubElement(data, "row", r=str(number))
        for column, value in sorted(row_data.items()):
            cell = XML.SubElement(row, "c", r=f"{column_name(column)}{number}")
            if isinstance(value, int):
                XML.SubElement(cell, "v").text = str(value)
            elif isinstance(value, tuple):
                kind, argument = value
                if kind == "s":
                    cell.set("t", "s")
                elif kind == "formula":
                    XML.SubElement(cell, "f").text = "1+1"
                XML.SubElement(cell, "v").text = str(argument)
            else:
                cell.set("t", "inlineStr")
                string = XML.SubElement(cell, "is")
                XML.SubElement(string, "t").text = value
    return XML.tostring(root, encoding="utf-8")


def write_workbook(path, records, headers=HEADERS, name="katalog", state="visible", external=False):
    workbook = XML.Element("workbook", {"xmlns": MAIN, "xmlns:r": OFFICE_REL})
    sheets = XML.SubElement(workbook, "sheets")
    XML.SubElement(sheets, "sheet", {"name": "W trakcie weryfikacji", "state": "hidden", "sheetId": "1", "r:id": "review"})
    XML.SubElement(sheets, "sheet", {"name": name, "state": state, "sheetId": "2", "r:id": "catalog"})
    shared = XML.Element("sst", xmlns=MAIN)
    item = XML.SubElement(shared, "si")
    for text in ("DEMO — Zażółć ", "gęślą_x000D_jaźń"):
        XML.SubElement(XML.SubElement(item, "r"), "t").text = text
    XML.SubElement(XML.SubElement(item, "rPh"), "t").text = "pomijane"

    def relations(entries):
        root = XML.Element("Relationships", xmlns=PACKAGE_REL)
        for identifier, kind, target, mode in entries:
            XML.SubElement(root, "Relationship", Id=identifier, Type=f"{OFFICE_REL}/{kind}", Target=target, TargetMode=mode)
        return XML.tostring(root, encoding="utf-8")

    with ZipFile(path, "w") as archive:
        archive.writestr("_rels/.rels", relations([("file", "officeDocument", "xl/workbook.xml", "Internal")]))
        archive.writestr("xl/workbook.xml", XML.tostring(workbook, encoding="utf-8"))
        archive.writestr("xl/_rels/workbook.xml.rels", relations([
            ("review", "worksheet", "worksheets/review.xml", "Internal"),
            ("catalog", "worksheet", "https://example.test/catalog.xml" if external else "/xl/worksheets/catalog.xml",
             "External" if external else "Internal"),
            ("strings", "sharedStrings", "sharedStrings.xml", "Internal"),
        ]))
        archive.writestr("xl/sharedStrings.xml", XML.tostring(shared, encoding="utf-8"))
        # The review sheet is deliberately malformed. The importer must never read it.
        archive.writestr("xl/worksheets/review.xml", b"<broken")
        archive.writestr("xl/worksheets/catalog.xml", worksheet([
            dict(enumerate(headers, start=1)), *records,
        ]))


class CatalogImportTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.workbook = self.root / "DEMO.xlsx"

    def invoke(self, *extra, output="output"):
        target = self.root / output
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--xlsx", str(self.workbook), "--dataset-version", "DEMO-2026.07",
             "--source-reference", "DEMO — fixtura testowa", "--output-dir", str(target), *extra],
            capture_output=True, text=True, check=False,
        )
        return result, target

    def read(self, directory, name):
        return json.loads((directory / name).read_text(encoding="utf-8"))

    def test_imports_only_catalog_and_exact_category_and_preserves_sparse_fields(self):
        write_workbook(self.workbook, [
            question("0007", " B1, b, B+E, B ", c3=("s", 0), c8=" Zdjęcie ścieżka.JPG ",
                     c12="PJM ą.WMV", c13="pytanie A.jpeg", c16="DEMO EN", c21="DEMO DE A"),
            question("8", "B1"),
            question(9, "C, B", c4="DEMO A", c5="DEMO B", c6="DEMO C", c7=" c ", c9="sPeCjAlIsTyCzNy"),
            {1: 4},
            {},
        ])
        execution, output = self.invoke()
        self.assertEqual(0, execution.returncode, execution.stderr)
        data = self.read(output, "questions.json")
        self.assertEqual("DEMO", data["source"])
        self.assertEqual("B", data["category"])
        self.assertEqual("DEMO-2026.07", data["datasetVersion"])
        self.assertEqual(hashlib.sha256(self.workbook.read_bytes()).hexdigest(), data["sourceSha256"])
        self.assertEqual(["0007", "9"], [record["id"] for record in data["questions"]])
        basic, specialist = data["questions"]
        self.assertEqual(["B1", "B", "B+E"], basic["categories"])
        self.assertEqual("DEMO — Zażółć gęślą\rjaźń", basic["prompt"])
        self.assertEqual("BASIC", basic["type"])
        self.assertEqual("YES", basic["correctAnswer"])
        self.assertEqual(3, basic["points"])
        self.assertIsNone(basic["optionA"])
        self.assertEqual(
            [
                {"language": "EN", "prompt": "DEMO EN", "optionA": None, "optionB": None, "optionC": None},
                {"language": "DE", "prompt": None, "optionA": "DEMO DE A", "optionB": None, "optionC": None},
            ],
            basic["translations"],
        )
        self.assertEqual((" Zdjęcie ścieżka.JPG ", "PJM ą.WMV", "pytanie A.jpeg"), tuple(
            item["originalName"] for item in basic["media"]
        ))
        self.assertEqual({"QUESTION", "PJM_QUESTION", "PJM_A"}, {item["role"] for item in basic["media"]})
        self.assertTrue(all(item["assetPath"] is None for item in basic["media"]))
        self.assertEqual("SPECIALIST", specialist["type"])
        self.assertEqual("C", specialist["correctAnswer"])
        self.assertEqual("DEMO C", specialist["optionC"])
        report = self.read(output, "import-report.json")
        self.assertEqual(["W trakcie weryfikacji"], report["ignoredSheets"])
        self.assertEqual(3, report["counts"]["records"])
        self.assertEqual(2, report["counts"]["selectedCategoryRecords"])
        self.assertEqual(1, report["counts"]["ignoredOrdinalOnlyRows"])
        self.assertEqual(1, report["counts"]["ignoredEmptyRows"])
        self.assertEqual(2, report["media"]["missing"])
        self.assertEqual(1, report["media"]["rejectedReferences"])

    def test_reports_conflicts_typo_duplicates_and_other_invalid_rows(self):
        # IDs reflect the validation cases documented in the repository. Payloads are DEMO.
        write_workbook(self.workbook, [
            question("good"),
            *(question(identifier, c7="A") for identifier in sorted(DOCUMENTED_CONFLICTS)),
            question("14063", c9="Specajlistyczny"),
            question("duplicate"), question("duplicate", "A"),
            question("bad_points", c10=4),
            question("bad_options", c7="A", c9="specjalistyczny"),
            question("bad_category", "B,,A"),
            question("bad_prompt", c3=""),
            question("bad_formula", c10=("formula", 1)),
            question("bad_translation", c21="🟠" * 4097),
            question("bad_media_name", c12="PMJ_x0009_name.wmv"),
            question("", c3="DEMO bez ID"),
        ])
        execution, output = self.invoke()
        self.assertEqual(0, execution.returncode, execution.stderr)
        report = self.read(output, "import-report.json")
        by_id = {}
        for entry in report["errors"]:
            by_id.setdefault(entry["questionId"], []).extend(issue["code"] for issue in entry["issues"])
        for identifier in sorted(DOCUMENTED_CONFLICTS):
            self.assertIn("type_answer_mismatch", by_id[identifier])
        self.assertIn("invalid_type", by_id["14063"])
        self.assertEqual(["duplicate_id", "duplicate_id"], by_id["duplicate"])
        self.assertIn("invalid_points", by_id["bad_points"])
        self.assertIn("missing_choices", by_id["bad_options"])
        self.assertIn("invalid_categories", by_id["bad_category"])
        self.assertIn("missing_prompt", by_id["bad_prompt"])
        self.assertIn("invalid_cell", by_id["bad_formula"])
        self.assertIn("invalid_translation", by_id["bad_translation"])
        self.assertIn("invalid_media_name", by_id["bad_media_name"])
        self.assertIn("missing_id", by_id[None])
        self.assertEqual("partial", report["status"])
        self.assertEqual(1, report["counts"]["importedRecords"])
        self.assertEqual(["good"], [entry["id"] for entry in self.read(output, "questions.json")["questions"]])

    def test_rejects_bad_header_count_order_and_language(self):
        malformed_headers = [
            HEADERS[:-1], (*HEADERS, "Dodatkowe dane"),
            ("Numer pytania", "Lp", *HEADERS[2:]),
            (*HEADERS[:24], "Odpowiedź A DE", *HEADERS[25:]),
            (*HEADERS[:11], HEADERS[12], HEADERS[11], *HEADERS[13:]),
        ]
        for index, headers in enumerate(malformed_headers):
            with self.subTest(index=index):
                write_workbook(self.workbook, [question("1")], headers=headers)
                result, output = self.invoke(output=f"bad-{index}")
                self.assertEqual(2, result.returncode, result.stderr)
                self.assertEqual("failed", self.read(output, "import-report.json")["status"])
                self.assertFalse((output / "questions.json").exists())

    def test_rejects_missing_hidden_or_external_catalog(self):
        cases = [{"name": "Katalog"}, {"state": "hidden"}, {"external": True}]
        for index, configuration in enumerate(cases):
            with self.subTest(configuration=configuration):
                write_workbook(self.workbook, [question("1")], **configuration)
                result, output = self.invoke(output=f"invalid-{index}")
                self.assertEqual(2, result.returncode, result.stderr)
                self.assertEqual("failed", self.read(output, "import-report.json")["status"])
                self.assertFalse((output / "questions.json").exists())

    def test_supports_alternate_category_without_activating_similar_category(self):
        write_workbook(self.workbook, [question("X", "B, B+E"), question("Y", "B")])
        result, output = self.invoke("--category", "b+e")
        self.assertEqual(0, result.returncode, result.stderr)
        bundle = self.read(output, "questions.json")
        self.assertEqual("B+E", bundle["category"])
        self.assertEqual(["X"], [record["id"] for record in bundle["questions"]])
        self.assertEqual(["B", "B+E"], bundle["questions"][0]["categories"])

    def test_empty_result_and_corrupt_input_report_without_bundle(self):
        write_workbook(self.workbook, [question("N", "B1")])
        result, output = self.invoke()
        self.assertEqual(2, result.returncode, result.stderr)
        report = self.read(output, "import-report.json")
        self.assertEqual(0, report["counts"]["importedRecords"])
        self.assertEqual("failed", report["status"])
        self.assertFalse((output / "questions.json").exists())
        self.workbook.write_bytes(b"to nie jest XLSX")
        result, output = self.invoke(output="broken")
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertEqual("failed", self.read(output, "import-report.json")["status"])

    def test_source_rights_guard_and_existing_output_protection(self):
        write_workbook(self.workbook, [question("X")])
        result, output = self.invoke("--verified-official")
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertFalse(output.exists())
        result, output = self.invoke("--prepare-media")
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertFalse(output.exists())
        result, output = self.invoke("--rights-verified")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("DEMO", self.read(output, "questions.json")["source"])
        original = (output / "questions.json").read_bytes()
        result, _ = self.invoke()
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertEqual(original, (output / "questions.json").read_bytes())

    def test_cli_preserves_question_bundle_and_reports_bad_media_archive(self):
        write_workbook(self.workbook, [question("X", c8="DEMO.JPG")])
        corrupt = self.root / "invalid.zip"
        corrupt.write_bytes(b"to nie jest ZIP")
        result, output = self.invoke("--media-zip", str(corrupt))
        self.assertEqual(3, result.returncode, result.stderr)
        self.assertEqual("partial", self.read(output, "import-report.json")["status"])
        self.assertEqual(1, len(self.read(output, "media-manifest.json")["archiveErrors"]))
        self.assertIsNone(self.read(output, "questions.json")["questions"][0]["media"][0]["assetPath"])


class CatalogJuly2026ControlTest(unittest.TestCase):
    @unittest.skipUnless(LOCAL_CATALOG.is_file(), "Brak oryginalnego XLSX w local-data/")
    def test_documented_july_2026_catalog_control_totals(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "catalog"
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--xlsx", str(LOCAL_CATALOG), "--dataset-version", "control-2026-07",
                 "--source-reference", "Ministerstwo Infrastruktury, lokalna kontrola katalogu",
                 "--output-dir", str(output)],
                capture_output=True, text=True, check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads((output / "import-report.json").read_text(encoding="utf-8"))

        counts = report["counts"]
        self.assertEqual(3518, counts["records"])
        self.assertEqual(3518, counts["uniqueIds"])
        self.assertEqual(2135, counts["selectedCategoryRecords"])
        self.assertEqual(8, counts["ignoredOrdinalOnlyRows"])
        self.assertEqual(2980, counts["recordsWithMedia"])
        self.assertEqual(1941, counts["selectedCategoryRecordsWithMedia"])
        self.assertEqual(1029, counts["selectedCategoryWmvReferences"])
        self.assertEqual(2635, counts["uniqueMainMediaNames"])
        self.assertEqual({".wmv": 1272, ".jpg": 1705, ".jpeg": 3}, counts["mainMediaExtensions"])
        self.assertEqual(1119, counts["pjmReferences"]["PJM_QUESTION"])
        self.assertEqual(273, counts["pjmReferences"]["PJM_A"])
        ids_by_code = {}
        for entry in report["errors"]:
            for issue in entry["issues"]:
                ids_by_code.setdefault(issue["code"], set()).add(entry["questionId"])
        self.assertEqual(DOCUMENTED_CONFLICTS, ids_by_code.get("type_answer_mismatch"))
        self.assertIn("14063", ids_by_code.get("invalid_type", set()))


if __name__ == "__main__":
    unittest.main()
