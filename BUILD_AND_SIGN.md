# Guida per principianti: build, installazione e firma

Questi passaggi richiedono un PC con accesso a Internet e un Android SDK funzionante. La build Gradle debug e release non firmata è riuscita in CI, non nella sandbox; firma, installazione release e compatibilità su dispositivi reali restano da verificare. Non installare un prototipo su un dispositivo con dati non sostituibili e non distribuirlo prima dei controlli di `LIMITATIONS.md`.

## 1. Preparare Android Studio

1. Installa una versione aggiornata di Android Studio che supporti **Android Gradle Plugin 9.4.1**. Usa JDK 17 come **Gradle JDK** in *Settings → Build, Execution, Deployment → Build Tools → Gradle*; un JDK 21 compatibile va normalmente bene, ma non è stato verificato con questo progetto.
2. Nel **SDK Manager**, installa Android SDK Platform **37.0**, Android SDK Build-Tools **36.0.0**, Platform-Tools e, se vuoi un emulatore, Emulator e un'immagine di sistema. Accetta le licenze Android dopo averle lette.
3. Ottieni il progetto completo, apri in Android Studio la cartella con `settings.gradle.kts` e attendi *Gradle Sync*. Il wrapper scarica Gradle 9.6.1 e controlla il checksum configurato. Serve accesso a `services.gradle.org`, al relativo host di download, Google Maven e Maven Central. Non disabilitare la verifica TLS per aggirare un errore di rete.
4. Se Android Studio richiede la posizione dell'SDK, impostala dall'interfaccia; `local.properties` è locale e non va condiviso.

## 2. Eseguire una versione debug

1. Crea un emulatore o abilita **Opzioni sviluppatore → Debug USB** su un dispositivo di prova. Accetta sul dispositivo l'eventuale richiesta di autorizzazione USB.
2. Seleziona la configurazione `app` e premi **Run**. In alternativa, dalla radice del progetto:

   ```bash
   ./gradlew :app:assembleDebug
   adb devices
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   In PowerShell/cmd usa `gradlew.bat` anziché `./gradlew`. `adb` è in `platform-tools`; se non è nel PATH, usa il percorso completo. Su più dispositivi usa `adb -s SERIAL install -r ...`.
3. La build debug usa una chiave di debug locale, non adatta alla pubblicazione. Se un'app con lo stesso package ma firma diversa è già installata, Android rifiuterà l'aggiornamento: **disinstallarla cancella i suoi dati**; prima esportali, sapendo che l'export attuale non comprende i disegni.

## 3. Controlli consigliati

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug testDebugUnitTest ktlintCheck
./gradlew :core-database:connectedDebugAndroidTest :feature-drawing:connectedDebugAndroidTest :app:connectedDebugAndroidTest
python3 -m unittest discover -s scripts/tests -v
python3 scripts/audit_apk_fonts.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/audit_apk_fonts.py app/build/outputs/apk/release/app-release-unsigned.apk
```

I test con `connected...` richiedono un dispositivo/emulatore acceso. I report sono nelle cartelle `*/build/reports/`. Prova in particolare API 26, API recenti, lingua italiana/inglese, RTL, caratteri grandi, TalkBack, tastiera hardware, edge-to-edge, rotazione e interruzioni durante il salvataggio. Il workflow `.github/workflows/android.yml` esegue build/test e controlli font quando GitHub Actions è abilitato; verificane il risultato reale prima di fidarti.

L'audit blocca font il cui hash non è esplicitamente approvato e verifica l'asset che sovrascrive il font SESL. **Non** certifica le licenze transitive, altre immagini, loghi, diritti di marchio o contenuto AAB; esamina il grafo risolto con `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, conserva gli avvisi richiesti e ispeziona il pacchetto finale anche con APK Analyzer prima di distribuirlo.

## 4. Creare e custodire una chiave release

Android Studio può creare una chiave tramite **Build → Generate Signed Bundle / APK**. In alternativa, su Linux/macOS con JDK installato:

```bash
keytool -genkeypair -v -keystore "$HOME/lume-release.keystore" \
  -alias lume-release -keyalg RSA -keysize 4096 -validity 10000
```

Il comando chiede interattivamente password e dati del certificato: **non scrivere password in file di progetto, repository, chat o riga di comando**. Conserva keystore e password in un posto sicuro con backup separato: perderli può impedire di firmare gli aggiornamenti. Non committare file `.jks` o `.keystore`; non usare la chiave debug per pubblicare.

## 5. Firmare un APK release manualmente

Dopo che il controllo qualità è riuscito, crea l'APK non firmato. L'esempio è per Linux/macOS e presuppone `ANDROID_HOME` puntato all'SDK:

```bash
./gradlew :app:assembleRelease
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -P 16 -f -v 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk "$HOME/lume-aligned.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign \
  --ks "$HOME/lume-release.keystore" --ks-key-alias lume-release \
  --out "$HOME/lume-release.apk" "$HOME/lume-aligned.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$HOME/lume-release.apk"
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 -v 4 "$HOME/lume-release.apk"
python3 scripts/audit_apk_fonts.py "$HOME/lume-release.apk"
```

`apksigner` chiederà le password interattivamente. L'allineamento deve avvenire **prima** della firma; l'opzione 16 KiB non sostituisce la verifica di compatibilità delle librerie native con pagine da 16 KiB. Per un **Android App Bundle**, usa la procedura guidata di Android Studio o firma con `jarsigner`: `apksigner` firma APK, non AAB. La release con R8 è stata compilata e sottoposta all'audit font in CI, ma il pacchetto firmato e l'esecuzione della release non sono ancora stati provati.

## 6. Problemi frequenti

- **Download fallito / SSL handshake:** verifica proxy, firewall, certificati e accesso a Gradle, Google Maven e Maven Central. Non disattivare TLS o checksum.
- **SDK 37 mancante:** installa `platforms;android-37.0` nel SDK Manager e risincronizza. In CLI: `sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'` dopo aver configurato l'SDK e accettato le licenze.
- **Classi duplicate o `NoSuchMethodError`:** i fork SESL sostituiscono alcune classi AndroidX. Ispeziona `:app:dependencies`, allinea o correggi il grafo; non assumere che le esclusioni attuali siano sufficienti.
- **Scrittura fallita:** tieni aperta la schermata e ritenta; se possibile esporta il testo. La bozza in memoria non è un backup e può andare persa se il processo viene chiuso.

Documentazione ufficiale: [build da riga di comando](https://developer.android.com/build/building-cmdline), [firma delle app](https://developer.android.com/studio/publish/app-signing), [zipalign](https://developer.android.com/tools/zipalign), [apksigner](https://developer.android.com/tools/apksigner).
