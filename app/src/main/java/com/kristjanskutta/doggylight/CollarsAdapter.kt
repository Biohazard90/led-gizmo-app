package com.kristjanskutta.doggylight

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.collar_item.view.*

class CollarsAdapter(val items: ArrayList<Collar>, val context: Context) : RecyclerView.Adapter<ViewHolder>() {

    // Gets the number of animals in the list
    override fun getItemCount(): Int {
        return items.size
    }

    // Inflates the item views
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(context, LayoutInflater.from(context).inflate(R.layout.collar_item, parent, false))
    }

    // Binds each animal in the ArrayList to a view
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val collar = items.get(position)
        holder?.tvCollarName?.text = collar.name
        holder?.tvChev?.visibility = if (collar.device != null) View.VISIBLE else View.INVISIBLE
        holder?.collar = collar
    }
}

class ViewHolder(val context: Context, view: View) : RecyclerView.ViewHolder(view) {
    // Holds the TextView that will add each animal to
    val tvCollarName = view.tv_collar
    val tvChev = view.tv_chev
    var collar: Collar? = null

    init {
        view.setOnClickListener { v ->
            if (collar?.device != null) {
                val stopScannerIntent = Intent("STOPSCANNER")
                context.sendBroadcast(stopScannerIntent)

                val intent = Intent(context, CollarSettingsActivity::class.java).apply {
                    putExtra("collarName", collar?.name)
                    putExtra("collarDevice", collar?.device)
                }
                context.startActivity(intent)
            }
        }
    }
}
