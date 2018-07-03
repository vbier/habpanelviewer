# <img alt="Logo" src="app/src/main/res/mipmap-hdpi/ic_launcher.png" border="0"> HABPanelViewer

An openHAB integrated kiosk browser.

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="90">](https://f-droid.org/packages/de.vier_bier.habpanelviewer/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
      alt="Get it on Google Play"
      height="90">](https://play.google.com/store/apps/details?id=de.vier_bier.habpanelviewer)

Main features:
- **mDNS server discovery**: finds the openHAB server on your network by using mDNS discovery
- **launcher functionality**: the app can be set as launcher, thus starting automatically with the tablet
- **app shortcut**: allows to start an app from the main menu
- **device control**: allows to control different device features via an openHAB item
- **motion detection**: reports motion to openHAB
- **device sensor value reporting**: reports device sensor values to openHAB

The app does support android from SDK version 19, which means it will run on devices running Android 4.4+ (Kitkat).

For further details see the <a href="app/src/main/assets/help.md">app's help file</a>.

The app uses the following libraries:
- **michaelklishin/eventsource-netty5** licensed under the BSD-3 License: https://opensource.org/licenses/BSD-3-Clause
- **com.jakewharton:process-phoenix** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- **org.greenrobot:eventbus** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- **com.github.mukeshsolanki:MarkdownView-Android** licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
