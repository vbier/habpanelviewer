# <a name="top"/>HABPanelViewer

HABPanelViewer is an Android home screen application visualizing HABPanel, a dedicated UI of openHAB.

Its functionality can mainly be divided into three categories: 
- [controlling the device](#control) in reaction to openHAB item state changes
- [reporting device sensor values](#reporting) to openHAB
- [usability features](#usability) making it easier to use HABPanel on a tablet

## <a name="control"/>Device Control
### flashlight control
Allows to enable, disable or blink the devices flashlight depending on an openHAB item (_available on Android 6+_).

In order to use this, configure the name of an openHAB item and regular expressions in the settings:
- Flash Item: the name of an openHAB item from which to derive the flashlight state
- Blinking Regexp: a regular expression that, if matched, activates the flashlight blinking 
- Enabled Regexp: a regular expression that, if matched, activates the flashlight

An example could be to use a **String Item** in openHAB than can be defined in an items file like this:

    String Tablet_Flashlight
  
If you now configure blinking regexp as *BLINKING* and enabled regexp as *ON*, you can enable the flashlight by setting the item in openHAB to "ON" or you can have the flashlight blinking by setting the value of the item in openHAB to "BLINKING".

You can also realize more complex things, e.g. derive the flashlight from the state of your openHAB alarm system. Make it blinking when the system is activating and have it on when the system is armed. This would allow to see from far way in which state the alarm system is without turning on the screen. 

### screen on control
Allows to turn on the devices screen depending on an openHAB item.

In order to use this, configure the name of an openHAB item and regular expressions in the settings:
- Screen On Item: the name of an openHAB item from which to derive the screen state
- Keep screen on: if checked, the app tries to force the screen to stay on as long as the regular expression matches. Otherwise, the system might turn the screen after inactivity. 
- Enabled Regexp: a regular expression that, if matched, turns on the screen 

### volume control
Allows to set the device volume depending on an openHAB item. Simply configure the name of an openHAB **Number** item. The device volume will be set to the value of this item. In order to find the valid range for your device, check the *Volume Control* section of the *Status Information* screen. Out of range values will be ignored.

[go back to top](#top)

## <a name="reporting"/>Sensor Reporting
Allows to set the values of openHAB items depending on the device sensors. You can then use the items in rules, e.g. for sending a notification when the battery is low.

### battery reporting
When enabled, the app updates the state of up to three openHAB items depending on the battery state:
- Battery Low Contact: the name of a **Contact** Item that shall be set whenever the battery is low.
- Battery Charging Contact: the name of a **Contact** Item that shall be set whenever the battery is charging.
- Battery Level Item: the name of a **Number** Item that shall be set when the battery level changes. This value is updated every 5 seconds while the device is connected to power, every 5 minutes when running on battery.
reports device sensor values to openHAB (currently only battery, more to come)

A sample openHAB items file looks like this:

    Contact Tablet_Battery_Low
    Contact Tablet_Battery_Charging
    Number Tablet_Battery_Level

Leave item names empty in the settings in order to skip reporting for this specific value. The contact item states will be *CLOSED* whenever the battery is low or the device is charging, *OPEN* otherwise.
The number item state will be set to the battery charging level (in percent).

### motion detection
Allows to set the value of an openHAB item when motion is detected (_does not work at the same time as flashlight control. there are two different implementations, one using the old camera API and one using the Camera 2 API on Android 5+_).

The detection process works as follows: it divides the picture into smaller areas, calculates a brightness average for every area and checks if this average deviates from the last value. If the deviation is higher than the leniency, motion is detected.

The detection can be enabled or disabled and detection parameters can be changed in the settings:
- Camera Preview: whether to show a preview of the detection as on overlay. This is useful for fine-tuning the detection.
- Use Lollipop camera API: Use the new Camera 2 API introduced with Lollipop. You should generally use this when available.
- Detection granularity: Amount by which is axis is divided. So a granularity of 10 results in the picture being divided into 100 areas. 
- Detection leniency: deviation threshold that triggers detected motion. 0 means every deviation results in detected motion, 255 means motion will never be detected.   

A sample openHAB items file looks like this:

    Contact Tablet_Motion

The contact state will be *CLOSED* whenever motion has been detected by the camera, *OPEN* otherwise.

### proximity sensor
Allows to set the value of an openHAB contact item depending on the device proximity sensor.

The contact state will be *CLOSED* whenever an object has been detected close to the device, *OPEN* otherwise.

A sample openHAB items file looks like this:

    Contact Tablet_Proximity

### brightness sensor
Allows to set the value of an openHAB number item depending on the device brightness sensor. As some devices report values in quick succession, brightness reporting additionally allows to collect values for a defined time and to only send the average to openHAB. 

The item state will be set to the measured brightness in lux.

A sample openHAB items file looks like this:

    Number Tablet_Brightness

### pressure sensor
Allows to set the value of an openHAB number item depending on the device pressure sensor.

The item state will be set to the measured pressure in mBar or hPa (depending on the sensor hardware).

A sample openHAB items file looks like this:

    Number Tablet_Pressure

### temperature sensor
Allows to set the value of an openHAB number item depending on the device temperature sensor.

The item state will be set to the measured temperature in degrees celsius.

A sample openHAB items file looks like this:

    Number Tablet_Temperature

[go back to top](#top)

## <a name="usability"/>Usability 
### mDNS server discovery
On first start (or when invoked from the server URL setting), the app uses mDNS discovery to find the openHAB server on the local network.
It first to find a HTTPS connection to the server and falls back to HTTP if that does not work.

### launcher functionality
The application can be set as the device launcher. It then starts with the device on replaces the device home screen. This has to be configured in the android settings. 

### HABPanel start panel configuration
If you want to skip the HABPanel menu on start and directly want to go to a panel, set the name of this panel in the settings. 

### kiosk mode (de)activation
You can activate or de-active kiosk mode in the settings. Kiosk mode hides the HABPanel status bar and the menu.

### scrolling prevention
In case you always scroll accidentally when trying to press buttons, activate **Prevent Dragging** in the Settings. This completely disables scrolling, and effectively disables the HABPanel menu.  

### app shortcut
Configure menu entry name and the package name of an app that can then be started from the main menu. In order to find out the package name of your favorite app, open it in the playstore. The package name is shown as id in the URL.

[go back to top](#top)