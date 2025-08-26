from typing import Any, Optional
import json

class Command:
    command: str
    args: Optional[dict[str, Any]]

    def __init__(self, command: str, args: Optional[dict[str, Any]]):
        self.command = command
        self.args = args

    def to_json(self, session: str, player: str):
        message: dict[str, Any] = {"session": session, "player": player, "command": self.command}

        if self.args is not None:
            message["args"] = self.args

        return json.dumps(message)


class StateCommand(Command):
    def __init__(self):
        super().__init__("state", None)


class PlaceCommand(Command):
    def __init__(self, col: int, token: str):
        super().__init__("place", {"col": col, "token": token})


class CloseCommand(Command):
    def __init__(self):
        super().__init__("close", None)


class NewGameCommand(Command):
    def __init__(self):
        super().__init__("new_game", None)
