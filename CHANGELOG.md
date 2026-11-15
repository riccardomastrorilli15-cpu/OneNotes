# Changelog

## In sviluppo

- Corretto il salvataggio invariato di testo e disegno: aprire e chiudere una nota senza modificarla non ne cambia più data e versione.
- Corretta la gomma a tratto: intercetta anche il centro dei segmenti con pochi punti e i tratti attraversati tra due eventi di movimento.
- Corretto il titolo delle importazioni di file senza estensione o con nome nascosto. Gli stream senza avanzamento non bloccano più l'importazione e il testo UTF-8 non valido viene rifiutato invece di essere sostituito silenziosamente; anche l'export segnala UTF-16 non valido e possibili file incompleti.
- Il ripristino del backup rifiuta sequenze Unicode surrogate non valide e cartelle/tag oltre i limiti dell'editor, evitando conversioni o troncamenti silenziosi durante i salvataggi successivi.
- La biblioteca carica solo anteprime limitate di titolo, testo e metadati senza alterare la nota nel database; corretto anche il rilevamento di dati di disegno non vuoti che iniziano con NUL.
- Aggiunta una regressione per ricreazione dell'editor e riapertura della nota; il ripristino dopo arresto forzato non è garantito.
- Rimossa la dipendenza debug LeakCanary. Aggiunta una build release ottimizzata firmabile esplicitamente con la chiave debug per prove interne, con audit dei permessi, firma e allineamento. R8 effettua shrinking e ottimizzazione senza rinominare le classi (`-dontobfuscate`).
- Aggiunto backup JSON manuale con SAF e ripristino transazionale senza sovrascrivere UUID già presenti; copre disegni, metadati, archivio e cestino, entro i limiti documentati. Non è cifrato.
- Rimossi dal manifest il servizio di invalidazione Room multi-processo non utilizzato e i componenti opzionali di installazione del profilo AndroidX; aggiunti controlli su servizi e receiver nel pacchetto.
- **Problema aperto:** nella [CI 43700790007](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43700790007) il primo carattere non è apparso nel test di digitazione su API 26 e 29; il problema si è ripetuto su API 29 nella [run 43702108301](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43702108301). La traccia sintetica mostra il rilascio del tasto iniziale senza una modifica del testo corrispondente, ma non identifica la causa. Il test ora attende più a lungo il cambio di campo dell'IME senza sostituire la digitazione con `setText`: anche se passa, ciò non dimostra che l'app sia priva di perdite.
- Corretto il controllo CI del manifest sui marcatori di rimozione e l'esecuzione dello script multilinea dell'emulatore; i risultati precedenti non vengono presentati come build riuscite.

## 0.1.0 — prototipo, verifiche parziali

- Identità originale Lume Notes, ricerca e documentazione di OneUI Design/SESL.
- Struttura Gradle Kotlin DSL multi-modulo, Hilt, Room 3/FTS5, DataStore e tema OneUI.
- Codice per note, editor Markdown, autosalvataggio, cartelle/tag testuali, preferiti, archivio, cestino, ricerca, sei modelli e backlink.
- Canvas vettoriale semplice, condivisione testo e import/export testo tramite SAF; impostazioni tema, privacy e licenze.
- Build debug, release non firmata con R8 e AAB, test JVM, lint, ktlint e audit font dei pacchetti riusciti in CI. Nella [matrice 43651714171](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43651714171) sono passati senza salti 2 test Room, 7 disegno e 7 app su ciascun emulatore API 26/29/35/37, incluso lo swipe verso i metadati di una nota lunga. Una precedente ripetizione aveva perso un carattere su API 26, un job API 29 si era bloccato e un'altra run non aveva un rapporto Room su API 26: le cause non sono accertate. La CI ora verifica la presenza e la completezza dei rapporti.
- Documentati gli avvisi di diritti riservati nei sorgenti SESL: distribuzione bloccata fino alla verifica dei diritti. Vedere `LIMITATIONS.md`.
