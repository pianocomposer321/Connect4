package server

import game.Game
import game.Token
import cask.endpoints.WsChannelActor

case class Player(val token: Token, var channel: Option[WsChannelActor] = None)

class Session(var channel: WsChannelActor) {
  var game = Game()
  val playerOne = Player(Token.YELLOW, Some(channel))
  var playerTwo = Player(Token.RED)
}
