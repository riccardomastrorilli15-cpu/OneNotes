# Changelog

## 0.1.0 — prototipo non verificato

- Identità originale Lume Notes, ricerca e documentazione di OneUI Design/SESL.
- Struttura Gradle Kotlin DSL multi-modulo, Hilt, Room 3/FTS5, DataStore e tema OneUI.
- Codice per note, editor Markdown, autosalvataggio, cartelle/tag testuali, preferiti, archivio, cestino, ricerca, sei modelli e backlink.
- Canvas vettoriale semplice, condivisione testo e import/export testo tramite SAF; impostazioni tema, privacy e licenze.
- Build debug, release non firmata con R8 e AAB, test JVM, lint, ktlint e audit font dei pacchetti riusciti in CI. Due test Room, tre di serializzazione del disegno e tre test app sono passati su emulatore API 29, compreso il flusso creazione/modifica/riapertura/ricerca. La matrice API e i nuovi test disegno sono in verifica.
- Documentati gli avvisi di diritti riservati nei sorgenti SESL: distribuzione bloccata fino alla verifica dei diritti. Vedere `LIMITATIONS.md`.
