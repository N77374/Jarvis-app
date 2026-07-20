import os
import requests
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.textinput import TextInput
from kivy.uix.label import Label
from jnius import autoclass

class JarvisLayout(BoxLayout):
    def __init__(self, **kwargs):
        super().__init__(orientation='vertical', **kwargs)
        self.label = Label(text="J.A.R.V.I.S. Core Online", size_hint_y=0.2, font_size='20sp')
        self.add_widget(self.label)
        
        self.input = TextInput(hint_text="Type command here, Sir...", multiline=False, size_hint_y=0.2)
        self.input.bind(on_text_validate=self.process_command)
        self.add_widget(self.input)
        
    def process_command(self, instance):
        command = self.input.text.strip().lower()
        self.input.text = ""
        if "open" in command:
            app_name = command.replace("open", "").strip()
            self.open_android_app(app_name)

    def open_android_app(self, app_name):
        try:
            app_packages = {
                "youtube": "com.google.android.youtube",
                "chrome": "com.android.chrome",
                "whatsapp": "com.whatsapp",
                "spotify": "com.spotify.music",
                "notes": "com.google.android.keep",
                "gallery": "com.oneplus.gallery3d"
            }
            package_id = app_packages.get(app_name, f"com.{app_name}")
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            currentActivity = PythonActivity.mActivity
            pm = currentActivity.getPackageManager()
            intent = pm.getLaunchIntentForPackage(package_id)
            if intent:
                currentActivity.startActivity(intent)
                self.label.text = f"JARVIS: Opening {app_name}, Sir."
            else:
                if app_name == "gallery":
                    intent = pm.getLaunchIntentForPackage("com.coloros.gallery3d")
                    if intent:
                        currentActivity.startActivity(intent)
                        return
                self.label.text = f"JARVIS: {app_name} package not found."
        except Exception as e:
            self.label.text = f"Error: {str(e)}"

class JarvisApp(App):
    def build(self):
        return JarvisLayout()

if __name__ == "__main__":
    JarvisApp().run()
      
