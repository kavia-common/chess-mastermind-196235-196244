package org.example.app.core.persistence

import android.content.Context
import org.example.app.core.CastlingRights
import org.example.app.core.ChessGame
import org.example.app.core.GameMode
import org.example.app.core.Piece
import org.example.app.core.PieceType
import org.example.app.core.PlayerColor
import org.example.app.core.Position
import org.example.app.core.Square

object GamePersistence {
    private const val PREFS = "chess_mastermind_prefs"
    private const val KEY_STATE = "last_game_state_v1"

    fun saveLastGame(context: Context, game: ChessGame) {
        val s = serialize(game)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, s)
            .apply()
    }

    fun loadLastGame(context: Context): ChessGame? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null) ?: return null
        return runCatching { deserialize(s) }.getOrNull()
    }

    /**
     * Format (single string):
     * mode|side|castling4|epOrDash|half|full|64chars
     *
     * 64chars uses: '.' empty, else piece letter:
     * White: KQ R B N P
     * Black: kq r b n p
     * rank1..rank8 (a1..h8) order (rank 1 first)
     */
    private fun serialize(game: ChessGame): String {
        val mode = game.mode.name
        val side = game.sideToMove.name
        val cr = game.position.castling
        val cast = "${b(cr.whiteKingSide)}${b(cr.whiteQueenSide)}${b(cr.blackKingSide)}${b(cr.blackQueenSide)}"
        val ep = game.position.enPassantTarget?.toAlgebraic() ?: "-"
        val half = game.position.halfmoveClock
        val full = game.position.fullmoveNumber

        val board = buildString(64) {
            for (r in 0..7) for (f in 0..7) {
                val p = game.position.pieceAt(Square(f, r))
                append(pieceToChar(p))
            }
        }

        // Captured pieces are optional; we keep them (simple) for UI.
        val capW = game.capturedBy(PlayerColor.WHITE).joinToString("") { pieceToChar(it).toString() }
        val capB = game.capturedBy(PlayerColor.BLACK).joinToString("") { pieceToChar(it).toString() }

        return listOf(mode, side, cast, ep, half.toString(), full.toString(), board, capW, capB).joinToString("|")
    }

    private fun deserialize(s: String): ChessGame {
        val parts = s.split("|")
        require(parts.size == 9) { "Bad state" }

        val mode = GameMode.valueOf(parts[0])
        val side = PlayerColor.valueOf(parts[1])

        val cast = parts[2]
        require(cast.length == 4)

        val cr = CastlingRights(
            whiteKingSide = cast[0] == '1',
            whiteQueenSide = cast[1] == '1',
            blackKingSide = cast[2] == '1',
            blackQueenSide = cast[3] == '1'
        )

        val ep = parts[3].let { if (it == "-") null else Square.fromAlgebraic(it) }
        val half = parts[4].toInt()
        val full = parts[5].toInt()

        val boardStr = parts[6]
        require(boardStr.length == 64)

        val board = Array<Piece?>(64) { null }
        for (i in 0 until 64) {
            board[i] = charToPiece(boardStr[i])
        }

        val pos = Position(board, cr, ep, half, full)

        val capW = parts[7].mapNotNull { charToPiece(it) }
        val capB = parts[8].mapNotNull { charToPiece(it) }

        return ChessGameRestorer.restore(mode, pos, side, capW, capB)
    }

    private fun b(v: Boolean) = if (v) '1' else '0'

    private fun pieceToChar(p: Piece?): Char {
        if (p == null) return '.'
        val c = when (p.type) {
            PieceType.KING -> 'k'
            PieceType.QUEEN -> 'q'
            PieceType.ROOK -> 'r'
            PieceType.BISHOP -> 'b'
            PieceType.KNIGHT -> 'n'
            PieceType.PAWN -> 'p'
        }
        return if (p.color == PlayerColor.WHITE) c.uppercaseChar() else c
    }

    private fun charToPiece(c: Char): Piece? {
        if (c == '.') return null
        val color = if (c.isUpperCase()) PlayerColor.WHITE else PlayerColor.BLACK
        val t = when (c.lowercaseChar()) {
            'k' -> PieceType.KING
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            'p' -> PieceType.PAWN
            else -> return null
        }
        return Piece(t, color)
    }
}
