package server

import game.Game
import game.GameState
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.collection.mutable.Map
import game.Token
import game.ColumnFull
import game.NotYourTurn
import upickle.default._
import java.util.UUID
import cask.endpoints.WsActor
import cask.endpoints.WsChannelActor
// import cask.router.Result.Success

def sendText(channel: cask.WsChannelActor, t: String) = {
  channel.send(cask.Ws.Text(t))
}

def sendJson[T: upickle.default.Writer](channel: cask.WsChannelActor, t: T) = {
  channel.send(cask.Ws.Text(upickle.default.write(t)))
}

sealed class ResponseError(msg: String) extends Exception(msg) {
  def toJson = ujson.Obj("ok" -> false, "reason" -> this.getMessage)
}
case class InvalidCommand(command: String) extends ResponseError(s"Invalid command: $command")
case class InvalidJson(text: String) extends ResponseError(s"Invalid json: $text")
case class InvalidArguments(command: String, args: Option[ujson.Value]) extends ResponseError(s"Invalid arguments to command $command: $args")
case class GameError(err: Throwable) extends ResponseError(err.getMessage)
case class InvalidSessionID(id: UUID) extends ResponseError(s"Invalid Session: ${id.toString()}")
case class InvalidPlayerID(id: UUID) extends ResponseError(s"Invalid Player: ${id.toString()}")
case class PlayerNotConnected(id: UUID) extends ResponseError(s"Player not connected: ${id.toString()}")


sealed trait CommandResponse
case class OkResponse(message: Message) extends CommandResponse
case class ErrResponse(err: ResponseError) extends CommandResponse
case class NilResponse() extends CommandResponse


sealed class Message(val typ: String, val data: ujson.Value) {
  def toJson = ujson.Obj("ok" -> true, "message_type" -> typ, "data" -> data)
}
case class StateMessage(state: GameState) extends Message("state", writeJs(state))
case class AssignPlayerMessage(session: UUID, player: UUID, token: Token, state: GameState) extends Message("assign_player", ujson.Obj("session" -> session.toString(), "player" -> player.toString(), "token" -> token.toString, "state" -> writeJs(state)))
case class CloseMessage() extends Message("close", ujson.Null)


case class Command(val player: UUID, session: UUID, command: String, args: Option[ujson.Value] = None) derives upickle.default.ReadWriter

object Server extends cask.MainRoutes {
  private var game = Game()
  // private var assignedYellow = false

  private var curSession = Session(UUID.randomUUID())
  private var sessions: Map[UUID, Session] = Map(curSession.id -> curSession)

  def newSession(channel: WsChannelActor) = {
    curSession = Session(UUID.randomUUID())
    sessions(curSession.id) = curSession
    curSession.connectPlayerOne(channel)
  }

  def handleCommand(text: String): CommandResponse = {
    val cmd = Try(upickle.default.read(text): Command) match {
      case Success(cmd) => cmd
      case Failure(e) => return ErrResponse(InvalidJson(text))
    }
    val session = sessions.get(cmd.session) match {
      case Some(session) => session
      case None => return ErrResponse(InvalidSessionID(cmd.session))
    }

    if (session.closed) {
      sessions.remove(session.id)
      ErrResponse(InvalidSessionID(cmd.session))
    } else {
      session.handleCommand(cmd)
    }
  }

  def connectNewPlayer(channel: WsChannelActor): Unit = {
    if (curSession.closed) {
      sessions.remove(curSession.id)
      newSession(channel)
      return
    }

    if (!curSession.playerOneConnected) {
      curSession.connectPlayerOne(channel)
    } else if (!curSession.playerTwoConnected) {
      curSession.connectPlayerTwo(channel)
    } else {
      newSession(channel)
    }
  }

  @cask.staticResources("/static")
  def static() = "."

  @cask.websocket("/websocket")
  def websocket(): cask.WsHandler =
    cask.WsHandler { channel =>
      connectNewPlayer(channel)

      cask.WsActor {
        case cask.Ws.Text(text) => handleCommand(text) match {
          case OkResponse(message) => sendJson(channel, message.toJson)
          case ErrResponse(err) => sendJson(channel, err.toJson)
          case NilResponse() => ()
        }
      }
    }

  initialize()
}
