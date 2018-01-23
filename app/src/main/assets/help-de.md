# <a name="top"/>HABPanelViewer

HABPanelViewer ist eine Android Anwendung zur Vollbild-Visualisierung von HABPanel, einer UI von openHAB.

Die Funktionalität kann in drei Bereiche aufgeteilt werden:
- [Geräte Steuerung](#control) als Reaktion auf openHAB Item Änderungen
- [Sensorwerte Meldung](#reporting) an openHAB
- [Benutzerfreundlichkeit](#usability) macht es einfacher HABPanel auf einem Tablet zu benutzen

## <a name="control"/>Geräte Steuerung
### Kommando Item
Überwacht das openHAB **String Item** und wartet auf Kommandos. Erlaubte Kommandos sind:
* RESTART: startet HABPanelViewer neu
* SCREEN_ON: schaltet den Bildschirm des Geräts ein
* KEEP_SCREEN_ON: schaltet den Bildschirm des Geräts ein und hindert das System daran, ihn auszuschalten
* ALLOW_SCREEN_OFF: erlaubt dem System, den Bildschirm auszuschalten
* SCREEN_DIM: regelt die Helligkeit so weit runter wie möglich und stellt sie zurück, wenn der Bildschirm berührt wird
* MUTE: stellt das Gerät stumm
* UNMUTE: stellt die Lautstärke auf den Wert, den das Gerät zum Zeitpunkt des MUTE Kommandos hatte
* SET_VOLUME n: stellt die Lautstärke auf den Wert n, welcher eine Ganzzahl im Bereich 0.._Maximallautstärke des Geräts_ sein muss.
* FLASH_ON: schaltet das Blitzlicht der hinteren Kamera ein
* FLASH_OFF: schaltet das Blitzlicht der hinteren Kamera aus
* FLASH_BLINK: lässt das Blitzlicht der hinteren Kamera im 1 Sekunden Intervall blinken
* FLASH_BLINK *n*: lässt das Blitzlicht der hinteren Kamera im *n* Millisekunden Intervall blinken
* BLUETOOTH_ON: schaltet bluetooth ein
* BLUETOOTH_OFF: schaltet bluetooth aus
* UPDATE_ITEMS: sendet alle aktuellen Item Werte an openHAB
* START_APP *app*: started die app mit dem Paket Namen *app*
* ADMIN_LOCK_SCREEN: aktiviert die Bildschirmsperre (beötigt, das die App in den Einstellungen als Device Admin aktiviert ist)

### Kommando Log
Zeigt die letzten 100 von HABPanelViewer prozessierten Kommandos. Der Status wird durch die Textfarbe angezeigt:
* grün: Das Kommando wurde erfolgreich ausgeführt.
* gelb: Das Kommando ist HABPanelViewer nicht bekannt.
* rot: Die Ausführung wurde aufgrund eines Fehlers abgebrochen.

Im Fehlerfall wird außerdem noch die Fehlermeldung im Kommando Log angezeigt.

[zurück nach oben](#top)

## <a name="reporting"/>Sensorwerte Meldung
Ermöglicht es, Werte der Geräte Sensoren an openHAB zu melden. Die gemeldeten Werte können dann z.B. in Regeln verwendet werden, um Sie zu benachrichtigen, bevor die Batterie des Tablets leer ist. 

### Batteriesensor
Wenn aktiviert, ändert die Anwendung die Werte von bis zu drei openHAB Items in Abhängigkeit des Batteriezustands:
- Batterie Leer Kontakt: Name des openHAB Kontakts (Item vom Typ **Contact**) der geschaltet wird wenn die Batterie leer ist
- Batterie wird geladen Kontakt: Name des openHAB Kontakts der geschaltet wird wenn die Batterie geladen wird.
- Batterieladung Item: Name des openHAB Items das den Batterie Ladezustand in Prozent anzeigen soll. Dieser Wert wird alle 5 Sekunden aktualisiert, solange das Gerät am Strom angeschlossen ist, alle 5 Minuten anderenfalls. 

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Battery_Low
    Contact Tablet_Battery_Charging
    Number Tablet_Battery_Level

Lassen sie Item Namen leer, um das Melden bestimmter Werte zu unterdrücken. Die Kontakte werden geschlossen, sobald die Batterie leer ist, bzw. das Gerät geladen wird.
Das Number Item reflektiert den Akku Ladezustand in Prozent.

### Bewegungserkennung
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter Bewegung (_funktioniert nicht gleichzeitig mit der Blitzlicht Steuerung_).

Die Erkennung funktioniert folgendermaßen: sie unterteilt das Bild in kleinere Bereiche und berechnet pro Bereich den Helligkeitsdurchschnitt. Wenn dieser Durchschnitt vom letzten Wert um mehr als die konfigurierte Schwelle abweicht,
wird Bewegung erkannt.

Die Erkennung kann aktiviert und deaktiviert werden, und es können einige Parameter in den Einstellungen verändert werden:
- Zeige Kamera Vorschau: Blendet eine Kamera Vorschau über dem Browser ein. Diese ist Nützlich, um die Erkennung richtig einzustellen.
- Benutze Lollipop Camera API 2: Benutze das Camera API 2 das mit Lollipop eingeführt wurde. Verändern Sie dies nur, falls die Erkennung nicht funktioniert. Die Anwendung startet automatisch neu, wenn der Wert verändert wird.
- Erkennungsgranularität: Die Anzahl der Teile pro Achse, in die das Bild eingeteilt wird um Bewegung zu erkennen. Eine Granularität von 10 unterteilt das Bild also in 100 Unterbereiche. 
- Erkennungsschwelle: Helligkeitsdifferenz, ab der Bewegung erkannt wird. 0 bedeutet jede Änderung resultiert in Bewegungserkennung, 255 bedeutet, es wird nie Bewegung erkannt.
- Erkennungsintervall: Zeit zwischen zwei aufeinanderfolgenden Erkennungsversuchen (in Millsekunden). Dies hat direkten Einfluss auf den CPU Verbrauch. 

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Motion

Der Kontakt wird geschlossen, wenn Bewegung erkannt wird, und nach einer Minute ohne Bewegung wieder geöffnet.

### Annäherungssensor
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter Annäherung.

Der Kontakt wird geschlossen, wenn Annäherung erkannt wird.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Proximity

### Helligkeitssensor
Setzt den Wert eines openHAB Items auf den vom Helligkeitssensor gemessenen Wert. Weil manche Geräte Sensor Werte in sehr schneller Aufeinanderfolge melden, erlaubt es die App, zyklisch Durchschnittswerte zu melden.

Die Einheit des gemeldeten Wertes ist lx.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Brightness

### Drucksensor
Setzt den Wert eines openHAB Items auf den vom Drucksensor gemessenen Wert.

Die Einheit des gemeldeten Wertes ist Geräte abhängig.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Pressure

### Temperatursensor
Setzt den Wert eines openHAB Items auf den vom Temperatursensor gemessenen Wert.

Die Einheit des gemeldeten Wertes ist Grad Celsius.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Temperature

[zurück nach oben](#top)

## <a name="usability"/>Benutzerfreundlichkeit 
### Server Suchen
Beim ersten Start (oder wenn in den Einstellungen aufgerufen), sucht die Anwendung den openHAB Server per mDNS discovery im lokalen Netz.
Es wird zuerst versucht, eine HTTPS Verbindung zu finden, und falls das nicht funktioniert, wird auf HTTP zurück gegriffen.

### Home Screen / Launcher Funktionalität
Die Anwendung kann in den Android Settings als Launcher eingestellt werden. Dann startet Sie mit dem System und ersetzt den Standard Launcher.  

### HABPanel Start Kachel
Falls Sie direkt mit einer Kachel beginnen wollen anstatt im HABPanel Menü, stellen Sie den Namen der Kachel in den Einstellungen ein.

### Kiosk Modus
Sie können den HABPanel Kiosk Modus in den Einstellungen ein oder ausschalten. Der Kiosk Modus versteckt das HABPanel Menü und die Titelleiste.

### Ziehen verhindern
Falls Sie aus Versehen Scrollen, wenn Sie einen Knopf drücken wollen, aktivieren Sie die Einstellung **Ziehen verhindern**. Dies deaktiviert Scrolling in der Anwendung komplett, und verhindert so auch das Öffnen des HABPanel Menüs.

### Starte App
Konfigurieren Sie einen Namen und den Paket Namen einer Android App, um diese aus dem Menü starten zu können. Um den Paketnamen heraus zu finden, öffnen Sie die Anwendung im Google Play Store. Der Paket Name wird dann als id in der URL angezeigt.

[zurück nach oben](#top)
