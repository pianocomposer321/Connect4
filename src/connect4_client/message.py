from abc import ABC
from typing import Optional
from dataclasses import dataclass


class Message(ABC):
    pass


@dataclass
class AssignPlayerMessage(Message):
    session: str
    player: str
    token: str
    board: list[list[Optional[str]]]
    turn: str

    @staticmethod
    def from_json(json_message: dict):
        return AssignPlayerMessage(
            json_message["session"],
            json_message["player"],
            json_message["token"],
            json_message["state"]["board"],
            json_message["state"]["turn"]
        )


@dataclass
class StateMessage(Message):
    board: list[list[Optional[str]]]
    turn: str

    @staticmethod
    def from_json(json_message: dict):
        return StateMessage(
            json_message["board"],
            json_message["turn"]
        )


@dataclass
class CloseMessage(Message):
    pass
