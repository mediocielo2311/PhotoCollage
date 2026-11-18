package com.sara.photocollage

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

// Adaptador de la galería: muestra en cuadrícula los collages ya guardados en el dispositivo
// y marca con un borde los que estén seleccionados para eliminar.
class CollagesAdapter(
    private val context: Context,
    private val collages: List<Uri>,
    private val seleccionados: Set<Uri>,
    private val alPulsar: (Uri) -> Unit,
    private val alMantenerPulsado: (Uri) -> Unit
) : RecyclerView.Adapter<CollagesAdapter.CollageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollageViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.item_collage, parent, false)
        return CollageViewHolder(vista)
    }

    override fun onBindViewHolder(holder: CollageViewHolder, position: Int) {
        val uri = collages[position]

        holder.ivCollage.setImageBitmap(BitmapCache.original(context, uri))
        holder.vBorde.visibility = if (seleccionados.contains(uri)) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { alPulsar(uri) }
        holder.itemView.setOnLongClickListener {
            alMantenerPulsado(uri)
            true
        }
    }

    override fun getItemCount(): Int = collages.size

    class CollageViewHolder(vista: View) : RecyclerView.ViewHolder(vista) {
        val ivCollage: ImageView = vista.findViewById(R.id.ivCollage)
        val vBorde: View = vista.findViewById(R.id.vBordeCollage)
    }
}
