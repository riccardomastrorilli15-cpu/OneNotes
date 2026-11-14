# OneUI/SESL: verifica dei diritti ancora aperta

**Esito: distribuzione bloccata; non è un parere legale.** Il progetto usa OneUI Design e artefatti SESL pubblicati da OneUI Project. Il file `LICENSE` dei repository upstream dichiara MIT, ma ciò da solo non chiarisce tutti i diritti su codice e risorse di terzi incorporati nei fork.

## Evidenze esaminate

- Il [repository `oneui-core`](https://github.com/OneUIProject/oneui-core/tree/8c7734aeb9c89cdd74f814e96952709515370142) al commit `8c7734aeb9c89cdd74f814e96952709515370142` dichiara in `manifest.gradle` versioni corrispondenti a quelle usate qui, tra cui SESL AppCompat 1.4.0 e Core 1.3.0. Non è stata verificata una corrispondenza byte per byte tra quel commit e tutti gli artefatti Maven risolti.
- Numerosi file del fork hanno sia un'intestazione Apache 2.0 Android Open Source Project sia il commento `Original code by Samsung, all rights reserved to the original author.`. Esempi: [`AppCompatDelegateImpl.java`](https://github.com/OneUIProject/oneui-core/blob/8c7734aeb9c89cdd74f814e96952709515370142/appcompat/src/main/java/androidx/appcompat/app/AppCompatDelegateImpl.java#L133-L135) e [`Toolbar.java`](https://github.com/OneUIProject/oneui-core/blob/8c7734aeb9c89cdd74f814e96952709515370142/appcompat/src/main/java/androidx/appcompat/widget/Toolbar.java#L82-L84). La portata e l'autorizzazione a ridistribuire le parti Samsung non sono state chiarite.
- Font upstream con diritti non chiariti o riservati vengono sostituiti tramite overlay DejaVu. L'audit automatico controlla i font visibili nel pacchetto, non codice, immagini, altri asset, marchi o l'intero grafo delle dipendenze.

## Gate prima di distribuire

1. Ricostruire la provenienza degli artefatti effettivamente risolti e delle parti Samsung che contengono; acquisire una valutazione e, se necessario, le autorizzazioni dai titolari, oppure sostituire le dipendenze interessate con componenti chiaramente redistribuibili.
2. Inventariare tutte le dipendenze transitive, i relativi avvisi e le risorse effettive di APK e AAB, inclusi drawable e bitmap. Ripetere l'audit per ogni build finale.
3. Non inferire un'autorizzazione dai soli file MIT/Apache, dal successo della build o dall'assenza dei font originali. Finché il gate resta aperto, gli artefatti CI sono esclusivamente per verifica tecnica, non per pubblicazione.
