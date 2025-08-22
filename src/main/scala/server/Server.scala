package server

import game.Game
import game.GameState
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import game.Token
import game.ColumnFull
import game.NotYourTurn
import upickle.default._
// import cask.router.Result.Success

def sendText(channel: cask.WsChannelActor, t: String) = {
  channel.send(cask.Ws.Text(t))
}

def sendJson[T: upickle.default.Writer](channel: cask.WsChannelActor, t: T) = {
  channel.send(cask.Ws.Text(upickle.default.write(t)))
}

sealed class ResponseError(msg: String) extends Exception(msg)
case class InvalidCommand(command: String) extends ResponseError(s"Invalid command: $command")
case class InvalidJson(text: String) extends ResponseError(s"Invalid json: $text")
case class InvalidArguments(command: String, args: Option[ujson.Value]) extends ResponseError(s"Invalid arguments to command $command: $args")
case class GameError(err: Throwable) extends ResponseError(err.getMessage)


sealed trait CommandResponse
case class OkResponse(message: Message) extends CommandResponse
case class ErrResponse(err: ResponseError) extends CommandResponse


sealed class Message(val typ: String, val data: ujson.Value) {
  def toJson = ujson.Obj("ok" -> true, "message_type" -> typ, "data" -> data)
}
case class StateMessage(state: GameState) extends Message("state", writeJs(state))
case class AssignPlayerMessage(player: String, state: GameState) extends Message("assign_player", ujson.Obj("player" -> player, "state" -> writeJs(state)))


def handleStateCmd(channel: cask.WsChannelActor, args: Option[ujson.Value], game: Game): CommandResponse = {
  OkResponse(StateMessage(game.gameState))
}

def handlePlaceCmd(channel: cask.WsChannelActor, maybe_args: Option[ujson.Value], game: Game): CommandResponse = {
  val args = maybe_args match {
    case Some(value) => value
    case None => return ErrResponse(InvalidArguments("place", maybe_args))
  }

  Try(args.obj) match {
    case Success(args) => (Try(args("col").num.toInt), Try(args("token").str)) match {
      case (Success(col), Success("RED")) => game.placeToken(col, Token.RED) match {
        case Failure(err) => return ErrResponse(GameError(err))
        case _ => ()
      }
      case (Success(col), Success("YELLOW")) => game.placeToken(col, Token.YELLOW) match {
        case Failure(err) => return ErrResponse(GameError(err))
        case _ => ()
      }
      case _ => return ErrResponse(InvalidArguments("place", Some(args)))
    }
    case Failure(_) => return ErrResponse(InvalidArguments("place", Some(args)))
  }

  OkResponse(StateMessage(game.gameState))
}

case class Command(command: String, args: Option[ujson.Value] = None) derives upickle.default.ReadWriter

object Server extends cask.MainRoutes {
  private var game = Game()
  private var assignedYellow = false

  @cask.staticResources("/static")
  def static() = "."

  @cask.websocket("/websocket")
  def websocket(): cask.WsHandler =
    cask.WsHandler { channel =>
      var player = "RED"
      if (!assignedYellow) {
        player = "YELLOW"
        assignedYellow = true
      }
      // sendJson(channel, ujson.Obj("ok" -> true, "state" -> writeJs(game.gameState), "player" -> player))
      sendJson(channel, AssignPlayerMessage(player, game.gameState).toJson)
      cask.WsActor {
        case cask.Ws.Text(text) => {
          val res = Try(upickle.default.read(text): Command) match {
            case Success(command) => command match {
              case Command("state", args) => handleStateCmd(channel, args, game)
              case Command("place", args) => handlePlaceCmd(channel, args, game)
              case Command(command, _) => ErrResponse(InvalidCommand(command))
            }
            case Failure(e) => ErrResponse(InvalidJson(text))
          }
          // sendResult(channel, res)
          res match {
            case OkResponse(message) => {
              sendJson(channel, message.toJson)
            }
            case ErrResponse(err) => sendJson(channel, ujson.Obj("ok" -> false, "reason" -> err.getMessage)) }
        }
      }
    }

  initialize()
}
