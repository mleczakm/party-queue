<p align="center"><img src="branding/banner.svg" alt="Party Queue" width="720"></p>

# Party Queue

<p align="center">
<a href="https://party-queue.github.io/pl/"><b>Strona projektu</b></a> ·
<a href="https://party-queue.github.io/en/"><b>Website (English)</b></a> ·
<a href="README.md">README in English</a> ·
<a href="https://github.com/party-queue/party-queue/releases/latest">Wydania</a>
</p>

**Jeden telefon. Jedna kolejka. Piosenki wszystkich.** Party Queue zamienia telefon z Androidem w imprezowy jukebox: gra YouTube **bez reklam**,
goście dodają piosenki **z własnych telefonów i laptopów** przez stronę WWW, a gospodarz decyduje, co zagra dalej.
Bez kont, bez kluczy API, bez instalowania czegokolwiek przez gości.

<table>
<tr>
<td align="center" width="220">
<a href="https://github.com/party-queue/party-queue/releases/latest/download/party-queue.apk"><img src="branding/qr-download.svg" alt="Kod QR: pobierz najnowszy APK" width="200"></a><br>
<b>Zeskanuj, aby pobrać</b><br><sub>najnowszy APK, Android 10+, arm64, ok. 225 MB</sub>
</td>
<td>

**Instalacja:** otwórz poniższy link na telefonie (albo zeskanuj kod), otwórz pobrany plik, w razie pytania zezwól na *Instalowanie nieznanych aplikacji*
i wybierz *Zainstaluj mimo to*, jeśli Play Protect zaprotestuje. **Aktualizacja:** pobierz nowy APK tak samo i zainstaluj go na starej wersji.

**[⬇ party-queue.apk](https://github.com/party-queue/party-queue/releases/latest/download/party-queue.apk)** ·
[suma kontrolna](https://github.com/party-queue/party-queue/releases/latest/download/party-queue.apk.sha256) ·
[wszystkie wydania](https://github.com/party-queue/party-queue/releases) · [kanał wydań](https://github.com/party-queue/party-queue/releases.atom)

Krok po kroku z obrazkami: **[party-queue.github.io](https://party-queue.github.io/pl/#install)**
</td>
</tr>
</table>

<p align="center"><img src="docs/screenshots/web-queue-dark.jpg" alt="Strona hosta na komputerze" width="860"></p>

## Co potrafi

- gra YouTube **bez reklam** w osadzonym silniku Firefoksa (GeckoView) z wbudowanym uBlock Origin i gra dalej przy wyłączonym ekranie,
- zmienia piosenki natychmiast (następna jest wczytana w ukrytym, drugim odtwarzaczu) i płynnie przechodzi między nimi, gdy przeskakujesz,
- wczytuje **publiczną playlistę** do lokalnej kolejki w kolejności z YouTube (bez logowania do Google i bez klucza API) albo piosenki wyszukane w aplikacji,
- **karta YouTube** z paskiem adresu: przeglądasz YouTube i okrągłym przyciskiem **+** ustawiasz playlistę jako aktualną albo dodajesz film jako następny / na koniec,
- gesty na kolejce: stuknięcie przenosi piosenkę na górę, przesunięcie w prawo gra od razu, w lewo usuwa, przytrzymanie i przeciągnięcie zmienia kolejność,
- udostępnia **stronę WWW dla gości** w sieci lokalnej; goście ją otwierają (kod QR w karcie *Zaproś* po prostu ją otwiera), proponują piosenki, a gospodarz zatwierdza je jako następne albo na koniec,
- gospodarz może zrobić z każdego gościa **współhosta**, który poprowadzi imprezę z laptopa (skróty klawiszowe, przeciąganie, głośność – wszystko, co host),
- motyw jasny i ciemny, układ poziomy i oszczędne gospodarowanie baterią.

Pełna lista z obrazkami jest na **[stronie projektu](https://party-queue.github.io/pl/#features)**. Kolejka jest lokalna w aplikacji. Nic nie wraca na YouTube.

Wymaga Androida 10 lub nowszego (API 29), arm64. Licencja: [AGPL-3.0](LICENSE); użyte składniki: [THIRD_PARTY.md](THIRD_PARTY.md).
Korzystanie z aplikacji może naruszać regulamin YouTube – robisz to na własną odpowiedzialność.

## Zrzuty ekranu

| | |
| --- | --- |
| ![Strona hosta, motyw jasny](docs/screenshots/web-queue-light.jpg) | ![Propozycje na stronie hosta](docs/screenshots/web-proposals.jpg) |
| ![Wyszukiwanie z przyciskami następna / na koniec](docs/screenshots/web-search.jpg) | ![Goście](docs/screenshots/web-guests.jpg) |
| ![Widok gościa](docs/screenshots/web-guest-queue.jpg) | ![Układ na telefon](docs/screenshots/web-mobile.jpg) |

## Dla programistów

Budowanie, testy, architektura i proces wydań są opisane po angielsku w [README.md](README.md), [CONTRIBUTING.md](CONTRIBUTING.md) i [docs/QA.md](docs/QA.md).
Historia zmian: [CHANGELOG.md](CHANGELOG.md).
