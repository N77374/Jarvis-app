from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.image import Image
from kivy.graphics import Color, RoundedRectangle, Line
from kivy.clock import Clock
import datetime
import re
import random

# --- Styled Dark Cyan Panels ---
class HUDPanel(BoxLayout):
    def __init__(self, title="", **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = [10, 8, 10, 8]
        self.spacing = 5

        # Glowing Border Canvas Effect
        with self.canvas.before:
            Color(0.0, 0.15, 0.25, 0.75)  # Dark Translucent Cyan Background
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[8])
            Color(0.0, 0.8, 1.0, 0.6)     # Cyan Neon Border
            self.border = Line(rounded_rectangle=(self.x, self.y, self.width, self.height, 8), width=1.2)

        self.bind(pos=self._update_graphics, size=self._update_graphics)

        if title:
            header = Label(
                text=f"[b][color=00FFFF]{title}[/color][/b]",
                markup=True,
                size_hint_y=None,
                height=22,
                font_size='13sp',
                halign='left'
            )
            header.bind(size=header.setter('text_size'))
            self.add_widget(header)

    def _update_graphics(self, *args):
        self.rect.pos = self.pos
        self.rect.size = self.size
        self.border.rounded_rectangle = (self.x, self.y, self.width, self.height, 8)


# --- Styled Chat Bubbles ---
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

        bg_color = (0.0, 0.35, 0.45, 0.85) if is_user else (0.05, 0.15, 0.22, 0.9)
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


# --- Main A.R.M.O.R. HUD App ---
class JarvisApp(App):
    def build(self):
        root = BoxLayout(orientation='vertical', padding=8, spacing=8)

        # ================= 1. TOP HEADER & HUD GRID =================
        top_hud = GridLayout(cols=3, size_hint_y=None, height=210, spacing=8)

        # LEFT PANEL: Mission Log & System Diagnostics
        left_box = BoxLayout(orientation='vertical', spacing=6)
        
        self.mission_panel = HUDPanel(title="MISSION LOG")
        self.mission_log = Label(
            text="[color=00FFCC]LOCATION: NEW YORK SECTOR 7\nSTATUS: MK7 ONLINE\nSYSTEM: A.R.M.O.R. v1.2[/color]",
            markup=True, font_size='11sp', halign='left'
        )
        self.mission_log.bind(size=self.mission_log.setter('text_size'))
        self.mission_panel.add_widget(self.mission_log)

        self.diag_panel = HUDPanel(title="SYSTEM DIAGNOSTICS")
        self.diag_label = Label(
            text="[color=00FFCC]POWER CORE: 98%\nARMOR INTEGRITY: 99%\nSUIT TEMP: 37.5°C[/color]",
            markup=True, font_size='11sp', halign='left'
        )
        self.diag_label.bind(size=self.diag_label.setter('text_size'))
        self.diag_panel.add_widget(self.diag_label)

        left_box.add_widget(self.mission_panel)
        left_box.add_widget(self.diag_panel)
        top_hud.add_widget(left_box)

        # CENTER PANEL: Holographic Mask Image
        center_box = HUDPanel()
        try:
            self.hud_center = Image(
                source='hud_face.png',
                allow_stretch=True,
                keep_ratio=True
            )
            center_box.add_widget(self.hud_center)
        except Exception:
            center_box.add_widget(Label(text="[color=00FFFF]A.R.M.O.R. CORE[/color]", markup=True))
        
        top_hud.add_widget(center_box)

        # RIGHT PANEL: Digital UTC Clock & MK7 Schema
        right_box = BoxLayout(orientation='vertical', spacing=6)

        self.clock_panel = HUDPanel(title="TACTICAL TIME")
        self.clock_label = Label(
            text="14:38:21 (UTC)\nDATE: 2026.07.21",
            color=(0, 1, 1, 1), font_size='12sp', bold=True, halign='center'
        )
        self.clock_panel.add_widget(self.clock_label)

        self.schema_panel = HUDPanel(title="MK7 SCHEMA")
        self.schema_label = Label(
            text="[color=00FFCC]FLIGHT MODULE: ACTIVE\nREPULSOR BAY: READY\nSTABILIZERS: OPTIMAL[/color]",
            markup=True, font_size='11sp', halign='left'
        )
        self.schema_label.bind(size=self.schema_label.setter('text_size'))
        self.schema_panel.add_widget(self.schema_label)

        right_box.add_widget(self.clock_panel)
        right_box.add_widget(self.schema_panel)
        top_hud.add_widget(right_box)

        root.add_widget(top_hud)

        # ================= 2. CHAT HISTORY VIEW =================
        self.scroll_view = ScrollView(size_hint=(1, 1), do_scroll_x=False)
        self.chat_layout = GridLayout(cols=1, spacing=10, size_hint_y=None)
        self.chat_layout.bind(minimum_height=self.chat_layout.setter('height'))
        
        self.scroll_view.add_widget(self.chat_layout)
        root.add_widget(self.scroll_view)

        # Initial System Boot Message
        self.add_message("J.A.R.V.I.S.: A.R.M.O.R. Tactical HUD online. All diagnostic feeds nominal, Sir.", is_user=False)

        # ================= 3. INPUT COMMAND BAR =================
        input_container = BoxLayout(orientation='horizontal', size_hint_y=None, height=45, spacing=6)

        self.input_field = TextInput(
            hint_text="Enter tactical command or math equation...",
            multiline=False,
            size_hint_x=0.78,
            background_color=(0.05, 0.12, 0.18, 1),
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

        root.add_widget(input_container)

        # Start Clock Updates (Every 1 Second)
        Clock.schedule_interval(self.update_tactical_hud, 1.0)

        return root

    def update_tactical_hud(self, dt):
        """Updates live clock and telemetry numbers continuously."""
        now = datetime.datetime.now()
        time_str = now.strftime("%H:%M:%S (LOCAL)\nDATE: %Y.%m.%d")
        self.clock_label.text = time_str

        # Subtle dynamic telemetry variations to make it feel alive
        temp = round(37.5 + random.uniform(-0.2, 0.3), 1)
        power = random.choice([98, 98, 99])
        self.diag_label.text = f"[color=00FFCC]POWER CORE: {power}%\nARMOR INTEGRITY: 99%\nSUIT TEMP: {temp}°C[/color]"

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

        # Math Processing Logic
        clean_math = re.sub(r'[^0-9\+\-\*\/\.\(\)\s]', '', command)
        
        if clean_math.strip() and any(op in clean_math for op in ['+', '-', '*', '/']):
            try:
                result = eval(clean_math, {"__builtins__": None}, {})
                reply = f"J.A.R.V.I.S.: Tactical Calculation: {clean_math.strip()} = {result}"
            except Exception:
                reply = "J.A.R.V.I.S.: Invalid mathematical expression, Sir."
        elif "status" in command_lower or "diagnostic" in command_lower:
            reply = "J.A.R.V.I.S.: All armor systems operational. Power Core vortex stable at 98%."
        elif "date" in command_lower:
            today = datetime.date.today().strftime("%B %d, %Y")
            reply = f"J.A.R.V.I.S.: Current date is {today}, Sir."
        elif "time" in command_lower:
            now = datetime.datetime.now().strftime("%I:%M %p")
            reply = f"J.A.R.V.I.S.: Local time is {now}, Sir."
        elif "hello" in command_lower or "hi" in command_lower:
            reply = "J.A.R.V.I.S.: Greetings, Sir. Tactical operating system ready."
        else:
            reply = f"J.A.R.V.I.S.: Command received and logged: '{command}'"

        self.add_message(reply, is_user=False)


if __name__ == '__main__':
    JarvisApp().run()
        
