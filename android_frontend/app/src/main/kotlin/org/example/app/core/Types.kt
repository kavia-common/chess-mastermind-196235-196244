package org.example.app.core

/**
 * Board square with 0-based file (0=a..7=h) and rank (0=1..7=8).
 */
data class Square(val file: Int, val rank: Int) {
    init {
        require(file in 0..7) { "file out of range" }
        require(rank in 0..7) { "rank out of range" }
    }

    fun toAlgebraic(): String = "${('a'.code + file).toChar()}${rank + 1}"

    companion object {
        fun fromAlgebraic(s: String): Square {
            require(s.length == 2) { "Invalid square: $s" }
            val f = s[0].lowercaseChar().code - 'a'.code
            val r = s[1].code - '1'.code
            return Square(f, r)
        }
    }
}

enum class PlayerColor {
    WHITE, BLACK;

    fun opposite(): PlayerColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

data class Piece(val type: PieceType, val color: PlayerColor) {
    fun toUnicode(): String {
        return when (color) {
            PlayerColor.WHITE -> when (type) {
                PieceType.KING -> "♔"
                PieceType.QUEEN -> "♕"
                PieceType.ROOK -> "♖"
                PieceType.BISHOP -> "♗"
                PieceType.KNIGHT -> "♘"
                PieceType.PAWN -> "♙"
            }
            PlayerColor.BLACK -> when (type) {
                PieceType.KING -> "♚"
                PieceType.QUEEN -> "♛"
                PieceType.ROOK -> "♜"
                PieceType.BISHOP -> "♝"
                PieceType.KNIGHT -> "♞"
                PieceType.PAWN -> "♟"
            }
        }
    }
}

enum class GameMode { PVP, VS_AI }
