# Changelog

## 0.1.0 — prototipo, verifiche parziali

- Identità originale Lume Notes, ricerca e documentazione di OneUI Design/SESL.
- Struttura Gradle Kotlin DSL multi-modulo, Hilt, Room 3/FTS5, DataStore e tema OneUI.
- Codice per note, editor Markdown, autosalvataggio, cartelle/tag testuali, preferiti, archivio, cestino, ricerca, sei modelli e backlink.
- Canvas vettoriale semplice, condivisione testo e import/export testo tramite SAF; impostazioni tema, privacy e licenze.
- Build debug, release non firmata con R8 e AAB, test JVM, lint, ktlint e audit font dei pacchetti riusciti in CI. Nella [matrice 43651714171](https://github.com/riccardomastrorilli15-cpu/OneNotes/actions/runs/43651714171) sono passati senza salti 2 test Room, 7 disegno e 7 app su ciascun emulatore API 26/29/35/37, incluso lo swipe verso i metadati di una nota lunga. Una precedente ripetizione aveva perso un carattere su API 26, un job API 29 si era bloccato e un'altra run non aveva un rapporto Room su API 26: le cause non sono accertate. La CI ora verifica la presenza e la completezza dei rapporti.
- Documentati gli avvisi di diritti riservati nei sorgenti SESL: distribuzione bloccata fino alla verifica dei diritti. Vedere `LIMITATIONS.md`.
