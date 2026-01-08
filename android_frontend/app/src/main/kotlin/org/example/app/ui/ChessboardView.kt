package org.example.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import org.example.app.R
import org.example.app.core.Position
import org.example.app.core.Square
import org.example.app.core.move.Move
import kotlin.math.min

class ChessboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onSquareTapped: ((Square) -> Unit)? = null

    private var position: Position? = null
    private var selectedSquare: Square? = null
    private var highlightedMoves: List<Move> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
    }

    fun setPosition(pos: Position) {
        position = pos
        invalidate()
    }

    fun setSelectedSquare(square: Square) {
        selectedSquare = square
    }

    fun getSelectedSquare(): Square? = selectedSquare

    fun setHighlightedMoves(moves: List<Move>) {
        highlightedMoves = moves
    }

    fun clearSelection() {
        selectedSquare = null
        highlightedMoves = emptyList()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pos = position ?: return

        val size = min(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val cell = size / 8f

        val light = ContextCompat.getColor(context, R.color.boardLight)
        val dark = ContextCompat.getColor(context, R.color.boardDark)
        val hiSel = ContextCompat.getColor(context, R.color.highlightSelect)
        val hiMove = ContextCompat.getColor(context, R.color.highlightMove)
        val hiCap = ContextCompat.getColor(context, R.color.highlightCapture)
        val textPrimary = ContextCompat.getColor(context, R.color.textPrimary)

        for (r in 0..7) for (f in 0..7) {
            val isLight = (f + r) % 2 == 0
            paint.color = if (isLight) light else dark
            val rect = RectF(left + f * cell, top + (7 - r) * cell, left + (f + 1) * cell, top + (8 - r) * cell)
            canvas.drawRect(rect, paint)
        }

        for (m in highlightedMoves) {
            val isCapture = (pos.pieceAt(m.to) != null) || m.isEnPassant
            paint.color = if (isCapture) hiCap else hiMove
            paint.alpha = 140
            canvas.drawRect(squareRect(m.to, left, top, cell), paint)
            paint.alpha = 255
        }

        selectedSquare?.let { sq ->
            paint.color = hiSel
            paint.alpha = 140
            canvas.drawRect(squareRect(sq, left, top, cell), paint)
            paint.alpha = 255
        }

        textPaint.color = textPrimary
        textPaint.textSize = cell * 0.7f
        val fm = textPaint.fontMetrics
        val textOffset = (fm.bottom - fm.top) / 2f - fm.bottom

        for (r in 0..7) for (f in 0..7) {
            val sq = Square(f, r)
            val p = pos.pieceAt(sq) ?: continue
            val (cx, cy) = squareCenter(sq, left, top, cell)
            canvas.drawText(p.toUnicode(), cx, cy + textOffset, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        val size = min(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val cell = size / 8f

        val x = event.x
        val y = event.y
        if (x < left || x > left + size || y < top || y > top + size) return true

        val file = ((x - left) / cell).toInt().coerceIn(0, 7)
        val rankFromTop = ((y - top) / cell).toInt().coerceIn(0, 7)
        val rank = 7 - rankFromTop

        onSquareTapped?.invoke(Square(file, rank))
        return true
    }

    private fun squareRect(sq: Square, left: Float, top: Float, cell: Float): RectF {
        val x = left + sq.file * cell
        val y = top + (7 - sq.rank) * cell
        return RectF(x, y, x + cell, y + cell)
    }

    private fun squareCenter(sq: Square, left: Float, top: Float, cell: Float): Pair<Float, Float> {
        val rect = squareRect(sq, left, top, cell)
        return rect.centerX() to rect.centerY()
    }
}
