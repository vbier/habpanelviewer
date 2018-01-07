# <a name="top"/>HABPanelViewer

HABPanelViewer ist eine Android Anwendung zur Vollbild-Visualisierung von HABPanel, einer UI von openHAB.

Die Funktionalität kann in drei Bereiche aufgeteilt werden:
- [Geräte Steuerung](#control) als Reaktion auf openHAB Item Änderungen
- [Sensorwerte Meldung](#reporting) an openHAB
- [Benutzerfreundlichkeit](#usability) macht es einfacher HABPanel auf einem Tablet zu benutzen

## <a name="control"/>Geräte Steuerung
### Kommando Item
Überwacht das openHAB **String Item** und wartet auf Kommandos. Momentan wird nur das RESTART Kommando unterstützt und dieses startet HABPanelViewer neu.

### Blitzlicht Steuerung
Erlaubt es, das Blitzlicht an oder auszuschalten, oder blinken zu lassen in Abhängigkeit eines openHAB Items (_verfügbar ab Android 6+_).

Um diese Funktion zu verwenden, konfigurieren sie das openHAB Item und die regulären Ausdrücke:
- Blitzlicht Item: Name des openHAB Items dessen Wert das Blitzlicht steuert
- Regulärer Ausdruck zum Blinken: Regulärer Ausdruck (java regexp), bei dessen Zutreffen das Blitzlicht blinken soll 
- Regulärer Ausdruck zum Einschalten: Regulärer Ausdruck (java regexp), bei dessen Zutreffen das Blitzlicht eingeschaltet werden soll

Ein Beispiel könnte ein openHAB **String Item** sein, das wie folgt in einer Items Datei konfiguriert ist:

    String Tablet_Flashlight
  
Wenn Sie nun den regulären Ausdruck zum Blinken als *BLINKING* und den regulären Ausdruck zum Einschalten als *ON* konfigurieren, können Sie das Blitzlicht einschalten, indem Sie das Item auf *ON* setzen, oder das Blitzlicht durch Setzen des Wertes BLINKING blinken lassen.

Durch die regulären Ausdrücke können Sie auch komplexere Dinge realisieren, z.B. den Status des Blitzlichtes vom Status ihrer openHAB Alarmanlage ableiten. So kann das Blitzlicht blinken, während die Anlage scharf geschaltet wird, oder durchgängig leuchten, solange sie aktiviert ist.
So kann man den Status der Alarmanlage sehen, ohne das Tablet einschalten zu müssen.

### Bildschirm Steuerung
Ermöglicht es den Bildschirm in Abhängigkeit eines openHAB Items an oder aus zu schalten.

Um diese Funktion zu verwenden, konfigurieren sie das openHAB Item und den regulären Ausdruck:
- Bildschirm Item: Name des openHAB Items dessen Wert den Bildschirm einschaltet
- Lasse Bildschirm an: Versucht den Bildschirm am ausschalten zu hindern, solange der reguläre Ausdruck zutrifft. 
- Regulärer Ausdruck zum Einschalten: Regulärer Ausdruck (java regexp), bei dessen Zutreffen der Bildschirm eingeschaltet werden soll 

### Lautstärke Steuerung
Verändert die Geräte Lautstärke in Abhängigkeit eines openHAB **Number** Items. Die Geräte Lautstärke wird auf den Wert des Items gesetzt.
Um die maximale Lautstärke herauszufinden, schauen Sie in den Eintrag *Lautstärke Steuerung* des *Status Information* Bildschirms. Ungültige Werte werden ignoriert.

[zurück nach oben](#top)

## <a name="reporting"/>Sensorwerte Meldung
Ermöglicht es, Werte der Geräte Sensoren an openHAB zu melden. Die gemeldeten Werte können dann z.B. in Regeln verwendet werden, um Sie zu benachrichtigen, bevor die Batterie des Tablets leer ist. 

### Batterie Sensor
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

### Bewegungs Erkennung
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter Bewegung (_funktioniert nicht gleichzeitig mit der Blitzlicht Steuerung_).

Die Erkennung funktioniert folgendermaßen: sie unterteilt das Bild in kleinere Bereiche und berechnet pro Bereich den Helligkeits Durchschnitt. Wenn dieser Durchschnitt vom letzten Wert um mehr als die konfigurierte Schwelle abweicht,
wird Bewegung erkannt.

Die Erkennung kann aktiviert und deaktiviert werden, und es können einige Parameter in den Einstellungen verändert werden:
- Zeige Kamera Vorschau: Blendet eine Kamera Vorschau über dem Browser ein. Diese ist Nützlich, um die Erkennung richtig einzustellen.
- Benutze Lollipop Camera API 2: Benutze das Camera API 2 das mit Lollipop eingeführt wurde. Verändern Sie dies nur, falls die Erkennung nicht funktioniert. Die Anwendung startet automatisch neu, wenn der Wert verändert wird.
- Erkennungs Granularität: Die Anzahl der Teile pro Achse, in die das Bild eingetilt wird um Bewegung zu erkennen. Eine Granularität von 10 unterteilt das Bild also in 100 Unterbereiche. 
- Erkennungs Schwelle: Helligkeits Differenz, ab der Bewegung erkannt wird. 0 bedeutet jede Änderung resultiert in Bewegungserkennung, 255 bedeutet, es wird nie Bewegung erkannt.   

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Motion

Der Kontakt wird geschlossen, wenn Bewegung erkannt wird, und nach einer Minute ohne Bewegung wieder geöffnet.

### Annäherungs Sensor
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter Annäherung.

Der Kontakt wird geschlossen, wenn Annäherung erkannt wird.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Proximity

### Helligkeits Sensor
Setzt den Wert eines openHAB Items auf den vom Helligkeits Sensor gemessenen Wert. Weil manche Geräte Sensor Werte in sehr schneller Aufeinanderfolge melden, erlaubt es die App, zyklisch Durchschnittswerte zu melden.

Die Einheit des gemeldeten Wertes ist lx.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Brightness

### Druck Sensor
Setzt den Wert eines openHAB Items auf den vom Druck Sensor gemessenen Wert.

Die Einheit des gemeldeten Wertes ist Geräte abhängig.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Pressure

### Temperatur Sensor
Setzt den Wert eines openHAB Items auf den vom Temperatur Sensor gemessenen Wert.

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
