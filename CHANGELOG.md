# Changelog

## 0.1.0 — prototipo non verificato

- Identità originale Lume Notes, ricerca e documentazione di OneUI Design/SESL.
- Struttura Gradle Kotlin DSL multi-modulo, Hilt, Room 3/FTS5, DataStore e tema OneUI.
- Codice per note, editor Markdown, autosalvataggio, cartelle/tag testuali, preferiti, archivio, cestino, ricerca, sei modelli e backlink.
- Canvas vettoriale semplice, condivisione testo e import/export testo tramite SAF; impostazioni tema, privacy e licenze.
- Build debug e release non firmata con R8, test JVM, lint, ktlint e audit font sugli APK riusciti in CI. Due test Room, tre del disegno, uno sul manifest e uno sull'avvio passati su emulatore API 29; il flusso UI crea e riapre una nota ma fallisce prima della ricerca. AAB e correzione del test sono in verifica.
- Documentati gli avvisi di diritti riservati nei sorgenti SESL: distribuzione bloccata fino alla verifica dei diritti. Vedere `LIMITATIONS.md`.
