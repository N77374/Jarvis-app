[app]

# (str) Title of your application
title = Jarvis App

# (str) Package name
package.name = jarvisapp

# (str) Package domain (needed for android/ios packaging)
package.domain = org.n77374

# (str) Source code where the main.py live
source.dir = .

# (list) Source files to include (let empty to include all the files)
source.include_exts = py,png,jpg,kv,atlas

# (str) Application versioning (method 1)
version = 0.1

# (list) Application requirements
# Added requests, urllib3, certifi, chardet, idna so HTTPS API calls work on mobile
requirements = python3,kivy,requests,urllib3,certifi,chardet,idna

# (list) Permissions
# Required for internet access (Gemini API) and voice features
android.permissions = INTERNET,RECORD_AUDIO

# (int) Target Android API, should be as high as possible.
android.api = 33

# (int) Minimum API required
android.minapi = 21

# (str) Android NDK version to use (Pinned to 25b to fix compilation failure)
android.ndk = 25b

# (bool) If True, then skip hosting the project
android.skip_update = False

# (bool) Accept SDK license automatically
android.accept_sdk_license = True

# (str) The Android arch to build for
android.archs = arm64-v8a, armeabi-v7a

# (bool) Copy library instead of making a libpymodules.so
android.copy_libs = 1

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug (with command output))
log_level = 2

# (int) Display warning if buildozer is run as root (0 = disable, 1 = enable)
warn_on_root = 1
