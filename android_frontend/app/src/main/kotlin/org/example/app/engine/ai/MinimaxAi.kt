package org.example.app.engine.ai

import org.example.app.core.PieceType
import org.example.app.core.PlayerColor
import org.example.app.core.Position
import org.example.app.core.Rules
import org.example.app.core.move.Move
import kotlin.math.max

class MinimaxAi {

    fun findBestMove(position: Position, sideToMove: PlayerColor, depth: Int): Move? {
        val legal = Rules.generateLegalMoves(position, sideToMove)
        if (legal.isEmpty()) return null

        var best: Move? = null
        var bestScore = Int.MIN_VALUE

        var alpha = Int.MIN_VALUE
        val beta = Int.MAX_VALUE

        for (m in legal) {
            val next = position.makeMove(m, sideToMove)
            val score = -negamax(next, sideToMove.opposite(), depth - 1, -beta, -alpha)
            if (score > bestScore) {
                bestScore = score
                best = m
            }
            alpha = max(alpha, score)
        }
        return best
    }

    private fun negamax(position: Position, side: PlayerColor, depth: Int, alphaIn: Int, betaIn: Int): Int {
        var alpha = alphaIn
        val beta = betaIn

        val legal = Rules.generateLegalMoves(position, side)
        val inCheck = Rules.isKingInCheck(position, side)

        if (depth <= 0 || legal.isEmpty()) {
            if (legal.isEmpty()) {
                return if (inCheck) -100000 + (3 - depth) else 0
            }
            return evaluate(position, side)
        }

        var best = Int.MIN_VALUE
        for (m in legal) {
            val next = position.makeMove(m, side)
            val score = -negamax(next, side.opposite(), depth - 1, -beta, -alpha)
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        return best
    }

    private fun evaluate(position: Position, side: PlayerColor): Int {
        var score = 0
        for ((_, p) in position.allPieces()) {
            val v = when (p.type) {
                PieceType.PAWN -> 100
                PieceType.KNIGHT -> 320
                PieceType.BISHOP -> 330
                PieceType.ROOK -> 500
                PieceType.QUEEN -> 900
                PieceType.KING -> 20000
            }
            score += if (p.color == side) v else -v
        }

        val myMoves = Rules.generateLegalMoves(position, side).size
        val oppMoves = Rules.generateLegalMoves(position, side.opposite()).size
        score += 2 * (myMoves - oppMoves)

        if (Rules.isKingInCheck(position, side)) score -= 50
        if (Rules.isKingInCheck(position, side.opposite())) score += 50

        return score
    }
}
