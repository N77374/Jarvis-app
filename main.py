from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.clock import Clock
import datetime

class JarvisApp(App):
    def build(self):
        # Root layout
        root = BoxLayout(orientation='vertical', padding=10, spacing=10)

        # 1. Scrollable Chat Container
        self.scroll_view = ScrollView(size_hint=(1, 1), do_scroll_x=False)
        
        # Grid layout inside ScrollView to hold chat messages
        self.chat_layout = GridLayout(cols=1, spacing=10, size_hint_y=None)
        self.chat_layout.bind(minimum_height=self.chat_layout.setter('height'))
        
        self.scroll_view.add_widget(self.chat_layout)
        root.add_widget(self.scroll_view)

        # Initial Welcome Message
        self.add_message("J.A.R.V.I.S.: Core Online. How may I assist you, Sir?", is_user=False)

        # 2. Bottom Input Bar
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
            background_color=(0.2, 0.6, 1, 1)
        )
        send_button.bind(on_release=self.process_command)

        input_container.add_widget(self.input_field)
        input_container.add_widget(send_button)

        root.add_widget(input_container)

        return root

    def add_message(self, text, is_user=False):
        """Adds a message bubble to the chat container."""
        align = "right" if is_user else "left"
        color = "00FFCC" if is_user else "FFFFFF"  # Cyan for User, White for J.A.R.V.I.S.
        
        lbl = Label(
            text=f"[color={color}]{text}[/color]",
            markup=True,
            size_hint_y=None,
            halign=align,
            valign="middle",
            font_size="16sp"
        )
        # Enable text wrapping and dynamic line height
        lbl.bind(width=lambda instance, value: setattr(instance, 'text_size', (value, None)))
        lbl.bind(texture_size=lambda instance, value: setattr(instance, 'height', value[1] + 10))
        
        self.chat_layout.add_widget(lbl)
        
        # Scroll automatically to the bottom on new message
        Clock.schedule_once(lambda dt: setattr(self.scroll_view, 'scroll_y', 0), 0.1)

    def process_command(self, instance):
        command = self.input_field.text.strip()
        if not command:
            return

        # Show user message in chat
        self.add_message(f"You: {command}", is_user=True)
        self.input_field.text = ""  # Clear input box

        command_lower = command.lower()

        # Generate J.A.R.V.I.S. response
        if "date" in command_lower:
            today = datetime.date.today().strftime("%B %d, %Y")
            reply = f"J.A.R.V.I.S.: Today's date is {today}, Sir."
        elif "time" in command_lower:
            now = datetime.datetime.now().strftime("%I:%M %p")
            reply = f"J.A.R.V.I.S.: The current time is {now}, Sir."
        elif "hello" in command_lower or "hi" in command_lower:
            reply = "J.A.R.V.I.S.: Hello Sir. All systems are fully operational."
        else:
            reply = f"J.A.R.V.I.S.: Command received: '{command}'"

        # Show response in chat
        self.add_message(reply, is_user=False)

if __name__ == '__main__':
    JarvisApp().run()
        
