# Lume Notes — note di design

**Fase 0, ricerca:** 14 novembre 2026. «Lume Notes» è il nome di lavoro proposto: non usa nomi, loghi o un'identità di Samsung. La disponibilità del marchio non è stata verificata. Il progetto non è affiliato a Samsung.

## Analisi della libreria richiesta

Sono stati esaminati [README e sorgenti](https://github.com/OneUIProject/oneui-design), `lib`, `sample-app`, `manifest.gradle`, i temi, l'esempio XML/Java, [la wiki](https://github.com/OneUIProject/oneui-design/wiki) e [oneui-core](https://github.com/OneUIProject/oneui-core). La wiki contiene solo una pagina di benvenuto; il link alla documentazione `https://oneuiproject.github.io/design/` restituisce 404. Riferimento sorgente: commit `765d4ff`, dichiarato versione **1.2.6**. L'[artefatto Maven Central 1.2.6](https://central.sonatype.com/artifact/io.github.oneuiproject/design/1.2.6) esiste. Il sample usa `minSdk 23`, `compileSdk 31`, AGP 7.3.1; noi richiediamo minSdk 26 e SDK più recente. Il progetto descrive esplicitamente One UI **4 / 4.x**, non garantisce fedeltà alle versioni successive né compatibilità con Android recenti.

La libreria è **View/XML, AppCompat e componenti Material/SESL**, non Compose. Il percorso documentato è `implementation("io.github.oneuiproject:design:1.2.6")` da Maven Central, insieme ai componenti `io.github.oneuiproject.sesl` effettivamente usati, e tema `OneUITheme`; JitPack e sottomoduli non sono necessari. SESL contiene fork con gli **stessi package** di AndroidX e Material: l'upstream richiede di escludere gli originali duplicati (`androidx.appcompat:appcompat`, `androidx.core:core`, ecc. secondo i componenti presenti). Questo è un rischio tecnico concreto: risoluzione dipendenze, test su dispositivi moderni e verifiche di compatibilità binaria sono obbligatori; l'esclusione indiscriminata può anche rompere API AndroidX più recenti.

### Inventario e applicazione

| Componente osservato | Scelta progettuale |
| --- | --- |
| `ToolbarLayout` (titolo espandibile, ricerca e action mode) | Header della biblioteca; area di lettura sopra le azioni e collasso allo scroll. |
| `DrawerLayout` | Navigazione verso raccolte, cartelle, cestino e impostazioni, dove integrato. |
| `RoundLinearLayout`, `RoundFrameLayout`, `RelatedCard` | Contenitori e blocchi arrotondati. |
| `GridMenuDialog`, `ProgressDialog`, `StartEndTimePickerDialog` | Menu e dialog contestuali solo dove realmente necessari. |
| `HapticSeekBar`, `MarginsTabLayout`, `TipPopup` | Spessore/opacity e tab ove disponibili; non inserire controlli fittizi. |
| `ColorPickerPreference`, `HorizontalRadioPreference`, `SeekBarPreferencePro`, `SwitchBarPreference`, altre preference | Impostazioni e selettori quando integrate; preferenze SESL per switch e liste. |
| SESL AppCompat, Material, picker-basic, picker-color, preference, RecyclerView | Dialog, switch, bottom navigation, selezione colori/date e liste; uso coerente col tema. |

Editor Markdown, canvas a penna, schede delle note, stati vuoti e layout adattivi non sono forniti dalla libreria: sono componenti propri, con gli stessi token visivi e senza copiare asset Samsung. Una funzione non presente non deve comparire come pulsante operativo. `ToolbarLayout` richiede un `AppCompatActivity`, come nel sample.

## Token visivi

- Superficie chiara `#F6F7FB`, scheda `#FFFFFF`, testo principale `#181B25`, secondario `#596171`, accento originale indaco `#4454C5`. Tema scuro: sfondo `#15161A`, scheda `#24262D`, testo `#F4F4F7`, secondario `#C3C5CE`, accento `#A8B4FF`; nero AMOLED opzionale solo se effettivamente disponibile.
- Font di sistema (`sans-serif`), dimensioni in sp e altezze flessibili; titoli indicativi 30–36sp, titolo scheda 18sp, corpo 16sp, metadati 13–14sp. Nessun font proprietario copiato nel progetto.
- Griglia da 4dp, margini laterali 20–24dp, spazio tra blocchi 12–16dp, raggio schede 20–24dp, controlli con target almeno 48dp. Testo multilinea e nessuna altezza fissa per contenuti scalabili.
- Aree principali in basso, margini delle gesture e della tastiera rispettati con inset; su finestre larghe il design prevede lista ed editor affiancati, non assume che il dispositivo sia un tablet in base al modello.
- Movimento discreto: circa 180–250 ms, interpolazione ease-in-out; usare la durata di sistema e non impedire l'opzione di ridurre le animazioni. Aptica leggera solo per azioni intenzionali. Non promettiamo transizioni condivise o animazioni che non siano state realizzate e verificate.
- Le icone dell'app sono vettori originali, tratto semplice e descrizione accessibile. Contrasto minimo AA da verificare anche in temi dinamici e a scala del testo elevata.

## Licenza e risorse di terzi

`oneui-design` e `oneui-core` dichiarano MIT, copyright **2022 Yanndroid & BlackMesa123**: il testo integrale e l'avviso devono accompagnare ogni distribuzione e comparire tra i crediti in app. AndroidX e altri componenti possiedono proprie licenze e avvisi da includere. MIT non concede automaticamente marchi o diritti di terzi. **Attenzione:** la libreria upstream include un file `res/font/samsungsharpsans_bold.otf` nel suo AAR; non va copiato come asset dell'app. Per distribuirla è necessario sostituire la risorsa durante il merge con un font liberamente redistribuibile e verificare l'APK/AAB risultante, oltre a una revisione legale degli altri asset SESL. Non dichiarare completata tale verifica prima di aver ispezionato il pacchetto. Il modulo SESL indexscroll contiene anche `assets/sesl_indexscroll_group_font.ttf`, il cui metadata riserva i diritti a Samsung: anche questo viene sovrascritto da DejaVu. Lo script di audit dell’APK deve passare prima di distribuire, ma non sostituisce la revisione delle altre risorse. Non usare il package di sistema Samsung né loghi, icone o font proprietari come identità del prodotto.

## Verifiche visuali necessarie

Controllare telefono, tablet/Fold e finestra ridimensionata; API 26 e versioni correnti; RTL; TalkBack; tastiera hardware; scala carattere massima; modalità chiara/scura; gesture e IME; contrasto e riduzione animazioni. «Indistinguibile da un'app di sistema» e prestazioni target sono obiettivi, non certificazioni.
