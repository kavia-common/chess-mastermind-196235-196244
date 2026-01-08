package org.example.app.core.notation

import org.example.app.core.move.Move

object SimpleNotation {
    fun toSimpleAlgebraic(move: Move): String {
        val castle = when {
            move.isCastleKingSide -> "O-O"
            move.isCastleQueenSide -> "O-O-O"
            else -> null
        }
        if (castle != null) return castle

        val base = move.from.toAlgebraic() + move.to.toAlgebraic()
        val promo = move.promotion?.let { "=" + it.name.first() } ?: ""
        return base + promo
    }
}
