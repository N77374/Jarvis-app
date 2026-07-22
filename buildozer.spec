[app]

# (str) Title of your application
title = Jarvis App

# (str) Package name
package.name = jarvisapp

# (str) Package domain (needed for android/ios packaging)
package.domain = org.test

# (str) Source code where the main.py lives
source.dir = .

# (list) Source files to include
source.include_exts = py,png,jpg,kv,atlas,json

# (str) Application versioning
version = 0.1

# (list) Application requirements
# Note: Keep comma-separated with NO spaces around commas
requirements = python3,kivy==2.3.0,requests,urllib3,certifi,chardet,idna

# (str) Supported orientation
orientation = portrait

# (bool) Indicate if the application should be fullscreen
fullscreen = 0

# (list) Permissions
android.permissions = INTERNET,ACCESS_NETWORK_STATE

# (int) Target Android API
android.api = 33

# (int) Minimum API supported
android.minapi = 21

# (str) Android NDK version
android.ndk = 25b

# (bool) Use private data dir (True) or public storage (False)
android.private_storage = True

# (bool) Accept SDK licenses automatically
android.accept_sdk_license = True

# (str) Architectures to build for
android.archs = arm64-v8a, armeabi-v7a

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug)
log_level = 2

# (int) Display warning if buildozer is run as root
warn_on_root = 1
