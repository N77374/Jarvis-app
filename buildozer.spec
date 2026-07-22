[app]

# (str) Title of your application
title = Jarvis App

# (str) Package name
package.name = jarvisapp

# (str) Package domain
package.domain = org.n77374

# (str) Source code location
source.dir = .

# (list) Source files to include
source.include_exts = py,png,jpg,kv,atlas

# (str) Application versioning
version = 0.1

# (list) Application requirements (Cleaned for p4a compatibility)
requirements = python3,kivy,requests

# (list) Permissions
android.permissions = INTERNET,RECORD_AUDIO

# (int) Target Android API
android.api = 33

# (int) Minimum API supported
android.minapi = 21

# (str) Android NDK version
android.ndk = 25b

# (bool) Skip updating build engine
android.skip_update = False

# (bool) Accept SDK license automatically
android.accept_sdk_license = True

# (str) Architectures to build for
android.archs = arm64-v8a, armeabi-v7a

# (bool) Copy library
android.copy_libs = 1

[buildozer]

# (int) Log level (Set to 1 so mobile logs are short and readable)
log_level = 1

# (int) Display warning if run as root
warn_on_root = 1
