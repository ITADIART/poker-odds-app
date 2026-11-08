# Odds Hold'em (Android)

App per allenarsi a Texas Hold'em nelle partite di prova con gli amici. Funziona **senza internet**:
la fotocamera inquadra il tavolo, un modello di riconoscimento dentro il telefono legge le carte in tempo reale,
e l'app calcola le probabilità di vittoria e consiglia **FOLD / CHIAMA / RILANCIA** (o **CHECK / PUNTA**).

## Come si usa
1. Tieni il telefono sopra il tavolo. Le **tue carte sotto la linea gialla**, le **carte comuni sopra**. Trascina la linea se serve.
2. Imposta **avversari**, **piatto** (tutte le fiches al centro, compresa la puntata) e **da chiamare**.
3. Se una carta è letta male, toccala e correggila: le carte vengono bloccate finché non premi "Sblocca".

## Come ottenere l'APK
Ogni modifica caricata su GitHub (ramo `main`) avvia la compilazione automatica (`.github/workflows/build.yml`):
scarica il modello pubblico delle carte (`mustafakemal0146/playing-cards-yolov8`, licenza MIT), lo converte in TensorFlow Lite
e crea l'APK. Lo trovi nella pagina **Releases** del repository: aprila dal telefono, scarica il file `.apk` e installalo
(Android chiede di consentire l'installazione da questa fonte).

## Limiti
- Il modello è pubblico e generico: legge bene carte nitide e ben illuminate, meno con riflessi, carte lontane o mazzi particolari.
- Il consiglio guarda solo le probabilità contro carte a caso: niente posizione, stack o stile degli avversari.
- Solo per allenamento: nelle partite vere usare un aiuto durante la mano è vietato.
