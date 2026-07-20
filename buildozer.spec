[app]
title = JARVIS
package.name = jarvisapp
package.domain = org.n77374
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 0.1
requirements = python3,kivy

orientation = portrait
osx.kivy_version = 2.1.0
fullscreen = 1
android.archs = arm64-v8a

# Android SDK / API configuration
android.api = 33
android.minapi = 21
android.ndk_api = 21
android.skip_update = False
android.accept_sdk_license = True

[buildozer]
log_level = 2
warn_on_root = 0
