[app]

# (str) Title of your application
title = Jarvis App

# (str) Package name
package.name = jarvisapp

# (str) Package domain (needed for android/ios packaging)
package.domain = org.n77374

# (str) Source code where the main.py lives
source.dir = .

# (list) Source files to include
source.include_exts = py,png,jpg,kv,atlas

# (str) Application versioning
version = 0.1

# (list) Application requirements
# Included 'openssl' which is required for HTTP/HTTPS requests to Gemini API on Android
requirements = python3,kivy,openssl,requests,urllib3,certifi,chardet,idna

# (list) Permissions
android.permissions = INTERNET,RECORD_AUDIO

# (int) Target Android API
android.api = 33

# (int) Minimum API supported
android.minapi = 21

# (str) Android NDK version (Pinned to 25b to fix toolchain/C compilation crashes)
android.ndk = 25b

# (bool) If True, skip updating the build engine
android.skip_update = False

# (bool) Accept SDK license automatically (Prevents build hangs/crashes)
android.accept_sdk_license = True

# (str) Architectures to build for
android.archs = arm64-v8a, armeabi-v7a

# (bool) Copy library instead of making a libpymodules.so
android.copy_libs = 1

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug)
log_level = 2

# (int) Display warning if buildozer is run as root
warn_on_root = 1
