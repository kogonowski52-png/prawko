# Etap 3A — dopracowanie interfejsu Prawko 2027 (bez regresji logiki egzaminu)

## Cel

Wydanie jakościowe aplikacji Android, nie redesign od zera. Użytkownik zainstalował Prawko DEMO na telefonie Samsung i potwierdził działanie nauki/egzaminu oraz ogólnie dobry wygląd, ale prosi o dopracowanie. Referencja: na ekranie ciemnego egzaminu podstawowego w fazie czytania widać górny niebieski pasek "DEMO · Dane syntetyczne", dużą kartę "Pytanie 1 z 32 / Łącznie 24:50", dwuwierszowe pytanie, komunikat o późniejszym udostępnieniu odpowiedzi i pustą przestrzeń w dolnej części.

## Co zachować

- Natywny Kotlin + Jetpack Compose/Material 3, obecna architektura, styl granatowy i jasne seledynowo-zielone akcenty oraz obecne jasne/ciemne motywy.
- Czytelne duże pytania, poprawną kolejność faz, obowiązujące timery, brak powrotu do poprzednich pytań, 32-pytaniowy silnik, Room v3, poprawną obsługę multimediów.
- Wersja DEMO musi pozostać wyraźnie oznaczona DEMO. Nie wolno przekształcić danych DEMO w oficjalne.

## Problemy i oczekiwane rozwiązania

### A. Ekran aktywnego egzaminu (pierwszy priorytet)

1. **Górna karta z postępem**: zmniejsz jej pionową wysokość, popraw hierarchię elementów. Wyraźny numer pytania, czas całego egzaminu i opis fazy. Pozostaw dostępność informacji czytnikom ekranowym. Pasek postępu powinien wyrażać jednoznacznie postęp 1/32, a licznik czasu pozostałego egzaminu nie może być mylony z czasem aktualnej fazy.
2. **Faza zapoznawania się**: zamiast samotnego szarego zdania "Odpowiedzi będą dostępne po zakończeniu tej części" wyświetl elegancki, kompaktowy komponent statusu z odliczaniem, np. "Zapoznaj się z pytaniem" + "Odpowiedzi za 00:10" (liczba dynamicznie pochodzi z istniejącego zegara). Nie pokazuj aktywnych opcji przed dozwoloną fazą, nie twórz sztucznego opóźnienia, nie modyfikuj limitów.
3. **Puste przestrzenie**: nie wypełniaj ich ozdobnikami na siłę. Kiedy pytanie ma realne foto lub film, to multimedia stanowią właściwy główny element. Kiedy w DEMO pytanie jest tylko tekstowe, zastosuj uporządkowany układ z kompaktowymi komunikatami, a w fazie odpowiedzi stabilną strefą wyboru; bez fikcyjnych zdjęć dróg, pozornych mediów, ilustracji imitujących egzamin lub dekoracji ograniczających miejsce na treść.
4. **Faza odpowiedzi**: pokazuj opcje TAK/NIE lub A/B/C dużymi kaflami z klarownym wyborem, widocznymi stanami disabled/selected i potwierdzeniem w bezpiecznej strefie na dole ekranu. Przycisk zatwierdzenia nie może nachodzić na systemowy pasek nawigacji ani zasłaniać odpowiedzi przy skalowaniu fontu. Gdy brak zaznaczenia, zachowaj dotychczasową bezpieczną możliwość pominięcia.
5. **Animacje**: dyskretna animacja przejścia między fazami i pytaniami; brak migotania, podwójnych kliknięć, nieodwracalnych zmian przez wyścigi stanów; respektuj reduce-motion gdy dostępny. Brak animowania zegara kosztem dokładności.
6. **Kolory i proporcje**: popraw spacing i kontrasty, zachowaj obecną paletę. Unikaj gradientów i nadmiaru badge'y/chipów. Zadbaj o ekran o szerokości ok. 360–400dp i dużą skalę czcionki oraz małe telefony.

### B. Spójność reszty aplikacji

- Przegląd `StudyScreen.kt`: czytelne oznaczenie typu/punktów, odpowiedzi i feedbacku; odpowiednia hierarchia, stany brakujących mediów, nie zasłaniaj treści.
- `ExamResultScreen.kt`: wyraźny wynik, próg zdania, lista pytań, przejście do powtórki; spójność motywu, przemyślane odstępy.
- Dashboard, ekran trybów, ustawienia, statystyki: dopracowanie spacing, nagłówków, przycisków i składników, bez burzenia nawigacji.
- UI ma działać w ciemnym i jasnym motywie, w orientacji pionowej, na ekranach w różnych rozmiarach.

## Kryteria jakości i testów

1. Użytkownik może ukończyć egzamin bez utraty funkcjonalności.
2. Brak zmian w `ExamEngine` i Room, chyba że są uzasadnione naprawą błędu potwierdzonego testem; nie zmieniaj przepisowych czasów i punktacji przy pracach wizualnych.
3. Testy Compose: faza READING nie udostępnia odpowiedzi; faza ANSWERING udostępnia je; liczniki są aktualne; wybór i zatwierdzenie działają; dostępność etykiet, TalkBack i skalowania.
4. Testy screenshot/Compose na emulatorze 360dp, 393dp, 432dp, font 1.3–1.5x. Jeżeli można wygenerować zdjęcia/screenshoty i załączyć do raportu, porównaj przed/po.
5. Uruchom `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` i `./gradlew :app:connectedDebugAndroidTest` (gdy emulator jest dostępny); przedstaw faktyczne wyniki.
6. Zapisz gotowe zmiany w repozytorium. Nie kończ na samym opisie lub makietach. Nie dodawaj oficjalnych mediów do GitHub ani dodatkowych bibliotek bez uzasadnienia.
7. Nie modyfikuj celowo skonfigurowanego workflow `.github/workflows/android-demo.yml` poza niezbędnymi naprawami.
