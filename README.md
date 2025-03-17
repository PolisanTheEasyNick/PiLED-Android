## PiLED for Android

<p align="left">
  <img src="https://github.com/user-attachments/assets/31671b08-1246-451f-ad12-4d499cce511a" width=30% height=30%>
</p>

The PiLED Android Client is designed to interact with the PiLED server running on a Raspberry Pi. It allows you to control an LED strip connected to the Pi's GPIO pins directly from your Android device over a network. With this app, you can set LED colors, trigger animations, and synchronize with other OpenRGB-enabled devices.  

# Features
* Change LED Colors: Adjust the LED strip's colors and set durations for smooth transitions.
* Animations: Supports fade and pulse animations with adjustable speed and duration.
* Synchronization with OpenRGB: If your PiLED server is connected to an OpenRGB server, the Android app can send color changes to sync with your PC's RGB devices.
* Connection Options: Connectі via TCP directly to the PiLED server.
* Suspend Mode: Toggle suspend mode to turn off LEDs and ignore non-suspend commands until re-enabled.
* NFC Integration: If your device supports NFC, you can trigger specific actions by scanning an NFC tag (tag must be with `piled://room_presence` data.). It will toggle `Suspend` mode. While suspended, PiLED will turn off all lights and will ignore all commands but `Suspend`.

# License
PiLED Android Client is licensed under the MIT License. See [LICENSE](LICENSE) for more information.

<p align="center">
  <img src="https://github.com/user-attachments/assets/4da8b11f-2653-421a-9e3d-0aee68f93c2d" width=30% height=30%>
</p>
