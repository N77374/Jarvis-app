[app]
title = JARVIS
package.name = jarvis
package.domain = org.boss
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 0.1
requirements = python3,kivy,requests,pyjnius

orientation = portrait
osx.kivy_version = 2.1.0
fullscreen = 1

android.api = 33
android.minapi = 21
android.sdk = 33
android.ndk_api_level = 21
android.archs = arm64-v8a

[buildozer]
log_level = 2
warn_on_root = 1
