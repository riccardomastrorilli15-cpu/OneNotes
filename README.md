# Lume Notes

Un prototipo Android di app per note **locale e indipendente**, con interfaccia View/XML ispirata a One UI tramite il progetto open source non ufficiale [OneUI Design](https://github.com/OneUIProject/oneui-design) e i fork SESL. Lume Notes non è affiliata a Samsung. Minimo Android 8.0 / API 26; progetto Kotlin, MVVM, Hilt, Room 3/FTS5 e DataStore.

> **Stato: sorgenti di prototipo, non rilascio.** In questa sandbox non è stato possibile scaricare Gradle/SDK/dipendenze Android: nessun APK/AAB è stato costruito o avviato, e compatibilità OneUI/SESL, Room, Hilt, R8, UI, prestazioni, accessibilità e licenze del pacchetto non sono ancora verificati. Non usare per dati importanti o riservati. Vedere [LIMITATIONS.md](LIMITATIONS.md) prima di provarlo.

## Cosa c'è nel codice

- Biblioteca con ricerca per prefisso FTS5, cartella e tag testuali, preferiti, pin, archivio, cestino e caricamento incrementale.
- Editor testuale Markdown con formattazioni base, anteprima limitata, undo/redo in sessione, autosalvataggio e indicazione degli errori; backlink `[[Titolo]]` e sei modelli.
- Disegno vettoriale sperimentale a pressione, gomma per tratto e opzione solo stilo; non è un motore S Pen a bassa latenza.
- Import/export di **solo testo/Markdown** tramite selettore documenti e condivisione esplicita del testo. Le esportazioni non includono disegni.
- Tema sistema/chiaro/scuro, stringhe italiane/inglesi, interfaccia ridimensionabile e indicazioni di privacy/licenze.

La presenza di codice o di un pulsante non equivale a un collaudo riuscito. Il progetto non ha account, tracking o pubblicità; il manifest rimuove esplicitamente il permesso di rete ereditato, ma il manifest risultante va verificato. I dati locali e le esportazioni **non sono cifrati**; il backup cloud Android è disattivato nel manifest, ma alcuni produttori possono comunque consentire il trasferimento diretto tra dispositivi. Un'esportazione o condivisione è avviata solo dall'utente. Disinstallare l'app elimina i dati locali.

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
- **4 test Python riusciti**: controllo font su APK fittizi e contrasto di 16 coppie di colori statiche; uno smoke test SQLite FTS5 Python e controlli di localizzazione/XML riusciti. Le coppie controllate non certificano l’intera interfaccia. Il test FTS Python non equivale a Room.
- **ktlint 1.8.0 CLI** eseguito su sorgenti Kotlin e script Gradle; il task Gradle `ktlintCheck` non è stato eseguito.
- **AAPT2 standalone** ha compilato le risorse e le ha collegate a un `android.jar` API 37 da mirror e a stub minimi delle risorse OneUI; una prova separata dell'overlay ha selezionato DejaVu al posto del font upstream. Non sono il merge delle dipendenze reali né una build APK funzionante.
- Checksum della distribuzione e del wrapper Gradle confrontati con il [log indipendente F-Droid](https://github.com/f-droid/gradle-transparency-log/tree/master/sha256); il wrapper non ha scaricato la distribuzione nella sandbox.
- Workflow [Android CI](.github/workflows/android.yml), test Room/UI e audit APK sono predisposti, **non eseguiti**. L'audit font va ripetuto su ogni APK generato; l'AAB richiede una verifica equivalente.

## Stato rispetto al capitolato

| Area | Stato | Evidenza / limite |
| --- | --- | --- |
| Ricerca iniziale OneUI, design e architettura | Completata come documentazione | [DESIGN_NOTES.md](DESIGN_NOTES.md), [ARCHITECTURE.md](ARCHITECTURE.md); compatibilità da provare. |
| Note, editor, autosave, archivio/cestino, cartelle/tag e ricerca | Parziale | Codice e alcuni test isolati; nessuna prova end-to-end Android o garanzia anti-perdita. |
| Markdown, modelli e backlink | Parziale | Comandi semplici, sei modelli e backlink testuali; no WYSIWYG completo o grafo. |
| Disegno e S Pen | Parziale | Canvas vettoriale base; niente latenza certificata, palm rejection affidabile, riconoscimento o OCR. |
| Import/export e privacy | Parziale | Solo testo; nessun backup completo/cifrato. Niente cifratura, blocco o biometria. |
| OneUI, temi, localizzazione, layout, accessibilità | Parziale | Componenti upstream e risorse EN/IT; nessuna certificazione visiva, RTL, contrasto o TalkBack. |
| CI e test | Parziale | Workflow e test presenti; build e test Android non eseguiti. |
| Prestazioni obiettivo (avvio, 5.000 note, 50k testo, latenza) | Non misurate | Non si dichiarano raggiunte. |
| Allegati, audio, PDF, OCR, promemoria, widget, sincronizzazione e AI | Non implementate | Nessun pulsante funzionale che simuli queste funzioni. |

## Roadmap proposta

1. Sbloccare Gradle/SDK, far passare build debug/release, lint e test su emulatore; risolvere i conflitti tra AndroidX e fork SESL.
2. Testare salvataggio, interruzioni, import/export, TalkBack, RTL, font grandi, edge-to-edge e reali Galaxy, Fold e S Pen; raccogliere benchmark.
3. Creare backup completo con ripristino verificato e protezione crittografica/biometrica prima di gestire dati sensibili.
4. Evolvere editor, ricerca e organizzazione; aggiungere progressivamente disegno avanzato, allegati, PDF/OCR e promemoria solo con test e permessi appropriati.
5. Valutare sincronizzazione e funzionalità AI solo con consenso esplicito, privacy documentata e modalità offline.

## Crediti e licenze

Il codice di Lume Notes è [MIT](LICENSE). OneUI Design/SESL: MIT, © 2022 Yanndroid & BlackMesa123; AndroidX, Kotlin/coroutines, Hilt, ZXing e LeakCanary: Apache 2.0; SQLite: pubblico dominio per il motore; DejaVu/Bitstream Vera: licenza font libera. JUnit ed Hamcrest sono dipendenze di test. Testi e avvisi sono in [`licenses/`](licenses/) e nella schermata Licenze in app. La lista non sostituisce un inventario reale delle dipendenze transitive: si veda [`licenses/NOTICE.txt`](licenses/NOTICE.txt).

Due font upstream dai diritti non chiariti o riservati sono sostituiti nei sorgenti da DejaVu con la stessa chiave/percorso. L'audit automatico dei font non è ancora stato applicato a un APK reale e non verifica immagini o altre risorse: la distribuzione resta subordinata a verifica tecnica e legale. Non sono utilizzati loghi o font Samsung come identità del prodotto.

[Note di design](DESIGN_NOTES.md) · [Architettura](ARCHITECTURE.md) · [Limiti dettagliati](LIMITATIONS.md) · [Changelog](CHANGELOG.md) · [Build e firma](BUILD_AND_SIGN.md)
