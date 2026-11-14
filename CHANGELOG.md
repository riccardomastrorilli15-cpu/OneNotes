# Changelog

## 0.1.0 — prototipo non verificato

- Identità originale Lume Notes, ricerca e documentazione di OneUI Design/SESL.
- Struttura Gradle Kotlin DSL multi-modulo, Hilt, Room 3/FTS5, DataStore e tema OneUI.
- Codice per note, editor Markdown, autosalvataggio, cartelle/tag testuali, preferiti, archivio, cestino, ricerca, sei modelli e backlink.
- Canvas vettoriale semplice, condivisione testo e import/export testo tramite SAF; impostazioni tema, privacy e licenze.
- Build debug e release non firmata con R8, test JVM, lint, ktlint e audit font sugli APK riusciti in CI. Due test Room, tre del disegno e uno sul manifest passati su emulatore API 29; **test UI fallito**, correzione dell'intent in verifica. Vedere `LIMITATIONS.md`.
