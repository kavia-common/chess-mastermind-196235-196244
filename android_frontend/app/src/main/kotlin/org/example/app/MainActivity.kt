package org.example.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.example.app.core.ChessGame
import org.example.app.core.GameMode
import org.example.app.core.PieceType
import org.example.app.core.PlayerColor
import org.example.app.core.Square
import org.example.app.core.move.Move
import org.example.app.core.notation.SimpleNotation
import org.example.app.core.persistence.GamePersistence
import org.example.app.engine.ai.MinimaxAi
import org.example.app.ui.ChessboardView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var chessboardView: ChessboardView
    private lateinit var statusText: TextView

    private lateinit var newGameButton: Button
    private lateinit var undoButton: Button
    private lateinit var modeButton: Button

    private lateinit var difficultySeek: SeekBar
    private lateinit var difficultyValue: TextView

    private lateinit var capturedWhiteText: TextView
    private lateinit var capturedBlackText: TextView

    private lateinit var moveList: RecyclerView
    private lateinit var moveAdapter: MoveHistoryAdapter

    private val uiHandler = Handler(Looper.getMainLooper())
    private val aiExecutor = Executors.newSingleThreadExecutor()
    private val aiThinking = AtomicBoolean(false)

    private var game: ChessGame = ChessGame.newGame(GameMode.PVP)
    private var aiDepth: Int = 2
    private val ai = MinimaxAi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        bindViews()
        setupMoveList()
        setupControls()
        setupBoardCallbacks()

        // Try restore last game (optional persistence).
        GamePersistence.loadLastGame(this)?.let { restored ->
            game = restored
        }

        renderAll()
        maybeTriggerAi()
    }

    override fun onStop() {
        super.onStop()
        // Save best-effort. If serialization fails, ignore to avoid crashing.
        GamePersistence.saveLastGame(this, game)
    }

    private fun bindViews() {
        chessboardView = findViewById(R.id.chessboardView)
        statusText = findViewById(R.id.statusText)

        newGameButton = findViewById(R.id.newGameButton)
        undoButton = findViewById(R.id.undoButton)
        modeButton = findViewById(R.id.modeButton)

        difficultySeek = findViewById(R.id.difficultySeek)
        difficultyValue = findViewById(R.id.difficultyValue)

        capturedWhiteText = findViewById(R.id.capturedWhiteText)
        capturedBlackText = findViewById(R.id.capturedBlackText)

        moveList = findViewById(R.id.moveList)
    }

    private fun setupMoveList() {
        moveAdapter = MoveHistoryAdapter()
        moveList.layoutManager = LinearLayoutManager(this)
        moveList.adapter = moveAdapter
    }

    private fun setupControls() {
        newGameButton.setOnClickListener {
            if (aiThinking.get()) return@setOnClickListener
            game = ChessGame.newGame(game.mode)
            renderAll()
            maybeTriggerAi()
        }

        undoButton.setOnClickListener {
            if (aiThinking.get()) return@setOnClickListener
            val undone = game.undoLastPly()
            if (undone) {
                // In AI mode, undo twice (player + AI) when possible, to feel natural.
                if (game.mode == GameMode.VS_AI && game.undoLastPly()) {
                    // ok
                }
                renderAll()
            }
        }

        modeButton.setOnClickListener {
            if (aiThinking.get()) return@setOnClickListener
            val items = arrayOf("Player vs Player", "Player vs AI (AI plays Black)")
            val checked = if (game.mode == GameMode.PVP) 0 else 1
            AlertDialog.Builder(this)
                .setTitle("Select mode")
                .setSingleChoiceItems(items, checked) { dialog, which ->
                    val mode = if (which == 0) GameMode.PVP else GameMode.VS_AI
                    game = ChessGame.newGame(mode)
                    dialog.dismiss()
                    renderAll()
                    maybeTriggerAi()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        difficultySeek.progress = (aiDepth - 1).coerceIn(0, 3)
        difficultyValue.text = aiDepth.toString()
        difficultySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                aiDepth = (progress + 1).coerceIn(1, 4)
                difficultyValue.text = aiDepth.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupBoardCallbacks() {
        chessboardView.onSquareTapped = { square ->
            if (!aiThinking.get()) {
                handleSquareTap(square)
            }
        }
    }

    private fun handleSquareTap(square: Square) {
        if (game.isGameOver) return

        val selection = chessboardView.getSelectedSquare()
        if (selection == null) {
            // Select only a piece belonging to current player.
            val piece = game.position.pieceAt(square) ?: return
            if (piece.color != game.sideToMove) return

            chessboardView.setSelectedSquare(square)
            chessboardView.setHighlightedMoves(game.legalMovesFrom(square))
            chessboardView.invalidate()
            return
        }

        // Same square: deselect.
        if (selection == square) {
            chessboardView.clearSelection()
            chessboardView.invalidate()
            return
        }

        // Try to make a move from selection to square.
        val candidates = game.legalMovesFrom(selection).filter { it.to == square }
        if (candidates.isEmpty()) {
            // Maybe user tapped another own piece: reselect.
            val piece = game.position.pieceAt(square)
            if (piece != null && piece.color == game.sideToMove) {
                chessboardView.setSelectedSquare(square)
                chessboardView.setHighlightedMoves(game.legalMovesFrom(square))
                chessboardView.invalidate()
            }
            return
        }

        // Promotion selection if needed.
        val moveToPlay: Move? = when {
            candidates.size == 1 && candidates[0].promotion == null && game.requiresPromotion(candidates[0]) -> null
            candidates.size > 1 -> null
            else -> candidates[0]
        }

        if (moveToPlay == null) {
            // Ask user which piece to promote to (for any promotion situation).
            promptPromotion(selection, square) { chosen ->
                attemptPlayMove(chosen)
            }
        } else {
            attemptPlayMove(moveToPlay)
        }
    }

    private fun promptPromotion(from: Square, to: Square, onChosen: (Move) -> Unit) {
        val promoOptions = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        val labels = promoOptions.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Promote to")
            .setItems(labels) { _, which ->
                val chosenType = promoOptions[which]
                val move = Move(from = from, to = to, promotion = chosenType)
                onChosen(move)
            }
            .setCancelable(false)
            .show()
    }

    private fun attemptPlayMove(move: Move) {
        val result = game.tryMakeMove(move)
        if (!result) return

        chessboardView.clearSelection()
        renderAll()
        showGameEndIfNeeded()
        maybeTriggerAi()
    }

    private fun maybeTriggerAi() {
        if (game.mode != GameMode.VS_AI) return
        if (game.isGameOver) return

        // AI plays black in this implementation.
        if (game.sideToMove != PlayerColor.BLACK) return
        if (aiThinking.getAndSet(true)) return

        statusText.text = "AI thinking…"

        val positionSnapshot = game.position // immutable snapshot
        val sideToMoveSnapshot = game.sideToMove

        aiExecutor.execute {
            try {
                val best = ai.findBestMove(positionSnapshot, sideToMoveSnapshot, aiDepth)
                uiHandler.post {
                    aiThinking.set(false)
                    if (best != null && !game.isGameOver && game.sideToMove == PlayerColor.BLACK) {
                        game.tryMakeMove(best)
                        renderAll()
                        showGameEndIfNeeded()
                    } else {
                        renderAll()
                    }
                }
            } catch (_: Throwable) {
                uiHandler.post {
                    aiThinking.set(false)
                    renderAll()
                }
            }
        }
    }

    private fun renderAll() {
        chessboardView.setPosition(game.position)
        chessboardView.clearSelection()

        val side = if (game.sideToMove == PlayerColor.WHITE) "White" else "Black"
        val inCheck = game.isInCheck(game.sideToMove)
        val checkSuffix = if (inCheck) " (check)" else ""
        statusText.text = "$side to move$checkSuffix"

        modeButton.text = if (game.mode == GameMode.PVP) getString(R.string.mode_pvp) else getString(R.string.mode_ai)

        val capturedWhite = game.capturedBy(PlayerColor.WHITE).joinToString(" ") { it.toUnicode() }
        val capturedBlack = game.capturedBy(PlayerColor.BLACK).joinToString(" ") { it.toUnicode() }

        capturedWhiteText.text = "White captured: ${capturedWhite.ifBlank { "—" }}"
        capturedBlackText.text = "Black captured: ${capturedBlack.ifBlank { "—" }}"

        moveAdapter.submitMoves(game.moveHistory.mapIndexed { idx, m ->
            val ply = idx + 1
            val san = SimpleNotation.toSimpleAlgebraic(m)
            "$ply. $san"
        })
        moveList.scrollToPosition(moveAdapter.itemCount.coerceAtLeast(1) - 1)
    }

    private fun showGameEndIfNeeded() {
        if (!game.isGameOver) return

        val msg = when {
            game.isCheckmate -> "Checkmate. ${game.winner!!.name.lowercase().replaceFirstChar { it.uppercase() }} wins."
            game.isStalemate -> "Stalemate. Draw."
            else -> "Game over."
        }

        AlertDialog.Builder(this)
            .setTitle("Game finished")
            .setMessage(msg)
            .setPositiveButton("New game") { _, _ ->
                game = ChessGame.newGame(game.mode)
                renderAll()
                maybeTriggerAi()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
