package org.example.app.core.persistence

import org.example.app.core.ChessGame
import org.example.app.core.GameMode
import org.example.app.core.Piece
import org.example.app.core.PlayerColor
import org.example.app.core.Position

/**
 * Internal utility to recreate a ChessGame with a restored Position/side.
 */
internal object ChessGameRestorer {
    fun restore(mode: GameMode, position: Position, sideToMove: PlayerColor): ChessGame {
        return ChessGame(
            mode = mode,
            _position = position,
            _sideToMove = sideToMove,
            history = mutableListOf(),
            capturedWhite = mutableListOf(),
            capturedBlack = mutableListOf()
        )
    }

    fun restore(
        mode: GameMode,
        position: Position,
        sideToMove: PlayerColor,
        capturedWhite: List<Piece>,
        capturedBlack: List<Piece>
    ): ChessGame {
        return ChessGame(
            mode = mode,
            _position = position,
            _sideToMove = sideToMove,
            history = mutableListOf(),
            capturedWhite = capturedWhite.toMutableList(),
            capturedBlack = capturedBlack.toMutableList()
        )
    }
}
