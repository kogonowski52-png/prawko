# AGENTS.md — instrukcje dla Codex

## Cel projektu

Buduj komercyjnie gotową natywną aplikację Android **Prawko — Testy na Prawo Jazdy 2027** (polski interfejs). Pierwsza wersja obsługuje kategorię B. Aplikacja jest niezależna od WORD oraz organów państwowych.

## Stos technologiczny

- Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL.
- MVVM, ViewModel + StateFlow, Navigation Compose.
- Room do danych pytań, wyników i postępu; DataStore do preferencji.
- Media3 do materiałów wideo.
- Spójna warstwowa organizacja `data/`, `domain/`, `ui/`.
- Korzystaj ze stabilnych i kompatybilnych wersji bibliotek. Nie podawaj fikcyjnych wyników kompilacji.

## Styl i doświadczenie użytkownika

Nowoczesny, profesjonalny produkt Android, nie demonstracyjna makieta. Wizualnie: eleganckie, jasne tło, granatowe teksty, zieleń jako oszczędny akcent; także dark mode. Dopracowane odstępy, typografia, karty, mikroanimacje i ikony. Responsywność na różne telefony, dostępność dla TalkBack, wysokie kontrasty. Nie naśladuj identyfikacji urzędowej i nie używaj logotypów państwowych/WORD.

## Funkcjonalności docelowe

1. Onboarding i startowy dashboard.
2. Nauka pytań według działów, zapamiętywanie błędów i ulubionych.
3. Egzamin próbny zgodny z aktualnymi przepisami (typy pytań, grupy trudności, punktacja, limity czasowe, brak powrotu, losowanie).
4. Ekran wyników i historia.
5. Statystyki postępu i powtórki.
6. Lokalna baza pytań/multimediów z jawną wersją.
7. Docelowo AdMob i Premium (Google Play Billing), przy czym nigdy nie wyświetlaj reklamy przerywającej egzamin.

## Dane i bezpieczeństwo

- Nie przedstawiaj fikcyjnych pytań jako prawdziwych egzaminacyjnych.
- Przygotuj importer oficjalnie udostępnionych plików oraz mapowanie zdjęć i filmów; nie kopiuj treści bez weryfikacji warunków wykorzystania.
- Dane testowe mogą być tylko jawnie oznaczone jako DEMO.
- W trybie egzaminu, gdy brak prawdziwej bazy, pokaż komunikat o braku danych, a nie pozorowany egzamin.
- Nie commituj kluczy, sekretów, plików `.jks`, danych dostępowych.
- Zachowaj prywatność użytkownika i minimalizuj uprawnienia Androida.

## Pierwsze zadanie implementacyjne

1. Stwórz działający projekt Android w tym repozytorium (nie stronę WWW).
2. Zaimplementuj nawigację, theme light/dark, onboarding, dopracowany dashboard, ekran wyboru trybu i puste stany dla danych egzaminacyjnych.
3. Dodaj modele pytań, interfejs repozytorium i podstawową strukturę Room, nie dodając fikcyjnych „oficjalnych” pytań.
4. Dodaj odpowiednie testy podstawowych mechanizmów i dokumentację uruchomienia.
5. Uruchom Gradle build/testy, jeśli środowisko ma Android SDK i JDK; raportuj dosłownie co uruchomiono, a czego nie dało się uruchomić.
6. Nie kończ na planie: edytuj rzeczywiste pliki i pokaż strukturę plików oraz wynik weryfikacji.

Kolejne etapy: właściwa baza i importer, kompletny egzamin, analiza wyników, monetyzacja, publikacja w Google Play.
