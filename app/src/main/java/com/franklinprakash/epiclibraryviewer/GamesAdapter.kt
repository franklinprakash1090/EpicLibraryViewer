package com.franklinprakash.epiclibraryviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class GamesAdapter : RecyclerView.Adapter<GamesAdapter.GameViewHolder>() {

    private var games: List<Game> = emptyList()

    fun updateGames(newGames: List<Game>) {
        games = newGames
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(games[position])
    }

    override fun getItemCount(): Int = games.size

    class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.gameTitle)
        private val detailsText: TextView = itemView.findViewById(R.id.gameDetails)
        private val appNameText: TextView = itemView.findViewById(R.id.gameAppName)
        private val imageView: ImageView = itemView.findViewById(R.id.gameImage)

        fun bind(game: Game) {
            titleText.text = game.title

            val details = buildString {
                game.developer?.let {
                    append("By $it")
                }
                if (game.buildVersion != null) {
                    if (isNotEmpty()) append(" • ")
                    append("v${game.buildVersion}")
                }
            }

            if (details.isNotEmpty()) {
                detailsText.text = details
                detailsText.visibility = View.VISIBLE
            } else {
                detailsText.visibility = View.GONE
            }

            appNameText.text = game.appName

            loadGameImage(game, imageView)
        }

        private fun loadGameImage(game: Game, imageView: ImageView) {
            val imageUrl = game.keyImages?.find {
                it.type == "DieselGameBoxTall" ||
                        it.type == "Thumbnail" ||
                        it.type == "OfferImageTall"
            }?.url

            imageView.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.placeholder)
                error(R.drawable.error_placeholder)
            }

            imageUrl?.let {
                android.util.Log.d("GamesAdapter", "Image URL for ${game.title}: $it")
            }
        }
    }
}