# Pijltjes Weg

Android-puzzelspel: tik op een pijl en hij schuift in zijn richting van het bord.
Staat er een andere pijl in de weg, dan bots je en verlies je een leven (3 per level).
Elk level is gegarandeerd oplosbaar en de borden worden steeds groter en voller.

Verdienmodel (Google AdMob):
- **Banner** onderaan, altijd zichtbaar.
- **Tussendoor-advertentie** na elk derde level (vanaf level 3).
- **Beloningsadvertentie** voor een hint of voor een extra leven als je af bent.
- **Toestemmingsvenster** (AVG/GDPR) via Google's UMP, verplicht voor spelers in de EU.

## Bouwen

**Makkelijkst: GitHub.** Zet dit project in een GitHub-repository. Bij elke push bouwt
GitHub Actions automatisch een APK (om zelf te installeren) en een AAB (voor de
Play Store). Je vindt ze onder het tabblad *Actions*, bij de laatste run, onder *Artifacts*.

**Of op je eigen pc:** installeer [Android Studio](https://developer.android.com/studio),
open deze map en druk op ▶ Run met je telefoon aangesloten (USB-debugging aan).

De debug-build gebruikt altijd Google's test-advertenties. Daar mag je gewoon op klikken.

## Van test naar echte advertenties

1. Maak een account op <https://admob.google.com> en voeg de app toe (Android).
2. Maak drie advertentieblokken: *Banner*, *Interstitial* en *Rewarded*.
3. Ga in AdMob naar *Privacy en berichten* en maak een **AVG-bericht** aan voor deze app.
   Zonder dat bericht krijgen spelers in de EU geen toestemmingsvenster en verdien je daar bijna niets.
4. Vul de ID's in `gradle.properties` in (`ADMOB_APP_ID=ca-app-pub-…~…` en de drie `…/…` blok-ID's),
   of bij GitHub als *Secrets* met dezelfde namen.
5. **Klik nooit op je eigen echte advertenties.** Daar blokkeert Google je account voor.

## Ondertekenen voor de Play Store

Eenmalig een upload-sleutel maken (bewaar het bestand en wachtwoord goed, kwijt = gedoe):

```
keytool -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

- Lokaal: kopieer `keystore.properties.example` naar `keystore.properties` en vul in.
- GitHub: zet deze *Secrets*: `KEYSTORE_BASE64` (uitvoer van `base64 -w0 upload-key.jks`),
  `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`upload`), `KEY_PASSWORD`.

Het bestand `app-release.aab` upload je in de Play Console. Verhoog bij elke nieuwe
versie `versionCode` in `app/build.gradle.kts`.

## Pakketnaam

De app heet intern `com.pijlpuzzel.game`. Wil je een andere naam, verander die dan
**vóór** de eerste upload (`applicationId` en `namespace` in `app/build.gradle.kts`):
daarna ligt hij voor altijd vast.

## Map `store/`

- `privacybeleid.html`: verplicht voor de Play Store en AdMob. Zet het online
  (bijvoorbeeld met GitHub Pages) en vul je naam en e-mailadres in.
- `winkelvermelding.md`: titel, korte en lange beschrijving, en wat je invult bij
  de Play Console-vragenlijsten.
- `app-ads.txt`: zet dit bestand in de hoofdmap van je website (de website die je in de Play Store opgeeft).
