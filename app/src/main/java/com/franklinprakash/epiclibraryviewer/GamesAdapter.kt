package com.franklinprakash.epiclibraryviewer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.franklinprakash.epiclibraryviewer.model.EpicGame

class GameAdapter(
    private val games: List<EpicGame>
) : RecyclerView.Adapter<GameAdapter.VH>() {

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = games[position].title
    }

    override fun getItemCount() = games.size
}
