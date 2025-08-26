import pyray as pr
from typing import Callable


FONT_SIZE = 22
MARGIN = 12

class Button:
    text: str
    position: tuple[int, int]
    on_click: Callable[[], None]

    def __init__(self, text: str, position: tuple[int, int], on_click: Callable[[], None], color: pr.Color = pr.WHITE):
        self.text = text
        self.position = position
        self.on_click = on_click
        self.color = color

    def get_rect(self):
        return pr.Rectangle(self.position[0] - self.get_width() // 2, self.position[1] - self.get_height() // 2, self.get_width(), self.get_height())

    def update(self):
        if pr.is_mouse_button_pressed(pr.MouseButton.MOUSE_BUTTON_LEFT):
            point = (pr.get_mouse_x(), pr.get_mouse_y())
            rect = self.get_rect()

            if pr.check_collision_point_rec(point, rect):
                self.on_click()

    def text_width(self):
        return pr.measure_text(self.text, FONT_SIZE)

    def text_height(self):
        return pr.measure_text_ex(pr.get_font_default(), self.text, FONT_SIZE, 0).y

    def get_width(self):
        return int(self.text_width() + MARGIN * 2)

    def get_height(self):
        return int(self.text_height() + MARGIN * 2)

    def draw(self):
        pr.draw_rectangle_rec(self.get_rect(), self.color)
        text_x = self.position[0] - self.text_width() / 2
        text_y = self.position[1] - self.text_height() / 2
        pr.draw_text(self.text, int(text_x), int(text_y), FONT_SIZE, pr.BLACK)
