package org.example.app.core

import org.example.app.core.move.Move

data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
)

data class Position(
    internal val board: Array<Piece?>,
    val castling: CastlingRights,
    val enPassantTarget: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {
    fun pieceAt(sq: Square): Piece? = board[idx(sq)]

    fun allPieces(): List<Pair<Square, Piece>> {
        val out = ArrayList<Pair<Square, Piece>>(32)
        for (r in 0..7) for (f in 0..7) {
            val p = board[r * 8 + f] ?: continue
            out.add(Square(f, r) to p)
        }
        return out
    }

    fun withPiece(sq: Square, piece: Piece?): Position {
        val nb = board.copyOf()
        nb[idx(sq)] = piece
        return copy(board = nb)
    }

    fun makeMove(move: Move, sideToMove: PlayerColor): Position {
        val moving = pieceAt(move.from) ?: error("No piece at from")
        val targetPiece = pieceAt(move.to)

        var next = this.withPiece(move.from, null)

        if (move.isEnPassant) {
            val capRank = if (sideToMove == PlayerColor.WHITE) move.to.rank - 1 else move.to.rank + 1
            val capSq = Square(move.to.file, capRank)
            next = next.withPiece(capSq, null)
        }

        if (move.isCastleKingSide || move.isCastleQueenSide) {
            val rank = if (sideToMove == PlayerColor.WHITE) 0 else 7
            if (move.isCastleKingSide) {
                next = next.withPiece(Square(5, rank), Piece(PieceType.ROOK, sideToMove))
                next = next.withPiece(Square(7, rank), null)
            } else {
                next = next.withPiece(Square(3, rank), Piece(PieceType.ROOK, sideToMove))
                next = next.withPiece(Square(0, rank), null)
            }
        }

        val placed = if (moving.type == PieceType.PAWN && move.promotion != null) {
            Piece(move.promotion, moving.color)
        } else moving

        next = next.withPiece(move.to, placed)

        val cr = updateCastlingRights(castling, moving, move.from, targetPiece, move.to)
        val ep = computeEnPassantTarget(moving, move.from, move.to, sideToMove)

        val nextHalf = if (moving.type == PieceType.PAWN || targetPiece != null || move.isEnPassant) 0 else halfmoveClock + 1
        val nextFull = if (sideToMove == PlayerColor.BLACK) fullmoveNumber + 1 else fullmoveNumber

        return Position(next.board, cr, ep, nextHalf, nextFull)
    }

    companion object {
        fun initial(): Position {
            val b = Array<Piece?>(64) { null }
            fun set(file: Int, rank: Int, piece: Piece) {
                b[rank * 8 + file] = piece
            }

            set(0, 0, Piece(PieceType.ROOK, PlayerColor.WHITE))
            set(1, 0, Piece(PieceType.KNIGHT, PlayerColor.WHITE))
            set(2, 0, Piece(PieceType.BISHOP, PlayerColor.WHITE))
            set(3, 0, Piece(PieceType.QUEEN, PlayerColor.WHITE))
            set(4, 0, Piece(PieceType.KING, PlayerColor.WHITE))
            set(5, 0, Piece(PieceType.BISHOP, PlayerColor.WHITE))
            set(6, 0, Piece(PieceType.KNIGHT, PlayerColor.WHITE))
            set(7, 0, Piece(PieceType.ROOK, PlayerColor.WHITE))
            for (f in 0..7) set(f, 1, Piece(PieceType.PAWN, PlayerColor.WHITE))

            set(0, 7, Piece(PieceType.ROOK, PlayerColor.BLACK))
            set(1, 7, Piece(PieceType.KNIGHT, PlayerColor.BLACK))
            set(2, 7, Piece(PieceType.BISHOP, PlayerColor.BLACK))
            set(3, 7, Piece(PieceType.QUEEN, PlayerColor.BLACK))
            set(4, 7, Piece(PieceType.KING, PlayerColor.BLACK))
            set(5, 7, Piece(PieceType.BISHOP, PlayerColor.BLACK))
            set(6, 7, Piece(PieceType.KNIGHT, PlayerColor.BLACK))
            set(7, 7, Piece(PieceType.ROOK, PlayerColor.BLACK))
            for (f in 0..7) set(f, 6, Piece(PieceType.PAWN, PlayerColor.BLACK))

            return Position(
                board = b,
                castling = CastlingRights(true, true, true, true),
                enPassantTarget = null,
                halfmoveClock = 0,
                fullmoveNumber = 1
            )
        }

        private fun idx(sq: Square): Int = sq.rank * 8 + sq.file

        private fun computeEnPassantTarget(moving: Piece, from: Square, to: Square, side: PlayerColor): Square? {
            if (moving.type != PieceType.PAWN) return null
            val dr = to.rank - from.rank
            if (kotlin.math.abs(dr) != 2) return null
            val epRank = if (side == PlayerColor.WHITE) from.rank + 1 else from.rank - 1
            return Square(from.file, epRank)
        }

        private fun updateCastlingRights(
            current: CastlingRights,
            moving: Piece,
            from: Square,
            captured: Piece?,
            to: Square
        ): CastlingRights {
            var wK = current.whiteKingSide
            var wQ = current.whiteQueenSide
            var bK = current.blackKingSide
            var bQ = current.blackQueenSide

            fun revokeWhite() {
                wK = false
                wQ = false
            }

            fun revokeBlack() {
                bK = false
                bQ = false
            }

            if (moving.type == PieceType.KING) {
                if (moving.color == PlayerColor.WHITE) revokeWhite() else revokeBlack()
            }

            if (moving.type == PieceType.ROOK) {
                if (moving.color == PlayerColor.WHITE) {
                    if (from == Square(0, 0)) wQ = false
                    if (from == Square(7, 0)) wK = false
                } else {
                    if (from == Square(0, 7)) bQ = false
                    if (from == Square(7, 7)) bK = false
                }
            }

            if (captured?.type == PieceType.ROOK) {
                if (captured.color == PlayerColor.WHITE) {
                    if (to == Square(0, 0)) wQ = false
                    if (to == Square(7, 0)) wK = false
                } else {
                    if (to == Square(0, 7)) bQ = false
                    if (to == Square(7, 7)) bK = false
                }
            }

            return CastlingRights(wK, wQ, bK, bQ)
        }
    }
}
