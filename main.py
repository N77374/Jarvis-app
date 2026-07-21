from kivy.app import App
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.image import Image
from kivy.core.window import Window
from kivy.graphics import Color, RoundedRectangle, Ellipse
from kivy.clock import Clock
import datetime
import re
import random
import threading
import requests
import os

# Securely fetches the API key from your GitHub Repository Secret
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")

Window.softinput_mode = 'below_target'


class ChatBubble(BoxLayout):
    def __init__(self, text, is_user=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_y = None
        self.size_hint_x = 0.85
        self.padding = [12, 10, 12, 10]
        self.pos_hint = {'right': 0.98} if is_user else {'x': 0.02}

        self.label = Label(
            text=text,
            color=(1, 1, 1, 1) if is_user else (0, 0.9, 1, 1),
            size_hint_y=None,
            halign='left',
            valign='top',
            font_size='14sp',
            markup=True
        )
        self.label.bind(width=lambda instance, val: setattr(instance, 'text_size', (val, None)))
        self.label.bind(texture_size=self._update_bubble_height)
        self.add_widget(self.label)

        bg_color = (0.0, 0.35, 0.5, 0.85) if is_user else (0.08, 0.12, 0.18, 0.85)
        with self.canvas.before:
            Color(*bg_color)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[12])
        self.bind(pos=self._update_rect, size=self._update_rect)

    def _update_bubble_height(self, instance, val):
        self.label.height = val[1]
        self.height = val[1] + 20

    def _update_rect(self, instance, val):
        self.rect.pos = self.pos
        self.rect.size = self.size


class GeminiPillInputBar(BoxLayout):
    """Custom Kivy widget replicating the Gemini pill search bar design."""
    def __init__(self, send_callback, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'horizontal'
        self.size_hint = (0.95, None)
        self.height = 58
        self.padding = [12, 6, 8, 6]
        self.spacing = 6
        self.pos_hint = {'center_x': 0.5}

        # Background Pill Drawing
        with self.canvas.before:
            Color(0.12, 0.13, 0.15, 0.95)  # Dark charcoal pill background
            self.bg_pill = RoundedRectangle(pos=self.pos, size=self.size, radius=[29])
        self.bind(pos=self._update_bg, size=self._update_bg)

        # 1. Plus (+) Attachment Button
        plus_btn = Button(
            text="+",
            font_size='22sp',
            color=(0.85, 0.88, 0.9, 1),
            size_hint=(None, 1),
            width=36,
            background_color=(0, 0, 0, 0)
        )

        # 2. Text Input Field
        self.input_field = TextInput(
            hint_text="Ask J.A.R.V.I.S.",
            hint_text_color=(0.55, 0.6, 0.65, 1),
            foreground_color=(1, 1, 1, 1),
            cursor_color=(0.3, 0.6, 1, 1),
            multiline=False,
            font_size='16sp',
            size_hint=(1, 1),
            padding=[4, 12, 4, 0],
            background_color=(0, 0, 0, 0)
        )
        self.input_field.bind(on_text_validate=send_callback)

        # 3. Mic Icon Button
        mic_btn = Button(
            text="🎙",
            font_size='18sp',
            color=(0.85, 0.88, 0.9, 1),
            size_hint=(None, 1),
            width=36,
            background_color=(0, 0, 0, 0)
        )

        # 4. Circular Blue Action/Send Button
        self.send_btn = Button(
            text="ııı",
            font_size='18sp',
            bold=True,
            color=(1, 1, 1, 1),
            size_hint=(None, 1),
            width=46,
            background_color=(0, 0, 0, 0)
        )
        with self.send_btn.canvas.before:
            Color(0.12, 0.25, 0.55, 1)  # Gemini Deep Blue
            self.circle = Ellipse(pos=self.send_btn.pos, size=(46, 46))
        self.send_btn.bind(pos=self._update_circle, size=self._update_circle)
        self.send_btn.bind(on_release=send_callback)

        # Assembly
        self.add_widget(plus_btn)
        self.add_widget(self.input_field)
        self.add_widget(mic_btn)
        self.add_widget(self.send_btn)

    def _update_bg(self, instance, val):
        self.bg_pill.pos = self.pos
        self.bg_pill.size = self.size

    def _update_circle(self, instance, val):
        self.circle.pos = (self.send_btn.x, self.send_btn.y + (self.send_btn.height - 46) / 2)
        self.circle.size = (46, 46)


class JarvisApp(App):
    def build(self):
        root = FloatLayout()

        # Background HUD
        try:
            self.bg_image = Image(
                source='hud_face.png',
                allow_stretch=True,
                keep_ratio=False,
                size_hint=(1, 1),
                pos_hint={'x': 0, 'y': 0}
            )
            root.add_widget(self.bg_image)
        except Exception:
            pass

        # Top Overlay Status Controls
        self.clock_label = Label(
            text="00:00:00\nDATE",
            color=(0, 1, 1, 1),
            font_size='13sp',
            bold=True,
            halign='center',
            size_hint=(0.35, 0.1),
            pos_hint={'right': 0.98, 'top': 0.98}
        )
        root.add_widget(self.clock_label)

        self.diag_label = Label(
            text="[color=00FFCC]POWER CORE: 98%\nSUIT TEMP: 37.5°C[/color]",
            markup=True,
            font_size='11sp',
            halign='left',
            size_hint=(0.35, 0.1),
            pos_hint={'x': 0.02, 'top': 0.98}
        )
        root.add_widget(self.diag_label)

        # Chat Section Container
        chat_container = BoxLayout(
            orientation='vertical',
            padding=[10, 10, 10, 10],
            spacing=10,
            size_hint=(1, 0.68),
            pos_hint={'x': 0, 'y': 0}
        )

        self.scroll_view = ScrollView(size_hint=(1, 1), do_scroll_x=False)
        self.chat_layout = GridLayout(cols=1, spacing=10, size_hint_y=None)
        self.chat_layout.bind(minimum_height=self.chat_layout.setter('height'))

        self.scroll_view.add_widget(self.chat_layout)
        chat_container.add_widget(self.scroll_view)

        self.add_message("J.A.R.V.I.S.: Tactical Core online. Real-time Search Uplink active, Sir.", is_user=False)

        # Gemini Styled Input Bar
        self.pill_bar = GeminiPillInputBar(send_callback=self.process_command)
        chat_container.add_widget(self.pill_bar)

        root.add_widget(chat_container)

        Clock.schedule_interval(self.update_tactical_hud, 1.0)
        return root

    def update_tactical_hud(self, dt):
        now = datetime.datetime.now()
        self.clock_label.text = now.strftime("%H:%M:%S (LOCAL)\n%Y.%m.%d")
        temp = round(37.5 + random.uniform(-0.2, 0.3), 1)
        power = random.choice([98, 98, 99])
        self.diag_label.text = f"[color=00FFCC]POWER CORE: {power}%\nSUIT TEMP: {temp}°C[/color]"

    def add_message(self, text, is_user=False):
        bubble = ChatBubble(text=text, is_user=is_user)
        self.chat_layout.add_widget(bubble)
        Clock.schedule_once(lambda dt: setattr(self.scroll_view, 'scroll_y', 0), 0.1)

    def process_command(self, instance):
        command = self.pill_bar.input_field.text.strip()
        if not command:
            return

        self.add_message(f"You: {command}", is_user=True)
        self.pill_bar.input_field.text = ""

        # Local Math Handling
        clean_math = re.sub(r'[^0-9\+\-\*\/\.\(\)\s]', '', command)
        if clean_math.strip() and any(op in clean_math for op in ['+', '-', '*', '/']):
            try:
                result = eval(clean_math, {"__builtins__": None}, {})
                self.add_message(f"J.A.R.V.I.S.: Result = {result}", is_user=False)
                return
            except Exception:
                pass

        # Query Gemini API with Google Search Grounding in background thread
        threading.Thread(target=self.fetch_ai_response, args=(command,), daemon=True).start()

    def fetch_ai_response(self, user_query):
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={GEMINI_API_KEY}"
        headers = {"Content-Type": "application/json"}
        
        prompt = f"You are J.A.R.V.I.S., an advanced AI assistant built for Tony Stark. Address the user respectfully as 'Sir'. Keep answers concise, clear, and direct. Query: {user_query}"
        
        payload = {
            "contents": [{
                "parts": [{"text": prompt}]
            }],
            "tools": [
                {"google_search": {}}  # Enables live real-time Google searching
            ]
        }

        try:
            response = requests.post(url, json=payload, headers=headers, timeout=15)
            if response.status_code == 200:
                data = response.json()
                reply_text = data['candidates'][0]['content']['parts'][0]['text'].strip()
                reply = f"J.A.R.V.I.S.: {reply_text}"
            else:
                reply = f"J.A.R.V.I.S.: Uplink error {response.status_code}. Sir, verify your API secret."
        except Exception:
            reply = "J.A.R.V.I.S.: Real-time satellite link unavailable. Check network connection, Sir."

        Clock.schedule_once(lambda dt: self.add_message(reply, is_user=False))


if __name__ == '__main__':
    JarvisApp().run()
