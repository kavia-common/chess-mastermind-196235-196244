package org.example.app.core

import org.example.app.core.move.Move

class ChessGame internal constructor(
    val mode: GameMode,
    internal var _position: Position,
    internal var _sideToMove: PlayerColor,
    private val history: MutableList<HistoryEntry>,
    private val capturedWhite: MutableList<Piece>,
    private val capturedBlack: MutableList<Piece>
) {

    data class HistoryEntry(
        val move: Move,
        val positionBefore: Position,
        val sideToMoveBefore: PlayerColor,
        val capturedPiece: Piece?
    )

    val position: Position get() = _position
    val sideToMove: PlayerColor get() = _sideToMove
    val moveHistory: List<Move> get() = history.map { it.move }

    val isCheckmate: Boolean
        get() = Rules.isKingInCheck(_position, _sideToMove) && legalMoves().isEmpty()

    val isStalemate: Boolean
        get() = !Rules.isKingInCheck(_position, _sideToMove) && legalMoves().isEmpty()

    val isGameOver: Boolean
        get() = isCheckmate || isStalemate

    val winner: PlayerColor?
        get() = if (isCheckmate) _sideToMove.opposite() else null

    fun legalMoves(): List<Move> = Rules.generateLegalMoves(_position, _sideToMove)

    fun legalMovesFrom(from: Square): List<Move> = Rules.generateLegalMovesFrom(_position, _sideToMove, from)

    fun isInCheck(color: PlayerColor): Boolean = Rules.isKingInCheck(_position, color)

    fun requiresPromotion(move: Move): Boolean {
        val piece = _position.pieceAt(move.from) ?: return false
        if (piece.type != PieceType.PAWN) return false
        val promoRank = if (piece.color == PlayerColor.WHITE) 7 else 0
        return move.to.rank == promoRank
    }

    fun tryMakeMove(move: Move): Boolean {
        val legal = legalMoves().any { sameMove(it, move) }
        if (!legal) return false

        val normalized = normalizeMove(move)
        val captured = computeCapturedPiece(_position, normalized, _sideToMove)

        history.add(
            HistoryEntry(
                move = normalized,
                positionBefore = _position,
                sideToMoveBefore = _sideToMove,
                capturedPiece = captured
            )
        )

        _position = _position.makeMove(normalized, _sideToMove)
        _sideToMove = _sideToMove.opposite()

        if (captured != null) {
            if (history.last().sideToMoveBefore == PlayerColor.WHITE) capturedWhite.add(captured) else capturedBlack.add(captured)
        }

        return true
    }

    fun undoLastPly(): Boolean {
        val entry = history.removeLastOrNull() ?: return false
        _position = entry.positionBefore
        _sideToMove = entry.sideToMoveBefore

        if (entry.capturedPiece != null) {
            if (_sideToMove == PlayerColor.WHITE) {
                if (capturedWhite.isNotEmpty()) capturedWhite.removeAt(capturedWhite.size - 1)
            } else {
                if (capturedBlack.isNotEmpty()) capturedBlack.removeAt(capturedBlack.size - 1)
            }
        }
        return true
    }

    fun capturedBy(color: PlayerColor): List<Piece> =
        if (color == PlayerColor.WHITE) capturedWhite.toList() else capturedBlack.toList()

    private fun computeCapturedPiece(position: Position, move: Move, side: PlayerColor): Piece? {
        return if (move.isEnPassant) {
            val capRank = if (side == PlayerColor.WHITE) move.to.rank - 1 else move.to.rank + 1
            position.pieceAt(Square(move.to.file, capRank))
        } else {
            position.pieceAt(move.to)
        }
    }

    private fun sameMove(a: Move, b: Move): Boolean =
        a.from == b.from && a.to == b.to && a.promotion == b.promotion &&
            a.isEnPassant == b.isEnPassant && a.isCastleKingSide == b.isCastleKingSide && a.isCastleQueenSide == b.isCastleQueenSide

    private fun normalizeMove(move: Move): Move {
        if (move.promotion == null && requiresPromotion(move)) {
            return move.copy(promotion = PieceType.QUEEN)
        }
        return move
    }

    companion object {
        // PUBLIC_INTERFACE
        fun newGame(mode: GameMode): ChessGame {
            /** Create a fresh chess game in the given mode. */
            return ChessGame(
                mode = mode,
                _position = Position.initial(),
                _sideToMove = PlayerColor.WHITE,
                history = mutableListOf(),
                capturedWhite = mutableListOf(),
                capturedBlack = mutableListOf()
            )
        }
    }
}

private fun <T> MutableList<T>.removeLastOrNull(): T? = if (isEmpty()) null else removeAt(size - 1)
