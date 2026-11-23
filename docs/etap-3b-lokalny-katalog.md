# Etap 3B — lokalny katalog w aplikacji testowej na telefon (bez publicznego udostępniania danych)

## Kontekst i cel

Repozytorium `kogonowski52-png/prawko` po commicie `ffaa4be` zawiera działający Android/Compose, Room v3, naukę i egzamin oraz media packs. Działa APK DEMO z *syntetycznymi pytaniami matematycznymi*. Użytkownik korzysta z Codexa przez WWW i chce przetestować na Androidzie **realne pytania kategorii B ze swojego lokalnego katalogu**, bez Android Studio.

Lokalny, już wykonany import XLSX lipiec 2026:
- plik na Windowsie: `C:\Prawko\prawko\local-data\review-2026-07-B-media-4\questions.json`;
- `source=DEMO`, `datasetVersion=DEMO-2026-07-B`;
- 2128 zaakceptowanych pytań kategorii B, 7 odrzuconych;
- 3052/3053 dopasowanych odwołań do multimediów z trzech archiwów, brakujące `5-9.2021.wmv`;
- **brak `--prepare-media`**: nazwy dopasowano, ale wartości `assetPath` pozostają null, więc multimedia nie są jeszcze dostępne w Androidzie;
- archiwa źródłowe zajmują łącznie około 19 GB i pozostają na komputerze. Katalog ma być testowany prywatnie, bez zapewnienia, że prawa do komercyjnej redystrybucji zostały rozstrzygnięte.

Zadanie: umożliwić łatwe *lokalne* wczytanie istniejącego `questions.json` **z pamięci telefonu przez systemowy wybór pliku Android**, bez kopiowania Excela, pytań ani ZIP do GitHuba i bez Android Studio. Użytkownik może przesłać mały JSON na swój telefon np. przez kabel, prywatną chmurę lub inny własny sposób.

## Wymagania implementacyjne

### 1. Bezpieczny wariant testowy „Prawko Katalog TEST”

- Wariant wyłącznie debug, najlepiej Gradle `-PprawkoLocalPreview=true` z oddzielnym `applicationIdSuffix=.preview` i wyraźną etykietą `Prawko Katalog TEST`/banerem na *każdym* ekranie.
- Nie używaj `-PprawkoDemo=true` (ta flaga celowo wymusza syntetyczne pytania). Nie instaluj na wierzchu istniejącego DEMO; aplikacje mają współistnieć.
- Wariant release i domyślna debug nie mogą niezamierzenie uaktywnić zestawu `DEMO` lub testowo wczytanego katalogu. Nie zmieniaj `source` w JSON na `VERIFIED_OFFICIAL`; nie fałszuj praw/pochodzenia i nie łącz testowego zestawu z produkcyjnym.
- Dodaj mały osobny debug-only przepływ udostępniający `source=DEMO` do `QuestionRepository` jedynie w wariancie preview; przełączanie aktywnej wersji i zapisy wyników wersjonowane. W razie niepewności dodaj wyraźny `DatasetSource.LOCAL_PREVIEW` tylko jako lokalną reprezentację, bez konwersji flagi źródłowej w JSON. Domyślne zapytania Room dla `VERIFIED_OFFICIAL` pozostają bez zmian dla release.
- Zadbaj, by podczas zmiany importowanego pliku z tą samą wersją nie nadpisywać istniejących danych; informuj użytkownika o wymaganej nowej wersji lub konieczności wyczyszczenia danych testowej aplikacji.

### 2. Import z telefonu, bez komputera deweloperskiego

- W `Ustawienia` debug-preview dodaj przycisk `Wczytaj lokalny katalog (JSON)`, otwierający Android Storage Access Framework `ActivityResultContracts.OpenDocument` z MIME JSON i fallback `*/*`.
- Odczyt wyłącznie przez `contentResolver.openInputStream(uri)`, bez szerokich uprawnień do pamięci i bez kopiowania zdalnych danych.
- Użyj istniejącego `parseCatalog(input)` i transakcyjnej instalacji `installCatalog` / Room v3. Walidacja limitu 32 MiB, struktury, wersji, integralności pakietu i bezpieczna obsługa błędów. Potwierdź obsługę `source=DEMO` w *preview*, bez oznaczania jej jako oficjalna.
- W UI pokaż postęp odczytu, nazwę i wersję zestawu, liczbę pytań po imporcie, informacje o braku ścieżek multimedialnych i komunikaty o błędach.
- Zachowaj dane importowanego katalogu jedynie w pamięci prywatnej aplikacji (Room). Nie wysyłaj nigdzie wyników ani treści pytań.

### 3. Działanie trybów przy niekompletnych mediach

- **Nauka**: umożliwiaj przegląd tekstu 2128 pytań z oznaczeniem `Brak wymaganego zdjęcia/filmu — odpowiedź może zależeć od materiału`. Dla pytań, w których niezbędne jest medium, nie udawaj pełnej symulacji.
- **Egzamin**: obecna polityka słusznie blokuje pytania wymagające niedostępnego medium. Nie omijaj zabezpieczenia ani losowania koszyków tylko po to, by pokazać egzamin. Jeśli brak jest wystarczająco wielu pytań bez mediów, blokada startu ma pokazywać konkretny powód (brak pakietów medialnych). Przy pustych `assetPath` dla całej bazy nie ma podstaw do obiecywania pełnego egzaminu.
- Przygotowanie mediów i paczek (z FFmpeg i `--prepare-media`) to osobny etap po potwierdzeniu praw do przetwarzania i rozpowszechniania. Należy przygotować małą paczkę demonstracyjną z własnych syntetycznych plików jako test importera mediów, bez przenoszenia oryginalnych filmów do repozytorium.

### 4. APK do pobrania z GitHub

- Rozszerz lub dodaj odrębny workflow GitHub Actions do zbudowania `app-debug.apk` dla `-PprawkoLocalPreview=true` i opublikowania *wyłącznie APK bez pytań*, jako artifact `prawko-katalog-test-apk`. Zadbaj o aktualne działające `android-actions/setup-android@v4` z `packages: ''`; stary `sdkmanager tools` jest nieobsługiwany.
- Zachowaj osobny, już działający workflow `Android DEMO APK`. Nie publikuj samego `questions.json` ani archiwów na GitHubie.
- Dokumentacja krok po kroku po polsku: pobierz artifact APK, zainstaluj na telefonie, skopiuj `questions.json` na telefon, otwórz `Ustawienia → Wczytaj lokalny katalog`, wybierz JSON, sprawdź liczbę pytań, przejdź do nauki.
- Jeśli funkcjonalność wymaga migracji Room v3→v4, zapewnij migrację bez utraty lokalnego postępu; preferuj wydzielenie testowego stanu i wykorzystanie istniejącego schematu.

### 5. Kryteria zakończenia

1. Kompiluje się wariant preview, DEMO oraz zwykły debug; release nie zawiera ekranu importu testowego i nie aktywuje testowej bazy.
2. Na emulatorze można wczytać mały syntetyczny plik `source=DEMO`, zobaczyć go w nauce, sprawdzić odpowiedzi i zapisać postęp.
3. Test z plikiem z błędem, zbyt dużym, z powieloną wersją z inną zawartością i bez mediów daje bezpieczny komunikat, nie korumpuje DB i nie udostępnia egzaminu bez materiałów.
4. Widoczne oznaczenie TEST i pochodzenie zestawu; zero prawdziwych danych katalogowych w repozytorium lub APK z CI.
5. Testy jednostkowe i instrumentowane + rzeczywisty wynik `assembleDebug`, `lintDebug`. Jeżeli emulator nie działa, podaj ograniczenie zamiast deklarować wykonane testy.
6. Zapisz kod, testy, workflow i polską instrukcję na GitHubie. Podaj commit, link do Actions, opis faktycznie uruchomionych testów oraz instrukcję uruchomienia na Samsungu.

To jest zadanie implementacyjne. Nie kończ na planie ani samej dokumentacji.
