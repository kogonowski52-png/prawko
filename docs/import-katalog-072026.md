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
