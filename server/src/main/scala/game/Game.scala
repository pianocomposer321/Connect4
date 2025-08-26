package game

import scala.util.Try
import scala.util.Success
import scala.util.Failure

sealed class GameError(msg: String) extends Exception(msg)
case class GameNotStarted() extends GameError("Game has not started")
case class GameOver() extends GameError("Game is over")
case class TokenAlreadyPlaced(col: Int, row: Int) extends GameError(s"Token already placed in position $col, $row")
case class ColumnFull(col: Int) extends GameError(s"Column $col is full")
case class NotYourTurn() extends GameError("It is the other player's turn")

enum Token derives upickle.default.ReadWriter {
  override def toString: String = this match {
    case RED => "RED"
    case YELLOW => "YELLOW"
  }

  case RED
  case YELLOW
}

type Board = Array[Array[Option[Token]]]

enum GameStage derives upickle.default.ReadWriter {
  case NOT_STARTED
  case PLAYING
  case OVER
}

case class GameState(stage: GameStage, board: Board, turn: Token) derives upickle.default.ReadWriter

class Game {
  private var board: Board = Array.fill(Game.COLS)(Array.fill(Game.ROWS)(None))
  private var turn: Token = Token.YELLOW
  private var stage: GameStage = GameStage.NOT_STARTED

  def gameState = GameState(stage, board, turn)
  def getToken(col: Int, row: Int): Option[Token] = board(col)(row)
  def getTurn = turn

  def startGame() = {
    stage = GameStage.PLAYING
  }

  def placeToken(col: Int, token: Token): Try[Unit] = {
    if stage == GameStage.NOT_STARTED then {
      return Failure(GameNotStarted())
    } else if stage == GameStage.OVER then {
      return Failure(GameOver())
    }

    val ind = board(col).lastIndexWhere(token => token == None)
    if ind == -1 then {
      Failure(ColumnFull(col))
    } else if token != turn then {
      Failure(NotYourTurn())
    } else {
      board(col)(ind) = Some(token)
      turn = turn match {
        case Token.RED => Token.YELLOW
        case Token.YELLOW => Token.RED
      }
      Success(())
    }
  }

  def printBoard = {
    def printRow(row: Int) = {
      if row == 0 then {
        print("+")
        print("---+" * Game.COLS)
        println()
        print("|")
      } else {
        print("+")
        println("---+" * Game.COLS)
        print("|")
      }
      for col <- 0 until Game.COLS do {
        getToken(col, row) match
          case None => print("   |")
          case Some(token) => print(s" $token |")
      }
      println()
    }

    for row <- 0 until Game.ROWS do {
      printRow(row)
    }
    print("+")
    println("---+" * Game.COLS)
  }
}

object Game {
  val ROWS = 6
  val COLS = 7
}
