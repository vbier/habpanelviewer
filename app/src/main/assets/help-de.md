# <a name="top"/>HABPanelViewer

HABPanelViewer ist eine Android Anwendung zur Vollbild-Visualisierung von HABPanel, einer UI von openHAB.

Die Funktionalität kann in drei Bereiche aufgeteilt werden:
- [Geräte Steuerung](#control) als Reaktion auf openHAB Item Änderungen
- [Sensorwerte Meldung](#reporting) an openHAB
- [Benutzerfreundlichkeit](#usability) macht es einfacher HABPanel auf einem Tablet zu benutzen

## <a name="configuration"/>Konfiguration

**Die folgenden Einstellungen sollten beim ersten Start der Applikation eingestellt werden.**

###openHAB URL
Die ist die URL des openHAB servers. Sie wird für die openHAB integration (d.h. Kommando Item, Sensortwerte Meldung, Verbindungsindikator) benötigt. Wenn die URL nicht richtig konfiguriert ist, wird die genannte Funktionalität nicht zur Verfügung stehen.

Die URL kann automatisch per mDNS discovery gesucht werden, wenn sich das Gerät im selben Subnetz wie der openHAB server befindet. Drücken Sie dafür auf den "" Knopf im openHAB URL Einstellungsdialog. Wenn die Suche nicht funktioniert, oder Sie die URL selbst eingeben möchten, wäre ein Beispiel für die URL:

`http://{ip or hostname}:8080/` <br>
Es sollte kein weiterer Pfad an die URL angehängt werden.

> Wenn Sie https (ssl) benutzen möchten, sollten Sie 8443 als Port verwenden.

###Startseite
Dies ist die Seite, die beim Starten der Applikation angezeigt werden soll. Dies kann eine beliebige URL sein, die vorgesehene Verwendung ist eine URL einer HABPanel Seite.    

Wenn Sie diese Einstellung leer lassen, wird die openHAB URL beim Starten der Applikation angezeigt.

Beispiele für HABPanel URLs:

`http://{ip or hostname}:8080/habpanel/index.html#/`<br>startet im HABPanel Menü.

`http://{ip or hostname}:8080/habpanel/index.html#/view/Info`<br>startet mit der speziellen HABPanel Seite Info<br>
   
> Achten Sie auf die richtige Groß- und Kleinschreibung der HABPanel URL.

Anstatt die Startseite in den Einstellungen zu Konfigurieren, können Sie sie auch interaktiv setzen. Lassen Sie die Einstellung leer, navigieren Sie im Browser zur gewünschen Seite und wählen Sie "Als Startseite setzen" im Kontextmenü.
 
###Kiosk Modus
Sie können den Kiosk Modus über das Kontextmenü an- und auschalten. Wenn Sie den Kiosk Modus für die Startseite verwenden wollen, aktivieren Sie ihn mit dem Kontextmenü und wählen Sie danach "Als Startseite setzen" aus dem Kontextmenü.

Eine andere Möglichkeit den Kiosk Modus zu aktivieren, ist an die URL den Parameter `?kiosk=on` anzuhängen.

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
* SHOW_URL *url*: zeigt die angegebene Webseite
* SHOW_DASHBOARD *dashboard*: zeigt die angegebene Habpanel Seite
> Dies funktioniert nur wenn HABPanel unter der Standard URL verfügbar ist. Wenn Sie eine angepasste HABPanel installation haben, benutzen Sie statt dessen SHOW_URL
* SHOW_START_URL: zeigt die konfigurierte Startseite
* RELOAD: lädt die angezeigte Seite neu
* CAPTURE_SCREEN *image item*: macht einen Screenshot und sendet diesen an das openHAB image item 
> Dies funktioniert nur mit Android Lollipop oder neuer und mit einem in openHAB definierten item vom Typ **Image**
* CAPTURE_CAMERA *image item*: macht ein Foto und sendet dieses an das openHAB image item
> Falls die Bilder zu dunkel sein sollten, erhöhen Sie die CAPTURE_CAMERA Verzögerung in den Einstellungen.
* ENABLE_MOTION_DETECTION: aktiviert die Bewegungserkennung in den App Einstellungen
* DISABLE_MOTION_DETECTION: deaktiviert die Bewegungserkennung in den App Einstellungen


### Kommando Log
Zeigt die letzten 100 von HABPanelViewer prozessierten Kommandos. Der Status wird durch die Textfarbe angezeigt:
* grün: Das Kommando wurde erfolgreich ausgeführt.
* gelb: Das Kommando ist HABPanelViewer nicht bekannt.
* grau: Das Kommando wird zur Zeit ausgeführt.
* rot: Die Ausführung wurde aufgrund eines Fehlers abgebrochen.

Klicken Sie auf ein Kommando um die Details anuzeigen, falls es welche gibt.

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

### Startseite
Browsen Sie eine beliebige Seite und setzen Sie sie mit dem Kontextmenü als Startseite. 

### Ziehen verhindern
Falls Sie aus Versehen Scrollen, wenn Sie einen Knopf drücken wollen, aktivieren Sie die Einstellung **Ziehen verhindern**. Dies deaktiviert Scrolling in der Anwendung komplett, und verhindert so auch das Öffnen des HABPanel Menüs.

### Starte App
Starten Sie eine auf dem Gerät installierte App über das Menü.

[zurück nach oben](#top)
