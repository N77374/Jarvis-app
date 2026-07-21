from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
import datetime

class JarvisApp(App):
    def build(self):
        # Main layout
        main_layout = BoxLayout(orientation='vertical', padding=10, spacing=10)

        # Output label to display J.A.R.V.I.S. responses
        self.response_label = Label(
            text="J.A.R.V.I.S. Core Online\nHow may I assist you, Sir?",
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.response_label.bind(size=self.response_label.setter('text_size'))
        main_layout.add_widget(self.response_label)

        # Bottom horizontal container for input + button
        input_container = BoxLayout(orientation='horizontal', size_hint_y=None, height=50, spacing=5)

        # Text Input Box
        self.input_field = TextInput(
            hint_text="Type command here, Sir...",
            multiline=False,
            size_hint_x=0.75
        )
        # Allows pressing the keyboard Enter key to submit as well
        self.input_field.bind(on_text_validate=self.process_command)

        # Send Button
        send_button = Button(
            text="Send",
            size_hint_x=0.25,
            background_color=(0.2, 0.6, 1, 1)
        )
        send_button.bind(on_release=self.process_command)

        # Add input and button to bottom container
        input_container.add_widget(self.input_field)
        input_container.add_widget(send_button)

        # Add bottom container to main layout
        main_layout.add_widget(input_container)

        return main_layout

    def process_command(self, instance):
        command = self.input_field.text.strip()
        if not command:
            return

        command_lower = command.lower()
        self.input_field.text = ""  # Clear input box

        # Command responses
        if "date" in command_lower:
            today = datetime.date.today().strftime("%B %d, %Y")
            self.response_label.text = f"Today's date is {today}, Sir."
        elif "time" in command_lower:
            now = datetime.datetime.now().strftime("%I:%M %p")
            self.response_label.text = f"The current time is {now}, Sir."
        elif "hello" in command_lower or "hi" in command_lower:
            self.response_label.text = "Hello Sir. All systems are fully operational."
        else:
            self.response_label.text = f"Command received: '{command}'"

if __name__ == '__main__':
    JarvisApp().run()
        
