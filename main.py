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
from kivy.graphics import Color, RoundedRectangle
from kivy.clock import Clock
import datetime
import re
import random

# Configure window so keyboard pushes input field UP above Android navigation bar
Window.softinput_mode = 'below_target'

class ChatBubble(BoxLayout):
    def __init__(self, text, is_user=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_y = None
        self.size_hint_x = 0.85
        self.padding = [10, 8, 10, 8]
        self.pos_hint = {'right': 0.98} if is_user else {'x': 0.02}

        self.label = Label(
            text=text,
            color=(1, 1, 1, 1) if is_user else (0, 1, 0.9, 1),
            size_hint_y=None,
            halign='left',
            valign='top',
            font_size='14sp',
            markup=True
        )
        self.label.bind(width=lambda instance, val: setattr(instance, 'text_size', (val, None)))
        self.label.bind(texture_size=self._update_bubble_height)
        self.add_widget(self.label)

        bg_color = (0.0, 0.35, 0.45, 0.85) if is_user else (0.05, 0.15, 0.22, 0.85)
        with self.canvas.before:
            Color(*bg_color)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[8])
        self.bind(pos=self._update_rect, size=self._update_rect)

    def _update_bubble_height(self, instance, val):
        self.label.height = val[1]
        self.height = val[1] + 16

    def _update_rect(self, instance, val):
        self.rect.pos = self.pos
        self.rect.size = self.size


class JarvisApp(App):
    def build(self):
        root = FloatLayout()

        # ================= 1. FULL SCREEN BACKGROUND =================
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

        # ================= 2. LIVE OVERLAY DATA (TOP) =================
        # Live UTC Clock Overlay (Top Right)
        self.clock_label = Label(
            text="14:38:21 (UTC)\n2026.07.21",
            color=(0, 1, 1, 1),
            font_size='13sp',
            bold=True,
            halign='center',
            size_hint=(0.3, 0.1),
            pos_hint={'right': 0.98, 'top': 0.98}
        )
        root.add_widget(self.clock_label)

        # Live Suit Telemetry Overlay (Top Left)
        self.diag_label = Label(
            text="[color=00FFCC]POWER CORE: 98%\nSUIT TEMP: 37.5°C[/color]",
            markup=True,
            font_size='11sp',
            halign='left',
            size_hint=(0.35, 0.1),
            pos_hint={'x': 0.02, 'top': 0.98}
        )
        root.add_widget(self.diag_label)

        # ================= 3. CHAT AREA & INPUT CONTAINER =================
        # Main container positioned over the center/bottom of screen
        chat_container = BoxLayout(
            orientation='vertical',
            padding=[10, 10, 10, 10],
            spacing=8,
            size_hint=(1, 0.65),
            pos_hint={'x': 0, 'y': 0}
        )

        # Scrollable Chat History
        self.scroll_view = ScrollView(size_hint=(1, 1), do_scroll_x=False)
        self.chat_layout = GridLayout(cols=1, spacing=10, size_hint_y=None)
        self.chat_layout.bind(minimum_height=self.chat_layout.setter('height'))
        
        self.scroll_view.add_widget(self.chat_layout)
        chat_container.add_widget(self.scroll_view)

        # Welcome Message
        self.add_message("J.A.R.V.I.S.: A.R.M.O.R. Full HUD active. All feeds connected, Sir.", is_user=False)

        # Input Bar
        input_container = BoxLayout(
            orientation='horizontal',
            size_hint_y=None,
            height=50,
            spacing=6
        )

        self.input_field = TextInput(
            hint_text="Ask J.A.R.V.I.S. or enter math...",
            multiline=False,
            size_hint_x=0.78,
            background_color=(0.05, 0.12, 0.18, 0.9),
            foreground_color=(0, 1, 1, 1),
            cursor_color=(0, 1, 1, 1)
        )
        self.input_field.bind(on_text_validate=self.process_command)

        send_button = Button(
            text="SEND",
            size_hint_x=0.22,
            background_color=(0.0, 0.7, 0.9, 1),
            color=(1, 1, 1, 1),
            bold=True
        )
        send_button.bind(on_release=self.process_command)

        input_container.add_widget(self.input_field)
        input_container.add_widget(send_button)

        chat_container.add_widget(input_container)
        root.add_widget(chat_container)

        # Start HUD Clock Updates
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
        if isinstance(instance, Button):
            command = self.input_field.text.strip()
        else:
            command = instance.text.strip()

        if not command:
            return

        self.add_message(f"You: {command}", is_user=True)
        self.input_field.text = ""

        command_lower = command.lower()
        clean_math = re.sub(r'[^0-9\+\-\*\/\.\(\)\s]', '', command)
        
        if clean_math.strip() and any(op in clean_math for op in ['+', '-', '*', '/']):
            try:
                result = eval(clean_math, {"__builtins__": None}, {})
                reply = f"J.A.R.V.I.S.: Result = {result}"
            except Exception:
                reply = "J.A.R.V.I.S.: Invalid mathematical expression, Sir."
        elif "date" in command_lower:
            today = datetime.date.today().strftime("%B %d, %Y")
            reply = f"J.A.R.V.I.S.: Today's date is {today}, Sir."
        elif "time" in command_lower:
            now = datetime.datetime.now().strftime("%I:%M %p")
            reply = f"J.A.R.V.I.S.: Current time is {now}, Sir."
        elif "hello" in command_lower or "hi" in command_lower:
            reply = "J.A.R.V.I.S.: Greetings, Sir. Systems fully operational."
        else:
            reply = f"J.A.R.V.I.S.: Command logged: '{command}'"

        self.add_message(reply, is_user=False)


if __name__ == '__main__':
    JarvisApp().run()
        
