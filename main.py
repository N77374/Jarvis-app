from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.widget import Widget
from kivy.graphics import Color, Line, Ellipse
from kivy.properties import NumericProperty
from kivy.clock import Clock
import datetime
import math

# --- 1. J.A.R.V.I.S. Holographic Arc Reactor (Canvas Widget) ---
class JarvisHologram(Widget):
    angle_outer = NumericProperty(0)
    angle_inner = NumericProperty(0)
    pulse_brightness = NumericProperty(0.8)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.bind(pos=self.update_canvas, size=self.update_canvas)
        self.bind(angle_outer=self.update_canvas, angle_inner=self.update_canvas, pulse_brightness=self.update_canvas)
        self.pulse_dir = 1
        
        # Animate rotation and breathing pulse
        Clock.schedule_interval(self.animate_hologram, 1/30.0)

    def animate_hologram(self, dt):
        # Rotate outer ring clockwise, inner ring counter-clockwise
        self.angle_outer = (self.angle_outer + 1.5) % 360
        self.angle_inner = (self.angle_inner - 2.5) % 360

        # Breathing glow effect
        self.pulse_brightness += 0.01 * self.pulse_dir
        if self.pulse_brightness >= 1.0:
            self.pulse_dir = -1
        elif self.pulse_brightness <= 0.4:
            self.pulse_dir = 1

    def update_canvas(self, *args):
        self.canvas.clear()
        cx, cy = self.center_x, self.center_y
        b = self.pulse_brightness

        with self.canvas:
            # Core Center Glow
            Color(0.0, 0.9, 1.0, b * 0.9)
            Ellipse(pos=(cx - 20, cy - 20), size=(40, 40))

            # Inner Dashed Ring (Counter-Clockwise)
            Color(0.0, 0.8, 1.0, b)
            Line(circle=(cx, cy, 45, self.angle_inner, self.angle_inner + 260), width=2)
            Line(circle=(cx, cy, 55, self.angle_inner + 90, self.angle_inner + 180), width=1.5)

            # Outer Dashed Ring (Clockwise)
            Color(0.0, 1.0, 0.8, b * 0.7)
            Line(circle=(cx, cy, 75, self.angle_outer, self.angle_outer + 120), width=2)
            Line(circle=(cx, cy, 75, self.angle_outer + 180, self.angle_outer + 300), width=2)

            # Static Outer Boundary Circle
            Color(0.0, 0.6, 0.8, 0.3)
            Line(circle=(cx, cy, 85), width=1)


# --- 2. Main Application Interface ---
class JarvisApp(App):
    def build(self):
        root = BoxLayout(orientation='vertical', padding=10, spacing=10)

        # 1. Holographic Engine (Fixed Height)
        self.hologram = JarvisHologram(size_hint_y=None, height=180)
        root.add_widget(self.hologram)

        # 2. Chatbox ScrollView
        self.scroll_view = ScrollView(size_hint=(1, 1), do_scroll_x=False)
        self.chat_layout = GridLayout(cols=1, spacing=10, size_hint_y=None)
        self.chat_layout.bind(minimum_height=self.chat_layout.setter('height'))
        
        self.scroll_view.add_widget(self.chat_layout)
        root.add_widget(self.scroll_view)

        # Welcome Message
        self.add_message("J.A.R.V.I.S.: Arc Reactor core online. Systems nominal.", is_user=False)

        # 3. Input & Send Bar
        input_container = BoxLayout(orientation='horizontal', size_hint_y=None, height=50, spacing=5)

        self.input_field = TextInput(
            hint_text="Type command here, Sir...",
            multiline=False,
            size_hint_x=0.75
        )
        self.input_field.bind(on_text_validate=self.process_command)

        send_button = Button(
            text="Send",
            size_hint_x=0.25,
            background_color=(0.0, 0.7, 1.0, 1)
        )
        send_button.bind(on_release=self.process_command)

        input_container.add_widget(self.input_field)
        input_container.add_widget(send_button)

        root.add_widget(input_container)

        return root

    def add_message(self, text, is_user=False):
        align = "right" if is_user else "left"
        color = "00FFCC" if is_user else "FFFFFF"
        
        lbl = Label(
            text=f"[color={color}]{text}[/color]",
            markup=True,
            size_hint_y=None,
            halign=align,
            valign="middle",
            font_size="16sp"
        )
        lbl.bind(width=lambda instance, value: setattr(instance, 'text_size', (value, None)))
        lbl.bind(texture_size=lambda instance, value: setattr(instance, 'height', value[1] + 10))
        
        self.chat_layout.add_widget(lbl)
        Clock.schedule_once(lambda dt: setattr(self.scroll_view, 'scroll_y', 0), 0.1)

    def process_command(self, instance):
        command = self.input_field.text.strip()
        if not command:
            return

        self.add_message(f"You: {command}", is_user=True)
        self.input_field.text = ""

        command_lower = command.lower()

        if "date" in command_lower:
            today = datetime.date.today().strftime("%B %d, %Y")
            reply = f"J.A.R.V.I.S.: Today's date is {today}, Sir."
        elif "time" in command_lower:
            now = datetime.datetime.now().strftime("%I:%M %p")
            reply = f"J.A.R.V.I.S.: The current time is {now}, Sir."
        elif "hello" in command_lower or "hi" in command_lower:
            reply = "J.A.R.V.I.S.: Greetings Sir. All diagnostics green."
        else:
            reply = f"J.A.R.V.I.S.: Command received: '{command}'"

        self.add_message(reply, is_user=False)

if __name__ == '__main__':
    JarvisApp().run()
        
