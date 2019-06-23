# <a name="top">HABPanelViewer (HPV)</a>

HABPanelViewer is an Android home screen application visualizing HABPanel, a dedicated UI of openHAB.

Its functionality can mainly be divided into three categories: 
- [controlling the device](#control) in reaction to openHAB item state changes
- [reporting values](#reporting) to openHAB
- [usability features](#usability) making it easier to use HABPanel on a tablet

If you want to see which permissions are needed by HPV, check the [permissions description](#permissions).

## <a name="configuration"/>Configuration

**The following preferences need to be configured for the initial operation of HPV.**

### openHAB URL
This is the base URL of your openHAB instance and is required for the integrations (e.g Command Item, Sensor Reporting, Connected Indicator). If this URL is not configured, these integration options will not function.

This URL can be automatically discovered using mDNS if the device is on the same subnet as your openHAB instance by clicking the "Discover Server" button in the "openHAB URL" preferences.  If it cannot be discovered or you wish to manually enter it, an example for this base URL would be:

<a href="javascript:void(0)">http://{host}:8080/</a>

No additional URL paths should be configured here.

> If you are using https (ssl), your port would normally be 8443.

### Start page
This is the initial page loaded when the application is started or restarted.  While this can be any accessible URL, the intended use is for the HABPanel dashboard that you want as your starting page when the application is launched or restarted.

You can leave this field empty, in which case the openHAB URL will be used as the start page.

This needs to be the full URL, examples:

<a href="javascript:void(0)">http://{host}:8080/habpanel/index.html#/</a>

would start at the main menu of dashboards.

<a href="javascript:void(0)">http://{host}:8080/habpanel/index.html#/view/Lights</a>

would start at a specific dashboard.
   
> The "dashboard" parameters are case sensitive.  In the example above, "view" is always all lower case and "Lights" is how you named it when creating the dashboard.  You can validate what your actual dashboard name and case is by navagating to it from your computer's browser.

Instead of configuring the start page in the preferences, it might be more comfortable to set it interactively. Simply leave it blank, then browse to the panel you want to have as start panel and select "Set as start page" from the context menu.
 
## <a name="control"/>Device Control

HPV allows to configure a command item. This has to be an openHAB **String Item** that will then be monitored by HABPanelViewer. Sending commands to this item from openHAB allows to control HABPanelViewer and/or the device itself.

Make sure you use *sendCommand* from openHAB rules and not *postUpdate*, as otherwise HPV will ignore the command.

The following commands are supported by HPV:

> Parameter values are shown in brackets and have to be replaced with actual values.
> Optional parameters are followed by a question mark.

#### RESTART

makes HPV restart.

#### SCREEN_ON

turns on the screen of the device.

Syntax: SCREEN_ON *\[seconds\]?*

Example: SCREEN_ON 30

The optional integer parameter allows to force the display to stay on for the given number of seconds.

#### KEEP_SCREEN_ON

turns on the devices screen and prevents the system from turning off the screen. This works as long is HPV is running in the foreground of the device.

Send ALLOW_SCREEN_OFF to allow the system to turn of the screen.

> It has been reported that the screen does not stay on on some devices when the command has been received while the screen is off. In this case send SCREEN_ON and KEEP_SCREEN_ON after a short delay.

#### ALLOW_SCREEN_OFF

allows the system to turn of the screen. Is only needed after having sent KEEP_SCREEN_ON.

#### SCREEN_DIM

dims the screen as much as possible. Show a blank screen and restores brightness when the screen is touched.

This can be used together with KEEP_SCREEN_ON to have a wake on touch feature.

#### SET_BRIGHTNESS

Syntax: SET_BRIGHTNESS *\[percentage|AUTO\]*: sets the device brightness

Example: SET_BRIGHTNESS 30
Example: SET_BRIGHTNESS AUTO

Allows to set the device screen brightness.

The parameter either has to be the fixed string AUTO (which activates adaptive screen brightness) or an integer value between 0 and 100 (which sets the brightness to the given percentage).

#### MUTE

mutes the device.

#### UNMUTE

restores the volume to the level the device had when it was muted with the MUTE command.

#### SET_VOLUME

Syntax: SET_VOLUME *\[volume\]*

Example: SET_VOLUME 5

sets the device volume.

The integer parameter specifies the desired volume level. The range for the parameter starts at 0 (device is muted) to a device dependent maximum value.

> When the device is muted using SET_VOLUME, UNMUTE does not restore the previous volume.

#### TTS_SPEAK

Syntax: TTS_SPEAK *\[text\]*

Example: TTS_SPEAK Say something

uses the devices tts engine to speak the given text.

#### TTS_SET_LANG

Syntax: TTS_SET_LANG *\[language code\]*

Example: TTS_SET_LANG en

sets the language to use for the TTS service. This has to be an ISO 639 alpha-2 or alpha-3 language code, or a language subtag up to 8 characters in length.
Valid examples are: de, en, fr, ... 

> A valid language code might not result in a working TTS. The given language also has to be supported by the TTS service on your device. 

#### FLASH_ON

turns on the flashlight of the back-facing camera.

#### FLASH_OFF

turns off the flashlight of the back-facing camera.

#### FLASH_BLINK

blinks the flashlight of the back-facing camera with an interval of one second.

Syntax: FLASH_BLINK *\[milliseconds\]?*

Example: FLASH_BLINK 100

The optional integer parameter allows to specify the blink interval in milliseconds.

#### BLUETOOTH_ON

turns on bluetooth.

#### BLUETOOTH_OFF

turn off bluetooth.

#### UPDATE_ITEMS

forces an update of all openHAB reporting items.

This means that the values of all reporting items defined in the preferences will be send to openHAB once regardless if they have changed.

#### START_APP

starts an app on the device.

Syntax: START_APP *\[app package name\]*

Example: START_APP com.google.android.calendar

The package name can be found in the android settings for the given app. E.g. it is com.google.android.calendar for the google calendar app.

#### ADMIN_LOCK_SCREEN

activates the lock screen.

This command requires the app to be set as device admin in the preferences.

#### SHOW_URL

shows an arbitrary web page.

Syntax: SHOW_URL *\[url\]*

Example: SHOW_URL www.google.de

#### SHOW_DASHBOARD

shows the given HABPanel dashboard.

Syntax: SHOW_DASHBOARD *\<dashboard\>*

Example: SHOW_DASHBOARD Overview

The dashboard parameter is a string parameter in which the name of the dashboard has to be specified.

> This only works as long as HABPanel is available under its standard URL. If you have a custom HABPanel installation use SHOW_URL instead.

#### SHOW_START_URL

shows the configured start URL.

#### RELOAD

reloads the current page.

#### CAPTURE_CAMERA

captures a photo with the front camera of the device and sends it to an openHAB *Image item*.

Syntax: CAPTURE_CAMERA *\[image item\]* *\[jpeg quality\]?*

Example: CAPTURE_CAMERA PictureItem 90

The mandatory parameter *image item* specifies the name of an openHAB image item to which the screenshot will be sent.
The optional integer parameter *jpeg quality* has to be in range 0-100 and defaults to the value defined in the preferences.

> In case the pictures are too dark, try to increase the CAPTURE_CAMERA delay in the preferences.

#### CAPTURE_SCREEN

captures a screenshot of the device and sends it to an openHAB *Image item*.

Syntax: CAPTURE_SCREEN *\[image item\]* *\[jpeg quality\]?*

Example: CAPTURE_SCREEN PictureItem 90

The mandatory parameter *image item* specifies the name of an openHAB image item to which the screenshot will be sent.
The optional integer parameter *jpeg quality* has to be in range 0-100 and defaults to the value defined in the preferences.

You need to allow HPV to record the screen in order for this to work.

> This only works with Android Lollipop or newer.

#### ENABLE_MOTION_DETECTION

enables the motion detection in the app preferences and starts motion detection.

Motion detection has to be configured in the preferences in order for this to work.

#### DISABLE_MOTION_DETECTION

disables the motion detection in the app preferences and stops motion detection.

#### NOTIFICATION_SHOW

show an android notification on the device.

Syntax: NOTIFICATION_SHOW *\[color\]* *\[text\]?*

Example: NOTIFICATION_SHOW white White notification with text

The mandatory parameter *color* specifies the color of the LED (if the device has a colored notification LED). Currently only the values white, red, green, blue are allowed.
The optional string parameter *text* specifies the notification text.

> If you send multiple notifications with the same color without dismissing them in-between, only the last notification will be shown per color.

### NOTIFICATION_HIDE

hides a previously shown notification on the device.

Syntax: NOTIFICATION_HIDE *\[color\]?*

The optional parameter *color* specifies the color of the notification to hide. Currently only the values white, red, green, blue are allowed.
If it is not specified, all HPV notifications are hidden.

### command log
Shows the last 100 commands that have been processed by HPV. Color coding indicates processing status:
* green: command has been processed successfully
* yellow: command is not known to HPV
* grey: command is currently executing
* red: command processing resulted in an exception.

Click on a command to expand its details, if any.

[go back to top](#top)

## <a name="reporting"/>Value Reporting
Allows to set the values of openHAB items depending on the device sensors or other things. You can then use the items in rules, e.g. for sending a notification when the battery is low.

- [battery](#batteryReporting) (battery charging status, battery level, batter low)
- [proximity sensor](#proximitySensor)
- [brightness sensor](#brightnessSensor)
- [pressure sensor](#pressureSensor)
- [temperature sensor](#temperatureSensor)
- [accelerometer](#accelerometer) (device movement)
- [motion detection](#motionDetection) (camera based motion detection)
- [screen](#screen) (screen on or off)
- [volume](#volume) 
- [usage](#usage) (current app usage)
- [connected indicators](#connectedIndicators) (app startup time, cyclic time stamp)
- [docking state](#dockingState)
- [URL](#url)

### <a name="batteryReporting"/>battery reporting
When enabled, the app updates the state of up to three openHAB items depending on the battery state:
- Battery Low Contact: the name of a **Contact** Item that shall be set whenever the battery is low.
- Battery Charging Contact: the name of a **Contact** Item that shall be set whenever the battery is charging.
- Battery Level Item: the name of a **Number** Item that shall be set when the battery level changes.
- Battery Temperature Item: the name of a **Number** Item that shall be set when the battery temperature changes.
reports device sensor values to openHAB (currently only battery, more to come)

A sample openHAB items file looks like this:

    Contact Tablet_Battery_Low
    Contact Tablet_Battery_Charging
    Number Tablet_Battery_Level
    Number Tablet_Battery_Temp

Leave item names empty in the preferences in order to skip reporting for this specific value. The contact item states will be *CLOSED* whenever the battery is low or the device is charging, *OPEN* otherwise.
The number item state will be set to the battery charging level (in percent).

### <a name="proximitySensor"/>proximity sensor
Allows to set the value of an openHAB contact item depending on the device proximity sensor.

The contact state will be *CLOSED* whenever an object has been detected close to the device, *OPEN* otherwise.

A sample openHAB items file looks like this:

    Contact Tablet_Proximity

### <a name="brightnessSensor"/>brightness sensor
Allows to set the value of an openHAB number item depending on the device brightness sensor. As some devices report values in quick succession, brightness reporting additionally allows to collect values for a defined time and to only send the average to openHAB. 

The item state will be set to the measured brightness in lux.

A sample openHAB items file looks like this:

    Number Tablet_Brightness

### <a name="pressureSensor"/>pressure sensor
Allows to set the value of an openHAB number item depending on the device pressure sensor.

The item state will be set to the measured pressure in mBar or hPa (depending on the sensor hardware).

A sample openHAB items file looks like this:

    Number Tablet_Pressure

### <a name="temperatureSensor"/>temperature sensor
Allows to set the value of an openHAB number item depending on the device temperature sensor.

The item state will be set to the measured temperature in degrees celsius.

A sample openHAB items file looks like this:

    Number Tablet_Temperature

### <a name="accelerometer"/>accelerometer
Allows to set the value of an openHAB contact item depending on whether the device is currently moved.

The contact state will be *CLOSED* whenever the device is moved. It will be opened again after one minute without movement.

A sample openHAB items file looks like this:

    Contact Tablet_Movement

### <a name="motionDetection"/>motion detection
Allows to close or open an openHAB contact item when motion is detected. Whenever motion is detected, the contact will be closed. It will be opened again after one minute without motion.

> Does not work at the same time as flashlight control.

The detection process works as follows: it divides the picture into smaller areas, calculates a brightness average for every area and checks if this average deviates from the last value. If the deviation is higher than the leniency, motion is detected.

The detection can be enabled or disabled and detection parameters can be changed in the preferences:
- Camera Preview: whether to show a preview of the detection as on overlay. This is useful for fine-tuning the detection.
- Use Lollipop camera API: Use the new Camera 2 API introduced with Lollipop. You should generally use this when available.
- Detection granularity: Amount by which is axis is divided. So a granularity of 10 results in the picture being divided into 100 areas. 
- Detection leniency: deviation threshold that triggers detected motion. 0 means every deviation results in detected motion, 255 means motion will never be detected.
- Detection interval: time to sleep between consecutive detection attempts in ms. Note hat this has direct impact on CPU usage.

A sample openHAB items file looks like this:

    Contact Tablet_Motion

The contact state will be *CLOSED* whenever motion has been detected by the camera, and will be *OPEN* again after one minute without motion.

### <a name="screen"/>screen
Allows to set the value of an openHAB contact item depending on the device screen state (ON/OFF).

The contact state will be *CLOSED* whenever when the screen is on, *OPEN* otherwise.

A sample openHAB items file looks like this:

    Contact Tablet_Screen

### <a name="volume"/>volume
Allows to set the value of an openHAB number item depending on the device volume.

The item state will be set to the current volume of the device.

A sample openHAB items file looks like this:

    Number Tablet_Volume

### <a name="usage"/>usage
Allows to set the value of an openHAB contact item depending on whether the app is currently in active use.

The contact state will be *CLOSED* whenever someone is actively using the app. After a configurable time of inactivity, the state will be set to *OPEN*.

A sample openHAB items file looks like this:

    Contact Tablet_Usage

### <a name="connectedIndicators"/>connected indicators
Allows to set the value of an openHAB datetime item to the app startup time and/or to cyclicly send a time stamp to openHAB.

A sample openHAB items file looks like this:

    DateTime Tablet_StartupTime
    DateTime Tablet_ConnectedTime
    
You can use the startup time to trigger rules that can send initializing commands to HPV.

### <a name="dockingState"/>docking state
Allows to set the value of an openHAB contact item depending on whether the device is currently in a docking station.

The contact state will be *CLOSED* whenever the device is docked, *OPEN* otherwise.

A sample openHAB items file looks like this:

    Contact Tablet_Docked

### <a name="url"/>URL
Allows to set the URL of the currently display page as value of an openHAB string item.

A sample openHAB items file looks like this:

    String Tablet_Url

[go back to top](#top)

## <a name="usability"/>Usability 
### mDNS server discovery
On first start (or when the Intro is started after clearing the server URL), the app uses mDNS discovery to find the openHAB server on the local network.

### launcher functionality
The application can be set as the device launcher. It then starts with the device on replaces the device home screen. This has to be configured in the android preferences. 

### start page configuration
Browse to an arbitrary page and set it as start page using the context menu.

### credentials storage
Whenever a web page asks for credentials using basic authentication, HPV opens a login dialog. This dialog has a checkbox allowing to store the credentials. Credentials are stored unencrypted in a database in a private part of the filesystem. This is safe as long as your device is not rooted, otherwise apps with root privileges may read the stored credentials.

### scrolling prevention
In case you always scroll accidentally when trying to press buttons, activate **Prevent Dragging** in the preferences. This completely disables scrolling, and effectively disables the HABPanel menu.  

### app shortcut
Start an app installed on your device from the main menu.

### kiosk mode
You can toggle "Kiosk Mode" by using the context menu. If you want to apply kiosk mode to your start page, simply activate kiosk mode with the context menu and then select "Set as start page" from the context menu.

Another way of activating kiosk mode is to add the URL parameter `?kiosk=on` at the end of either example listed above.

## <a name="configuration"/>Permissions

HPV needs the following permissions:
* android.permission.INTERNET - for displaying openHAB in a webview
* android.permission.ACCESS_NETWORK_STATE - for tracking state of connection to openHAB

Additional optional permissions allowing to use the full functionality:
* android.permission.BLUETOOTH
& android.permission.BLUETOOTH_ADMIN - for turning bluetooth on and off
* android.permission.FLASHLIGHT - for controlling the camera flashlight
* android.permission.CAMERA - for capturing photos, motion detection and WebRTC
* android.permission.WAKE_LOCK - for waking up the device
* android.permission.WRITE_EXTERNAL_STORAGE - for exporting preferences
* android.permission.FOREGROUND_SERVICE - to make sure camera is correctly closed 
* android.permission.RECORD_AUDIO
& android.permission.MODIFY_AUDIO_SETTINGS - for WebRTC

HPV can be set as "Device Admin" in the preferences. This allows to lock the device when the appropriate command is received.

[go back to top](#top)