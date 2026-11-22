#!/usr/bin/env python3
"""Build a validated versioned question bundle from the Ministry's XLSX catalog."""

import argparse
from collections import Counter
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import posixpath
import re
import shutil
import sys
import tempfile
import unicodedata
from urllib.parse import unquote, urlsplit
from xml.etree import ElementTree
from zipfile import BadZipFile, ZIP_DEFLATED, ZIP_STORED, ZipFile
import zlib

from catalog_media import resolve_media


SCHEMA_VERSION = 1
COLUMN_COUNT = 27
MAX_XML_BYTES = 64 * 1024 * 1024
MAX_ROWS = 100_000
MAX_BUNDLE_BYTES = 32 * 1024 * 1024
MAX_BUNDLE_ROWS = 50_000
FIXED_HEADERS = (
    "Lp", "Numer pytania", "Pytanie", "Odpowiedź A", "Odpowiedź B", "Odpowiedź C",
    "Poprawna odp", "Media", "Zakres struktury", "Liczba punktów", "Kategorie",
)
MEDIA_COLUMNS = ((7, "QUESTION"), (11, "PJM_QUESTION"), (12, "PJM_A"), (13, "PJM_B"), (14, "PJM_C"))
LANGUAGES = {
    "EN": {"en", "ang", "angielski", "angielskim", "angielsku", "english"},
    "DE": {"de", "niem", "niemiecki", "niemieckim", "niemiecku", "german"},
    "UK": {"uk", "ua", "ukr", "ukrainski", "ukrainskim", "ukrainsku", "ukrainian"},
}
STRUCTURES = {"podstawowy": "BASIC", "specjalistyczny": "SPECIALIST"}
CATEGORY_CODE = re.compile(r"[A-Z][A-Z0-9]*(?:\+[A-Z][A-Z0-9]*)?")
VERSION_CODE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
CELL_ADDRESS = re.compile(r"([A-Z]{1,3})([1-9][0-9]{0,6})")
ESCAPED_XSTRING = re.compile(r"_x([0-9a-fA-F]{4})_")
INVALID_TEXT = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]")
INVALID_LABEL = re.compile(r"[\x00-\x1f\x7f-\x9f]")


class CatalogError(ValueError):
    """The input cannot produce a catalog bundle."""


@dataclass
class WorksheetRow:
    number: int
    cells: list[str]
    invalid_cells: list[str]


def local_name(tag):
    return tag.rsplit("}", 1)[-1]


def children(node, name):
    return [child for child in node if local_name(child.tag) == name]


def only_child(node, name):
    matches = children(node, name)
    return matches[0] if len(matches) == 1 else None


def normalize_words(value):
    folded = unicodedata.normalize("NFKD", value.casefold().replace("ł", "l"))
    normalized = "".join(character for character in folded if not unicodedata.combining(character))
    return " ".join(re.findall(r"[a-z0-9]+", normalized))


def normalized_role(words, role):
    terms = words.split()
    answer_term = any(term.startswith("odp") or term in {"answer", "wariant"} for term in terms)
    if role == "QUESTION":
        return not answer_term and any(term.startswith("pyt") or term == "question" for term in terms)
    return answer_term and set(terms) & {"a", "b", "c"} == {role.lower()}


def validate_headers(headers):
    if len(headers) != COLUMN_COUNT or any(not cell.strip() for cell in headers):
        raise CatalogError("Wiersz 1 arkusza katalog musi zawierać 27 niepustych nagłówków.")
    for column, expected in enumerate(FIXED_HEADERS):
        if normalize_words(headers[column]) != normalize_words(expected):
            raise CatalogError(f"Kolumna {column + 1}: oczekiwano nagłówka {expected!r}, odczytano {headers[column]!r}.")

    for offset, role in enumerate(("QUESTION", "A", "B", "C")):
        column = 11 + offset
        words = normalize_words(headers[column])
        if not (("pjm" in words.split() or "migow" in words) and normalized_role(words, role)):
            raise CatalogError(f"Kolumna {column + 1}: oczekiwano nagłówka PJM dla pola {role}.")

    for group, (language, aliases) in enumerate(LANGUAGES.items()):
        for offset, role in enumerate(("QUESTION", "A", "B", "C")):
            column = 15 + group * 4 + offset
            words = normalize_words(headers[column])
            if not (set(words.split()) & aliases and normalized_role(words, role)):
                raise CatalogError(f"Kolumna {column + 1}: oczekiwano nagłówka {language} dla pola {role}.")


def read_xml(archive, name):
    try:
        info = archive.getinfo(name)
    except KeyError as error:
        raise CatalogError(f"Brak wymaganej części XLSX: {name}.") from error
    if info.compress_type not in {ZIP_STORED, ZIP_DEFLATED}:
        raise CatalogError(f"Część {name} używa formatu kompresji spoza specyfikacji XLSX.")
    if info.file_size > MAX_XML_BYTES:
        raise CatalogError(f"Część {name} przekracza limit {MAX_XML_BYTES // (1024 * 1024)} MiB.")
    payload = archive.read(info)
    # Disallow document types even in UTF-16/32 encoded XML parts.
    if b"<!DOCTYPE" in payload.replace(b"\x00", b"").upper():
        raise CatalogError(f"Część {name} zawiera niedozwoloną deklarację DOCTYPE.")
    try:
        return ElementTree.fromstring(payload)
    except ElementTree.ParseError as error:
        raise CatalogError(f"Niepoprawny XML części {name}: {error}") from error


def package_target(directory, target):
    target = unquote(target)
    try:
        url = urlsplit(target)
    except ValueError as error:
        raise CatalogError("Niepoprawne odwołanie do części XLSX.") from error
    if not target or url.scheme or url.netloc or url.query or url.fragment or "\\" in target:
        raise CatalogError("Niepoprawne odwołanie do części XLSX.")
    path = posixpath.normpath(target[1:] if target.startswith("/") else posixpath.join(directory, target))
    if path in {"", ".", ".."} or path.startswith(("../", "/")):
        raise CatalogError("Odwołanie wskazuje poza pakiet XLSX.")
    return path


def read_relationships(archive, rel_part, directory):
    namespaces = read_xml(archive, rel_part)
    relationships = {}
    for relation in children(namespaces, "Relationship"):
        key = relation.get("Id")
        if not key or key in relationships:
            raise CatalogError(f"Brakujący lub powtórzony identyfikator relacji: {rel_part}.")
        relationships[key] = (
            relation.get("Type", "").rsplit("/", 1)[-1],
            relation.get("Target"),
            relation.get("TargetMode", "Internal"),
        )
    return relationships


def internal_part(relationship, directory):
    _, target, mode = relationship
    if not target or mode != "Internal":
        raise CatalogError("Część XLSX musi mieć wewnętrzny cel relacji.")
    return package_target(directory, target)


def decode_xstring(value):
    if not ESCAPED_XSTRING.search(value):
        return value
    decoded = ESCAPED_XSTRING.sub(lambda escape: chr(int(escape[1], 16)), value)
    try:
        # SpreadsheetML uses UTF-16 code units in these escapes.
        return decoded.encode("utf-16", errors="surrogatepass").decode("utf-16")
    except UnicodeError as error:
        raise CatalogError("Błędna sekwencja Unicode w komórce XLSX.") from error


def read_rich_text(node):
    # Phonetic runs aren't part of the displayed text.
    texts = []
    for child in node:
        element = local_name(child.tag)
        if element == "t":
            texts.append(child.text or "")
        elif element == "r":
            texts.extend(text.text or "" for text in children(child, "t"))
    return decode_xstring("".join(texts))


def read_sheet(archive, report):
    if len(archive.infolist()) != len(set(archive.namelist())):
        raise CatalogError("Archiwum XLSX zawiera zduplikowaną nazwę części.")
    root_relations = read_relationships(archive, "_rels/.rels", "")
    workbooks = [entry for entry in root_relations.values() if entry[0] == "officeDocument"]
    if len(workbooks) != 1:
        raise CatalogError("Nie znaleziono jednej części skoroszytu XLSX.")
    workbook_part = internal_part(workbooks[0], "")
    workbook_path = PurePosixPath(workbook_part)
    directory = str(workbook_path.parent)
    relation_part = str(workbook_path.parent / "_rels" / f"{workbook_path.name}.rels")
    relations = read_relationships(archive, relation_part, directory)
    workbook = read_xml(archive, workbook_part)
    sheets_node = only_child(workbook, "sheets")
    sheets = children(sheets_node, "sheet") if sheets_node is not None else []
    catalogs = [sheet for sheet in sheets if sheet.get("name") == "katalog"]
    report["ignoredSheets"] = [sheet.get("name") for sheet in sheets if sheet.get("name") != "katalog"]
    if len(catalogs) != 1:
        raise CatalogError("Skoroszyt musi zawierać dokładnie jeden arkusz o nazwie katalog.")
    catalog = catalogs[0]
    if catalog.get("state", "visible") != "visible":
        raise CatalogError("Arkusz katalog musi być widoczny.")
    catalog_id = next((value for key, value in catalog.attrib.items() if local_name(key) == "id"), None)
    relation = relations.get(catalog_id)
    if not relation or relation[0] != "worksheet":
        raise CatalogError("Arkusz katalog nie wskazuje na część worksheet.")
    string_tables = [entry for entry in relations.values() if entry[0] == "sharedStrings"]
    if len(string_tables) > 1:
        raise CatalogError("Skoroszyt wskazuje więcej niż jedną tabelę ciągów.")
    shared = []
    if string_tables:
        shared_root = read_xml(archive, internal_part(string_tables[0], directory))
        shared = [read_rich_text(item) for item in children(shared_root, "si")]
    worksheet = read_xml(archive, internal_part(relation, directory))
    data = only_child(worksheet, "sheetData")
    if data is None:
        raise CatalogError("Arkusz katalog nie zawiera danych.")
    raw_rows = children(data, "row")
    if len(raw_rows) > MAX_ROWS:
        raise CatalogError(f"Arkusz katalog przekracza limit {MAX_ROWS} wierszy.")
    previous_number = 0
    for raw_row in raw_rows:
        try:
            number = int(raw_row.get("r", previous_number + 1))
        except ValueError as error:
            raise CatalogError("Niepoprawny numer wiersza w arkuszu katalog.") from error
        if not previous_number < number <= 1_048_576:
            raise CatalogError(f"Niepoprawna kolejność wierszy przy numerze {number}.")
        previous_number = number
        yield read_row(raw_row, number, shared)


def read_row(raw_row, number, shared):
    values = [""] * COLUMN_COUNT
    invalid = []
    previous_column = 0
    for cell in children(raw_row, "c"):
        address = cell.get("r")
        column = previous_column + 1
        if address:
            match = CELL_ADDRESS.fullmatch(address)
            if not match or int(match[2]) != number:
                raise CatalogError(f"Niepoprawny adres komórki: {address}.")
            column = 0
            for letter in match[1]:
                column = column * 26 + ord(letter) - ord("A") + 1
        if not previous_column < column <= 16_384:
            raise CatalogError(f"Niepoprawna kolejność komórek w wierszu {number}.")
        previous_column = column
        label = address or f"wiersz {number}, kolumna {column}"
        value_node = only_child(cell, "v")
        value = (value_node.text or "") if value_node is not None else ""
        kind = cell.get("t", "n")
        if children(cell, "f") or kind == "e":
            invalid.append(f"{label}: formuła lub błąd arkusza.")
        elif kind == "s":
            if not re.fullmatch(r"[0-9]{1,9}", value) or int(value) >= len(shared):
                invalid.append(f"{label}: niepoprawne odwołanie do tabeli ciągów.")
                value = ""
            else:
                value = shared[int(value)]
        elif kind == "inlineStr":
            node = only_child(cell, "is")
            value = read_rich_text(node) if node is not None else ""
        elif kind == "str":
            value = decode_xstring(value)
        elif kind != "n" and value:
            invalid.append(f"{label}: niedozwolony format komórki ({kind}).")
        if column > COLUMN_COUNT:
            if value.strip() or invalid and invalid[-1].startswith(f"{label}:"):
                raise CatalogError(f"Wiersz {number} zawiera dane poza 27. kolumną.")
        else:
            values[column - 1] = value
    return WorksheetRow(number, values, invalid)


def optional_text(value):
    return value.strip() or None


def valid_text(value, maximum, *, label=False):
    prohibited = INVALID_LABEL if label else INVALID_TEXT
    try:
        return not prohibited.search(value) and len(value.encode("utf-16-le")) // 2 <= maximum
    except UnicodeEncodeError:
        return False


def issue(code, message):
    return {"code": code, "message": message}


def parse_categories(value):
    tokens = [part.strip().upper() for part in value.split(",")]
    if any(len(token) > 16 or CATEGORY_CODE.fullmatch(token) is None for token in tokens):
        return []
    distinct = list(dict.fromkeys(tokens))
    return distinct if len(distinct) <= 128 else []


def validate_row(row, duplicate_ids, categories):
    cells = row.cells
    identifier = cells[1].strip()
    issues = [issue("invalid_cell", detail) for detail in row.invalid_cells]
    if not identifier:
        issues.append(issue("missing_id", "Brak numeru pytania."))
    elif not valid_text(identifier, 128, label=True):
        issues.append(issue("invalid_id", "Niepoprawny numer pytania."))
    if identifier in duplicate_ids:
        issues.append(issue("duplicate_id", "Wszystkie wystąpienia powtórzonego numeru pominięto."))
    prompt = cells[2].strip()
    if not prompt:
        issues.append(issue("missing_prompt", "Brak treści pytania po polsku."))
    if not categories:
        issues.append(issue("invalid_categories", "Lista kategorii jest pusta lub ma niepoprawny token."))
    if cells[9].strip() not in {"1", "2", "3"}:
        issues.append(issue("invalid_points", "Punktacja musi mieć wartość 1, 2 albo 3."))

    structure = STRUCTURES.get(normalize_words(cells[8]))
    correct = cells[6].strip().upper()
    choices = [optional_text(value) for value in cells[3:6]]
    if any(not valid_text(value, 8192) for value in [prompt, *choices] if value is not None):
        issues.append(issue("invalid_text", "Tekst pytania lub odpowiedzi ma niedozwolone znaki albo przekracza limit."))
    if structure is None:
        issues.append(issue("invalid_type", f"Nieznany zakres struktury: {cells[8]!r}."))
    elif structure == "BASIC":
        if correct not in {"T", "N"}:
            issues.append(issue("type_answer_mismatch", "Pytanie podstawowe wymaga poprawnej odpowiedzi T lub N."))
        if any(choices):
            issues.append(issue("unexpected_choices", "Pytanie TAK/NIE zawiera odpowiedzi wariantowe."))
    elif structure == "SPECIALIST":
        if correct not in {"A", "B", "C"}:
            issues.append(issue("type_answer_mismatch", "Pytanie specjalistyczne wymaga poprawnej odpowiedzi A, B lub C."))
        if not all(choices):
            issues.append(issue("missing_choices", "Pytanie wariantowe musi mieć odpowiedzi A, B i C."))

    media = []
    for index, role in MEDIA_COLUMNS:
        original_name = cells[index]
        if original_name.strip():
            if not valid_text(original_name, 1024, label=True):
                issues.append(issue("invalid_media_name", f"Niepoprawna nazwa medium w kolumnie {index + 1}."))
            media.append({"role": role, "originalName": original_name, "assetPath": None})

    translations = []
    for offset, language in enumerate(LANGUAGES):
        start = 15 + offset * 4
        values = [optional_text(value) for value in cells[start:start + 4]]
        if any(value is not None and not valid_text(value, 8192) for value in values):
            issues.append(issue("invalid_translation", f"Niepoprawny tekst tłumaczenia {language}."))
        if any(values):
            translations.append({
                "language": language, "prompt": values[0],
                "optionA": values[1], "optionB": values[2], "optionC": values[3],
            })
    if issues:
        return None, issues
    return {
        "id": identifier,
        "prompt": prompt,
        "points": int(cells[9].strip()),
        "type": structure,
        "correctAnswer": {"T": "YES", "N": "NO"}.get(correct, correct),
        "optionA": choices[0],
        "optionB": choices[1],
        "optionC": choices[2],
        "categories": categories,
        "translations": translations,
        "media": media,
    }, []


def ingest_rows(rows, category, report):
    header = next(rows, None)
    if header is None or header.number != 1 or header.invalid_cells:
        raise CatalogError("Brak poprawnego wiersza nagłówków w arkuszu katalog.")
    validate_headers(header.cells)
    rows = list(rows)
    identifiers = Counter(row.cells[1].strip() for row in rows if row.cells[1].strip())
    duplicate_ids = {identifier for identifier, count in identifiers.items() if count > 1}
    counts = report["counts"]
    counts.update({
        "records": sum(identifiers.values()), "uniqueIds": len(identifiers),
        "selectedCategoryRecords": 0, "selectedCategoryRecordsWithMedia": 0,
        "selectedCategoryWmvReferences": 0, "recordsWithMedia": 0,
        "ignoredOrdinalOnlyRows": 0, "ignoredEmptyRows": 0, "rejectedRecords": 0,
        "rejectedSelectedCategoryRecords": 0, "importedRecords": 0,
        "mainMediaExtensions": {}, "uniqueMainMediaNames": 0,
    })
    all_main_media = []
    pjm_references = {role: 0 for _, role in MEDIA_COLUMNS[1:]}
    questions = []
    for row in rows:
        identifier = row.cells[1].strip()
        if not any(value.strip() for value in row.cells[1:]) and not row.invalid_cells:
            key = "ignoredOrdinalOnlyRows" if row.cells[0].strip() else "ignoredEmptyRows"
            counts[key] += 1
            continue
        categories = parse_categories(row.cells[10])
        selected = category in categories
        if identifier and selected:
            counts["selectedCategoryRecords"] += 1
        if identifier:
            for column, role in MEDIA_COLUMNS[1:]:
                if row.cells[column].strip():
                    pjm_references[role] += 1
        main_media = row.cells[7]
        if identifier and main_media.strip():
            all_main_media.append(main_media)
            counts["recordsWithMedia"] += 1
            if selected:
                counts["selectedCategoryRecordsWithMedia"] += 1
                if PurePosixPath(main_media).suffix.lower() == ".wmv":
                    counts["selectedCategoryWmvReferences"] += 1
        question, issues = validate_row(row, duplicate_ids, categories)
        if issues:
            counts["rejectedRecords"] += 1
            if selected:
                counts["rejectedSelectedCategoryRecords"] += 1
            report["errors"].append({
                "row": row.number,
                "questionId": identifier or None,
                "selectedCategory": selected,
                "issues": issues,
            })
        elif selected:
            questions.append(question)
    counts["importedRecords"] = len(questions)
    counts["mainMediaExtensions"] = dict(Counter(PurePosixPath(name).suffix.lower() for name in all_main_media))
    counts["uniqueMainMediaNames"] = len(set(all_main_media))
    counts["pjmReferences"] = pjm_references
    return questions


def load_questions(path, category, report):
    try:
        with path.open("rb") as handle:
            initial = os.fstat(handle.fileno())
            report["sourceSha256"] = hashlib.file_digest(handle, "sha256").hexdigest()
            handle.seek(0)
            with ZipFile(handle) as archive:
                questions = ingest_rows(read_sheet(archive, report), category, report)
            current = os.fstat(handle.fileno())
            if (current.st_size, current.st_mtime_ns) != (initial.st_size, initial.st_mtime_ns):
                raise CatalogError("Plik XLSX zmienił się podczas odczytu.")
    except (BadZipFile, EOFError, NotImplementedError, OSError, RuntimeError, zlib.error) as error:
        raise CatalogError(f"Nie można odczytać pliku XLSX: {error}") from error
    return questions


def write_json(path, data):
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")


def run_import(args):
    output = args.output_dir
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise CatalogError("Katalog wyjściowy musi być nowy lub pusty.")
    output.parent.mkdir(parents=True, exist_ok=True)
    report = {
        "schemaVersion": SCHEMA_VERSION,
        "datasetVersion": args.dataset_version,
        "source": "VERIFIED_OFFICIAL" if args.verified_official else "DEMO",
        "sourceReference": args.source_reference.strip(),
        "sourceSha256": None,
        "category": args.category,
        "sheet": "katalog",
        "ignoredSheets": [],
        "counts": {},
        "errors": [],
    }
    stage = Path(tempfile.mkdtemp(prefix=f".{output.name}.", dir=output.parent))
    try:
        try:
            questions = load_questions(args.xlsx, args.category, report)
            if not questions:
                raise CatalogError(f"Brak poprawnych pytań kategorii {args.category}.")
            if len(questions) > MAX_BUNDLE_ROWS:
                raise CatalogError(f"Zestaw przekracza limit {MAX_BUNDLE_ROWS} pytań obsługiwanych w aplikacji.")
            names = {item["originalName"] for question in questions for item in question["media"]}
            media_paths, media_report = resolve_media(
                sorted(names),
                args.media_zip,
                output_dir=stage,
                prepare=args.prepare_media,
                rights_confirmed=args.rights_verified,
                ffmpeg=args.ffmpeg,
            )
            for question in questions:
                for item in question["media"]:
                    item["assetPath"] = media_paths.get(item["originalName"])
            report["media"] = {
                key: len(media_report[key])
                for key in (
                    "matched", "missing", "ambiguous", "unreferenced", "rejectedEntries",
                    "rejectedReferences", "archiveErrors", "failures",
                )
            }
            report["status"] = (
                "partial" if report["errors"] or any(
                    report["media"][key] for key in (
                        "missing", "ambiguous", "archiveErrors", "failures", "rejectedReferences",
                    )
                )
                else "complete"
            )
            bundle = {
                key: report[key]
                for key in ("schemaVersion", "datasetVersion", "source", "sourceReference", "sourceSha256", "category")
            }
            bundle["questions"] = questions
            write_json(stage / "media-manifest.json", media_report)
            bundle_path = stage / "questions.json"
            write_json(bundle_path, bundle)
            if bundle_path.stat().st_size > MAX_BUNDLE_BYTES:
                raise CatalogError(f"Zestaw przekracza limit {MAX_BUNDLE_BYTES // (1024 * 1024)} MiB aplikacji.")
        except CatalogError as error:
            report["status"] = "failed"
            report["fatalError"] = str(error)
            for name in ("questions.json", "media-manifest.json"):
                (stage / name).unlink(missing_ok=True)
            if (stage / "media").is_dir():
                shutil.rmtree(stage / "media")
        write_json(stage / "import-report.json", report)
        os.replace(stage, output)
    finally:
        if stage.exists():
            shutil.rmtree(stage)
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xlsx", required=True, type=Path)
    parser.add_argument("--dataset-version", required=True)
    parser.add_argument("--source-reference", required=True)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--category", default="B", type=lambda value: value.strip().upper())
    parser.add_argument("--verified-official", action="store_true")
    parser.add_argument("--rights-verified", action="store_true")
    parser.add_argument("--media-zip", action="append", type=Path, default=[])
    parser.add_argument("--prepare-media", action="store_true")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args(argv)
    if not VERSION_CODE.fullmatch(args.dataset_version):
        parser.error("Identyfikator wersji musi mieć 1–80 znaków ASCII z zestawu liter, cyfr, kropki, _ i -.")
    if not args.source_reference.strip() or not valid_text(args.source_reference.strip(), 2048):
        parser.error("Odwołanie do źródła musi mieć od 1 do 2048 znaków bez niedozwolonych znaków sterujących.")
    if len(args.category) > 16 or not CATEGORY_CODE.fullmatch(args.category):
        parser.error("Niepoprawny kod kategorii.")
    if (args.verified_official or args.prepare_media) and not args.rights_verified:
        parser.error("Opcje --verified-official i --prepare-media wymagają potwierdzenia --rights-verified.")
    if args.prepare_media and not args.media_zip:
        parser.error("Opcja --prepare-media wymaga co najmniej jednego archiwum --media-zip.")
    try:
        report = run_import(args)
    except (CatalogError, OSError) as error:
        print(error, file=sys.stderr)
        return 2
    if report["status"] == "failed":
        print(report["fatalError"], file=sys.stderr)
        return 2
    print(f"Zaimportowano {report['counts']['importedRecords']} pytań kategorii {args.category}. Raport: {args.output_dir / 'import-report.json'}")
    if report["media"]["archiveErrors"] or report["media"]["failures"]:
        print("Nie udało się odczytać lub przygotować części archiwów lub mediów. Sprawdź manifest.", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
