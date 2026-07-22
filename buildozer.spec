[app]

title = Jarvis App
package.name = jarvisapp
package.domain = org.n77374
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 0.1

requirements = python3,kivy,requests,urllib3,certifi,chardet,idna

android.permissions = INTERNET,RECORD_AUDIO
android.api = 33
android.minapi = 21
android.ndk = 25b
android.skip_update = False
android.accept_sdk_license = True
android.archs = arm64-v8a, armeabi-v7a
android.copy_libs = 1

[buildozer]

log_level = 2
warn_on_root = 1
