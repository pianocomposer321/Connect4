import threading
import pyray as pr
import websocket
from connect4_client.app import Game

from connect4_client.connection import Connection

def main():
    app = Game()
    app.main_loop()
    app.end()

if __name__ == '__main__':
    main()
