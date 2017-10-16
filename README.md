# HABPanelViewer
An android homescreen app as a HABPanel client.

This is an app with very limited functionality that addresses the problems I had when using other WebView based apps for browsing habpanel:
- **unwanted scrolling in kiosk mode**: whenever I enabled kiosk mode, pressing buttons activated scrolling even though the panel completely fit on the screen.
- **no flashlight control**: i use this on a wall mounted tabled that serves as a control for my alarm system. My tablet does not have a controllable LED, so I wanted to use the cameras flashlight as inidicator when the alarm system is armed.
- **no screen backlight control**: i wanted the tablet to turn on when somebody opens the door

So I wrote this app that fixes the above mentioned problems. It features:
- **home screen functionality**: the app can be set as homescreen, thus starting automatically with the tablet
- **openHAB URL and panel name configuration**: the configured panel is then shown on start
- **configurable kiosk mode activation**: simply check or uncheck to enable/disable kiosk mode
- **configurable scrolling prevention**: when checked, scrolling is completely disabled
- **flashlight control**: allows to configure an openHAB item and regexps to enable/disable or pulse the cameras flash
- **screen on control**: allows to configure an openHAB item and a regexp to enable the screen backlight
- **app shortcut**: allows to configure the package name of an app that can then be started from the main menu
- **motion detection**: turns the screen on when motion is detected (does not work at the same time as flashlight control). 

The app does support android from SDK version 23, which means it will run on Android 6 (Marshmallow) or newer.