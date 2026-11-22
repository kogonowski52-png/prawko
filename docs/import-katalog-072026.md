# Import katalogu egzaminacyjnego — lipiec 2026

Źródło analizy: plik użytkownika `KATALOG_dla_kandydatów_na_kierowców_072026.xlsx` (nie przechowujemy pliku źródłowego w publicznym repozytorium).

## Struktura pliku i liczby kontrolne

- Arkusz `katalog` (widoczny): 3518 kompletnych rekordów z unikalnym `Numer pytania` i 8 końcowych wierszy zawierających jedynie `Lp`. Przetwarzaj **tylko rekordy z niepustym numerem**.
- Arkusz `W trakcie weryfikacji` (ukryty): 180 pytań — **nie importuj do aktywnej puli egzaminacyjnej**. Ewentualnie osobny raport.
- Liczba kolumn: 27.
- Liczba rekordów przypisanych do kategorii `B`: **2135**. Kategoria musi być rozpoznawana przez rozdzielenie `Kategorie` po przecinku i porównanie dokładnych tokenów. `B` nie może pasować przypadkiem do `B1`.
- Liczba rekordów z polem `Media`: **2980**, z czego 1272 pliki `.wmv`, 1705 `.jpg`, 3 `.jpeg`; liczba różnych nazw w kolumnie `Media`: **2635**.
- Dla kategorii `B`: **1941** rekordów z odwołaniem do multimediów; **1029** referencji do plików `.wmv`.
- `Nazwa media tłumaczenie migowe (PJM) treść pyt` niepuste dla 1119 rekordów; tłumaczenie odp. A niepuste dla 273.
- Unikalnych numerów pytań w `katalog`: 3518. Nagłówki w wierszu 1.

## Kolumny 1–27

1. `Lp` — numer porządkowy, nie jest identyfikatorem pytania
2. `Numer pytania` — trwały identyfikator źródłowy pytania (string)
3. `Pytanie` — tekst PL
4. `Odpowiedź A`
5. `Odpowiedź B`
6. `Odpowiedź C`
7. `Poprawna odp` — T/N lub A/B/C
8. `Media` — oryginalna nazwa pliku (zdjęcie/film)
9. `Zakres struktury` — typ pytania
10. `Liczba punktów` — 1/2/3
11. `Kategorie` — lista kodów rozdzielonych przecinkiem
12–15. Nazwy mediów PJM dla pytania i odpowiedzi A/B/C
16–19. Pytanie i odpowiedzi w języku angielskim
20–23. Pytanie i odpowiedzi w języku niemieckim
24–27. Pytanie i odpowiedzi w języku ukraińskim

## Reguły walidacji

1. Odrzuć puste wiersze i wiersze bez numeru pytania. Puste odpowiedzi A/B/C przy typie TAK/NIE są prawidłowe.
2. Nie traktuj `Lp` jako ID. Zachowaj `Numer pytania` jako identyfikator niezależny od kolejności.
3. Normalizuj odmiany wielkości liter i białe znaki w typie, np. `PODSTAWOWY`, `Podstawowy`, `SPECJALISTYCZNY`, `Specjalistyczny`. Literówka `Specajlistyczny` występuje przy pytaniu ID **14063** i musi być raportowana, a nie bezgłośnie ignorowana.
4. Zwracaj raport błędów typ–odpowiedź. W danych występuje **10 sprzeczności** między typem a formatem odpowiedzi; ID: **3759, 3761, 4600, 4602, 7462, 11087, 11088, 11229, 13560, 13684**. Nie interpretuj ich bez weryfikacji merytorycznej — nie uruchamiaj jako oficjalnych pytań egzaminacyjnych, dopóki konflikt nie zostanie rozstrzygnięty.
5. Waliduj wartości `Poprawna odp` (T/N dla pytań TAK/NIE, A/B/C dla wariantowych), punktację (1/2/3), unikatowość ID i niepuste teksty.
6. Zachowaj oryginalne nazwy plików w osobnym polu i mapuj je do mediów po rzeczywistym rozpakowaniu ZIP. Nazwy mogą zawierać spacje, kropki, Unicode, różne wielkości liter. Nie buduj nazw w ciemno.
7. Tłumaczenia EN/DE/UA i PJM są opcjonalne. Nie wstawiaj polskiego tekstu do niezapełnionych pól i nie udawaj dostępności tłumaczenia.
8. Podczas importu generuj raport: wszystkie pytania, B, błędne rekordy, brakujące media i pliki bez referencji.
9. Nie zakładaj, że 2026 = zweryfikowany katalog na 2027. Wersjonuj zestaw danych i umożliwiaj aktualizację.
10. Osobna weryfikacja zasad komercyjnego użycia publikowanych treści i multimediów przed publikacją aplikacji.

## Implementacja

- Dodaj build-time importer `tools/import_questions.py` wyciągający dane z XLSX do walidowanego JSON/SQLite (można użyć Python `zipfile` + XML streaming, bez ciężkiego Apache POI w aplikacji).
- Stwórz encje `Question`, `QuestionTranslation`, `QuestionCategory`, `MediaAsset` i `DatasetVersion`; połącz je przez stabilny `questionId`.
- Generuj plik manifestu mediów dla brakujących/znalezionych plików.
- Pliki WMV nie powinny być odtwarzane bezpośrednio jako format docelowy Android. Przygotuj etap transkodowania do kompatybilnego MP4 H.264/AAC **po weryfikacji praw do przetwarzania i rozpowszechniania**.
- ZIP-y medialne są wielogigabajtowe: nie commituj ich do GitHub; rozważ CDN i pakiety pobierane opcjonalnie, z cache i kontrolą integralności.
- Napisz testy sprawdzające liczby kontrolne i powyższe przypadki brzegowe.
- Gdy XLSX nie jest dostępny w środowisku Codex, zaimplementuj importer i testy z minimalnymi syntetycznymi fixture, bez zmyślania oficjalnych danych.

## Zakres pierwszego etapu

Import `katalog` dla kategorii B i statystyki, z walidacją 10 sprzecznych rekordów. Nie podłączaj jeszcze płatności ani reklam do niedziałającego egzaminu. Najpierw zweryfikuj poprawność danych i mediów, potem silnik egzaminu.

## Uruchomienie importera

Użyj Pythona 3.11 lub nowszego. Polecenia wykonuj w katalogu głównym repozytorium. Umieść oryginalny plik Ministerstwa Infrastruktury pod ścieżką `local-data/KATALOG_dla_kandydatów_na_kierowców_072026.xlsx`. Archiwa ZIP, jeśli je masz, umieść w `local-data/media/`. Repozytorium ignoruje katalog `local-data/`.

Najpierw wygeneruj raport kontrolny. Katalog wyjściowy musi być nowy lub pusty. Domyślne oznaczenie `DEMO` pozwala przejrzeć wyniki bez udostępniania pytań w oficjalnej puli aplikacji.

```sh
python3 tools/import_questions.py \
  --xlsx 'local-data/KATALOG_dla_kandydatów_na_kierowców_072026.xlsx' \
  --dataset-version DEMO-2026-07-B \
  --category B \
  --source-reference 'Ministerstwo Infrastruktury, katalog z lipca 2026' \
  --output-dir local-data/review-2026-07-B
```

Import wymaga 27 niepustych nagłówków w pierwszym wierszu. Dla kolumn 1–11 sprawdza ustalone nazwy, ignorując różnice wielkości liter, znaków diakrytycznych i interpunkcji. Dla kolumn 12–27 sprawdza pozycję, pole pytania lub odpowiedzi oraz oznaczenie PJM lub języka. Rozpoznaje między innymi EN lub angielski, DE lub niemiecki oraz UK/UA lub ukraiński.

W `import-report.json` sprawdź liczby rekordów, wybraną kategorię, liczbę przyjętych pytań oraz zgłoszenia z numerami wierszy i identyfikatorami. Porównaj wyniki z liczbami kontrolnymi powyżej. Rekordy z błędami pozostają poza pakietem. Raport może zawierać błędy pojedynczych rekordów również wtedy, gdy importer wygenerował `questions.json`. Kod wyjścia 0 oznacza użyteczny pakiet, również gdy raport zawiera odrzucone wiersze lub brakujące media. Kod 2 oznacza niepoprawne wywołanie lub brak użytecznego pakietu, na przykład niezgodny układ arkusza lub brak poprawnych rekordów wybranej kategorii. Kod 3 oznacza błąd odczytu archiwum albo przygotowania mediów. W takim przypadku poprawne pytania i raporty pozostają w katalogu wynikowym.

Po potwierdzeniu pochodzenia pliku i warunków użycia w aplikacji oraz po przeglądzie raportu uruchom import z `--verified-official --rights-verified`. Zachowaj identyfikator wersji powiązany z rzeczywistym wydaniem katalogu. Jeśli zawartość pakietu się zmieni, użyj nowego identyfikatora wersji.

```sh
python3 tools/import_questions.py \
  --xlsx 'local-data/KATALOG_dla_kandydatów_na_kierowców_072026.xlsx' \
  --dataset-version 2026-07-B \
  --category B \
  --source-reference 'Ministerstwo Infrastruktury, katalog z lipca 2026' \
  --verified-official --rights-verified \
  --output-dir local-data/import-2026-07-B
```

Zachowaj raporty i źródła lokalnie. `questions.json` zawiera wersję formatu, wersję i identyfikację źródła, jego SHA-256, kategorię importu oraz pytania z kategoriami, punktacją, odpowiedziami i dostępnymi tłumaczeniami. Puste wartości tłumaczeń pozostają puste.

## Media

Dodaj `--media-zip local-data/media/nazwa-archiwum.zip` dla każdego pobranego archiwum. Tryb domyślny porównuje referencje pytań z katalogami ZIP. W `media-manifest.json` sprawdź znalezione, brakujące, niejednoznaczne i nieużywane pliki. Nieużywane pliki są liczone względem przyjętych pytań wybranej kategorii. Dopasowanie uwzględnia najpierw dokładną nazwę pliku, a w dalszej kolejności pojedynczy wynik po normalizacji Unicode i wielkości liter. W pakiecie jest zachowana oryginalna nazwa z arkusza.

Do przygotowania mediów podaj dodatkowo `--prepare-media --rights-verified`. Potrzebny jest `ffmpeg` z koderami H.264 (libx264) i AAC. Wskaż własną instalację przez `--ffmpeg /ścieżka/do/ffmpeg`, jeśli program jest poza `PATH`. Importer przetwarza wyłącznie znalezione pliki, do których odwołują się przyjęte pytania. Zdjęcia pozostają plikami JPG, a filmy WMV przechodzą konwersję do MP4 z wideo H.264 i dźwiękiem AAC, jeśli ścieżka dźwiękowa występuje. Ścieżki plików wynikowych w `media/` są zapisywane w pakiecie pytań. Brak lub niejednoznaczność pliku pozostawia pustą ścieżkę. Przejrzyj raport mediów przed użyciem zestawu w egzaminie.

Archiwa, oryginały i pliki wynikowe przechowuj poza Gitem. Przy kompilacji z przygotowanymi mediami APK będzie zawierał wyniki z `media/`. Do testów importu tekstowego wygeneruj pakiet bez `--prepare-media`.

## Wczytanie katalogu w Androidzie

```sh
./gradlew :app:assembleDebug -PprawkoCatalogDir=local-data/import-2026-07-B
```

Opcjonalna właściwość `prawkoCatalogDir` wskazuje katalog wyjściowy importera. Gradle dołącza `questions.json` i przygotowane pliki z `media/`. Po uruchomieniu aplikacja waliduje pakiet i zapisuje go w jednej transakcji Room. Schemat v2 rozdziela wersje, pytania, kategorie, tłumaczenia i media. Migracja z v1 zachowuje dotychczasowe rekordy. Wersja z identyczną zawartością może być uruchamiana ponownie bez duplikowania danych. Zmieniona zawartość z tym samym identyfikatorem wersji powoduje odrzucenie importu i zachowanie już zapisanych danych. Pakiety DEMO nie są udostępniane w oficjalnej puli. Kompilacja bez tej właściwości pomija dołączany pakiet.

## Testy

```sh
python3 -m unittest discover -s tools/tests -v
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Testy Pythona tworzą syntetyczne arkusze DEMO i archiwa ZIP podczas uruchomienia. Test liczb kontrolnych katalogu 07.2026 jest pomijany bez pliku źródłowego pod podaną ścieżką. Test kodeków wykorzystuje `ffmpeg` oraz `ffprobe`. Testy Androida obejmują odczyt pakietu i lokalną bazę Room. Uruchomienie ostatniego polecenia wymaga emulatora lub urządzenia.
