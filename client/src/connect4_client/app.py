import json
from typing import Optional
import os

import pyray as pr
from connect4_client.button import Button
from connect4_client.command import CloseCommand, Command, NewGameCommand, PlaceCommand

from connect4_client.connection import Connection
from connect4_client.message import AssignPlayerMessage, Message, StateMessage

ROWS = 6
COLUMNS = 7
CELL_SIZE = 100
GRID_WIDTH = CELL_SIZE * COLUMNS
GRID_HEIGHT = CELL_SIZE * ROWS
PADDING = 10

IP_ADDR = os.environ.get("CONNECT4_IP_ADDR", "localhost")
PORT = os.environ.get("CONNECT4_PORT", 8080)

class Game:
    stage: str
    board: list[list[Optional[str]]]
    red_checker: pr.Texture
    yellow_checker: pr.Texture
    connection: Connection
    session: Optional[str]
    player: Optional[str]
    token: Optional[str]
    my_turn: bool
    quit: bool

    play_again_button: Button
    quit_button: Button

    def __init__(self):
        pr.init_window(GRID_WIDTH, GRID_HEIGHT, "Connect 4")
        pr.set_target_fps(60)

        self.stage = "NOT_STARTED"
        self.board = [[None for _ in range(ROWS)] for _ in range(COLUMNS)]

        self.connection = Connection(f"ws://{IP_ADDR}:{PORT}/websocket", on_message=self._on_message)
        self.connection.start()

        self.red_checker = pr.load_texture("assets/checker_red.png")
        self.yellow_checker = pr.load_texture("assets/checker_yellow.png")

        self.session = None
        self.token = None
        self.my_turn = False
        self.quit = False

        play_again_button_pos = (GRID_WIDTH // 3, GRID_HEIGHT * 2 // 3)
        self.play_again_button = Button("Play again", play_again_button_pos, lambda: self.send_command(NewGameCommand()), pr.GREEN)
        quit_button_pos = (GRID_WIDTH * 2 // 3, GRID_HEIGHT * 2 // 3)
        self.quit_button = Button("Quit", quit_button_pos, self.do_quit, pr.RED)

    def do_quit(self):
        self.quit = True

    def main_loop(self):
        while not self.quit:
            self.update()

            pr.begin_drawing()
            self.draw()
            pr.end_drawing()

            if pr.window_should_close():
                self.quit = True

    def end(self):
        pr.unload_texture(self.red_checker)
        pr.unload_texture(self.yellow_checker)
        pr.close_window()

        if self.session is None:
            print("Not connected to server.")
            return

        self.send_command(CloseCommand())

    def get_mouse_column(self):
        return int(pr.get_mouse_x() / CELL_SIZE)

    def update(self):
        # if self.stage != "PLAYING":
        if self.stage == "NOT_STARTED":
            return

        if self.stage == "WON":
            self.play_again_button.update()
            self.quit_button.update()
            return

        if pr.is_mouse_button_pressed(pr.MouseButton.MOUSE_BUTTON_LEFT) and self.my_turn:
            if self.token is None:
                print("Not connected to server.")
                return

            self.send_command(PlaceCommand(self.get_mouse_column(), self.token))

    def draw_shadow(self):
        column = self.get_mouse_column()
        row_ind = 0
        for ind, row in enumerate(self.board[column]):
            if row is not None:
                row_ind = ind - 1
                break
        else:
            row_ind = ROWS - 1

        if self.token == "RED":
            pr.draw_texture(self.red_checker, column * CELL_SIZE, CELL_SIZE * row_ind, pr.Color(255, 255, 255, 150))
        elif self.token == "YELLOW":
            pr.draw_texture(self.yellow_checker, column * CELL_SIZE, CELL_SIZE * row_ind, pr.Color(255, 255, 255, 150))

    def draw_grid(self):
        for x, column in enumerate(self.board):
            for y, token in enumerate(column):
                if token is None:
                    continue

                if token == "RED":
                    pr.draw_texture(self.red_checker, x * CELL_SIZE, CELL_SIZE * y, pr.WHITE)
                elif token == "YELLOW":
                    pr.draw_texture(self.yellow_checker, x * CELL_SIZE, CELL_SIZE * y, pr.WHITE)

    def draw(self):
        pr.clear_background(pr.Color(0x18, 0x18, 0x18, 0xff))

        if self.stage == "NOT_STARTED":
            text_width = pr.measure_text("Not started", 36)
            pr.draw_text("Not started", int((GRID_WIDTH - text_width) / 2), int(GRID_HEIGHT * 2 / 5), 36, pr.WHITE)
            text_width = pr.measure_text("Waiting for player 2 to join", 24)
            pr.draw_text("Waiting for player 2 to join", int((GRID_WIDTH - text_width) / 2), int(GRID_HEIGHT / 2), 24, pr.WHITE)
            return

        if self.stage == "WON":
            text_width = pr.measure_text("Game over", 36)
            # text_width = pr.measure_text_ex(pr.get_font_default(), "Game over", 36, 0).x
            pr.draw_text("Game over", int((GRID_WIDTH - text_width) / 2), int(GRID_HEIGHT * 2 / 5), 36, pr.WHITE)
            if self.my_turn:
                text_width = pr.measure_text("You won!", 24)
                pr.draw_text("You won!", int((GRID_WIDTH - text_width) / 2), int(GRID_HEIGHT / 2), 24, pr.WHITE)
            else:
                text_width = pr.measure_text("You lost!", 24)
                pr.draw_text("You lost!", int((GRID_WIDTH - text_width) / 2), int(GRID_HEIGHT / 2), 24, pr.WHITE)

            self.play_again_button.draw()
            self.quit_button.draw()
            return

        self.draw_grid()
        if self.my_turn:
            self.draw_shadow()

    def _on_message(self, message: Message):
        match message:
            case AssignPlayerMessage(session, player, token, stage, board, turn):
                self.session = session
                self.player = player
                self.token = token
                self.stage = stage
                self.board = board
                self.my_turn = turn == self.token
                print(f"Assigned player {player} to session {session} with token {token}.")
            case StateMessage(stage, board, turn):
                self.stage = stage
                self.board = board
                self.my_turn = turn == self.token
            case _:
                print(f"Unknown message type: {message}")

    def send_command(self, command: Command):
        if self.session is None or self.player is None:
            print("Not connected to server.")
            return

        self.connection.send_message(command.to_json(self.session, self.player))
