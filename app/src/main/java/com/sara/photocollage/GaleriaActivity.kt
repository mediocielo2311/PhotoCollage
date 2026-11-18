package com.sara.photocollage

import android.app.Dialog
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Muestra dentro de la app los collages que ella misma ha guardado en la galería del dispositivo,
// y permite eliminarlos. La lista es siempre un reflejo de lo que hay de verdad en el dispositivo.
class GaleriaActivity : AppCompatActivity() {

    private val collages = mutableListOf<Uri>()
    private val seleccionados = mutableSetOf<Uri>()
    private var modoSeleccion = false

    private lateinit var adapter: CollagesAdapter
    private lateinit var barraTitulo: LinearLayout
    private lateinit var barraSeleccion: LinearLayout
    private lateinit var tvContador: TextView
    private lateinit var btnSeleccionarTodos: Button
    private lateinit var tvGaleriaVacia: TextView

    // A partir de Android 11 es el propio sistema quien pide confirmación para borrar
    // archivos de la galería; aquí se recoge su respuesta.
    private val confirmarBorradoSistema =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { resultado ->
            if (resultado.resultCode == RESULT_OK) {
                Toast.makeText(this, R.string.collages_eliminados, Toast.LENGTH_SHORT).show()
            }
            salirModoSeleccion()
            cargarCollages()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_galeria)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.galeriaRoot)) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        barraTitulo = findViewById(R.id.barraTitulo)
        barraSeleccion = findViewById(R.id.barraSeleccion)
        tvContador = findViewById(R.id.tvContador)
        btnSeleccionarTodos = findViewById(R.id.btnSeleccionarTodos)
        tvGaleriaVacia = findViewById(R.id.tvGaleriaVacia)

        configurarBotones()
        configurarCuadricula()

        // El botón atrás sale primero del modo selección y solo después de la pantalla.
        onBackPressedDispatcher.addCallback(this) {
            if (modoSeleccion) {
                salirModoSeleccion()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        cargarCollages()
    }

    private fun configurarBotones() {
        findViewById<ImageButton>(R.id.btnVolver).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnCerrarSeleccion).setOnClickListener { salirModoSeleccion() }

        btnSeleccionarTodos.setOnClickListener {
            seleccionados.clear()
            seleccionados.addAll(collages)
            adapter.notifyDataSetChanged()
            actualizarBarras()
        }

        findViewById<ImageButton>(R.id.btnEliminarCollages).setOnClickListener {
            confirmarEliminar()
        }
    }

    private fun configurarCuadricula() {
        adapter = CollagesAdapter(
            this,
            collages,
            seleccionados,
            { uri -> alPulsarCollage(uri) },
            { uri -> alMantenerPulsadoCollage(uri) }
        )

        val rvCollages = findViewById<RecyclerView>(R.id.rvCollages)
        rvCollages.layoutManager = GridLayoutManager(this, 2)
        rvCollages.adapter = adapter
    }

    // --- Carga de datos --------------------------------------------------------

    // Vuelve a preguntar al dispositivo qué collages hay y actualiza la pantalla.
    private fun cargarCollages() {
        collages.clear()
        collages.addAll(buscarCollagesGuardados())

        adapter.notifyDataSetChanged()
        tvGaleriaVacia.visibility = if (collages.isEmpty()) View.VISIBLE else View.GONE
        actualizarBarras()
    }

    // Consulta a MediaStore las imágenes guardadas por esta app (su nombre empieza por "collage_").
    // Entra: nada. Sale: lista de Uris, de la más reciente a la más antigua.
    private fun buscarCollagesGuardados(): List<Uri> {
        val encontrados = mutableListOf<Uri>()

        val columnas = arrayOf(MediaStore.Images.Media._ID)
        val condicion = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val argumentos = arrayOf("collage_%")
        val orden = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            columnas,
            condicion,
            argumentos,
            orden
        )?.use { cursor ->
            val indiceId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(indiceId)
                encontrados.add(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                )
            }
        }

        return encontrados
    }

    // --- Selección -------------------------------------------------------------

    // Pulsación normal: abre el collage, o marca y desmarca si se está seleccionando.
    private fun alPulsarCollage(uri: Uri) {
        if (!modoSeleccion) {
            mostrarCollage(uri)
            return
        }

        if (!seleccionados.remove(uri)) seleccionados.add(uri)
        if (seleccionados.isEmpty()) modoSeleccion = false

        adapter.notifyDataSetChanged()
        actualizarBarras()
    }

    // Mantener pulsado: entra en modo selección con ese collage ya marcado.
    private fun alMantenerPulsadoCollage(uri: Uri) {
        modoSeleccion = true
        seleccionados.add(uri)

        adapter.notifyDataSetChanged()
        actualizarBarras()
    }

    private fun salirModoSeleccion() {
        modoSeleccion = false
        seleccionados.clear()

        adapter.notifyDataSetChanged()
        actualizarBarras()
    }

    // Muestra la barra que corresponda y actualiza el contador.
    private fun actualizarBarras() {
        barraTitulo.visibility = if (modoSeleccion) View.GONE else View.VISIBLE
        barraSeleccion.visibility = if (modoSeleccion) View.VISIBLE else View.GONE

        tvContador.text = getString(R.string.seleccionados, seleccionados.size)

        // "Seleccionar todos" solo tiene sentido si hay más de un collage.
        btnSeleccionarTodos.visibility = if (collages.size > 1) View.VISIBLE else View.GONE
    }

    // --- Eliminar --------------------------------------------------------------

    private fun confirmarEliminar() {
        if (seleccionados.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.confirmar_eliminar_collages_titulo)
            .setMessage(getString(R.string.confirmar_eliminar_collages_mensaje, seleccionados.size))
            .setPositiveButton(R.string.confirmar_eliminar_boton) { _, _ -> eliminarSeleccionados() }
            .setNegativeButton(R.string.cancelar_boton, null)
            .show()
    }

    // Borra del dispositivo los collages marcados.
    // En Android 11 o superior se delega en el diálogo del sistema; por debajo se borra directamente.
    private fun eliminarSeleccionados() {
        val aBorrar = seleccionados.toList()
        aBorrar.forEach { BitmapCache.liberar(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val peticion = MediaStore.createDeleteRequest(contentResolver, aBorrar)
            confirmarBorradoSistema.launch(
                IntentSenderRequest.Builder(peticion.intentSender).build()
            )
            return
        }

        var borrados = 0
        for (uri in aBorrar) {
            try {
                borrados += contentResolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                // Si el sistema no permite borrar ese archivo, simplemente se deja como estaba.
            }
        }

        if (borrados > 0) {
            Toast.makeText(this, R.string.collages_eliminados, Toast.LENGTH_SHORT).show()
        }

        salirModoSeleccion()
        cargarCollages()
    }

    // --- Vista ampliada --------------------------------------------------------

    // Abre un collage a pantalla completa; se cierra al tocarlo.
    private fun mostrarCollage(uri: Uri) {
        val dialogo = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialogo.setContentView(R.layout.dialogo_ver_collage)

        val imagen = dialogo.findViewById<ImageView>(R.id.ivCollageGrande)
        imagen.setImageBitmap(BitmapCache.original(this, uri))
        imagen.setOnClickListener { dialogo.dismiss() }

        dialogo.show()
    }
}
