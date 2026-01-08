package org.example.app.core

import org.example.app.core.move.Move

object Rules {

    fun generateLegalMoves(position: Position, sideToMove: PlayerColor): List<Move> {
        val pseudo = generatePseudoLegalMoves(position, sideToMove)
        return pseudo.filter { m ->
            val next = position.makeMove(m, sideToMove)
            !isKingInCheck(next, sideToMove)
        }
    }

    fun generateLegalMovesFrom(position: Position, sideToMove: PlayerColor, from: Square): List<Move> {
        return generateLegalMoves(position, sideToMove).filter { it.from == from }
    }

    fun isKingInCheck(position: Position, color: PlayerColor): Boolean {
        val kingSq = position.allPieces()
            .firstOrNull { it.second.color == color && it.second.type == PieceType.KING }
            ?.first ?: return false
        return isSquareAttacked(position, kingSq, color.opposite())
    }

    fun isSquareAttacked(position: Position, target: Square, by: PlayerColor): Boolean {
        for ((sq, piece) in position.allPieces()) {
            if (piece.color != by) continue
            if (attacksSquare(position, sq, piece, target)) return true
        }
        return false
    }

    private fun attacksSquare(position: Position, from: Square, piece: Piece, target: Square): Boolean {
        val df = target.file - from.file
        val dr = target.rank - from.rank
        return when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (piece.color == PlayerColor.WHITE) 1 else -1
                dr == dir && kotlin.math.abs(df) == 1
            }

            PieceType.KNIGHT -> {
                val a = kotlin.math.abs(df)
                val b = kotlin.math.abs(dr)
                (a == 1 && b == 2) || (a == 2 && b == 1)
            }

            PieceType.BISHOP -> rayAttacks(position, from, target, listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1))
            PieceType.ROOK -> rayAttacks(position, from, target, listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1))
            PieceType.QUEEN -> rayAttacks(
                position,
                from,
                target,
                listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
            )

            PieceType.KING -> kotlin.math.abs(df) <= 1 && kotlin.math.abs(dr) <= 1
        }
    }

    private fun rayAttacks(position: Position, from: Square, target: Square, dirs: List<Pair<Int, Int>>): Boolean {
        for ((dx, dy) in dirs) {
            var f = from.file + dx
            var r = from.rank + dy
            while (f in 0..7 && r in 0..7) {
                val sq = Square(f, r)
                if (sq == target) return true
                if (position.pieceAt(sq) != null) break
                f += dx
                r += dy
            }
        }
        return false
    }

    fun generatePseudoLegalMoves(position: Position, sideToMove: PlayerColor): List<Move> {
        val moves = ArrayList<Move>(64)
        for ((sq, piece) in position.allPieces()) {
            if (piece.color != sideToMove) continue
            when (piece.type) {
                PieceType.PAWN -> genPawn(position, sq, piece, moves)
                PieceType.KNIGHT -> genKnight(position, sq, piece, moves)
                PieceType.BISHOP -> genSlider(position, sq, piece, moves, listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1))
                PieceType.ROOK -> genSlider(position, sq, piece, moves, listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1))
                PieceType.QUEEN -> genSlider(
                    position,
                    sq,
                    piece,
                    moves,
                    listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
                )

                PieceType.KING -> genKing(position, sq, piece, moves)
            }
        }
        return moves
    }

    private fun genPawn(position: Position, from: Square, piece: Piece, out: MutableList<Move>) {
        val dir = if (piece.color == PlayerColor.WHITE) 1 else -1
        val startRank = if (piece.color == PlayerColor.WHITE) 1 else 6
        val promoRank = if (piece.color == PlayerColor.WHITE) 7 else 0

        val oneRank = from.rank + dir
        if (oneRank in 0..7) {
            val one = Square(from.file, oneRank)
            if (position.pieceAt(one) == null) {
                if (one.rank == promoRank) addPromotions(from, one, out) else out.add(Move(from, one))

                if (from.rank == startRank) {
                    val two = Square(from.file, from.rank + 2 * dir)
                    if (position.pieceAt(two) == null) out.add(Move(from, two))
                }
            }
        }

        for (df in listOf(-1, 1)) {
            val f = from.file + df
            val r = from.rank + dir
            if (f !in 0..7 || r !in 0..7) continue
            val to = Square(f, r)
            val target = position.pieceAt(to)
            if (target != null && target.color != piece.color) {
                if (to.rank == promoRank) addPromotions(from, to, out) else out.add(Move(from, to))
            }
        }

        val ep = position.enPassantTarget
        if (ep != null) {
            if (kotlin.math.abs(ep.file - from.file) == 1 && ep.rank == from.rank + dir) {
                out.add(Move(from = from, to = ep, isEnPassant = true))
            }
        }
    }

    private fun addPromotions(from: Square, to: Square, out: MutableList<Move>) {
        out.add(Move(from, to, promotion = PieceType.QUEEN))
        out.add(Move(from, to, promotion = PieceType.ROOK))
        out.add(Move(from, to, promotion = PieceType.BISHOP))
        out.add(Move(from, to, promotion = PieceType.KNIGHT))
    }

    private fun genKnight(position: Position, from: Square, piece: Piece, out: MutableList<Move>) {
        val jumps = listOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1)
        for ((dx, dy) in jumps) {
            val f = from.file + dx
            val r = from.rank + dy
            if (f !in 0..7 || r !in 0..7) continue
            val to = Square(f, r)
            val t = position.pieceAt(to)
            if (t == null || t.color != piece.color) out.add(Move(from, to))
        }
    }

    private fun genSlider(position: Position, from: Square, piece: Piece, out: MutableList<Move>, dirs: List<Pair<Int, Int>>) {
        for ((dx, dy) in dirs) {
            var f = from.file + dx
            var r = from.rank + dy
            while (f in 0..7 && r in 0..7) {
                val to = Square(f, r)
                val t = position.pieceAt(to)
                if (t == null) {
                    out.add(Move(from, to))
                } else {
                    if (t.color != piece.color) out.add(Move(from, to))
                    break
                }
                f += dx
                r += dy
            }
        }
    }

    private fun genKing(position: Position, from: Square, piece: Piece, out: MutableList<Move>) {
        for (dx in -1..1) for (dy in -1..1) {
            if (dx == 0 && dy == 0) continue
            val f = from.file + dx
            val r = from.rank + dy
            if (f !in 0..7 || r !in 0..7) continue
            val to = Square(f, r)
            val t = position.pieceAt(to)
            if (t == null || t.color != piece.color) out.add(Move(from, to))
        }

        val side = piece.color
        val rank = if (side == PlayerColor.WHITE) 0 else 7
        if (from != Square(4, rank)) return
        if (isKingInCheck(position, side)) return

        val rights = position.castling
        if (side == PlayerColor.WHITE) {
            if (rights.whiteKingSide && canCastleKingSide(position, side)) out.add(Move(from, Square(6, rank), isCastleKingSide = true))
            if (rights.whiteQueenSide && canCastleQueenSide(position, side)) out.add(Move(from, Square(2, rank), isCastleQueenSide = true))
        } else {
            if (rights.blackKingSide && canCastleKingSide(position, side)) out.add(Move(from, Square(6, rank), isCastleKingSide = true))
            if (rights.blackQueenSide && canCastleQueenSide(position, side)) out.add(Move(from, Square(2, rank), isCastleQueenSide = true))
        }
    }

    private fun canCastleKingSide(position: Position, side: PlayerColor): Boolean {
        val rank = if (side == PlayerColor.WHITE) 0 else 7
        val f5 = Square(5, rank)
        val f6 = Square(6, rank)
        if (position.pieceAt(f5) != null || position.pieceAt(f6) != null) return false
        if (isSquareAttacked(position, f5, side.opposite())) return false
        if (isSquareAttacked(position, f6, side.opposite())) return false
        val rook = position.pieceAt(Square(7, rank))
        return rook != null && rook.color == side && rook.type == PieceType.ROOK
    }

    private fun canCastleQueenSide(position: Position, side: PlayerColor): Boolean {
        val rank = if (side == PlayerColor.WHITE) 0 else 7
        val f1 = Square(1, rank)
        val f2 = Square(2, rank)
        val f3 = Square(3, rank)
        if (position.pieceAt(f1) != null || position.pieceAt(f2) != null || position.pieceAt(f3) != null) return false
        if (isSquareAttacked(position, f3, side.opposite())) return false
        if (isSquareAttacked(position, f2, side.opposite())) return false
        val rook = position.pieceAt(Square(0, rank))
        return rook != null && rook.color == side && rook.type == PieceType.ROOK
    }
}
