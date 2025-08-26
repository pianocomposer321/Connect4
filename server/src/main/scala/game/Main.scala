package game

@main def main(): Unit = {
  var game = Game()
  game.placeToken(0, Token.YELLOW)
  println(game.getTurn)
  game.placeToken(0, Token.YELLOW)
  game.printBoard
}
