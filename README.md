# Prawko — Testy na Prawo Jazdy 2027

Niezależna polskojęzyczna aplikacja na Androida do nauki teorii i rozwiązywania egzaminów próbnych na prawo jazdy.

> Niezależny projekt edukacyjny bez powiązań z WORD lub Ministerstwem Infrastruktury.

## Założenia

- Pierwsza wersja: kategoria B.
- Android: Kotlin + Jetpack Compose + Material 3.
- Architektura MVVM, Room (pytania i wyniki), DataStore (preferencje), Media3 (multimedia).
- Tryb nauki, symulator egzaminu, historia wyników, powtarzanie błędów.
- Wsparcie działania offline po zaimportowaniu kompletnej, uprawnionej do użycia bazy danych.
- Planowana monetyzacja: Google AdMob i jednorazowy zakup Premium przez Google Play Billing.

## Jakość i publikacja

- Nie wstawiamy fikcyjnych pytań jako oficjalnych.
- Źródło pytań, aktualność i prawa do korzystania z mediów wymagają weryfikacji przed publikacją.
- Nie przechowujemy kluczy API, sekretów ani plików podpisu w repozytorium.
- Egzamin powinien odwzorowywać obowiązujące w chwili publikacji zasady, które trzeba zweryfikować z aktualnym prawem.
- Build i testy uznajemy za zakończone dopiero po rzeczywistym uruchomieniu.

## Nauka i egzamin

Nauka kategorii B oferuje kolejność losową lub według identyfikatorów, filtry typu i punktacji oraz serie z ulubionych pytań lub ostatnich błędów. Po sprawdzeniu odpowiedzi użytkownik widzi poprawny wybór, a Room zapisuje postęp dla wersji katalogu.

Egzamin próbny losuje 32 różne pytania w sześciu koszykach punktowych. Przebieg i zegar są odtwarzane z Room po powrocie do próby. Postęp pokazuje statystyki nauki i historię egzaminów. Z wyniku można przejść do powtórki błędów z tej samej wersji katalogu. [Zasady egzaminu opisane w etapie 2](docs/etap-2-nauka-egzamin.md).

Standardowa kompilacja wyświetla stan braku danych, dopóki nie udostępnisz zweryfikowanego katalogu. APK może zawierać tekst katalogu. Zdjęcia i filmy instaluje się osobno przez **Ustawienia → Paczki multimediów**. [Instrukcja importu katalogu i przygotowania mediów](docs/import-katalog-072026.md).

## Uruchomienie

### Wymagania

- JDK 17, Android SDK Platform 36 i SDK Build-Tools 35.0.0.
- Android Studio obsługujące Android Gradle Plugin 8.13 lub terminal z dostępem do Android SDK.
- Telefon lub emulator z Androidem 7.0 (API 24) lub nowszym do uruchomienia aplikacji i testów instrumentowanych.

Otwórz katalog repozytorium w Android Studio i uruchom konfigurację `app`. W terminalu wskaż SDK przez `ANDROID_HOME` i użyj dołączonego Gradle Wrappera. Na Windows użyj `gradlew.bat` zamiast `./gradlew`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Plik APK znajdziesz w `app/build/outputs/apk/debug/app-debug.apk`. Po podłączeniu urządzenia lub uruchomieniu emulatora możesz zainstalować aplikację i wykonać testy instrumentowane.

```sh
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

### Demonstracja bez katalogu

```sh
./gradlew :app:installDebug -PprawkoDemo=true
```

Wariant **Prawko DEMO** ma osobny identyfikator `pl.prawko.app.demo`, lokalną bazę i stałe oznaczenie na ekranach. Jego syntetyczne pytania ćwiczą obsługę nauki i 32-pytaniowego egzaminu. Flaga działa dla typu kompilacji debug. Użyj osobnego wywołania Gradle do kompilacji z oficjalnym katalogiem.

Testy JVM obejmują limity czasu, koszyki, wznowienie, cały przebieg egzaminu z kontrolowanym zegarem oraz filtrowanie nauki. Testy instrumentowane obejmują migrację Room, przejście przez ekrany, zapis postępu i lokalne odtwarzanie syntetycznego MP4. Testy narzędzi uruchomisz przez `python3 -m unittest discover -s tools/tests -v`.

## Struktura

| Katalog | Odpowiedzialność |
| --- | --- |
| `app/src/main/java/pl/prawko/app/ui/` | Ekrany Compose, motyw, nawigacja i ViewModel ze StateFlow |
| `app/src/main/java/pl/prawko/app/domain/` | Modele pytań i kontrakty repozytoriów |
| `app/src/main/java/pl/prawko/app/data/` | Repozytoria, integracja Room i DataStore |
| `app/schemas/` | Eksport wersji schematu Room |
| `app/src/test/`, `app/src/androidTest/` | Testy jednostkowe i instrumentowane |
