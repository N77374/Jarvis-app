from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.core.window import Window
from kivy.graphics import Color, RoundedRectangle

# Set background color for the window (Dark Theme: #121212)
Window.clearcolor = (0.07, 0.07, 0.07, 1)

class CustomInputBar(BoxLayout):
    """Custom chat bar containing text input and send button only"""
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'horizontal'
        self.size_hint_y = None
        self.height = 60
        self.padding = [15, 5, 10, 5]
        self.spacing = 10

        # Background styling for the input container bar
        with self.canvas.before:
            Color(0.15, 0.15, 0.15, 1) # Dark grey container
            self.rect = RoundedRectangle(size=self.size, pos=self.pos, radius=[30])
        self.bind(size=self._update_rect, pos=self._update_rect)

        # 1. Text Input Field
        self.text_input = TextInput(
            hint_text="Message Jarvis...",
            hint_text_color=(0.5, 0.5, 0.5, 1),
            background_color=(0, 0, 0, 0),
            foreground_color=(1, 1, 1, 1),
            cursor_color=(1, 1, 1, 1),
            multiline=False,
            size_hint=(1, None),
            height=45
        )

        # 2. Circular Blue Send (↑) Button
        self.send_btn = Button(
            text="↑", 
            size_hint=(None, None), 
            size=(45, 45),
            background_color=(0.1, 0.4, 0.9, 1), # Vibrant Blue
            color=(1, 1, 1, 1),
            font_size=22
        )
        # Make send button circular using canvas radius
        with self.send_btn.canvas.before:
            Color(0.1, 0.4, 0.9, 1)
            self.send_rect = RoundedRectangle(size=self.send_btn.size, pos=self.send_btn.pos, radius=[22.5])
        self.send_btn.bind(size=self._update_send_rect, pos=self._update_send_rect)

        # Add widgets to the horizontal input container layout
        self.add_widget(self.text_input)
        self.add_widget(self.send_btn)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def _update_send_rect(self, instance, value):
        self.send_rect.pos = instance.pos
        self.send_rect.size = instance.size

class JarvisApp(App):
    def build(self):
        root = BoxLayout(orientation='vertical', padding=15, spacing=10)

        # Chat display area header / conversation history placeholder
        self.chat_display = Label(
            text="[ Jarvis Initialized ]",
            color=(0.6, 0.6, 0.6, 1),
            halign='center',
            valign='middle'
        )
        root.add_widget(self.chat_display)

        # Instantiate our custom bottom input bar layout
        self.input_bar = CustomInputBar()
        
        # Bind send actions
        self.input_bar.send_btn.bind(on_press=self.send_message)
        
        root.add_widget(self.input_bar)
        return root

    def send_message(self, instance):
        text = self.input_bar.text_input.text.strip()
        if text:
            self.chat_display.text = f"You: {text}"
            self.input_bar.text_input.text = ""

if __name__ == '__main__':
    JarvisApp().run()
  
