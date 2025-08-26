package server

import game.Game
import game.Token
import cask.endpoints.WsChannelActor
import scala.util.Try
import scala.util.Failure
import java.util.UUID
import scala.util.Success
import game.GameStage

case class PlayerConnection(val token: Token, var channel: Option[WsChannelActor] = None)


class Session(val id: UUID) {
  var game = Game()
  var closed = false

  var playerOneId = UUID.randomUUID()
  var playerOne = PlayerConnection(Token.YELLOW)
  var playerTwoId = UUID.randomUUID()
  var playerTwo = PlayerConnection(Token.RED)

  def playerOneConnected = playerOne.channel.isDefined
  def playerTwoConnected = playerTwo.channel.isDefined

  def connectPlayerOne(channel: WsChannelActor) = {
    sendJson(channel, AssignPlayerMessage(id, playerOneId, Token.YELLOW, game.gameState).toJson)
    playerOne.channel = Some(channel)
  }
  def connectPlayerTwo(channel: WsChannelActor) = {
    game.startGame()
    sendJson(channel, AssignPlayerMessage(id, playerTwoId, Token.RED, game.gameState).toJson)
    playerTwo.channel = Some(channel)
    playerOne.channel.foreach(channel => sendJson(channel, StateMessage(game.gameState).toJson))
  }

  def broadcast(msg: Message) = {
    playerOne.channel.foreach(channel => sendJson(channel, msg.toJson))
    playerTwo.channel.foreach(channel => sendJson(channel, msg.toJson))
  }

  def handleCommand(cmd: Command): Action = {
    cmd match {
      case Command(_, _, "state", args) => SendMessage(StateMessage(game.gameState))
      case Command(_, _, "place", args) => handlePlaceCmd(args)
      case Command(player, _, "close", _) => {

        if (player == playerOneId) {
          playerOne.channel = None
          return CloseSession(id)
        }
        playerTwo.channel = None
        return CloseSession(id)


        // if (!playerOneConnected || !playerTwoConnected) {
        //   closed = true
        //   return NilAction()
        // }
        //
        // // Create new game
        // game = Game()
        // if (player == playerOneId) {
        //   playerOne.channel = playerTwo.channel
        //   playerOneId = playerTwoId
        //   playerTwo.channel = None
        //   playerTwoId = UUID.randomUUID()
        //   playerOne.channel.foreach(channel => sendJson(channel, StateMessage(game.gameState).toJson))
        // } else {
        //   playerTwo.channel = None
        //   playerOne.channel.foreach(channel => sendJson(channel, StateMessage(game.gameState).toJson))
        // }
        //
        // NilAction()
      }
      case Command(_, _, command, _) => SendError(InvalidCommand(command))
    }
  }

  def handlePlaceCmd(maybe_args: Option[ujson.Value]): Action = {
    val args = maybe_args match {
      case Some(value) => value
      case None => return SendError(InvalidArguments("place", maybe_args))
    }

    // TODO: rewrite this
    Try(args.obj) match {
      case Success(args) => (Try(args("col").num.toInt), Try(args("token").str)) match {
        case (Success(col), Success("RED")) => game.placeToken(col, Token.RED) match {
          case Failure(err) => return SendError(GameError(err))
          case _ => ()
        }
        case (Success(col), Success("YELLOW")) => game.placeToken(col, Token.YELLOW) match {
          case Failure(err) => return SendError(GameError(err))
          case _ => ()
        }
        case _ => return SendError(InvalidArguments("place", Some(args)))
      }
      case Failure(_) => return SendError(InvalidArguments("place", Some(args)))
    }

    broadcast(StateMessage(game.gameState))
    NilAction()
  }
}
