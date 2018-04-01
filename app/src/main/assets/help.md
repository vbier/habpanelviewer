# <a name="top"/>HABPanelViewer

HABPanelViewer is an Android home screen application visualizing HABPanel, a dedicated UI of openHAB.

Its functionality can mainly be divided into three categories: 
- [controlling the device](#control) in reaction to openHAB item state changes
- [reporting device sensor values](#reporting) to openHAB
- [usability features](#usability) making it easier to use HABPanel on a tablet

## <a name="configuration"/>Configuration

**The following settings need to be configured for the initial operation of HABPanelViewer.**

### openHAB URL
This is the base URL of your openHAB instance and is required for the integrations (e.g Command Item, Sensor Reporting, Connected Indicator). If this URL is not configured, these integration options will not function.

This URL can be automatically discovered using mDNS if the device is on the same subnet as your openHAB instance by clicking the "Discover Server" button in the "openHAB URL" settings.  If it cannot be discovered or you wish to manually enter it, an example for this base URL would be:

`http://{ip or hostname}:8080/` <br>
No additional URL paths should be configured here.

> If you are using https (ssl), your port would normally be 8443_

### Start page
This is the initial page loaded when the application is started or restarted.  While this can be any accessible URL, the intended use is for the HABPanel dashboard that you want as your starting page when the application is launched or restarted.

You can leave this field empty, in which case the openHAB URL will be used as the start page.

This needs to be the full URL, examples:

`http://{ip or hostname}:8080/habpanel/index.html#/`<br>would start at the main menu of dashboards.

`http://{ip or hostname}:8080/habpanel/index.html#/view/Lights`<br>would start at a specific dashboard.<br>
   
> The "dashboard" parameters are case sensitive.  In the example above, "view" is always all lower case and "Lights" is how you named it when creating the dashboard.  You can validate what your actual dashboard name and case is by navagating to it from your computer's browser.

Instead of configuring the start page in the settings, it might be more comfortable to set it interactively. Simply leave it blank, then browse to the panel you want to have as start panel and select "Set as start page" from the context menu.
 
### Kiosk Mode
You can toggle "Kiosk Mode" by using the context menu. If you want to apply kiosk mode to your start page, simply activate kiosk mode with the context menu and then select "Set as start page" from the context menu.

Another way of activating kiosk mode is to add the URL parameter `?kiosk=on` at the end of either example listed above.

## <a name="control"/>Device Control
### command item
Monitors an openHAB **String Item** for supported commands. Supported commands are:
* RESTART: makes HabPanelViewer restart
* SCREEN_ON: turns on the devices screen
* KEEP_SCREEN_ON: turns on the devices screen and prevents the system from turning off the screen
* ALLOW_SCREEN_OFF: allows the system to turn of the screen
* SCREEN_DIM: dims the screen as much as possible and restores the brightness on touch event
* MUTE: mutes the device
* UNMUTE: restores the volume to the level the device had when it was muted
* SET_VOLUME n: sets the device volume to n, being an integer between 0 and device max volume.
* FLASH_ON: turns on the flashlight of the back-facing camera
* FLASH_OFF: turns off the flashlight of the back-facing camera
* FLASH_BLINK: blinks the flashlight of the back-facing camera with an interval of one second
* FLASH_BLINK *n*: blinks the flashlight of the back-facing camera with an interval of *n* milliseconds
* BLUETOOTH_ON: turn bluetooth on
* BLUETOOTH_OFF: turn bluetooth off
* UPDATE_ITEMS: forces an update of all openHAB reporting items
* START_APP *app*: starts the app with the package name *app*
* ADMIN_LOCK_SCREEN: activates the lock screen (requires the app to be set as device admin in the settings)
* SHOW_URL *url*: shows the given URL
* SHOW_DASHBOARD *dashboard*: shows the given HABPanel dashboard
> This only works as long as HABPanel is available under its standard URL. If you have a custom HABPanel installation use SHOW_URL instead.
* SHOW_START_URL: shows the configured start URL
* RELOAD: reloads the current page
* CAPTURE_SCREEN *image item*: captures a screenshot and send it to the openHAB image item
> This only works with Android Lollipop or newer and a defined openHAB item of type **Image**
* CAPTURE_CAMERA *image item*: takes a picture and sends it to the openHAB image item
> In case the pictures are too dark, try to increase the CAPTURE_CAMERA delay in the settings.
* ENABLE_MOTION_DETECTION: enables the motion detection in the app settings.
* DISABLE_MOTION_DETECTION: disables the motion detection in the app settings.

### command log
Shows the last 100 commands that have been processed by HABPanelViewer. Color coding indicates processing status:
* green: command has been processed successfully
* yellow: command is not known to HABPanelViewer
* grey: command is currently executing
* red: command processing resulted in an exception.

Click on a command to expand its details, if any.

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
- Detection interval: time to sleep between consecutive detection attempts in ms. Note hat this has direct impact on CPU usage.

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

### start page configuration
Browse to an arbitrary page and set it as start page using the context menu.

### scrolling prevention
In case you always scroll accidentally when trying to press buttons, activate **Prevent Dragging** in the Settings. This completely disables scrolling, and effectively disables the HABPanel menu.  

### app shortcut
Start an app installed on your device from the main menu.

[go back to top](#top)