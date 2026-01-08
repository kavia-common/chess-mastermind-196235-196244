package org.example.app

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MoveHistoryAdapter : RecyclerView.Adapter<MoveHistoryAdapter.VH>() {

    private val items: MutableList<String> = mutableListOf()

    fun submitMoves(moves: List<String>) {
        items.clear()
        items.addAll(moves)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context)
        tv.setPadding(8, 8, 8, 8)
        return VH(tv)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        (holder.itemView as TextView).text = items[position]
    }

    class VH(itemView: TextView) : RecyclerView.ViewHolder(itemView)
}
