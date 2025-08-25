import json
from typing import Optional
import pyray as pr
from connect4_client.command import CloseCommand, Command, PlaceCommand

from connect4_client.connection import Connection
from connect4_client.message import AssignPlayerMessage, CloseMessage, Message, StateMessage

ROWS = 6
COLUMNS = 7
CELL_SIZE = 100
GRID_WIDTH = CELL_SIZE * COLUMNS
GRID_HEIGHT = CELL_SIZE * ROWS

class Game:
    red_checker: pr.Texture
    yellow_checker: pr.Texture
    connection: Connection
    session: Optional[str]
    player: Optional[str]
    token: Optional[str]
    quit: bool

    def __init__(self):
        pr.init_window(GRID_WIDTH, GRID_HEIGHT, "Connect 4")
        pr.set_target_fps(60)

        self.connection = Connection("ws://localhost:8080/websocket", on_message=self._on_message)
        self.connection.start()

        self.red_checker = pr.load_texture("assets/checker_red.png")
        self.yellow_checker = pr.load_texture("assets/checker_yellow.png")

        self.quit = False

    def main_loop(self):
        while not self.quit:
            pr.begin_drawing()
            pr.clear_background(pr.BLACK)

            # if not self.connection.is_connected():
            #     pr.draw_text("Connecting...", 190, 200, 20, pr.VIOLET)
            # else:
            #     pr.draw_text("Connected", 190, 200, 20, pr.VIOLET)

            if pr.is_mouse_button_pressed(pr.MouseButton.MOUSE_BUTTON_LEFT):
                if self.token is None:
                    print("Not connected to server.")
                    return

                self.send_command(PlaceCommand(1, self.token))

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

    def _on_message(self, message: Message):
        match message:
            case AssignPlayerMessage(session, player, token, board, turn):
                print("Assign player")
                print(f"Session: {session}")
                print(f"Player: {player}")
                print(f"Token: {token}")
                print(f"Board: {board}")
                print(f"Turn: {turn}")

                self.session = session
                self.player = player
                self.token = token
            case StateMessage(board, turn):
                print("State")
                print(f"Board: {board}")
                print(f"Turn: {turn}")
            case CloseMessage():
                print("Connection closed")
                self.quit = True
            case _:
                print(f"Unknown message type: {message}")

    def send_command(self, command: Command):
        if self.session is None or self.player is None:
            print("Not connected to server.")
            return

        self.connection.send_message(command.to_json(self.session, self.player))
