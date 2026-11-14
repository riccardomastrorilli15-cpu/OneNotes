# Architettura di OneNotes

Questo documento descrive come è costruito il progetto: livelli, cartelle, modello dei dati e
le scelte tecniche che spiegano perché il codice è scritto così.

---

## 1. Modello architetturale: MVVM con un layer dati unico

```
┌─────────────────────────────────────────────────────────────────┐
│ UI (View/XML)                                                   │
│  Activity/Fragment  →  ViewModel  →  Repository  →  Room / File │
│        ▲                   │                                    │
│        └────── Flow (StateFlow) ──────┘                         │
└─────────────────────────────────────────────────────────────────┘
```

- **UI (View/XML).** Nessun `LiveData`, nessun binding bidirezionale: i fragment osservano
  `StateFlow` con `repeatOnLifecycle(STARTED)` e disegnano. Le view sono XML, i componenti sono
  quelli One UI (`ToolbarLayout`, `DrawerLayout`, `BottomSheetDialog`, preferenze sesl).
- **ViewModel.** Uno per schermata (`MainViewModel` per la lista, `EditorViewModel` per
  l'editor). Contiene lo stato, non tocca Android se non per `Application` e non conosce
  le view. Tutto il lavoro pesante gira su `Dispatchers.IO`.
- **Repository.** È l'unico punto che parla con il database. Applica anche due regole
  trasversali: mantiene l'indice full text allineato alla nota e cifra il corpo delle note
  bloccate (che non finisce mai nell'indice di ricerca).
- **Dipendenze.** Nessun framework DI: `AppContainer` (in `di/`) è un oggetto scritto a mano,
  creato una volta sola in `OneNotesApp`. Con una decina di classi Hilt/Dagger sarebbe solo peso.

### Perché non Compose

La libreria One UI (`oneui-design`) è **View/XML** e i fork sesl lo sono altrettanto: mescolare
Compose significherebbe perdere i componenti Samsung (app bar collassante, action mode, drawer,
preferenze) o riscriverli. Tutta l'interfaccia è quindi View/XML + Kotlin.

---

## 2. Struttura delle cartelle

```
app/src/main/java/com/onenotes/app/
├── OneNotesApp.kt
│     Application: crea il container, i canali di notifica, applica il tema scelto
│     e lancia (fuori dal percorso critico di avvio) la pulizia del cestino e degli allegati.
│
├── di/AppContainer.kt
│     Grafo delle dipendenze. Espone: database, fileStorage, settings, notes, folders,
│     tags, reminders (scheduler), drive, export. Ogni scrittura passa da onNotesChanged()
│     che aggiorna il widget e riprogramma i promemoria.
│
├── data/
│   ├── model/
│   │   ├── Entities.kt    NoteEntity, NoteFtsEntity, FolderEntity, TagEntity (+ FTS4)
│   │   ├── Blocks.kt      Blocchi della nota e serializzazione JSON / Markdown
│   │   └── Filters.kt     NoteFilter, NoteSection, SortMode, ListLayout
│   ├── db/                AppDatabase, NoteDao, FolderDao, TagDao
│   ├── repository/        NotesRepository, FoldersRepository, TagsRepository,
│   │                      SettingsRepository, FileStorage (allegati privati)
│   ├── security/          CryptoManager (AES-256-GCM, prefisso "enc1:")
│   └── export/            ExportManager (JSON/Markdown/testo/PDF), DriveBackupClient,
│                          SamsungNotesImporter
│
├── ui/
│   ├── main/       MainActivity (drawer + due riquadri), NoteListFragment, adapter, ViewModel
│   ├── editor/     EditorActivity (app bar) + EditorFragment (contenuto) + EditorViewModel
│   │               BlockAdapter (un adapter, 5 tipi di riga), SpanMapper, BlockEditText
│   ├── draw/       DrawingView (canvas vettoriale + cache bitmap), DrawingActivity,
│   │               DrawingRenderer (riuso off-screen per PDF/anteprime), DrawModels
│   ├── settings/   SettingsActivity + SettingsFragment (PreferenceFragmentCompat)
│   ├── lock/       LockActivity (BiometricPrompt, attività trasparente)
│   ├── scan/       ScanActivity (fotocamera di sistema, filtri, OCR ML Kit)
│   ├── share/      CreateNoteActivity (target "OneNotes" nella condivisione)
│   ├── reminder/   ReminderScheduler, NotificationHelper, ReminderReceiver, BootReceiver
│   ├── widget/     NotesWidgetProvider + NotesWidgetService (RemoteViews)
│   └── common/     Sheets (bottom sheet), Pickers (data/ora, link), FontScale
│
└── util/           Estensioni (toast, clipboard, condivisione, FileProvider) e TimeUtils
```

---

## 3. Modello dei dati

### Il corpo di una nota è una lista di blocchi

Una nota non è un blob di HTML: è un **array JSON di blocchi** (`NoteBody`):

```json
[{"id":"…","type":"text","style":"HEADING1","text":"Spesa","checked":false,
  "spans":[[0,5,"b"]]},
 {"type":"image","file":"….jpg","name":"volantino","w":1200,"h":1600},
 {"type":"audio","file":"….m4a","dur":8400},
 {"type":"drawing","file":"….json","h":520},
 {"type":"file","file":"….pdf","name":"contratto","size":81234,"mime":"application/pdf"}]
```

Vantaggi concreti:

* il **testo piatto** per la ricerca e le anteprime (`plainText`) è generato una volta sola;
* gli allegati sono **file privati** copiati in `filesDir`, quindi non spariscono se l'utente
  cancella l'originale dalla galleria e non serve alcun permesso di archiviazione;
* un blocco sconosciuto (di una versione futura) viene ignorato invece di rompere la nota.

### Indice full text

`notes_fts` (FTS4) è scritta **solo** da `NoteDao.upsert`, insieme alla nota: non può mai
desincronizzarsi. La query dell'utente diventa una MATCH con prefissi
(`"note"* AND "imp"*`), quindi "note imp" trova "note importanti".

### Note bloccate

Il corpo è cifrato con AES‑256‑GCM e salvato con il prefisso `enc1:`; `plainText` resta vuoto,
per cui la nota bloccata **non** compare nelle anteprime né nei risultati di ricerca.

### Cestino

Soft delete (`deleted`/`deleted_at`) con ripristino; una nota in cestino da più di 30 giorni
viene eliminata definitivamente (con i suoi allegati) all'avvio dell'app.

---

## 4. Schermate e responsabilità

| Schermata | File | Note |
|---|---|---|
| Lista + drawer | `MainActivity`, `NoteListFragment`, `MainViewModel` | Su tablet/Fold aperto l'editor appare nel riquadro destro: `MainActivity` sostituisce `EditorFragment` nel contenitore |
| Editor | `EditorActivity` (app bar) + `EditorFragment` (contenuto) | La stessa `EditorFragment` funziona a tutto schermo **e** come riquadro: tutto ciò che è app bar sta nell'Activity |
| Disegno | `DrawingActivity` + `DrawingView` | Vettoriale + cache bitmap; la S Pen modula lo spessore, il pulsante della penna cancella |
| Impostazioni | `SettingsActivity` + `SettingsFragment` | Le preferenze scrivono negli stessi `SharedPreferences` letti da `SettingsRepository` |
| Blocco | `LockActivity` | Attività trasparente: mostra solo il dialogo di sistema (impronta/volto/PIN) |
| Scansione | `ScanActivity` | Fotocamera di sistema (nessun permesso), filtri, OCR on‑device |
| Condivisione | `CreateNoteActivity` | Attività trasparente: crea la nota e apre l'editor |

### Editor: come funziona il salvataggio

* le modifiche di testo aggiornano l'istanza del blocco e chiamano `markDirty()`;
* ogni modifica **strutturale** passa da `change { }`, che salva uno snapshot nello stack undo;
* il salvataggio su disco è **debounced** (700 ms) e forzato quando si esce;
* una nota rimasta completamente vuota viene **eliminata** (come fa Samsung Notes).

---

## 5. Prestazioni

* **Avvio veloce:** nessun DI framework, nessuna reflection; il database è aperto una volta sola
  e il lavoro di pulizia gira su un altro dispatcher dopo l'avvio.
* **Liste lunghe:** `ListAdapter` con `DiffUtil`, payload per la selezione, nessun `notifyDataSetChanged()`.
* **Immagini:** decodifica con `inSampleSize` calcolato sulle dimensioni reali.
* **Disegno:** cache bitmap + riuso dei tratti vettoriali; durante il tratto viene ridisegnata
  solo la stroke in corso.
* **Batteria:** nessun job periodico; i promemoria usano `AlarmManager` (esatti quando possibile)
  e vengono riprogrammati solo quando i dati cambiano o al boot.

---

## 6. Scelte tecniche dichiarate

1. **Nessun WorkManager / Navigation / Glance / security-crypto:** le funzioni che servono sono
   poche e scritte direttamente (scheduler, FragmentManager, RemoteViews, `KeyStore`+AES-GCM).
2. **PDF con `android.graphics.pdf.PdfDocument`:** zero dipendenze, funziona offline.
3. **Drive con chiamate OkHttp alla REST v3** invece dell'SDK Java (pesante) o della vecchia
   Drive Android API (chiusa da Google). I backup stanno in `appDataFolder`: invisibili
   all'utente e rimossi con la disinstallazione.
4. **OCR con ML Kit on‑device:** nessun dato esce dal telefono, serve connessione solo per
   scaricare il modello la prima volta.
5. **Tema:** `AppCompatDelegate` (chiaro/scuro/sistema) + colori One UI; la dimensione del
   carattere è applicata sovrascrivendo `fontScale` della configurazione.
6. **Versioni Jetpack allineate all'epoca dei fork sesl** (activity 1.2.4, fragment 1.3.6,
   lifecycle 2.5.1): vedi il README per il motivo.
