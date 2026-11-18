package com.sara.photocollage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

// Adaptador del RecyclerView: pinta cada foto del collage en una celda de la cuadrícula,
// aplicando sobre la marcha el filtro, brillo, saturación, rotación y texto de esa foto.
class FotosAdapter(
    private val context: Context,
    private val fotos: MutableList<Foto>,
    private val alPulsarFoto: (Int) -> Unit,
    private val alMantenerPulsadaFoto: (Int) -> Unit
) : RecyclerView.Adapter<FotosAdapter.FotoViewHolder>() {

    // Posición de la foto seleccionada, o -1 si no hay ninguna.
    var posicionSeleccionada: Int = -1
        private set

    // Guarda el resultado ya procesado de cada combinación de edición,
    // para no recalcular los píxeles cada vez que el RecyclerView repinta una celda.
    private val procesadas = mutableMapOf<String, Bitmap>()

    // Selecciona una foto; si ya estaba seleccionada, la deselecciona.
    fun seleccionar(posicion: Int) {
        val anterior = posicionSeleccionada
        posicionSeleccionada = if (posicionSeleccionada == posicion) -1 else posicion

        if (anterior != -1) notifyItemChanged(anterior)
        if (posicionSeleccionada != -1) notifyItemChanged(posicionSeleccionada)
    }

    // Quita la selección actual.
    fun limpiarSeleccion() {
        val anterior = posicionSeleccionada
        posicionSeleccionada = -1
        if (anterior != -1 && anterior < fotos.size) notifyItemChanged(anterior)
    }

    // Recupera una selección guardada (por ejemplo, tras girar la pantalla).
    fun restaurarSeleccion(posicion: Int) {
        posicionSeleccionada = if (posicion in fotos.indices) posicion else -1
    }

    // Devuelve el bitmap ya editado de una foto (lo usa el collage final).
    fun bitmapProcesado(posicion: Int): Bitmap = procesar(fotos[posicion])

    // Libera de memoria las versiones procesadas de una foto concreta.
    fun liberarCacheDe(uri: Uri) {
        val prefijo = "$uri|"
        procesadas.keys.filter { it.startsWith(prefijo) }.forEach { procesadas.remove(it) }
    }

    // Libera de memoria todas las versiones procesadas.
    fun limpiarCache() {
        procesadas.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FotoViewHolder {
        val vista = LayoutInflater.from(parent.context).inflate(R.layout.item_foto, parent, false)
        return FotoViewHolder(vista)
    }

    override fun onBindViewHolder(holder: FotoViewHolder, position: Int) {
        val foto = fotos[position]

        holder.ivFoto.setImageBitmap(procesar(foto))
        holder.vBorde.visibility = if (position == posicionSeleccionada) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { alPulsarFoto(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            alMantenerPulsadaFoto(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount(): Int = fotos.size

    // Aplica en cadena todas las ediciones de una foto sobre su original.
    // Entra: la foto con su estado. Sale: el bitmap listo para mostrar.
    private fun procesar(foto: Foto): Bitmap {
        val firma = "${foto.uriOriginal}|${foto.filtro}|${foto.brillo}|" +
                "${foto.saturacion}|${foto.rotacion}|${foto.texto}"

        procesadas[firma]?.let { return it }

        var bitmap = BitmapCache.original(context, foto.uriOriginal)

        bitmap = when (foto.filtro) {
            TipoFiltro.BLANCO_Y_NEGRO -> FiltrosImagen.blancoYNegro(bitmap)
            TipoFiltro.SEPIA -> FiltrosImagen.sepia(bitmap)
            TipoFiltro.NINGUNO -> bitmap
        }
        bitmap = FiltrosImagen.saturacion(bitmap, foto.saturacion)
        bitmap = FiltrosImagen.brillo(bitmap, foto.brillo)
        bitmap = FiltrosImagen.rotar(bitmap, foto.rotacion)

        val texto = foto.texto
        if (texto != null) bitmap = FiltrosImagen.conTexto(bitmap, texto)

        procesadas[firma] = bitmap
        return bitmap
    }

    // Contenedor de las vistas de una celda.
    class FotoViewHolder(vista: View) : RecyclerView.ViewHolder(vista) {
        val ivFoto: ImageView = vista.findViewById(R.id.ivFoto)
        val vBorde: View = vista.findViewById(R.id.vBordeSeleccion)
    }
}
