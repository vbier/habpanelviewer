# <img alt="Logo" src="app/src/main/res/mipmap-hdpi/ic_launcher.png" border="0"> HABPanel<img alt="Logo" src="V.png" border="0">iewer

An android homescreen app as a HABPanel client.

This is an app with very limited functionality that addresses the problems I had when using other WebView based apps for browsing HABPanel:
- **unwanted scrolling in kiosk mode**: whenever I enabled kiosk mode, pressing buttons activated scrolling even though the panel completely fit on the screen.
- **no flashlight control**: i use this on a wall mounted tabled that serves as a control for my alarm system. My tablet does not have a controllable LED, so I wanted to use the cameras flashlight as inidicator when the alarm system is armed.
- **no screen backlight control**: i wanted the tablet to turn on when somebody opens the door

So I wrote this app that fixes the above mentioned problems. It features:
- **mDNS server discovery**: finds the openHAB server on your network by using mDNS discovery
- **home screen functionality**: the app can be set as homescreen, thus starting automatically with the tablet
- **HABPanel panel name configuration**: the configured panel is then shown on start
- **configurable kiosk mode activation**: simply check or uncheck to enable/disable kiosk mode
- **configurable scrolling prevention**: when checked, scrolling is completely disabled
- **app shortcut**: allows to configure the package name of an app that can then be started from the main menu
- **flashlight control**: allows to configure an openHAB item and regexps to enable/disable or pulse the cameras flash (_available on Android 6+_)
- **screen on control**: allows to configure an openHAB item and a regexp to enable the screen backlight
- **motion detection**: turns the screen on when motion is detected (_does not work at the same time as flashlight control. there are two different implementations, one using the old camera API and one using the Camera 2 API on Android 5+_). 

The app does support android from SDK version 19, which means it will run on devices running Android 4.4+ (Kitkat).

The app uses the following libraries:
- **com.tylerjroach:eventsource**licensed under the BSD License: http://www.opensource.org/licenses/bsd-license
- **com.jakewharton:process-phoenix**licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- **org.greenrobot:eventbus**licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
