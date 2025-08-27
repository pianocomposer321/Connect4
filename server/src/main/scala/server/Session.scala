package server

import game.Game
import game.Token
import cask.endpoints.WsChannelActor
import scala.util.Try
import scala.util.Failure
import java.util.UUID
import scala.util.Success
import game.GameStage

case class Player(val token: Token, var session: SessionID = SessionID.newSessionID, var id: PlayerID = PlayerID.newPlayerID)


class Session(val id: SessionID = SessionID.newSessionID) {
  var game = Game()

  var playerOne = Player(Token.YELLOW, id)
  var playerTwo = Player(Token.RED, id)

  def broadcast(msg: Message): Action = SendMessage(msg, Set(playerOne.id, playerTwo.id))

  def handleCommand(cmd: Command): Action = {
    cmd match {
      case Command(_, _, "state", args) => SendMessage(StateMessage(game.gameState))
      case Command(_, _, "place", args) => handlePlaceCmd(args)
      case Command(playerId, _, "close", _) => {
        val player = if playerId == playerOne.id then playerOne else playerTwo
        DisconnectPlayer(player)
      }
      case Command(_, _, "new_game", _) => {
        if (game.gameState.stage == GameStage.PLAYING) {
          return SendError(InvalidCommand("new_game"))
        }

        game = Game()
        game.startGame()
        broadcast(StateMessage(game.gameState))
      }
      case Command(_, _, command, _) => SendError(InvalidCommand(command))
    }
  }

  def handlePlaceCmd(maybe_args: Option[ujson.Value]): Action = {
    val args = maybe_args match {
      case Some(value) => value
      case None => return SendError(InvalidArguments("place", maybe_args))
    }
    val args_obj = Try(args.obj) match {
      case Success(value) => value
      case Failure(_) => return SendError(InvalidArguments("place", Some(args)))
    }
    val col = Try(args_obj("col").num.toInt) match {
      case Success(value) => value
      case Failure(_) => return SendError(InvalidArguments("place", Some(args)))
    }
    val token = Try(args_obj("token").str) match {
      case Success(value) => value
      case Failure(_) => return SendError(InvalidArguments("place", Some(args)))
    }

    token match {
      case "RED" => game.placeToken(col, Token.RED) match {
        case Failure(err) => return SendError(GameError(err))
        case _ => ()
      }
      case "YELLOW" => game.placeToken(col, Token.YELLOW) match {
        case Failure(err) => return SendError(GameError(err))
        case _ => ()
      }
      case _ => return SendError(InvalidArguments("place", Some(args)))
    }
    broadcast(StateMessage(game.gameState))
  }
}
