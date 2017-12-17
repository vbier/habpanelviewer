# <img alt="Logo" src="app/src/main/res/mipmap-hdpi/ic_launcher.png" border="0"> HABPanelViewer

An android launcher/home screen app as a HABPanel client.

Main features:
- **mDNS server discovery**: finds the openHAB server on your network by using mDNS discovery
- **launcher functionality**: the app can be set as launcher, thus starting automatically with the tablet
- **HABPanel panel name configuration**: the configured panel is then shown on start
- **kiosk mode activation**: simply check or uncheck to enable/disable kiosk mode
- **scrolling prevention**: when checked, scrolling is completely disabled
- **app shortcut**: allows to configure the package name of an app that can then be started from the main menu
- **flashlight control**: allows to configure an openHAB item and regexps to enable/disable or blink the cameras flash (_available on Android 6+_)
- **screen on control**: allows to configure an openHAB item and a regexp to turn on the screen
- **motion detection**: reports motion to openHAB (_does not work at the same time as flashlight control. there are two different implementations, one using the old camera API and one using the Camera 2 API on Android 5+_).
- **device sensor value reporting**: reports device sensor values to openHAB

The app does support android from SDK version 19, which means it will run on devices running Android 4.4+ (Kitkat).

The app uses the following libraries:
- **com.tylerjroach:eventsource** licensed under the BSD License: http://www.opensource.org/licenses/bsd-license
- **com.jakewharton:process-phoenix** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- **org.greenrobot:eventbus** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- **com.github.mukeshsolanki:MarkdownView-Android** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0