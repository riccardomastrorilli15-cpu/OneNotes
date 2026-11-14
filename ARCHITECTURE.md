# Lume Notes — architettura

**Decisioni della Fase 0, 14 novembre 2026.** Questo documento descrive il disegno e i confini del progetto; lo stato effettivo delle funzioni e delle verifiche è in `LIMITATIONS.md`, non si deduce dall'esistenza di un modulo.

## Principi

Applicazione locale, offline-first, Kotlin e View/XML con ViewBinding per compatibilità con OneUI Project. Livelli separati: presentazione MVVM → contratti e modelli → repository → database/file. Hilt costruisce dipendenze e cicli di vita; coroutines/Flow propagano aggiornamenti senza lavoro I/O sul thread principale. Non c'è server né telemetria. Le azioni non implementate non devono essere spacciate per presenti.

## Moduli previsti

| Modulo | Responsabilità | Dipendenze |
| --- | --- | --- |
| `app` | Application, Activity, navigazione, ViewBinding, integrazione Hilt e manifest | feature e core |
| `core-model` | Modelli e funzioni di dominio pure, identificativi e regole | nessuna UI |
| `core-database` | Room, DAO, schema, FTS5 e test di integrazione | core-model |
| `core-data` | Repository, DataStore, transazioni e I/O SAF | core-model, core-database |
| `core-ui` | Tema, token e dipendenze OneUI/SESL condivise | OneUI Project |
| `feature-editor` | Logica dell'editor/Markdown e trasformazioni verificabili | core-model |
| `feature-search` | Normalizzazione delle query FTS e regole di ricerca | core-model |
| `feature-drawing` | Canvas e serializzazione dei tratti, qualora implementati | core-model/core-ui |
| `feature-settings` | Modelli e regole delle impostazioni, qualora implementati | core-data/core-ui |

La presenza di un modulo senza interfaccia completa non equivale a una funzione pronta. I confini possono essere ridotti se necessario per evitare codice vuoto o dipendenze circolari.

## Dati e flusso

1. L'utente modifica una nota nella View; il ViewModel conserva stato e produce aggiornamenti.
2. Il repository salva su Room in background e fornisce Flow; gli aggiornamenti della lista derivano dal database. Il salvataggio deve essere serializzato e resistente alle modifiche rapide, senza sovrascrivere flag aggiornati da altre schermate.
3. Le note hanno ID UUID stabili, chiave numerica interna per SQLite, timestamp di creazione e modifica, versione e flag di stato (archivio/cestino); la sincronizzazione futura richiede ancora risoluzione conflitti e tombstone affidabili, che non sono implementate solo con questi campi.
4. Ricerca testuale: **Room 3.0.3** e `@Fts5` con `BundledSQLiteDriver` (SQLite con FTS5 anche sui dispositivi in cui SQLite di sistema non lo offre). Query composte da token normalizzati e parametri, non da SQL concatenato. L'indice va mantenuto transazionalmente, compresi modifica, ripristino ed eliminazione; coprirlo con test. [Room FTS5](https://developer.android.com/reference/androidx/room3/Fts5), [driver](https://developer.android.com/kotlin/multiplatform/room).
5. Per dataset grandi, la versione corrente usa query LIMIT incrementali, proiezioni brevi e DiffUtil; Paging 3/Room `PagingSource` è un miglioramento futuro. Prestazioni da misurare, non garantite dal solo design. [Room 3 richiede il converter Paging](https://developer.android.com/jetpack/androidx/releases/room3).
6. Le impostazioni applicative sono in DataStore; SAF usa URI concessi esplicitamente, niente permessi di storage ampi. L'esportazione è una copia esplicita, non un backup cifrato.

## Piattaforma e build

- minSdk 26; Android 17 stabile usa `compileSdk`/`targetSdk` 37 ([setup ufficiale](https://developer.android.com/about/versions/17/setup-sdk)). AGP 9.4.1 supporta API 37 e richiede almeno Gradle 9.6.0 e JDK 17 ([compatibilità](https://developer.android.com/build/releases/agp-9-4-0-release-notes)); wrapper 9.6.1. Kotlin integrato in AGP 9, KSP per Room e Hilt; version catalog e Gradle Kotlin DSL.
- OneUI Design 1.2.6 e SESL sono fork vecchi del framework UI: isolare e controllare il grafo delle dipendenze per evitare classi duplicate e `NoSuchMethodError`; serviranno prove reali su API 26–37. Non sostituire SELS con AndroidX standard senza una migrazione completa della libreria.
- Insets di barre, display cutout e IME devono essere applicati esplicitamente: Android 15 impone edge-to-edge con target 35+ ([documentazione](https://developer.android.com/develop/ui/views/layout/edge-to-edge)). Layout sempre ridimensionabile.
- `release` con R8 e firma esterna: mai chiavi o password nel repository. CI: compilazione, lint e test unitari; test strumentali richiedono emulatori/dispositivi.

## Sicurezza e resilienza

Non presentare un semplice flag di blocco o `BiometricPrompt` come cifratura. Finché non sono stati integrati e testati cifratura autenticata, gestione chiavi e ciclo di vita, le note sono **in chiaro nel database privato dell'app** e nessuna funzione di blocco deve promettere sicurezza. Backup cifrati, Drive, notifiche affidabili, OCR e AI richiedono implementazioni e permessi specifici; restano fuori finché non verificati. Il manifest disabilita il backup cloud Android. Su Android 12+ alcuni produttori possono comunque consentire il trasferimento diretto tra dispositivi anche con `allowBackup=false`: non promettere che sia disabilitato ovunque.

## Qualità e limiti

Test unitari per normalizzazione FTS e trasformazioni Markdown; test Room per sincronizzazione indice/transazioni; test UI per creazione, modifica e ricerca; misure su dispositivo per avvio, 5.000 note, 50.000 caratteri e latenza penna. Compatibilità SESL/AndroidX, accessibilità e asset di terzi sono gate di rilascio. La sandbox inizialmente non conteneva Java, Gradle o SDK Android; successivamente sono stati reperiti un runtime Java e un compilatore Kotlin temporanei, non un SDK/build Gradle completo. La rete consente solo host selezionati: eventuali build/test non eseguibili devono essere segnalati esplicitamente, mai dati per riusciti.
