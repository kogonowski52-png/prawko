# Test multimediów w Prawko Katalog TEST

Generator tworzy dwa pytania kategorii B oznaczone jako DEMO. Pierwsze używa JPG z kolorowymi pasami. Drugie odwołuje się do ośmiosekundowego MP4 z ruchomym wzorem i cichym tonem. Film zawiera obraz H.264 i dźwięk AAC. Wszystkie materiały powstają w FFmpeg z syntetycznych wzorów.

## Wygenerowanie plików w PowerShell na Windows 11

Użyj Pythona 3.11 lub nowszego oraz `ffmpeg.exe` z obsługą `libx264` i `aac`. Poniższe polecenia działają w PowerShell 5.1. Pobierają aktualny kod z GitHuba do folderu tymczasowego i zapisują gotowe pliki w folderze wskazanym przez `$output`.

```powershell
$tempDir = Join-Path $env:TEMP ('prawko-media-' + [guid]::NewGuid().ToString('N'))
$zip = "$tempDir.zip"
Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/kogonowski52-png/prawko/archive/refs/heads/main.zip' -OutFile $zip -ErrorAction Stop
Expand-Archive -LiteralPath $zip -DestinationPath $tempDir -ErrorAction Stop
$generator = Join-Path $tempDir 'prawko-main\tools\generate_demo_media.py'
$output = 'C:\Prawko\prawko\local-data\media-demo'
py -3 $generator --output-dir $output
if ($LASTEXITCODE -eq 0) { Invoke-Item -LiteralPath $output }
```

Jeżeli FFmpeg znajduje się poza `PATH`, podaj pełną ścieżkę do programu.

```powershell
py -3 $generator --output-dir $output --ffmpeg 'C:\ffmpeg\bin\ffmpeg.exe'
```

Przy kolejnym generowaniu ustaw `$output` na nową ścieżkę, np. `C:\Prawko\prawko\local-data\media-demo-2`, i ponownie wywołaj `py -3 $generator --output-dir $output`. Wersja `DEMO-media-…` zależy od treści katalogu i wygenerowanych mediów.

| Plik lub folder | Zastosowanie |
| --- | --- |
| `questions.json` | Katalog dwóch pytań do wczytania na telefonie |
| `DEMO-media-…-pack-001.zip` | Paczka do instalacji w aplikacji, z manifestem oraz JPG i MP4 |
| `SHA256SUMS` | Sumy kontrolne paczki, katalogu i obu plików multimedialnych |
| `media/` | Wygenerowane pliki JPG i MP4 do samodzielnego podglądu |

Wartość SHA-256 paczki ZIP to pierwsze 64 znaki wiersza z nazwą ZIP w `SHA256SUMS`. Możesz ją też obliczyć w PowerShell.

```powershell
$pack = Get-ChildItem -LiteralPath $output -Filter '*-pack-001.zip' -File
(Get-FileHash -LiteralPath $pack.FullName -Algorithm SHA256).Hash
```

## Test na Samsungu

1. Otwórz zainstalowane **Prawko Katalog TEST**. Jeśli potrzebujesz APK, pobierz artefakt `prawko-katalog-test-apk` z ostatniego udanego uruchomienia workflow na `main` i zainstaluj `app-debug.apk`. [Android Katalog TEST APK](https://github.com/kogonowski52-png/prawko/actions/workflows/android-catalog-test.yml)
2. Skopiuj `questions.json`, `DEMO-media-…-pack-001.zip` i `SHA256SUMS` do osobnego folderu **Pobrane/Prawko-media-demo**. Paczkę `*-pack-001.zip` skopiuj w całości.
3. Wybierz **Ustawienia → Wczytaj lokalny katalog (JSON)** i wskaż skopiowany `questions.json`. Folder `Download/Prawko-media-demo` znajdziesz też po wskazaniu pamięci telefonu w menu systemowego wyboru plików. W Ustawieniach zobaczysz wersję rozpoczynającą się od `DEMO-media-` i **Pytania w katalogu · 2**.
4. Wybierz **Ustawienia → Paczki multimediów**. W polu **Suma SHA-256** wklej tylko 64 znaki dla pliku ZIP z `SHA256SUMS`. Naciśnij **Wybierz paczkę ZIP** i wskaż odpowiadający tej sumie plik. Aplikacja powinna pokazać **Paczka gotowa. Zapisane pliki: 2.**
5. Otwórz **Start → Nauka pytań**, zostaw **Wszystkie typy** i kolejność **Po kolei**, a następnie wybierz **Rozpocznij naukę**.
6. W pierwszym pytaniu zobaczysz zdjęcie z pionowymi pasami kolorów u góry. Zaznacz **A · Pionowe kolorowe pasy**, naciśnij **Sprawdź odpowiedź** i przejdź do **Następne pytanie**.
7. W drugim pytaniu naciśnij **▶** na odtwarzaczu. Sprawdź ruch wzoru, działanie pauzy i dźwięk przy włączonej głośności multimediów. Poprawna odpowiedź to **TAK**.

Jeżeli aplikacja sygnalizuje brak multimediów, sprawdź, czy aktywna wersja katalogu odpowiada nazwie paczki oraz czy instalacja zapisała dwa pliki. W tym zestawie są dwa pytania przeznaczone do nauki. Pełny egzamin wymaga 32 pytań z odpowiednich grup.

Aby wrócić do własnego zestawu, ponownie wybierz wcześniejszy `questions.json` w Ustawieniach. Room przechowuje postęp osobno dla każdej wersji katalogu.

## Testy automatyczne

```sh
python3 -m unittest discover -s tools/tests -v
./gradlew -PprawkoLocalPreview=true :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew -PprawkoLocalPreview=true :app:connectedDebugAndroidTest
```

Test generatora używa `ffprobe` do sprawdzenia obrazu JPG, kodeków H.264 i AAC oraz długości filmu. Porównuje też zawartość ZIP z manifestem i sumami kontrolnymi. Test Androida instaluje syntetyczną paczkę, odczytuje JPG i sprawdza, czy Media3 wyrenderował klatkę MP4 i zakończył odtwarzanie.
