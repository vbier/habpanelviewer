# <img alt="Logo" src="app/src/main/res/mipmap-hdpi/ic_launcher.png" border="0"> HABPanelViewer

An openHAB integrated kiosk browser.

Main features:
- **mDNS server discovery**: finds the openHAB server on your network by using mDNS discovery
- **launcher functionality**: the app can be set as launcher, thus starting automatically with the tablet
- **scrolling prevention**: when checked, scrolling is completely disabled
- **app shortcut**: allows to start an app from the main menu
- **device control**: allows to control different device features via an openHAB item
- **motion detection**: reports motion to openHAB
- **device sensor value reporting**: reports device sensor values to openHAB

The app does support android from SDK version 19, which means it will run on devices running Android 4.4+ (Kitkat).

For further details see the <a href="app/src/main/assets/help.md">in app help</a>

The app uses the following libraries:
- **michaelklishin/eventsource-netty5** licensed under the BSD-3 License: https://opensource.org/licenses/BSD-3-Clause
- **com.jakewharton:process-phoenix** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- **org.greenrobot:eventbus** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- **com.github.mukeshsolanki:MarkdownView-Android** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0