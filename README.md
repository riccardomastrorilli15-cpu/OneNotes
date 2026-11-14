# Lume Notes

Un prototipo Android di app per note **locale e indipendente**, con interfaccia View/XML ispirata a One UI tramite il progetto open source non ufficiale [OneUI Design](https://github.com/OneUIProject/oneui-design) e i fork SESL. Lume Notes non è affiliata a Samsung. Minimo Android 8.0 / API 26; progetto Kotlin, MVVM, Hilt, Room 3/FTS5 e DataStore.

> **Stato: prototipo, non rilascio.** La CI ha compilato debug, release non firmata con R8 e AAB, superando lint, test JVM e audit font. Una matrice di test di base è passata su emulatori API 26/29/35/37; in precedenza un test API 26 aveva perso un carattere e un job API 29 si era bloccato, con cause non accertate. Compatibilità generale, stabilità, altri flussi, prestazioni e accessibilità non sono ancora verificati. La distribuzione è bloccata da [diritti SESL da chiarire](licenses/SESL-RIGHTS-REVIEW.md). Non usare per dati importanti o riservati. Vedere [LIMITATIONS.md](LIMITATIONS.md).

## Cosa c'è nel codice

- Biblioteca con ricerca per prefisso FTS5, cartella e tag testuali, preferiti, pin, archivio, cestino e caricamento incrementale.
- Editor testuale Markdown con formattazioni base, anteprima limitata, undo/redo in sessione, autosalvataggio e indicazione degli errori; backlink `[[Titolo]]` e sei modelli.
- Disegno vettoriale sperimentale a pressione, gomma per tratto e opzione solo stilo; non è un motore S Pen a bassa latenza.
- Import/export di **solo testo/Markdown** tramite selettore documenti e condivisione esplicita del testo. Le esportazioni non includono disegni.
- Tema sistema/chiaro/scuro, stringhe italiane/inglesi, interfaccia ridimensionabile e indicazioni di privacy/licenze.

La presenza di codice o di un pulsante non equivale a un collaudo riuscito. Il progetto non ha account, tracking o pubblicità; un test del manifest installato su API 29 ha verificato l'assenza dei permessi INTERNET e ACCESS_NETWORK_STATE nella build debug. I dati locali e le esportazioni **non sono cifrati**; il backup cloud Android è disattivato nel manifest, ma alcuni produttori possono comunque consentire il trasferimento diretto tra dispositivi. Un'esportazione o condivisione è avviata solo dall'utente. Disinstallare l'app elimina i dati locali.

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
- **20 test Python riusciti localmente**: controllo font su APK/AAB fittizi, diagnostica e validazione dei rapporti CI, contrasto di 16 coppie di colori statiche; uno smoke test SQLite FTS5 Python e controlli di localizzazione/XML riusciti. Le coppie controllate non certificano l’intera interfaccia. Il test FTS Python non equivale a Room.
- **ktlint 1.8.0** eseguito localmente tramite CLI e, per la revisione collegata sotto, tramite il task Gradle `ktlintCheck` in CI.
- **AAPT2 standalone** ha compilato le risorse e le ha collegate a un `android.jar` API 37 da mirror e a stub minimi delle risorse OneUI; una prova separata dell'overlay ha selezionato DejaVu al posto del font upstream. Non sono il merge delle dipendenze reali né una build APK funzionante.
- Checksum della distribuzione e del wrapper Gradle confrontati con il [log indipendente F-Droid](https://github.com/f-droid/gradle-transparency-log/tree/master/sha256); il wrapper non ha scaricato la distribuzione nella sandbox.
- La [run CI 43639623424](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43639623424) ha completato `assembleDebug`, `assembleRelease` e `bundleRelease` non firmati con R8, `testDebugUnitTest`, `ktlintCheck`, `lintDebug`, controllo schema Room e audit font su entrambi gli APK e sull'AAB. La soppressione `AppCompatResource` è limitata ai due menu SESL. Su emulatore API 29 sono passati **due test Room, tre test di serializzazione del disegno e tre test app**, compreso il flusso creazione/modifica/riapertura/ricerca. L'audit va ripetuto su ogni pacchetto e non copre codice o altre risorse.
- Nelle run [43643878484](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43643878484) e [43645334217](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43645334217), sullo stesso commit, sono passati almeno una volta **2 test Room, 7 disegno e 5 app per ciascuna API 26/29/35/37**, compresi creazione, digitazione, riapertura, ricerca e persistenza del disegno. Non è però una matrice interamente verde: la prima è stata interrotta dopo che il job API 29 era rimasto in corso oltre 25 minuti; nella ripetizione API 26 il primo carattere digitato non è comparso. La causa non è accertata. Espresso 3.7 ha consentito l'esecuzione dei test UI su Android 17.
- Nella [run 43646193805](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43646193805) sono passati **15 test su API 26, 29 e 35**; su API 37 ne sono passati 14/15. Il flusso con digitazione e le asserzioni contro la sovrascrittura da una bozza obsoleta sono passati anche su API 37, ma quel test è poi scaduto attendendo il ritorno alla biblioteca.
- La [run 43649516799](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43649516799) sul commit `176224c` ha completato build e **15/15 test su ciascuna delle quattro API**: 2 Room, 7 disegno e 6 app. La pulizia del test della bozza è stata isolata dalla navigazione; quest'ultima è ancora verificata dal flusso end-to-end. Nella [ripetizione 43650106161](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43650106161) i job sono risultati verdi, ma manca il rapporto Room su API 26: quella suite non è verificabile in tale ripetizione. Il carattere perso nella precedente esecuzione resta senza causa accertata: la protezione da bozze obsolete e l'attesa della connessione IME non sono una dimostrazione di correzione.
- La [run 43650285577](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43650285577) sul commit `27f8204` ha completato build e **16/16 test su ciascuna API**: 2 Room, 7 disegno e 7 app. Il nuovo test raggiunge con swipe i campi cartella/tag in una nota di 40 righe, li modifica e ne verifica la persistenza dopo la riapertura, a tastiera chiusa. Non prova note da 50.000 caratteri, tastiera aperta o tutti i dispositivi. Lo scorrimento della biblioteca usato dal test di riapertura è programmatico, non una prova degli swipe nella lista.
- La CI ora richiede rapporti XML completi per ciascuno dei tre moduli, con un numero minimo di test e senza test saltati; i test instrumented dei moduli sono eseguiti senza parallelismo sullo stesso emulatore. Nella [run 43650956934](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43650956934) i rapporti indicavano 16 test senza errori né salti su ogni API, ma il primo validatore ha rifiutato il formato aggregato e la run è fallita. Il validatore è stato aggiornato per gestire anche tale formato; questo controllo è distinto dalla verifica dell'app.

## Stato rispetto al capitolato

| Area | Stato | Evidenza / limite |
| --- | --- | --- |
| Ricerca iniziale OneUI, design e architettura | Completata come documentazione | [DESIGN_NOTES.md](DESIGN_NOTES.md), [ARCHITECTURE.md](ARCHITECTURE.md); compatibilità da provare. |
| Note, editor, autosave, archivio/cestino, cartelle/tag e ricerca | Parziale | Flusso UI di base passato nella stessa matrice API 26/29/35/37; un carattere perso in una precedente ripetizione API 26 resta senza causa accertata. Altri flussi e nessuna garanzia anti-perdita. |
| Markdown, modelli e backlink | Parziale | Comandi semplici, sei modelli e backlink testuali; no WYSIWYG completo o grafo. |
| Disegno e S Pen | Parziale | Canvas vettoriale base; niente latenza certificata, palm rejection affidabile, riconoscimento o OCR. |
| Import/export e privacy | Parziale | Solo testo; nessun backup completo/cifrato. Niente cifratura, blocco o biometria. |
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

Il codice di Lume Notes è [MIT](LICENSE). I repository OneUI dichiarano MIT, © 2022 Yanndroid & BlackMesa123; alcuni file SESL riportano anche avvisi di diritti riservati Samsung, e **i diritti di ridistribuzione di quelle parti non sono stati accertati**. Vedere [la verifica aperta](licenses/SESL-RIGHTS-REVIEW.md). AndroidX, Kotlin/coroutines, Hilt, ZXing e LeakCanary dichiarano Apache 2.0; SQLite è pubblico dominio per il motore; DejaVu/Bitstream Vera ha una licenza font libera. JUnit ed Hamcrest sono dipendenze di test. Testi e avvisi sono in [`licenses/`](licenses/) e nella schermata Licenze; non costituiscono un inventario completo delle dipendenze.

Due font upstream dai diritti non chiariti o riservati sono sostituiti nei sorgenti da DejaVu con la stessa chiave/percorso. L'audit automatico è passato sugli APK debug/release e sull'AAB della run citata, ma non verifica codice, immagini o altre risorse: la distribuzione resta subordinata a verifica tecnica e legale. Non sono utilizzati loghi o font Samsung come identità del prodotto.

[Note di design](DESIGN_NOTES.md) · [Architettura](ARCHITECTURE.md) · [Limiti dettagliati](LIMITATIONS.md) · [Changelog](CHANGELOG.md) · [Build e firma](BUILD_AND_SIGN.md)
