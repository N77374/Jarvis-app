from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.video import Video
from kivy.graphics import Color, RoundedRectangle
from kivy.clock import Clock
import datetime
import re

# Custom Widget for Bubble Backgrounds
class ChatBubble(BoxLayout):
    def __init__(self, text, is_user=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_y = None
        self.size_hint_x = 0.8
        self.padding = [12, 8, 12, 8]
        
        # Position user bubbles on the right, J.A.R.V.I.S. on the left
        self.pos_hint = {'right': 0.98} if is_user else {'x': 0.02}

        # Create message text
        self.label = Label(
            text=text,
            color=(1, 1, 1, 1) if is_user else (0, 1, 0.9, 1),
            size_hint_y=None,
            halign='left',
            valign='top',
            font_size='15sp'
        )
        self.label.bind(width=lambda instance, value: setattr(instance, 'text_size', (value, None)))
        self.label.bind(texture_size=self._update_bubble_height)
        self.add_widget(self.label)

        # Bubble background colors
        bg_color = (0.0, 0.35, 0.45, 0.85) if is_user else (0.08, 0.12, 0.18, 0.9)
        
        with self.canvas.before:
            Color(*bg_color)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[12, 12, 12, 12])
        self.bind(pos=self._update_rect, size=self._update_rect)

    def _update_bubble_height(self, instance, value):
        self.label.height = value[1]
        self.height = value[1] + 16

    def _update_rect(self, instance, value):
        self.rect.pos = self.pos
        self.rect.size = self.size


class JarvisApp(App):
    def build(self):
        root = BoxLayout(orientation='vertical', padding=10, spacing=10)

        # 1. Video UI Header
        try:
            self.jarvis_video = Video(
                source='jarvis_ui.mp4',
                state='play',
                options={'eos': 'loop'},
                size_hint=(1, None),
                height=200,
                allow_stretch=True
            )
            root.add_widget(self.jarvis_video)
        except Exception:
            pass

        # 2. Scrollable Chatbox Container
        self.scroll_view = ScrollView(size_hint=(1, 1), do_scroll_x=False)
        self.chat_layout = GridLayout(cols=1, spacing=12, size_hint_y=None)
        self.chat_layout.bind(minimum_height=self.chat_layout.setter('height'))
        
        self.scroll_view.add_widget(self.chat_layout)
        root.add_widget(self.scroll_view)

        # Initial Welcome
        self.add_message("J.A.R.V.I.S.: Systems active. Online and ready, Sir.", is_user=False)

        # 3. Input Bar
        input_container = BoxLayout(orientation='horizontal', size_hint_y=None, height=50, spacing=5)

        self.input_field = TextInput(
            hint_text="Ask J.A.R.V.I.S. a question...",
            multiline=False,
            size_hint_x=0.75,
            background_color=(0.1, 0.15, 0.2, 1),
            foreground_color=(1, 1, 1, 1)
        )
        self.input_field.bind(on_text_validate=self.process_command)

        send_button = Button(
            text="Send",
            size_hint_x=0.25,
            background_color=(0.0, 0.6, 0.9, 1)
        )
        send_button.bind(on_release=self.process_command)

        input_container.add_widget(self.input_field)
        input_container.add_widget(send_button)

        root.add_widget(input_container)

        return root

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
        # Extracts math expressions like 2+2, 15 * 3, 100 / 5, etc.
        clean_math = re.sub(r'[^0-9\+\-\*\/\.\(\)\s]', '', command)
        
        if clean_math.strip() and any(op in clean_math for op in ['+', '-', '*', '/']):
            try:
                # Safely calculate math query
                result = eval(clean_math, {"__builtins__": None}, {})
                reply = f"J.A.R.V.I.S.: Calculation complete. Result: {result}"
            except Exception:
                reply = "J.A.R.V.I.S.: Unable to parse mathematical expression, Sir."
        elif "date" in command_lower:
            today = datetime.date.today().strftime("%B %d, %Y")
            reply = f"J.A.R.V.I.S.: Today's date is {today}, Sir."
        elif "time" in command_lower:
            now = datetime.datetime.now().strftime("%I:%M %p")
            reply = f"J.A.R.V.I.S.: Current time is {now}, Sir."
        elif "hello" in command_lower or "hi" in command_lower:
            reply = "J.A.R.V.I.S.: Greetings, Sir. How may I assist you?"
        else:
            reply = f"J.A.R.V.I.S.: Command logged: '{command}'"

        self.add_message(reply, is_user=False)

if __name__ == '__main__':
    JarvisApp().run()
        
