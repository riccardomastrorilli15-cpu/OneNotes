# OneNotes

App di note per i dispositivi Samsung Galaxy, pensata come alternativa a Samsung Notes:
più veloce, più ricca e visivamente identica a un'app One UI nativa.

- **Lingua:** Kotlin, solo Jetpack (ViewModel, Room, Flow/Coroutines), architettura MVVM.
- **UI:** [oneui-design](https://github.com/OneUIProject/oneui-design) + librerie **sesl**
  (i fork Samsung di AppCompat/Material): niente Compose, niente "quasi One UI".
- **Dati:** 100% locali e offline. Nessun server, nessuna telemetria.
- **Requisiti:** minSdk 26 (Android 8.0), targetSdk 35, compilato con SDK 36.

---

## 1. Cosa sa fare

| Area | Funzionalità |
|---|---|
| **Editor** | titolo, grassetto/corsivo/sottolineato/barrato, titoli H1‑H3, elenchi puntati e numerati, checklist, citazione, link, colore del testo, evidenziatore, annulla/ripeti illimitati |
| **Blocchi** | la nota è una lista di blocchi: testo, immagine, foto, registrazione audio, disegno, file/PDF |
| **Disegno** | penna, evidenziatore, gomma, 7 colori, 5 spessori, supporto S Pen (pressione + pulsante per cancellare), modalità "solo S Pen", undo/redo |
| **Organizzazione** | cartelle, tag, colori nota, note fissate, archivio, cestino con ripristino (pulizia automatica dopo 30 giorni) |
| **Ricerca** | full text su titolo e corpo (FTS4), filtri per tag/cartella/intervallo di date, ordinamento |
| **Sicurezza** | blocco per nota con impronta/volto/PIN del dispositivo (AES‑256‑GCM sul contenuto), blocco dell'intera app |
| **Promemoria** | data e ora, notifica con azioni "Apri" e "Segna come fatto", riprogrammati al riavvio |
| **Backup** | esportazione/importazione JSON completa, esportazione Markdown / testo / PDF (anche di tutto l'archivio), backup e ripristino su Google Drive (`appDataFolder`), importazione da Samsung Notes (`.sdocx`, `.memo`, `.zip`) |
| **Extra** | widget home screen, condivisione verso altre app, target "OneNotes" nella condivisione di sistema, scansione documenti con OCR (ML Kit on‑device), schermo diviso e finestra a comparsa, supporto dimensione carattere |

---

## 2. Come aprire il progetto in Android Studio (passo per passo)

### 2.1 Prerequisiti

1. **Android Studio** Ladybug (2024.2) o successivo — con **JDK 17** incluso
   (*File ▸ Settings ▸ Build, Execution, Deployment ▸ Build Tools ▸ Gradle ▸ Gradle JDK*
   deve puntare a **jbr-17** o a un JDK 17 installato).
2. **Android SDK 36** (Android 16) installato:
   *Settings ▸ Android SDK ▸ SDK Platforms* → spunta **Android 16 (API 36)**;
   nella scheda *SDK Tools* servono **Android SDK Build-Tools 36**, **Android SDK Platform-Tools**
   e **Android SDK Command-line Tools**.
3. Almeno ~2,5 GB liberi (la prima build scarica Gradle e le dipendenze).

### 2.2 Apertura e prima build

1. Clona il repository e aprilo con **File ▸ Open…** selezionando la cartella che contiene
   `settings.gradle.kts` (non la cartella `app`).
2. Android Studio propone **"Sync Now"**: conferma. Al termine non devono esserci errori nella
   finestra *Build ▸ Sync*.
3. Collega un Galaxy via USB (con *Debug USB* attivo) oppure crea un AVD
   (*Device Manager ▸ Create device ▸ Phone ▸ Pixel 8 / API 36*). L'app funziona anche su
   dispositivi non Samsung: i componenti One UI restano identici, cambiano solo i colori di
   sistema.
4. Premi **Run ▶** (Shift+F10). Il modulo da eseguire è `app`.
5. Al primo avvio l'app crea il database e mostra la lista vuota con il pulsante **+**.

### 2.3 APK / App Bundle firmato

* **Debug:** *Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)* →
  `app/build/outputs/apk/debug/app-debug.apk`.
* **Release:** *Build ▸ Generate Signed Bundle / APK ▸ APK o Android App Bundle* → crea
  (o seleziona) un keystore e completa la procedura. Il `build.gradle.kts` del modulo `app`
  abilita già **minify + shrinkResources** con `proguard-rules.pro`.

### 2.4 Debug su un Galaxy reale (consigliato)

Su un Galaxy si vedono il tema One UI completo, la S Pen e le finestre a comparsa.
Attiva *Opzioni sviluppatore ▸ Debug USB* e seleziona il dispositivo nella barra degli strumenti.

---

## 3. Struttura del progetto

```
OneNotes/
├── settings.gradle.kts          # repositories: google() + mavenCentral()
├── build.gradle.kts             # solo plugin (AGP, Kotlin, KSP)
├── gradle/libs.versions.toml    # catalogo versioni (unico posto dove si toccano le versioni)
└── app/
    ├── build.gradle.kts         # modulo app: SDK, esclusioni sesl, dipendenze
    ├── proguard-rules.pro
    ├── schemas/                 # schemi Room generati da KSP (da versionare)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                 # 101 file: layout, menu, drawable vettoriali, valori it/en, xml
        └── java/com/onenotes/app/
            ├── OneNotesApp.kt           # Application: container, canali, tema, pulizia
            ├── di/AppContainer.kt        # grafo delle dipendenze scritto a mano
            ├── data/
            │   ├── model/               # entità Room, blocchi della nota, filtri
            │   ├── db/                  # AppDatabase + DAO (note, cartelle, tag)
            │   ├── repository/          # Notes, Folders, Tags, Settings, FileStorage
            │   ├── security/            # CryptoManager (AES-256-GCM)
            │   └── export/              # JSON/Markdown/PDF, Drive, import Samsung Notes
            ├── ui/
            │   ├── main/                # MainActivity, lista note, drawer, ViewModel
            │   ├── editor/              # EditorActivity/Fragment, adapter blocchi, span
            │   ├── draw/                # DrawingView, DrawingActivity, renderer, modello
            │   ├── settings/            # preferenze (PreferenceFragmentCompat)
            │   ├── lock/                # BiometricPrompt
            │   ├── scan/                # scansione + OCR
            │   ├── share/               # target di condivisione
            │   ├── reminder/            # notifiche e scheduling
            │   ├── widget/              # widget home screen
            │   └── common/              # bottom sheet, dialoghi, palette, font scale
            └── util/                    # estensioni Kotlin, formattazione date
```

Il dettaglio dell'architettura (flusso dei dati, modello del database, scelte tecniche) è in
**[docs/ARCHITETTURA.md](docs/ARCHITETTURA.md)**.

---

## 4. Dipendenze e perché alcune versioni sono "basse"

Le librerie One UI (**sesl**) sono fork di AppCompat, core, fragment, recyclerview, preference,
drawerlayout, coordinatorlayout e Material: **contengono gli stessi package** di quelle Google.
Per questo `app/build.gradle.kts`:

```kotlin
configurations.all {
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.core", module = "core")
}
```

(esattamente le due esclusioni documentate da oneui‑core) e dichiara per intero la pila sesl.
Di conseguenza `activity` (1.2.4), `fragment` (1.3.6) e `lifecycle` (2.5.1) sono allineati
all'epoca dei fork: alzarli a caso porta a errori come *"cannot access androidx.core.view.MenuHost"*
o a classi duplicate. **Room, OkHttp, ML Kit, coroutines e KSP invece sono alle ultime versioni.**

Se un giorno vorrai aggiornare la pila One UI, aggiorna insieme `oneuiDesign` e tutte le
`sesl*` nel catalogo versioni, e solo dopo ritoccare activity/fragment/lifecycle.

---

## 5. Permessi (richiesti solo quando servono)

| Permesso | Quando | Perché |
|---|---|---|
| `POST_NOTIFICATIONS` | al primo promemoria | mostrare la notifica, con spiegazione prima della richiesta |
| `RECORD_AUDIO` | premendo "Registrazione" | memo vocali, con dialogo di spiegazione |
| `SCHEDULE_EXACT_ALARM` | al primo promemoria | sveglia puntuale |
| `INTERNET` | solo backup Drive | l'app funziona offline; nessun altro uso |

Fotocamera e galleria **non** richiedono permessi: passano dal `FileProvider` e
dall'`ACTION_IMAGE_CAPTURE` / selettore di sistema.

---

## 6. Stato del progetto: cosa è completo e cosa va rifinito

### Completo e funzionante

- [x] Progetto Gradle completo (wrapper 8.11.1 incluso), si apre e si compila in Android Studio
- [x] Shell One UI: `DrawerLayout`, `ToolbarLayout`, app bar collassante, action mode, ricerca
- [x] Lista note (lista/griglia), selezione multipla, stati vuoti, ordinamenti, widget
- [x] Editor a blocchi con formattazione inline, annulla/ripeti, salvataggio automatico
- [x] Disegno a mano libera con S Pen,pressione, undo/redo, anteprima PNG
- [x] Allegati: immagini, foto, registrazioni audio, file/PDF, disegni, scansioni con OCR
- [x] Cartelle, tag, colori, fissati, archivio, cestino con ripristino e pulizia a 30 giorni
- [x] Ricerca full text (FTS4) e filtri
- [x] Blocco nota e blocco app con biometrico/PIN, crittografia AES-256-GCM
- [x] Promemoria con notifiche e riprogrammazione dopo il riavvio
- [x] Esportazione/importazione JSON, Markdown, testo, PDF; import da Samsung Notes
- [x] Backup/ripristino Google Drive (`appDataFolder`)
- [x] Layout adattivo: telefono, tablet, Galaxy Z Fold/Flip (due riquadri)
- [x] Tema chiaro/scuro automatico, colori One UI, dimensione carattere
- [x] Condivisione (verso e da altre app), target "OneNotes", schermo diviso

### Da rifinire (miglioramenti noti, non blocca nulla)

- **Sincronizzazione multi-device:** il backup è un file su Drive, non una sincronizzazione
  continua: due modifiche sullo stesso giorno da dispositivi diversi non si fondono.
- **Google Drive in release:** `GoogleSignIn` richiede che la SHA‑1 del keystore sia registrata
  in un progetto Google Cloud; senza registrazione il backup funziona solo in debug.
- **OCR:** il modello ML Kit viene scaricato al primo utilizzo (serve connessione una volta).
- **Scansione:** la foto è affidata all'app fotocamera di sistema (nessun ritaglio automatico
  dei bordi del documento: si ruota e si filtra a mano).
- **Editor:** il trascinamento dei blocchi per riordinarli e le tabelle non sono implementati.
- **Import Samsung Notes:** testo e immagini sono recuperati in modo affidabile; i disegni e
  l'audio dei formati più recenti vengono importati come allegato quando presenti.
- **Accessibilità:** da rivedere le descrizioni dei contenuti e l'ordine del focus sul
  disegno e sulla scansione.
- **Test:** non ci sono ancora test automatici (la struttura a repository li rende semplici da
  aggiungere); serve una suite di UI test su Fold/Flip.

---

## 7. Licenza e crediti

Codice dell'app: MIT.

L'app usa [oneui-design](https://github.com/OneUIProject/oneui-design) e le librerie
**sesl** di OneUIProject (MIT), che permettono di replicare i componenti Samsung.
"One UI" e "Samsung Notes" sono marchi di Samsung Electronics: questo progetto non è affiliato.
