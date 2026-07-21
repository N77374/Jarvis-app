[app]
title = JARVIS
package.name = jarvisapp
package.domain = org.n77374
source.dir = .
source.include_exts = py,png,jpg,jpeg,mp4,kv,atlas
version = 0.1

# Network and Kivy requirements for LLM API integration
requirements = python3,kivy,requests,urllib3,certifi,charset_normalizer,idna

orientation = portrait
osx.kivy_version = 2.1.0
fullscreen = 0

# Adjust layout automatically when keyboard opens
android.window_softinput_mode = below_target

# Android permissions to allow internet connection
android.permissions = INTERNET, ACCESS_NETWORK_STATE

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
