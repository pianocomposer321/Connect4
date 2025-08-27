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
case class InvalidSessionID(id: SessionID) extends ResponseError(s"Invalid Session: ${id.toString()}")
case class InvalidPlayerID(id: PlayerID) extends ResponseError(s"Invalid Player: ${id.toString()}")
case class PlayerNotConnected(id: PlayerID) extends ResponseError(s"Player not connected: ${id.toString()}")


case class PlayerID(uuid: UUID)
case class SessionID(uuid: UUID)

object PlayerID {
  def newPlayerID = PlayerID(UUID.randomUUID())
}

object SessionID {
  def newSessionID = SessionID(UUID.randomUUID())
}

sealed trait Action
case class SendMessage(message: Message, recipients: Set[PlayerID] = Set.empty) extends Action
case class SendError(err: ResponseError, recipients: Set[PlayerID] = Set.empty) extends Action
case class CloseSession(session: SessionID) extends Action
// case class ReplaceSession(session: SessionID) extends Action
case class DisconnectPlayer(player: Player) extends Action
case class NilAction() extends Action


sealed class Message(val typ: String, val data: ujson.Value) {
  def toJson = ujson.Obj("ok" -> true, "message_type" -> typ, "data" -> data)
}
case class StateMessage(state: GameState) extends Message("state", writeJs(state))
case class AssignPlayerMessage(session: UUID, player: UUID, token: Token, state: GameState) extends Message("assign_player", ujson.Obj("session" -> session.toString(), "player" -> player.toString(), "token" -> token.toString, "state" -> writeJs(state)))


case class Command(val player: PlayerID, session: SessionID, command: String, args: Option[ujson.Value] = None)
case class CommandJSON(val player: UUID, session: UUID, command: String, args: Option[ujson.Value] = None) derives upickle.default.ReadWriter

object Command {
  def fromJson(json: CommandJSON) = Command(PlayerID(json.player), SessionID(json.session), json.command, json.args)
}

object Server extends cask.MainRoutes {
  private var game = Game()

  private var curSession = Session()
  private var sessions: Map[SessionID, Session] = Map(curSession.id -> curSession)
  private var playerConnections: Map[PlayerID, WsChannelActor] = Map.empty

  def newSession(channel: WsChannelActor) = {
    curSession = Session()
    sessions(curSession.id) = curSession
    playerConnections(curSession.playerOne.id) = channel
    sendJson(channel, AssignPlayerMessage(curSession.id.uuid, curSession.playerOne.id.uuid, Token.YELLOW, curSession.game.gameState).toJson)
    // curSession.connectPlayerOne(channel)
  }

  def playerConnected(player: PlayerID) = {
    playerConnections.get(player) match {
      case Some(channel) => true
      case None => false
    }
  }

  def handleCommand(text: String): Action = {
    val cmd = Try(upickle.default.read(text): CommandJSON) match {
      case Success(cmd) => Command.fromJson(cmd)
      case Failure(e) => return SendError(InvalidJson(text))
    }
    val session = sessions.get(cmd.session) match {
      case Some(session) => session
      case None => return SendError(InvalidSessionID(cmd.session))
    }

    session.handleCommand(cmd)
  }

  def connectPlayerOne(channel: WsChannelActor): Unit = {
    playerConnections(curSession.playerOne.id) = channel
    sendJson(channel, AssignPlayerMessage(curSession.id.uuid, curSession.playerOne.id.uuid, Token.YELLOW, curSession.game.gameState).toJson)
  }

  def connectPlayerTwo(channel: WsChannelActor): Unit = {
    playerConnections(curSession.playerTwo.id) = channel
    sendJson(channel, AssignPlayerMessage(curSession.id.uuid, curSession.playerTwo.id.uuid, Token.RED, curSession.game.gameState).toJson)
    curSession.game.startGame()
    sendJson(channel, StateMessage(curSession.game.gameState).toJson)
    val playerOneChannel = playerConnections(curSession.playerOne.id)
    sendJson(playerOneChannel, StateMessage(curSession.game.gameState).toJson)
  }

  def connectNewPlayer(channel: WsChannelActor): Unit = {
    if (!playerConnected(curSession.playerOne.id))
      connectPlayerOne(channel)
    else if (!playerConnected(curSession.playerTwo.id))
      connectPlayerTwo(channel)
    else newSession(channel)
  }

  def sendMessage(message: Message, recipients: Set[PlayerID], defaultChannel: WsChannelActor): Unit = {
    if (recipients.isEmpty) {
      sendJson(defaultChannel, message.toJson)
      return
    }

    recipients.foreach { recipient =>
      playerConnections.get(recipient) match {
        case Some(channel) => sendJson(channel, message.toJson)
        case None => sendJson(defaultChannel, InvalidPlayerID(recipient).toJson)
      }
    }
  }

  def sendError(err: ResponseError, recipients: Set[PlayerID], defaultChannel: WsChannelActor): Unit = {
    if (recipients.isEmpty) {
      sendJson(defaultChannel, err.toJson)
      return
    }

    recipients.foreach { recipient =>
      playerConnections.get(recipient) match {
        case Some(channel) => sendJson(channel, err.toJson)
        case None => sendJson(defaultChannel, InvalidPlayerID(recipient).toJson)
      }
    }
  }

  def disconnectPlayer(player: Player, defaultChannel: WsChannelActor): Unit = {
    val session = sessions.get(player.session) match {
      case Some(session) => session
      case None => {
        sendError(InvalidSessionID(player.session), Set(player.id), defaultChannel)
        return
      }
    }

    if (!playerConnected(session.playerOne.id) || !playerConnected(session.playerTwo.id)) {
      closeSession(session.id)
      return
    }

    playerConnections.remove(player.id)
    if (player.id == session.playerOne.id) {
      val channel = playerConnections(session.playerTwo.id)
      session.game = Game()
      sendJson(channel, AssignPlayerMessage(session.id.uuid, session.playerTwo.id.uuid, Token.YELLOW, session.game.gameState).toJson)

      session.playerOne = Player(Token.YELLOW, session.id, session.playerTwo.id)
      session.playerTwo = Player(Token.RED, session.id)
    } else {
      val channel = playerConnections(session.playerOne.id)
      session.game = Game()
      sendJson(channel, AssignPlayerMessage(session.id.uuid, session.playerOne.id.uuid, Token.YELLOW, session.game.gameState).toJson)

      session.playerTwo = Player(Token.RED, session.id)
    }
  }

  def closeSession(sessionId: SessionID): Unit = {
    sessions.remove(sessionId)
    if (curSession.id == sessionId) {
      curSession = Session()
      sessions(curSession.id) = curSession
    }
  }

  // def replaceSession(sessionId: SessionID): Unit = {
  //   val oldSession = sessions.remove(sessionId).get
  //   val newSession = Session(sessionId)
  //   sessions(sessionId) = newSession
  //
  //   // if (oldSession.playerOneConnected) {
  //   if (playerConnected(oldSession.playerOne.id)) {
  //     playerConnections(newSession.playerOne.id) = playerConnections(oldSession.playerOne.id)
  //     playerConnections.remove(oldSession.playerOne.id)
  //   } else if (playerConnected(oldSession.playerTwo.id)) {
  //     playerConnections(newSession.playerTwo.id) = playerConnections(oldSession.playerTwo.id)
  //     playerConnections.remove(oldSession.playerTwo.id)
  //   }
  //
  //   if (curSession.id == sessionId) {
  //     curSession = newSession
  //   }
  // }

  @cask.staticResources("/static")
  def static() = "."

  @cask.websocket("/websocket")
  def websocket(): cask.WsHandler =
    cask.WsHandler { channel =>
      connectNewPlayer(channel)

      cask.WsActor {
        case cask.Ws.Text(text) => handleCommand(text) match {
          case SendMessage(message, recipients) => sendMessage(message, recipients, channel)
          case SendError(err, recipients) => sendError(err, recipients, channel)
          case CloseSession(sessionId) => closeSession(sessionId)
          // case ReplaceSession(sessionId) => replaceSession(sessionId)
          case DisconnectPlayer(player) => disconnectPlayer(player, channel)
          case NilAction() => ()
        }
      }
    }

  initialize()
}
