# Etap 2 — działające tryby nauki i egzaminu (kategoria B)

## Stan repozytorium przed etapem 2

Repozytorium zawiera natywną aplikację Android (Kotlin/Compose), Room v2, import pakietu `questions.json` z katalogu XLSX, oraz ekrany startowe i wybór trybu. Obecny `QuestionAvailabilityScreen` jest ekranem zastępczym: **nie ma jeszcze silnika nauki ani egzaminu**.

Źródła danych, których nie ma w środowisku Codexa, należy zostawić poza Gitem:
- `local-data/KATALOG_dla_kandydatów_na_kierowców_072026.xlsx`
- `local-data/media/*.zip` (ok. 19 GB łącznie)
- `local-data/review-2026-07-B-media-4/questions.json` i raporty

Import na komputerze użytkownika: 2135 rekordów kat. B, z czego 2128 przyjętych i 7 odrzuconych; 3052 dopasowane odwołania do mediów, 1 brak: `5-9.2021.wmv`. **Dopasowanie ZIP nie jest równoważne z gotowością plików do odtwarzania:** bieżący import bez `--prepare-media` ma ścieżki `assetPath = null`. Pytanie wymagające niedostępnego wideo **nie może być użyte w egzaminie symulującym WORD**.

## Główny cel implementacyjny

Zastąpić puste ekrany nauki i egzaminu prawdziwymi, dopracowanymi interaktywnymi funkcjonalnościami. Kod musi zostać zaimplementowany i przetestowany, nie tylko opisany. Pierwsza kategoria to B; projektuj rozszerzalnie, bez wpisywania B na stałe w silniku.

### 1. Tryb nauki

- Czytaj faktycznie wybrany, aktywny zbiór z istniejącego `QuestionRepository` i Room; nie produkuj atrap pytań w trybie produkcyjnym.
- Wybór losowego/po kolei pytania, filtrowanie podstawowe/specjalistyczne i 1/2/3 punkty, liczba przerobionych pytań.
- Ekran pojedynczego pytania: treść, obraz lub film jeśli jest dostępny, odpowiedzi TAK/NIE albo A/B/C, sprawdzenie wyboru, czytelny wynik, następne pytanie.
- Zapis lokalny odpowiedzi, poprawności, powtórek i oznaczonych ulubionych, bez logowania.
- Wystąpienia mediów z `assetPath == null` oznacz jako niedostępne; nie pokazuj fikcyjnego obrazu drogowego. W nauce można wyświetlać treść pytania z ostrzeżeniem o niepełnym materiale, ale nie sugeruj, że użytkownik widział kompletną sytuację drogową.
- Pytania z odrzuconych rekordów nie trafiają do puli.

### 2. Egzamin próbny

Wersja bazowa według §19 rozporządzenia MI z 24 listopada 2023 r. (Dz.U. 2023 poz. 2659; aktualność sprawdzaj przed publikacją):
- 20 podstawowych: 10 x 3 pkt, 6 x 2 pkt, 4 x 1 pkt;
- 12 specjalistycznych: 6 x 3 pkt, 4 x 2 pkt, 2 x 1 pkt;
- razem 32 unikalne pytania, maks. 74 pkt, wynik pozytywny od 68 pkt;
- 25 minut limitu całego testu (wariant języka migowego i dostosowane limity dopiero jako oddzielna, prawidłowo zweryfikowana ścieżka);
- podstawowe: 20 s na zapoznanie się z pytaniem + 15 s na odpowiedź; specjalistyczne: 50 s łącznie;
- brak powrotu do już zatwierdzonych pytań, zmiana odpowiedzi do zatwierdzenia/limitu, brak odpowiedzi = 0 punktów;
- losowanie bez powtórzeń, wyłącznie z aktywnej zweryfikowanej kategorii B i ze zdatnymi do pokazania materiałami. Jeśli w którymkolwiek koszyku brakuje pytań, **zablokuj start** i pokaż dokładnie, czego brakuje — nie oszukuj, zastępując pytania innym koszykiem lub fikcyjnym materiałem.
- Pamiętaj o przejściu aplikacji w tło: timer oparty o monotoniczny zegar i trwały stan sesji; nie pozwól resetować czasu przez obrót telefonu lub odtworzenie ekranu.
- Wynik: punkty, wynik zdany/niezdany, podsumowanie odpowiedzi, lista błędów i ponowny trening. Zachowuj historię prób w Room.
- Używaj czystej logiki Kotlin w warstwie `domain` (losowanie, punktacja, stan sesji i czas) i osobnych testów jednostkowych.

### 3. Media i dystrybucja

- Zaimplementuj interfejs odtwarzania obrazów JPG/JPEG oraz MP4 (Media3) z faktycznych plików dostępnych lokalnie. WMV nie traktuj jako gotowego do odtwarzania na Androidzie.
- Nie dodawaj do repozytorium 19 GB ZIP ani wygenerowanych filmów, ani nie wkładaj ich w całości do APK.
- Wydziel kontrakt `MediaRepository` i przygotuj wsparcie małych, wersjonowanych paczek pobieranych na żądanie, z hashami i cache. Nie implementuj na ślepo nieistniejącego produkcyjnego URL/CDN; konfiguracja hostingu jest kolejnym etapem.
- Kiedy treść pytania ma media wymagane do prawidłowej interpretacji, a dany plik nie jest dostępny, zapobiegaj użyciu tego pytania w egzaminie. Nie wolno oznaczać danych jako gotowych na podstawie samej obecności nazwy w ZIP.
- Przeprowadź lokalne testy na własnych syntetycznych mediach. Publiczne rozpowszechnianie zawartości Ministerstwa, a także jej konwersję w ramach publikacji aplikacji, poprzedź potwierdzeniem odpowiednich praw do wykorzystania.

### 4. UI / UX

- Zachowaj istniejący styl Compose/Material 3 i aktualny system motywów, ale zastąp `QuestionAvailabilityScreen` konkretnymi ekranami i nawigacją.
- Nowoczesne stany ładowania, błędu, braku katalogu, braku medium, ekrany potwierdzenia, timer, pasek postępu, duże obszary dotykowe, dostępność.
- Dla testowania interfejsu w środowisku bez prawdziwej bazy wykorzystaj wyłącznie syntetyczne fixture jawnie oznaczone **DEMO**, w osobnym debug/test source set. Nie aktywuj DEMO jako `VERIFIED_OFFICIAL`; testy mogą korzystać z osobnego dostarczonego testowego repozytorium.
- Nie dodawaj jeszcze AdMob, Google Play Billing, logowania ani deklaracji „oficjalna aplikacja WORD”.

### 5. Dobre praktyki Room

- W przypadku rozszerzenia schematu wykonaj bezpieczną migrację v2→v3 i aktualizuj eksportowane schematy.
- Nie niszcz wersji katalogu ani istniejących odpowiedzi użytkownika przy aktualizacji.
- Zapisuj identyfikator pytania **wraz z wersją katalogu** w historii/odpowiedziach, aby aktualizacje nie mieszały danych.

### 6. Kryteria zakończenia

1. Można przejść z dashboardu do nauki i faktycznie odpowiadać na pytania z repozytorium, a w testach debug z jawnie oznaczonych syntetycznych danych.
2. Silnik potrafi wylosować dokładnie 32 pytania z kwotami 10/6/4 oraz 6/4/2 i policzyć maksimum 74 i próg 68.
3. Nie uruchamia egzaminu bez wymaganych mediów lub bez kompletnych koszyków.
4. Można przejść przez cały egzamin z prawidłowym odmierzaniem czasu, zapisem wyniku i przeglądem błędów.
5. Wszystko działa po odtworzeniu procesu/konfiguracji, bez utraty zaliczonych odpowiedzi.
6. Testy obejmują losowanie (również przypadki braku pytań), timery, brak powrotu, punktację, niekompletne media i migrację Room.
7. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` i (jeżeli dostępny emulator) `./gradlew :app:connectedDebugAndroidTest` uruchomione; wyniki raportuj wprost z logów.
8. Kod, testy i dokumentacja faktycznie zapisane w GitHubie (osobny PR preferowany, jeśli Codex ma takie możliwości). Podaj commit/PR, listę nowych ekranów, log testów i listę pozostałych blokad do publikacji.

Link do przepisów: https://eli.gov.pl/api/acts/DU/2023/2659/text.html (szczególnie §19). Aktualność wersji przepisów należy sprawdzić przed wydaniem na Google Play.
