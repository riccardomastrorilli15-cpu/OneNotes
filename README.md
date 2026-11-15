# Lume Notes

Un prototipo Android di app per note **locale e indipendente**, con interfaccia View/XML ispirata a One UI tramite il progetto open source non ufficiale [OneUI Design](https://github.com/OneUIProject/oneui-design) e i fork SESL. Lume Notes non è affiliata a Samsung. Minimo Android 8.0 / API 26; progetto Kotlin, MVVM, Hilt, Room 3/FTS5 e DataStore.

> **Stato: prototipo, non rilascio.** La CI ha compilato debug, release e AAB con R8, superando lint, test JVM e audit font. In una matrice precedente i test sono passati su API 26/29/35/37, ma nella [run 43700790007](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43700790007) il primo carattere è mancato nel test di digitazione su API 26 e 29: causa non accertata. Compatibilità generale, stabilità, prestazioni e accessibilità non sono certificate. La distribuzione è bloccata da [diritti SESL da chiarire](licenses/SESL-RIGHTS-REVIEW.md). Non usare per dati importanti o riservati. Vedere [LIMITATIONS.md](LIMITATIONS.md).

## Cosa c'è nel codice

- Biblioteca con ricerca per prefisso FTS5, cartella e tag testuali, preferiti, pin, archivio, cestino e caricamento incrementale.
- Editor testuale Markdown con formattazioni base, anteprima limitata, undo/redo in sessione, autosalvataggio e indicazione degli errori; backlink `[[Titolo]]` e sei modelli.
- Disegno vettoriale sperimentale a pressione, gomma per tratto e opzione solo stilo; non è un motore S Pen a bassa latenza.
- Import/export della singola nota in **solo testo/Markdown** e condivisione esplicita del testo; questi export non includono disegni. Backup manuale JSON separato tramite selettore documenti, con note, disegni, metadati, archivio e cestino.
- Tema sistema/chiaro/scuro, stringhe italiane/inglesi, interfaccia ridimensionabile e indicazioni di privacy/licenze.

La presenza di codice o di un pulsante non equivale a un collaudo riuscito. Non sono stati aggiunti account, analytics, tracking o pubblicità; il manifest rimuove INTERNET e ACCESS_NETWORK_STATE, e la CI controlla il pacchetto risultante. Non è stato effettuato un audit completo di tutto il codice delle dipendenze. I dati locali e le esportazioni **non sono cifrati**; il backup cloud Android è disattivato nel manifest, ma alcuni produttori possono comunque consentire trasferimenti diretti tra dispositivi. L'esportazione è avviata dall'utente: il provider di documenti scelto può avere una propria rete o conservazione. Disinstallare l'app elimina i dati privati, **non** copie esportate o condivise altrove.

## Backup manuale delle note

Dalla biblioteca apri il menu o il pannello laterale e scegli **Esporta tutte le note (backup)**; conferma l'avviso e scegli esplicitamente un file con il selettore Android. L'istantanea JSON comprende tutte le note indipendentemente dai filtri, inclusi disegni, metadati, archivio e cestino; non comprende impostazioni, esportazioni esterne o funzioni non implementate. **Non è cifrata**: conservala e cancellala separatamente in un luogo sicuro.

Per ripristinare, scegli **Ripristina un backup** e seleziona un JSON creato dall'app. Il ripristino aggiunge solo UUID mancanti: se una nota con lo stesso identificativo esiste già, viene mantenuta senza confrontare il contenuto; il messaggio finale riporta importate e saltate. Una transazione annulla gli inserimenti se il file è incompleto o non valido. Non chiudere l'app durante l'operazione; se l'export viene interrotto può rimanere un file incompleto nella destinazione. L'app usa anche un file temporaneo nella propria cache privata: un arresto forzato può lasciarlo fino alla pulizia della cache o alla disinstallazione.

Limiti: 128 MiB per file, 10.000 note, 8.000.000 di unità UTF-16 per campo e 12.000.000 per nota; cartella e tag sono limitati rispettivamente a 120 e 500 unità, come nell'editor. Anche una stringa JSON con più di 16.000.000 di caratteri codificati viene rifiutata. Se i dati superano un limite, l'operazione fallisce anziché esportare solo una parte. Il backup non sostituisce prove di ripristino su un dispositivo e non garantisce recupero dopo un arresto forzato.

## Per iniziare

Prerequisiti: Android Studio aggiornato e compatibile con **AGP 9.4.1**, JDK 17, Android SDK Platform **37.0**, SDK Build-Tools **36.0.0**, e connessione a Google Maven, Maven Central e Gradle. Il wrapper richiede Gradle 9.6.1. Da un checkout completo, su Linux/macOS:

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug testDebugUnitTest ktlintCheck
./gradlew :core-database:connectedDebugAndroidTest :feature-drawing:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

Il terzo comando richiede un emulatore o dispositivo collegato. L'APK debug, **se la build riesce**, sarà `app/build/outputs/apk/debug/app-debug.apk`. Su Windows usare `gradlew.bat`. La guida passo-passo, installazione, firma release e risoluzione problemi sono in **[BUILD_AND_SIGN.md](BUILD_AND_SIGN.md)**. Sono indicazioni da verificare eseguendo il progetto in un ambiente completo, non una prova di compilazione.

## Verifiche effettivamente eseguite

- **15 test JVM isolati riusciti**: ricerca, trasformazioni/anteprima Markdown, undo/redo, coda di autosalvataggio e serializzazione/validazione del disegno. Per il test isolato del disegno è stato usato JSON-java su JVM e rimosso il runner Android dal test copiato temporaneamente; non sostituisce il test con `org.json` Android. Test compilati con Kotlin 2.2.10 temporaneo e JUnit compilato dal tag `r4.13.2` (il runner riporta `4.13.2-SNAPSHOT`), non dal Gradle del progetto.
- **24 test Python riusciti localmente**: controllo font e permessi su APK/AAB fittizi, diagnostica e validazione dei rapporti CI, contrasto di 16 coppie di colori statiche; smoke test SQLite e controlli di localizzazione/XML riusciti. Le coppie controllate non certificano l’intera interfaccia. Il test SQLite Python non equivale a Room.
- **8 nuovi test JVM isolati riusciti** per lettura UTF-8 e guardia JSON: Kotlin 2.3.21, JRE 17 e JUnit 4.13.2 temporanei, con la libreria coroutine inclusa nel compilatore. Non sostituiscono i task Gradle o i test Android del backup.
- **ktlint 1.8.0** eseguito localmente tramite CLI e, per la revisione collegata sotto, tramite il task Gradle `ktlintCheck` in CI.
- **AAPT2 standalone** ha compilato le risorse e le ha collegate a un `android.jar` API 37 da mirror e a stub minimi delle risorse OneUI; una prova separata dell'overlay ha selezionato DejaVu al posto del font upstream. Non sono il merge delle dipendenze reali né una build APK funzionante.
- Checksum della distribuzione e del wrapper Gradle confrontati con il [log indipendente F-Droid](https://github.com/f-droid/gradle-transparency-log/tree/master/sha256); il wrapper non ha scaricato la distribuzione nella sandbox.
- La [run CI 43639623424](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43639623424) ha completato `assembleDebug`, `assembleRelease` e `bundleRelease` non firmati con R8, `testDebugUnitTest`, `ktlintCheck`, `lintDebug`, controllo schema Room e audit font su entrambi gli APK e sull'AAB. La soppressione `AppCompatResource` è limitata ai due menu SESL. Su emulatore API 29 sono passati **due test Room, tre test di serializzazione del disegno e tre test app**, compreso il flusso creazione/modifica/riapertura/ricerca. L'audit va ripetuto su ogni pacchetto e non copre codice o altre risorse.
- Nelle run [43643878484](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43643878484) e [43645334217](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43645334217), sullo stesso commit, sono passati almeno una volta **2 test Room, 7 disegno e 5 app per ciascuna API 26/29/35/37**, compresi creazione, digitazione, riapertura, ricerca e persistenza del disegno. Non è però una matrice interamente verde: la prima è stata interrotta dopo che il job API 29 era rimasto in corso oltre 25 minuti; nella ripetizione API 26 il primo carattere digitato non è comparso. La causa non è accertata. Espresso 3.7 ha consentito l'esecuzione dei test UI su Android 17.
- Nella [run 43646193805](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43646193805) sono passati **15 test su API 26, 29 e 35**; su API 37 ne sono passati 14/15. Il flusso con digitazione e le asserzioni contro la sovrascrittura da una bozza obsoleta sono passati anche su API 37, ma quel test è poi scaduto attendendo il ritorno alla biblioteca.
- La [run 43649516799](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43649516799) sul commit `176224c` ha completato build e **15/15 test su ciascuna delle quattro API**: 2 Room, 7 disegno e 6 app. La pulizia del test della bozza è stata isolata dalla navigazione; quest'ultima è ancora verificata dal flusso end-to-end. Nella [ripetizione 43650106161](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43650106161) i job sono risultati verdi, ma manca il rapporto Room su API 26: quella suite non è verificabile in tale ripetizione. Il carattere perso nella precedente esecuzione resta senza causa accertata: la protezione da bozze obsolete e l'attesa della connessione IME non sono una dimostrazione di correzione.
- La [run 43650285577](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43650285577) sul commit `27f8204` ha completato build e **16/16 test su ciascuna API**: 2 Room, 7 disegno e 7 app. Il nuovo test raggiunge con swipe i campi cartella/tag in una nota di 40 righe, li modifica e ne verifica la persistenza dopo la riapertura, a tastiera chiusa. Non prova note da 50.000 caratteri, tastiera aperta o tutti i dispositivi. Lo scorrimento della biblioteca usato dal test di riapertura è programmatico, non una prova degli swipe nella lista.
- La CI ora richiede rapporti XML completi per ciascuno dei tre moduli, con un numero minimo di test e senza test saltati; i test instrumented dei moduli sono eseguiti senza parallelismo sullo stesso emulatore. Nella [run 43650956934](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43650956934) i rapporti indicavano 16 test senza errori né salti su ogni API, ma il primo validatore ha rifiutato il formato aggregato e la run è fallita. Il validatore è stato aggiornato per gestire anche tale formato: la [run 43651714171](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43651714171) sul commit `5b082aa` ha superato build e convalida di **16/16 test senza salti su ogni API**. Questo controllo è distinto dalla verifica generale dell'app.

## Stato rispetto al capitolato

| Area | Stato | Evidenza / limite |
| --- | --- | --- |
| Ricerca iniziale OneUI, design e architettura | Completata come documentazione | [DESIGN_NOTES.md](DESIGN_NOTES.md), [ARCHITECTURE.md](ARCHITECTURE.md); compatibilità da provare. |
| Note, editor, autosave, archivio/cestino, cartelle/tag e ricerca | Parziale | Flusso UI passato in una precedente matrice, ma un carattere è mancato nella ripetizione su API 26/29; causa non accertata. Altri flussi e nessuna garanzia anti-perdita. |
| Markdown, modelli e backlink | Parziale | Comandi semplici, sei modelli e backlink testuali; no WYSIWYG completo o grafo. |
| Disegno e S Pen | Parziale | Canvas vettoriale base; niente latenza certificata, palm rejection affidabile, riconoscimento o OCR. |
| Import/export e privacy | Parziale | Export testo senza disegno; backup JSON manuale di note e disegni con limiti, senza cifratura. Niente blocco o biometria. |
| OneUI, temi, localizzazione, layout, accessibilità | Parziale | Componenti upstream e risorse EN/IT; nessuna certificazione visiva, RTL, contrasto o TalkBack. |
| CI e test | Parziale | Build, test JVM e lint passati; in una matrice sono passati 16/16 test su ciascuna API 26/29/35/37. Dispositivi reali, stabilità e altri flussi restano da verificare. |
| Prestazioni obiettivo (avvio, 5.000 note, 50k testo, latenza) | Non misurate | Non si dichiarano raggiunte. |
| Allegati, audio, PDF, OCR, promemoria, widget, sincronizzazione e AI | Non implementate | Nessun pulsante funzionale che simuli queste funzioni. |

## Roadmap proposta

1. Ampliare i test UI e verificare la compatibilità tra AndroidX e fork SESL su altre versioni; mantenere verdi build debug/release e lint.
2. Testare salvataggio, interruzioni, import/export, TalkBack, RTL, font grandi, edge-to-edge e reali Galaxy, Fold e S Pen; raccogliere benchmark.
3. Creare backup completo con ripristino verificato e protezione crittografica/biometrica prima di gestire dati sensibili.
4. Evolvere editor, ricerca e organizzazione; aggiungere progressivamente disegno avanzato, allegati, PDF/OCR e promemoria solo con test e permessi appropriati.
5. Valutare sincronizzazione e funzionalità AI solo con consenso esplicito, privacy documentata e modalità offline.

## Crediti e licenze

Il codice di Lume Notes è [MIT](LICENSE). I repository OneUI dichiarano MIT, © 2022 Yanndroid & BlackMesa123; alcuni file SESL riportano anche avvisi di diritti riservati Samsung, e **i diritti di ridistribuzione di quelle parti non sono stati accertati**. Vedere [la verifica aperta](licenses/SESL-RIGHTS-REVIEW.md). AndroidX, Kotlin/coroutines, Hilt e ZXing dichiarano Apache 2.0; SQLite è pubblico dominio per il motore; DejaVu/Bitstream Vera ha una licenza font libera. JUnit ed Hamcrest sono dipendenze di test. Testi e avvisi sono in [`licenses/`](licenses/) e nella schermata Licenze; non costituiscono un inventario completo delle dipendenze.

Due font upstream dai diritti non chiariti o riservati sono sostituiti nei sorgenti da DejaVu con la stessa chiave/percorso. L'audit automatico è passato sugli APK debug/release e sull'AAB della run citata, ma non verifica codice, immagini o altre risorse: la distribuzione resta subordinata a verifica tecnica e legale. Non sono utilizzati loghi o font Samsung come identità del prodotto.

[Note di design](DESIGN_NOTES.md) · [Architettura](ARCHITECTURE.md) · [Limiti dettagliati](LIMITATIONS.md) · [Changelog](CHANGELOG.md) · [Build e firma](BUILD_AND_SIGN.md)
