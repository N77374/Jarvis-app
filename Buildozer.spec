[app]
title = JARVIS
package.name = jarvis
package.domain = org.boss
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 0.1
requirements = python3,kivy,requests,pyjnius

orientation = portrait
fullscreen = 1

android.api = 33
android.minapi = 21
android.sdk = 33
android.archs = arm64-v8a
android.accept_sdk_license = True
android.skip_update = False

[buildozer]
log_level = 2
warn_on_root = 1
