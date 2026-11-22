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

## Pierwszy etap

Aplikacja uruchamia polski onboarding, pulpit kategorii B, wybór trybu i ustawienia motywu. DataStore zapamiętuje ukończenie onboardingu oraz motyw jasny, ciemny lub zgodny z systemem. Interfejs działa offline.

Room przygotowuje lokalny schemat pytań i wersji bazy. Aplikacja startuje z pustą bazą. Nauka i egzamin próbny wyjaśniają, że do ich rozpoczęcia potrzebna jest zweryfikowana baza dla kategorii B. Import bazy, rozwiązywanie pytań, punktacja egzaminu, zapis postępu i odtwarzanie multimediów należą do kolejnych etapów. Format źródłowy i reguły walidacji opisuje [dokumentacja importu katalogu](docs/import-katalog-072026.md).

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

Testy jednostkowe obejmują model pytań i trwałość preferencji. Testy instrumentowane sprawdzają nawigację po onboardingu i trybach, wybór motywu oraz dostępność pytań w Room.

## Struktura

| Katalog | Odpowiedzialność |
| --- | --- |
| `app/src/main/java/pl/prawko/app/ui/` | Ekrany Compose, motyw, nawigacja i ViewModel ze StateFlow |
| `app/src/main/java/pl/prawko/app/domain/` | Modele pytań i kontrakty repozytoriów |
| `app/src/main/java/pl/prawko/app/data/` | Repozytoria, integracja Room i DataStore |
| `app/schemas/` | Eksport wersji schematu Room |
| `app/src/test/`, `app/src/androidTest/` | Testy jednostkowe i instrumentowane |
