package com.sara.photocollage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Adaptador del RecyclerView: pinta cada foto del collage en una celda de la cuadrícula,
// aplicando el filtro, brillo, saturación, rotación y texto de esa foto.
// El cálculo de los píxeles se hace en segundo plano para no bloquear la interfaz.
class FotosAdapter(
    private val context: Context,
    private val fotos: MutableList<Foto>,
    private val alPulsarFoto: (Int) -> Unit,
    private val alMantenerPulsadaFoto: (Int) -> Unit
) : RecyclerView.Adapter<FotosAdapter.FotoViewHolder>() {

    // Posición de la foto seleccionada, o -1 si no hay ninguna.
    var posicionSeleccionada: Int = -1
        private set

    // Guarda el resultado ya procesado de cada foto, para no recalcular los píxeles
    // cada vez que el RecyclerView repinta una celda. Solo se conserva la versión vigente
    // de cada fotografía: al cambiar su edición se descarta la anterior, de forma que la
    // memoria ocupada no crece aunque se mueva mucho un deslizador.
    private val procesadas = mutableMapOf<String, Bitmap>()

    // Pinta en el hilo principal, calcula en segundo plano.
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    // Devuelve el bitmap ya editado de una foto. Lo usa el collage final.
    // Entra: la posición de la foto. Sale: su imagen con todas las ediciones aplicadas.
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
        val firma = firmaDe(foto)

        // La firma identifica qué imagen debe acabar en esta celda; si la celda se reutiliza
        // para otra foto mientras se calcula, el resultado antiguo se descarta.
        holder.ivFoto.tag = firma

        holder.vBorde.visibility = if (position == posicionSeleccionada) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { alPulsarFoto(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            alMantenerPulsadaFoto(holder.bindingAdapterPosition)
            true
        }

        val yaCalculada = procesadas[firma]
        if (yaCalculada != null) {
            holder.ivFoto.setImageBitmap(yaCalculada)
            return
        }

        holder.ivFoto.setImageDrawable(null)
        ambito.launch {
            val imagen = withContext(Dispatchers.Default) { procesar(foto) }
            if (holder.ivFoto.tag == firma) holder.ivFoto.setImageBitmap(imagen)
        }
    }

    override fun getItemCount(): Int = fotos.size

    // Al desaparecer la lista se cancelan los cálculos que quedaran en marcha.
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        ambito.cancel()
    }

    // Identifica el estado de edición de una foto: si no cambia, la imagen tampoco.
    private fun firmaDe(foto: Foto): String =
        "${foto.uriOriginal}|${foto.filtro}|${foto.brillo}|" +
                "${foto.saturacion}|${foto.rotacion}|${foto.texto}"

    // Aplica en cadena todas las ediciones de una foto sobre su original.
    // Entra: la foto con su estado. Sale: el bitmap listo para mostrar.
    // Está sincronizada porque varias celdas pueden pedir su imagen a la vez.
    @Synchronized
    private fun procesar(foto: Foto): Bitmap {
        val firma = firmaDe(foto)
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

        // Fuera la versión anterior de esta misma foto antes de guardar la nueva.
        liberarCacheDe(foto.uriOriginal)
        procesadas[firma] = bitmap

        return bitmap
    }

    // Contenedor de las vistas de una celda.
    class FotoViewHolder(vista: View) : RecyclerView.ViewHolder(vista) {
        val ivFoto: ImageView = vista.findViewById(R.id.ivFoto)
        val vBorde: View = vista.findViewById(R.id.vBordeSeleccion)
    }
}
