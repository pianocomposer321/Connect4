import json
import threading
import queue
from typing import Any, Callable, Optional
import websocket

from connect4_client.message import AssignPlayerMessage, CloseMessage, Message, StateMessage

class Connection:
    uri: str
    ws: Optional[websocket.WebSocket]
    on_message: Optional[Callable[[Message], None]]
    message_queue: queue.Queue[str]

    def __init__(self, uri, on_message: Callable[[Message], None] | None = None):
        self.uri = uri
        self.ws = None
        self.on_message = on_message
        self.message_queue = queue.Queue()

    def _start_ws(self):
        def on_open(ws):
            self.ws = ws

        def on_close(ws, *args):
            self.ws = None
            

        def on_message(ws, message):
            json_message = json.loads(message)
            if not json_message["ok"]:
                print(f"Received message with error: {json_message['reason']}")
                return

            if not self.on_message:
                return

            match json_message["message_type"]:
                case "assign_player":
                    self.on_message(AssignPlayerMessage.from_json(json_message["data"]))
                case "state":
                    self.on_message(StateMessage.from_json(json_message["data"]))
                case "close":
                    self.on_message(CloseMessage())
                case _:
                    print(f"Unknown message type: {json_message['type']}")


        # on_message = lambda _, message: self.on_message(message) if self.on_message else None

        ws_app = websocket.WebSocketApp(
            self.uri,
            on_open=on_open,
            on_message=on_message,
            on_close=on_close
        )
        ws_app.run_forever()

    def _start_listener(self):
        while True:
            message = self.message_queue.get()
            if self.ws:
                self.ws.send(message)

    def start(self):
        ws_thread = threading.Thread(target=self._start_ws, daemon=True)
        ws_thread.start()

        listener_thread = threading.Thread(target=self._start_listener, daemon=True)
        listener_thread.start()

    def send_message(self, message: str):
        if self.ws:
            self.ws.send(message)
        else:
            print("Not connected to server.")

    def is_connected(self):
        return self.ws is not None
