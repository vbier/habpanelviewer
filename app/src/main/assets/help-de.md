# <a name="top"/>HABPanelViewer

HABPanelViewer ist eine Android Anwendung zur Vollbild-Visualisierung von HABPanel, einer UI von openHAB.

Die Funktionalität kann in drei Bereiche aufgeteilt werden:
- [Geräte Steuerung](#control) als Reaktion auf openHAB Item Änderungen
- [Werte Meldung](#reporting) an openHAB
- [Benutzerfreundlichkeit](#usability) macht es einfacher HABPanel auf einem Tablet zu benutzen

Eine Übersicht der Berechtigungen die HPV benötigt findet sich im Abschnitt [Berechtigungen](#permissions).

## <a name="configuration"/>Konfiguration

**Die folgenden Einstellungen sollten beim ersten Start der Applikation eingestellt werden.**

### openHAB URL
Die ist die URL des openHAB servers. Sie wird für die openHAB integration (d.h. Kommando Item, Sensortwerte Meldung, Verbindungsindikator) benötigt. Wenn die URL nicht richtig konfiguriert ist, wird die genannte Funktionalität nicht zur Verfügung stehen.

Die URL kann automatisch per mDNS discovery gesucht werden, wenn sich das Gerät im selben Subnetz wie der openHAB server befindet. Dies kann während des Intros gemacht werden. Wenn die Suche nicht funktioniert, oder du die URL selbst eingeben möchtest, wäre ein Beispiel für die URL:

<a href="javascript:void(0)">http://{host}:8080/</a>

Es sollte kein weiterer Pfad an die URL angehängt werden.

> Wenn du https (ssl) benutzen möchtest, solltest du 8443 als Port verwenden.

### openHAB Version
Die Version deines openHAB Servers. 

In OH3 wurden leider die Namen der SSE Topics geändert, so dass HPV keine Item Updates mehr bekommt wenn die Version nicht richtig gesetzt ist.

### Startseite
Dies ist die Seite, die beim Starten der Applikation angezeigt werden soll. Dies kann eine beliebige URL sein, die vorgesehene Verwendung ist eine URL einer HABPanel Seite.    

Wenn du diese Einstellung leer lässt, wird die openHAB URL beim Starten der Applikation angezeigt.

Beispiele für HABPanel URLs:

<a href="javascript:void(0)">http://{host}:8080/habpanel/index.html#/</a>

startet im HABPanel Menü.

<a href="javascript:void(0)">http://{host}:8080/habpanel/index.html#/view/Info</a>

startet mit der speziellen HABPanel Seite Info<br>
   
> Achte auf die richtige Groß- und Kleinschreibung der HABPanel URL.

Anstatt die Startseite in den Einstellungen zu konfigurieren, kannst du sie auch interaktiv setzen. Lasse die Einstellung leer, navigiere im Browser zur gewünschen Seite und wähle "Als Startseite setzen" im Kontextmenü.
 
## <a name="control"/>Geräte Steuerung

HPV erlaubt die Konfiguration eines Kommando Items. Dies ist ein openHAB **String Item** das fortan von HPV überwacht wird. Das Schicken von Kommandos an dieses Item von openHAB erlaubt es HPV und/oder das Gerät zu steuern.

Stelle sicher, in den openHAB Regeln *sendCommand* zu verwenden, und nicht *postUpdate*, sonst ignoriert HPV die Kommandos.

Die folgenden Kommandos werden von HPV unterstützt:

> Parameter Werte stehen in Klammern und müssen durch tatsächliche Werte ersetzt werden.
> Optionale Parameter werden durch ein folgendes Fragezeichen gekennzeichnet.

#### RESTART

startet HABPanelViewer neu.

#### SCREEN_ON

schaltet den Bildschirm des Geräts ein.

Syntax: SCREEN_ON *\[Sekunden\]?*

Beispiel: SCREEN_ON 30

Der optionale Ganzzahlparameter hindert das System für die angegeben Anzahl von Sekunden daran, den Bildschirm auszuschalten.

#### KEEP_SCREEN_ON

schaltet den Bildschirm des Geräts ein und hindert das System daran, ihn auszuschalten. Dies funktioniert solange HPV de aktive App auf dem Gerät ist.

Sende ALLOW_SCREEN_OFF um es dem System zu erlauben den Bildschirm auszuschalten.

> Es wurde berichtet das der Bildschirm auf manchen Geräten trotzdem ausgeht, wenn das Kommando empfangen wurde während der Bildschirm aus war.
In diesem falle sende SCREEN_ON und KEEP_SCREEN_ON nach einer kurzen Pause.

#### ALLOW_SCREEN_OFF

erlaubt dem System, den Bildschirm auszuschalten. Wird nur nach Senden von KEEP_SCREEN_ON benötigt.

#### SCREEN_DIM

regelt die Helligkeit so weit runter wie möglich und stellt sie zurück, wenn der Bildschirm berührt wird.

Kann zusammen mit KEEP_SCREEN_ON benutzt werden um zu realisieren, dass das Gerät aufwacht, wenn der Bildschirm berührt wird.

#### SET_BRIGHTNESS

Syntax: SET_BRIGHTNESS *\[Prozent|AUTO\]*: sets the device brightness

Beispiel: SET_BRIGHTNESS 50
Beispiel: SET_BRIGHTNESS AUTO

Setzt die Helligkeit des Bildschirms.

Als Parameterwert muss entweder AUTO (aktiviert die adaptive Helligkeit) oder eine Zahl zwischen 0 und 100 (setzt die Helligkeit auf den angegebenen Prozentwert) übergeben werden..

#### MUTE

stellt das Gerät stumm.

#### UNMUTE

stellt die Lautstärke auf den Wert, den das Gerät zum Zeitpunkt des MUTE Kommandos hatte

#### SET_VOLUME

Syntax: SET_VOLUME *\[Lautstärke\]*

Beispiel: SET_VOLUME 5

stellt die Lautstärke auf den angegebenen Wert.

Der Ganzzahlparameter gibt die gewünschte Lautstärke an. Diese muss im Bereich 0 (lautlos) bis zum geräteabhängigen Maximalwert liegen.

> Wenn das Gerät mit SET_VOLUME lautlos gestellt wurde, stellt UNMUTE nicht die vorherige Lautstärke zurück.

#### TTS_SPEAK

Syntax: TTS_SPEAK *\[Text\]*

Beispiel: TTS_SPEAK Ich sage etwas

Benutzt den TTS Service des Gerätes um den angegebenen Text zu sprechen.

#### TTS_SET_LANG

Syntax: TTS_SET_LANG *\[Sprachcode\]*

Beispiel: TTS_SET_LANG de

Setzt die Sprache des TTS Services. Dies muss ein ISO 639 alpha-2 oder alpha-3 Sprachcode sein, oder ein Sprach Subtag von bis zu 8 Buchstaben Länge.
Gültige Beispiele sind: de, en, fr, ... 

> Ein gültiger Sprachcode führt nicht zwingenderweise zu einer funktionieren Sprachsynthese. Die angegebene Sprache muss dazu vom TTS Service des Gerätes unterstützt werden. 

#### FLASH_ON

schaltet das Blitzlicht der hinteren Kamera ein.

#### FLASH_OFF

schaltet das Blitzlicht der hinteren Kamera aus.

#### FLASH_BLINK

lässt das Blitzlicht der hinteren Kamera im 1 Sekunden Intervall blinken.

Syntax: FLASH_BLINK *\[Millisekunden\]?*

Beispiel: FLASH_BLINK 100

Der optionale Ganzzahlparameter gibt das gewünschte Blinkintervall in Millisekunden an.

#### BLUETOOTH_ON

schaltet bluetooth ein.

#### BLUETOOTH_OFF

schaltet bluetooth aus.

#### UPDATE_ITEMS

sendet alle aktuellen Item Werte an openHAB.

Dies bedeutet, dass alle in den Einstellungen konfigurierten Werte einmalig an die entsprechenden openHAB Items gemeldet werden.

#### START_APP

Startet eine App auf dem Gerät.

Syntax: START_APP *\[App Paket Name\]*

Beispiel: START_APP com.google.android.calendar

Der Paket Name kann in den Android Einstellungen der App nachgesehen werden. Z.B. ist es com.google.android.calendar für den Google Kalendar.

#### ADMIN_LOCK_SCREEN

aktiviert die Bildschirmsperre.

Benötigt, das die App in den Einstellungen als Device Admin aktiviert ist.

#### SHOW_URL

lädt eine beliebige Webseite.

Syntax: SHOW_URL *\[url\]*

Beispiel: SHOW_URL www.google.de

#### SHOW_DASHBOARD

lädt die angegebene HABPanel Seite.

Syntax: SHOW_DASHBOARD *\<Dashboard\>*

Beispiel: SHOW_DASHBOARD Overview

Der Dashboard Parameter ist ein String Parameter in welchem der Name des Dashboards übergeben werden muss.

> Dies funktioniert nur wenn HABPanel unter der Standard URL verfügbar ist. Wenn Sie eine angepasste HABPanel installation haben, benutzen Sie statt dessen SHOW_URL.

#### SHOW_START_URL

lädt die konfigurierte Startseite.

#### RELOAD

lädt die angezeigte Seite neu.

#### TO_FRONT

bringt die App in den Vordergrund.

Kann benutzt werden wenn man andere Apps auf dem Gerät laufen hat, die HPV überdecken.

#### CAPTURE_CAMERA

macht ein Foto mit der Frontkamera und sendet dieses an ein openHAB Image Item.

Syntax: CAPTURE_CAMERA *\[Image Item\]* *\[jpeg Qualität\]?*

Beispiel: CAPTURE_CAMERA PictureItem 90

Der erforderliche Parameter *Image Item* gibt das openHAB Image Item an, an das das Bild geschickt wird.
Der optionale Ganzzahlparameter *jpeg Qualität* muss im Bereich 0-100 liegen, sein Standardwert kann in den Einstellungen festgelegt werden.

> Falls die Bilder zu dunkel sein sollten, erhöhen Sie die CAPTURE_CAMERA Verzögerung in den Einstellungen.

#### CAPTURE_SCREEN

macht einen Screenshot und sendet diesen an ein openHAB Image Item.

Syntax: CAPTURE_SCREEN *\[Image Item\]* *\[jpeg Qualität\]?*

Beispiel: CAPTURE_SCREEN PictureItem 90

Der erforderliche Parameter *Image Item* gibt das openHAB Image Item an, an das das Bild geschickt wird.
Der optionale Ganzzahlparameter *jpeg Qualität* muss im Bereich 0-100 liegen, sein Standardwert kann in den Einstellungen festgelegt werden.

Du musst HPV erlauben, den Bildschirm aufzunehmen um dieses Kommando zu benutzen.

> Dies funktioniert nur mit Android Lollipop oder neuer.

#### ENABLE_MOTION_DETECTION

aktiviert die Bewegungserkennung in den App Einstellungen und startet sie.

#### DISABLE_MOTION_DETECTION

deaktiviert die Bewegungserkennung in den App Einstellungen und stoppt sie.

#### NOTIFICATION_SHOW

Zeigt eine Android Benachrichtigung auf dem Gerät.

Syntax: NOTIFICATION_SHOW *\[Farbe\]* *\[Text\]?*

Beispiel: NOTIFICATION_SHOW white Text der weißen Benachrichtigung

Der erforderliche Parameter *Farbe* spezifiziert die Farbe der LED (wenn das Gerät eine farbige Benachrichtigungs LED hat). Momentan sind nur die Werte white, red, green, blue erlaubt.
Der optionale String parameter *Text* spezifiziert den Benachrichtigungs Text.

> Wenn du mehrere Benachrichtigungen mit der gleichen Farbe schickst, ohne sie in der Zwischenzeit auf dem Gerät zu bestätigen, wird nur die letzte Benachrichtigung pro Farbe angezeigt.

### NOTIFICATION_HIDE

Bestätigt eine vorher geschickte Benachrichtigung, so dass sie nicht länger auf dem Gerät angezeigt wird.

Syntax: NOTIFICATION_HIDE *\[Farbe\]?*

Der optionale Parameter *Farbe* bestimmt welche Benachrichtigungen bestätigt werden. Momentan sind nur die Werte white, red, green, blue erlaubt.
Wenn die Farbe nicht angegeben wird, werden alle von HPV angeziegten Benachrichtigungen bestätigt.

### Kommando Log
Zeigt die letzten 100 von HABPanelViewer prozessierten Kommandos. Der Status wird durch die Textfarbe angezeigt:
* grün: Das Kommando wurde erfolgreich ausgeführt.
* gelb: Das Kommando ist HABPanelViewer nicht bekannt.
* grau: Das Kommando wird zur Zeit ausgeführt.
* rot: Die Ausführung wurde aufgrund eines Fehlers abgebrochen.

Klicke auf ein Kommando um die Details anuzeigen, falls es welche gibt.

[zurück nach oben](#top)

## <a name="reporting"/>Werte Meldung
Ermöglicht es, Werte der Geräte Sensoren oder andere Dinge an openHAB zu melden. Die gemeldeten Werte können dann z.B. in Regeln verwendet werden, um dich zu benachrichtigen, bevor die Batterie des Tablets leer ist.

- [Batteriesensor](#batteryReporting) (Ladestatus, Ladezustand, Batterie leer)
- [Annäherungssensor](#proximitySensor)
- [Helligkeitssensor](#brightnessSensor)
- [Drucksensor](#pressureSensor)
- [Temperatursensor](#temperatureSensor)
- [Beschleunigungssensor](#accelerometer) (Gerätebewegung)
- [Bewegungserkennung](#motionDetection) (kamerabasierte Bewegungserkennung)
- [Bildschirm](#screen) (Bildschirm an oder aus)
- [Lautstärke](#volume) 
- [Benutzung](#usage) (momentane App Benutzung)
- [Verbindungsindikatoren](#connectedIndicators) (App Startzeit, zyklischer Zeitstempel)
- [Docking Status](#dockingState)
- [Geräusch Pegel](#noiseLevel)  
- [URL](#url)

### <a name="batteryReporting"/>Batteriesensor
Wenn aktiviert, ändert die Anwendung die Werte von openHAB Items in Abhängigkeit des Batteriezustands:
- Batterie Leer Kontakt: Name des openHAB Kontakts (Item vom Typ **Contact**) der geschaltet wird wenn die Batterie leer ist
- Batterie wird geladen Kontakt: Name des openHAB Kontakts der geschaltet wird wenn die Batterie geladen wird.
- Batterieladung Item: Name des openHAB Items (vom Typ **Number**) das den Batterie Ladezustand in Prozent anzeigen soll.
- Battertemperatur Item: Name des openHAB Items (vom Typ **Number**) das die Batterie Temperatur anzeigen soll.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Battery_Low
    Contact Tablet_Battery_Charging
    Number Tablet_Battery_Level
    Number Tablet_Battery_Temp

Lasse Item Namen leer, um das Melden bestimmter Werte zu unterdrücken. Die Kontakte werden geschlossen, sobald die Batterie leer ist, bzw. das Gerät geladen wird.
Das Number Item reflektiert den Akku Ladezustand in Prozent.

### <a name="proximitySensor"/>Annäherungssensor
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter Annäherung.

Der Kontakt wird geschlossen, wenn Annäherung erkannt wird.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Proximity

### <a name="brightnessSensor"/>Helligkeitssensor
Setzt den Wert eines openHAB Items auf den vom Helligkeitssensor gemessenen Wert. Weil manche Geräte Sensor Werte in sehr schneller Aufeinanderfolge melden, erlaubt es die App, zyklisch Durchschnittswerte zu melden.

Die Einheit des gemeldeten Wertes ist lx.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Brightness

### <a name="pressureSensor"/>Drucksensor
Setzt den Wert eines openHAB Items auf den vom Drucksensor gemessenen Wert.

Die Einheit des gemeldeten Wertes ist Geräte abhängig.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Pressure

### <a name="temperatureSensor"/>Temperatursensor
Setzt den Wert eines openHAB Items auf den vom Temperatursensor gemessenen Wert.

Die Einheit des gemeldeten Wertes ist Grad Celsius.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Temperature

### <a name="accelerometer"/>Beschleunigungssensor
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter Gerätebewegung. Bei erkannter Bewegung wird der Kontakt geschlossen, und nach einer Minute ohne Bewegung wieder geöffnet.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Movement

### <a name="motionDetection"/>Bewegungserkennung
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter Bewegung. Bei erkannter Bewegung wird der Kontakt geschlossen, und nach einer Minute ohne Bewegung wieder geöffnet.
 
> Funktioniert nicht gleichzeitig mit der Blitzlicht Steuerung.

Die Erkennung funktioniert folgendermaßen: sie unterteilt das Bild in kleinere Bereiche und berechnet pro Bereich den Helligkeitsdurchschnitt. Wenn dieser Durchschnitt vom letzten Wert um mehr als die konfigurierte Schwelle abweicht,
wird Bewegung erkannt.

Die Erkennung kann aktiviert und deaktiviert werden, und es können einige Parameter in den Einstellungen verändert werden:
- Zeige Kamera Vorschau: Blendet eine Kamera Vorschau über dem Browser ein. Diese ist Nützlich, um die Erkennung richtig einzustellen.
- Benutze Lollipop Camera API 2: Benutze das Camera API 2 das mit Lollipop eingeführt wurde. Verändere dies nur, falls die Erkennung nicht funktioniert.
- Erkennungsgranularität: Die Anzahl der Teile pro Achse, in die das Bild eingeteilt wird um Bewegung zu erkennen. Eine Granularität von 10 unterteilt das Bild also in 100 Unterbereiche. 
- Erkennungsschwelle: Helligkeitsdifferenz, ab der Bewegung erkannt wird. 0 bedeutet jede Änderung resultiert in Bewegungserkennung, 255 bedeutet, es wird nie Bewegung erkannt.
- Erkennungsintervall: Zeit zwischen zwei aufeinanderfolgenden Erkennungsversuchen (in Millsekunden). Dies hat direkten Einfluss auf den CPU Verbrauch. 

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Motion

Der Kontakt wird geschlossen, wenn Bewegung erkannt wird, und nach einer Minute ohne Bewegung wieder geöffnet.

### <a name="screen"/>Bildschirm
Setzt den Wert eines openHAB Items auf den Bildschirm Schaltzustand (an/aus).

Der Kontakt wird geschlossen, wenn der Bildschirm an ist.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Screen

### <a name="volume"/>Lautstärke
Setzt den Wert eines openHAB Items auf die momentane Lautstärke des Gerätes.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_Volume

### <a name="usage"/>Benutzung
Ermöglicht das Schalten eines openHAB Kontakts bei erkannter aktive App Benutzung.

Der Kontakt wird geschlossen, wenn die App aktive benutzt wird. Nach einer konfigurierbaren Zeit der Inaktivität wird der Kontakt wieder geöffnet.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Usage

### <a name="connectedIndicators"/>Verbindungsindikatoren
Meldet die App Startzeit und/oder meldet zyklisch einen Zeitstempel an openHAB.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    DateTime Tablet_StartupTime
    DateTime Tablet_ConnectedTime
    
Die Startzeit kann z.B. genutzt werden, um nach dem App Start Initialisierungskommandos an HPV zu schicken.

### <a name="dockingState"/>Docking Status
Ermöglicht das Schalten eines openHAB Kontakts wenn das Gerät in einer Docking Station ist.

Der Kontakt wird geschlossen, wenn das Gerät in der Docking Station ist.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Contact Tablet_Docked

### <a name="noiseLevel"/>Geräusch Pegel
Meldet den mit dem Mikrofon des Gerätes aufgezeichneten Geräusch Pegel zyklisch an openHAB.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    Number Tablet_NoiseLevel

### <a name="url"/>URL
Setzt den Wert eines openHAB Items auf die URL der momentan angezeigten Webseite.

Eine beispielhafte openHAB Items Datei könnte so aussehen:

    String Tablet_Url

[zurück nach oben](#top)

## <a name="usability"/>Benutzerfreundlichkeit 
### Server Suchen
Beim ersten Start (oder wenn das Intro im Menü gestartet wird), sucht die Anwendung den openHAB Server per mDNS discovery im lokalen Netz.
Es wird zuerst versucht, eine HTTPS Verbindung zu finden, und falls das nicht funktioniert, wird auf HTTP zurück gegriffen.

### Home Screen / Launcher Funktionalität
Die Anwendung kann in den Android Einstellungen als Launcher konfiguriert werden. Dann startet Sie mit dem System und ersetzt den Standard Launcher.  

### Startseite
Browse eine beliebige Seite und setze sie mit dem Kontextmenü als Startseite.

### Zugangsdaten speichern
Immer wenn eine Webseite mit basic/digest authentication nach Zugangsdaten fragt, öffnet HPV einen Dialog. Dieser hat eine Checkbox die es erlaubt, die Zugansdaten zu speichern. Die Daten werden entweder unverschlüsselt (nicht empfohlen) oder mit einem Master Passwort verschlüsselt in einer Datenbank in einem privaten Teil des Dateisystems gespeichert.
HPV fragt dich ein mal pro Start nach dem Master Passwort, wenn erstmals ein Passwort gespeichert/gelesen werden soll.

### Ziehen verhindern
Falls du aus Versehen scrollst, wenn du einen Knopf drücken willst, aktiviere die Einstellung **Ziehen verhindern**. Dies deaktiviert Scrolling in der Anwendung komplett, und verhindert so auch das Öffnen des HABPanel Menüs.

### Starte App
Starte eine auf dem Gerät installierte App über das Menü.

### Kiosk Modus
Du kannst den Kiosk Modus über das Kontextmenü an- und auschalten. Wenn du den Kiosk Modus für die Startseite verwenden willst, aktiviere ihn mit dem Kontextmenü und wähle danach "Als Startseite setzen" aus dem Kontextmenü.

Eine andere Möglichkeit den Kiosk Modus zu aktivieren, ist an die URL den Parameter `?kiosk=on` anzuhängen.

## <a name="permissions"/>Berechtigungen

Habpanelviewer benötigt zwingend die folgenden Berechtigungen:  
* android.permission.INTERNET - um openHAB anzuzeigen
* android.permission.ACCESS_NETWORK_STATE - um den Verbindungsstatus zu openHAB zu überwachen

Weitere Berechtigungen um die volle Funktionalität verfügbar zu machen:
* android.permission.BLUETOOTH
& android.permission.BLUETOOTH_ADMIN - um Bluetooth an- und auszuschalten
* android.permission.FLASHLIGHT - um den Kamera Blitz zu kontrollieren
* android.permission.CAMERA - um Fotos aufzunehmen, für die Bewegungserkennung und WebRTC
* android.permission.WAKE_LOCK - um das Gerät aufzuwecken
* android.permission.WRITE_EXTERNAL_STORAGE - für den Export der Einstellungen
* android.permission.FOREGROUND_SERVICE - um die Kamera zu beenden, wenn die App geschlossen wird 
* android.permission.RECORD_AUDIO
& android.permission.MODIFY_AUDIO_SETTINGS - Für WebRTC

HPV kann zusätzlich in den Einstellungen als "Geräte Admin" eingetragen werden. Dies erlaubt es, bei entsprechendem Kommando die Bildschirmsperre zu aktivieren.
Wenn HPV Screenshots per Kommando an openHAB schicken soll, muss das Aufnehmen des Bildschirms erlaubt werden.

[zurück nach oben](#top)
