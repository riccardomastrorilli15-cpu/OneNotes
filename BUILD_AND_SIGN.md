# Guida per principianti: build, installazione e firma

Questi passaggi richiedono un PC con accesso a Internet e un Android SDK funzionante. La build Gradle debug e la release firmata con chiave debug sono state prodotte in CI, non nella sandbox; controlla sempre l'esito della run riferita alla revisione che vuoi usare. Compatibilità e installazione su dispositivi reali restano da verificare. Non installare un prototipo su un dispositivo con dati non sostituibili e non distribuirlo prima dei controlli di `LIMITATIONS.md`.

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
3. La build debug usa una chiave di debug locale, non adatta alla pubblicazione. Se un'app con lo stesso package ma firma diversa è già installata, Android rifiuterà l'aggiornamento: **disinstallarla cancella i suoi dati privati**. Prima usa, se disponibile nella versione installata, il backup manuale completo delle note e verifica di poterlo ripristinare; l'export testo singolo non comprende i disegni. Le copie esportate restano nella posizione scelta.

## 3. Controlli consigliati

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:lintDebug testDebugUnitTest ktlintCheck
./gradlew :core-database:connectedDebugAndroidTest :feature-drawing:connectedDebugAndroidTest :app:connectedDebugAndroidTest
python3 -m unittest discover -s scripts/tests -v
python3 scripts/audit_apk_fonts.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/audit_apk_fonts.py app/build/outputs/apk/release/app-release-unsigned.apk
python3 scripts/audit_apk_fonts.py app/build/outputs/bundle/release/app-release.aab
```

I test con `connected...` richiedono un dispositivo/emulatore acceso. I report sono nelle cartelle `*/build/reports/`. Prova in particolare API 26, API recenti, lingua italiana/inglese, RTL, caratteri grandi, TalkBack, tastiera hardware, edge-to-edge, rotazione e interruzioni durante il salvataggio. Il workflow `.github/workflows/android.yml` esegue build/test e controlli font quando GitHub Actions è abilitato; verificane il risultato reale prima di fidarti.

L'audit blocca font il cui hash non è esplicitamente approvato e verifica l'asset che sovrascrive il font SESL negli APK o nell'AAB; è passato sui tre pacchetti della CI citata nel README. **Non** certifica le licenze transitive, il codice SESL, altre immagini, loghi o diritti di marchio. La distribuzione è bloccata dai [diritti SESL da chiarire](licenses/SESL-RIGHTS-REVIEW.md): esamina il grafo risolto con `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`, conserva gli avvisi richiesti e ispeziona il pacchetto finale con APK Analyzer.

## 4. APK release firmato per sole prove interne

```bash
# Genera automaticamente ~/.android/debug.keystore se non esiste.
./gradlew :app:assembleDebug
./gradlew -Plume.signTestRelease=true :app:assembleRelease
./scripts/ci_inspect_apk.sh app/build/outputs/apk/release/app-release.apk apk-inspection.txt
adb install -r app/build/outputs/apk/release/app-release.apk
```

Servono JDK 17, SDK e `ANDROID_HOME`; lo script usa `aapt`, `apksigner` e `zipalign` dei Build-Tools 36.0.0. R8 rimuove codice/risorse inutilizzati e ottimizza, ma `-dontobfuscate` disattiva il rinominamento delle classi. Le dipendenze vengono scaricate durante la build dai repository configurati e non sono state tutte sottoposte ad audit indipendente.

La chiave debug standard è `~/.android/debug.keystore` sul computer che compila; normalmente alias `androiddebugkey`, password standard `android`. La CI la crea su un runner effimero e **non la conserva**. Una chiave rigenerata ha un certificato differente e non può aggiornare un APK firmato con la vecchia: prima salvare e verificare il backup, perché la disinstallazione cancella i dati privati. Per ricreare manualmente una chiave debug assente su Linux/macOS:

```bash
mkdir -p "$HOME/.android"
keytool -genkeypair -keystore "$HOME/.android/debug.keystore" -storepass android \
  -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' \
  -keyalg RSA -keysize 2048 -validity 10000
```

Non sovrascrivere un keystore esistente. Questa firma è nota/pubblica e solo per test: non usarla per distribuire l'app.

## 5. Creare e custodire una chiave release

Android Studio può creare una chiave tramite **Build → Generate Signed Bundle / APK**. In alternativa, su Linux/macOS con JDK installato:

```bash
keytool -genkeypair -v -keystore "$HOME/lume-release.keystore" \
  -alias lume-release -keyalg RSA -keysize 4096 -validity 10000
```

Il comando chiede interattivamente password e dati del certificato: **non scrivere password in file di progetto, repository, chat o riga di comando**. Conserva keystore e password in un posto sicuro con backup separato: perderli può impedire di firmare gli aggiornamenti. Non committare file `.jks` o `.keystore`; non usare la chiave debug per pubblicare.

## 6. Firmare un APK release manualmente

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

`apksigner` chiederà le password interattivamente. L'allineamento deve avvenire **prima** della firma; l'opzione 16 KiB non sostituisce la verifica di compatibilità delle librerie native con pagine da 16 KiB. Per un **Android App Bundle**, usa la procedura guidata di Android Studio o firma con `jarsigner`: `apksigner` firma APK, non AAB. La CI produce e ispeziona anche una release firmata con chiave debug temporanea. Per conoscere il risultato e l'eventuale prova di avvio, controlla la run riferita al commit effettivo.

## 7. Problemi frequenti

- **Download fallito / SSL handshake:** verifica proxy, firewall, certificati e accesso a Gradle, Google Maven e Maven Central. Non disattivare TLS o checksum.
- **SDK 37 mancante:** installa `platforms;android-37.0` nel SDK Manager e risincronizza. In CLI: `sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'` dopo aver configurato l'SDK e accettato le licenze.
- **Classi duplicate o `NoSuchMethodError`:** i fork SESL sostituiscono alcune classi AndroidX. Ispeziona `:app:dependencies`, allinea o correggi il grafo; non assumere che le esclusioni attuali siano sufficienti.
- **Scrittura fallita:** tieni aperta la schermata e ritenta; se possibile esporta il testo. La bozza in memoria non è un backup e può andare persa se il processo viene chiuso.

Documentazione ufficiale: [build da riga di comando](https://developer.android.com/build/building-cmdline), [firma delle app](https://developer.android.com/studio/publish/app-signing), [zipalign](https://developer.android.com/tools/zipalign), [apksigner](https://developer.android.com/tools/apksigner).
