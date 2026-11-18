package com.sara.photocollage

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// Etiqueta con la que esta pantalla escribe en el log del sistema.
private const val TAG = "PhotoCollage"

class MainActivity : AppCompatActivity() {

    // --- Estado y vistas -------------------------------------------------------

    private val viewModel: FotosViewModel by viewModels()

    private lateinit var adapter: FotosAdapter
    private lateinit var gestorCuadricula: GridLayoutManager

    private lateinit var rvFotos: RecyclerView
    private lateinit var tvCollageVacio: TextView
    private lateinit var barraPrincipal: LinearLayout
    private lateinit var barraEdicion: LinearLayout
    private lateinit var panelDeslizadores: LinearLayout
    private lateinit var panelVistaGrande: FrameLayout
    private lateinit var ivVistaGrande: ImageView
    private lateinit var sbBrillo: SeekBar
    private lateinit var sbSaturacion: SeekBar

    // Evita que los SeekBar reaccionen cuando es el código quien cambia su posición.
    private var actualizandoControles = false

    // Cálculo en curso de la imagen ampliada, para poder cancelarlo si llega otro.
    private var trabajoVistaGrande: Job? = null

    // Número máximo de fotos que se pueden elegir de una vez.
    private val maximoFotos = 20

    // Selector de fotos del sistema (Photo Picker): no necesita permisos de almacenamiento.
    private val seleccionarImagenes =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maximoFotos)) { uris ->
            agregarFotos(uris)
        }

    // --- Ciclo de vida ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        rvFotos = findViewById(R.id.rvFotos)
        tvCollageVacio = findViewById(R.id.tvCollageVacio)
        barraPrincipal = findViewById(R.id.barraPrincipal)
        barraEdicion = findViewById(R.id.barraEdicion)
        panelDeslizadores = findViewById(R.id.panelDeslizadores)
        panelVistaGrande = findViewById(R.id.panelVistaGrande)
        ivVistaGrande = findViewById(R.id.ivVistaGrande)
        sbBrillo = findViewById(R.id.sbBrillo)
        sbSaturacion = findViewById(R.id.sbSaturacion)

        savedInstanceState?.let { recuperarEstado(it) }

        configurarCuadricula()
        configurarBarraPrincipal()
        configurarBarraEdicion()
        configurarFlotantes()
        configurarDeslizadores()

        // Con el botón atrás, primero se cierra la edición y solo después se sale de la app.
        onBackPressedDispatcher.addCallback(this) {
            when {
                viewModel.modoEdicion -> cerrarEdicion()
                viewModel.vistaGrande -> cerrarVistaGrande()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        aplicarModoEdicion()
        aplicarVistaGrande()
        actualizarEstadoVacio()
        actualizarControles()
    }

    // --- Configuración inicial -------------------------------------------------

    // Prepara el RecyclerView con la cuadrícula, su adaptador y la selección guardada.
    private fun configurarCuadricula() {
        adapter = FotosAdapter(
            this,
            viewModel.fotos,
            { posicion -> alPulsarFoto(posicion) },
            { posicion -> alMantenerPulsadaFoto(posicion) }
        )
        adapter.restaurarSeleccion(viewModel.seleccionada)

        gestorCuadricula = GridLayoutManager(this, columnasEfectivas())
        rvFotos.layoutManager = gestorCuadricula
        rvFotos.adapter = adapter
    }

    // Botones de la barra inferior principal.
    private fun configurarBarraPrincipal() {
        findViewById<ImageButton>(R.id.btnEditar).setOnClickListener { abrirEdicion() }

        findViewById<ImageButton>(R.id.btnEliminar).setOnClickListener {
            conFotoSeleccionada { posicion, _ -> eliminarFoto(posicion) }
        }

        findViewById<ImageButton>(R.id.btnEliminarTodas).setOnClickListener {
            confirmarEliminarTodas()
        }

        findViewById<ImageButton>(R.id.btnRestaurar).setOnClickListener {
            conFotoSeleccionada { posicion, foto -> restaurarFoto(posicion, foto) }
        }

        findViewById<ImageButton>(R.id.btnCompartir).setOnClickListener { compartirCollage() }

        findViewById<ImageButton>(R.id.btnGuardar).setOnClickListener { guardarCollage() }

        findViewById<ImageButton>(R.id.btnGaleria).setOnClickListener {
            startActivity(Intent(this, GaleriaActivity::class.java))
        }
    }

    // Botones de la barra de edición, que solo se usa con una foto seleccionada.
    private fun configurarBarraEdicion() {
        findViewById<ImageButton>(R.id.btnBrillo).setOnClickListener {
            viewModel.deslizadoresVisibles = !viewModel.deslizadoresVisibles
            aplicarModoEdicion()
        }

        findViewById<ImageButton>(R.id.btnFiltros).setOnClickListener { vista ->
            mostrarMenuFiltros(vista)
        }

        findViewById<ImageButton>(R.id.btnRotar).setOnClickListener {
            conFotoSeleccionada { posicion, foto ->
                foto.rotacion = (foto.rotacion + 90) % 360
                actualizarFoto(posicion)
            }
        }

        findViewById<ImageButton>(R.id.btnTexto).setOnClickListener {
            conFotoSeleccionada { posicion, foto -> pedirTexto(posicion, foto) }
        }

        findViewById<ImageButton>(R.id.btnRestaurarEdicion).setOnClickListener {
            conFotoSeleccionada { posicion, foto -> restaurarFoto(posicion, foto) }
        }

        findViewById<ImageButton>(R.id.btnCerrarEdicion).setOnClickListener { cerrarEdicion() }

        findViewById<ImageButton>(R.id.btnCerrarVista).setOnClickListener { cerrarVistaGrande() }
        ivVistaGrande.setOnClickListener { cerrarVistaGrande() }
    }

    // Botones flotantes sobre la cuadrícula: añadir fotos y cambiar la vista.
    private fun configurarFlotantes() {
        findViewById<FloatingActionButton>(R.id.fabAnadir).setOnClickListener {
            seleccionarImagenes.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        findViewById<FloatingActionButton>(R.id.fabVista).setOnClickListener { vista ->
            mostrarMenuVista(vista)
        }
    }

    // Deslizadores de brillo y saturación, que actúan sobre la foto seleccionada.
    private fun configurarDeslizadores() {
        sbBrillo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progreso: Int, deUsuario: Boolean) {
                if (!deUsuario || actualizandoControles) return
                conFotoSeleccionada { posicion, foto ->
                    foto.brillo = progreso
                    actualizarFoto(posicion)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbSaturacion.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progreso: Int, deUsuario: Boolean) {
                if (!deUsuario || actualizandoControles) return
                conFotoSeleccionada { posicion, foto ->
                    foto.saturacion = progreso
                    actualizarFoto(posicion)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // --- Menús desplegables ----------------------------------------------------

    // Menú de filtros: blanco y negro o sepia, sobre la foto seleccionada.
    private fun mostrarMenuFiltros(ancla: View) {
        val menu = PopupMenu(this, ancla)
        menu.menu.add(0, 1, 0, R.string.btn_byn)
        menu.menu.add(0, 2, 1, R.string.btn_sepia)

        menu.setOnMenuItemClickListener { opcion ->
            conFotoSeleccionada { posicion, foto ->
                foto.filtro =
                    if (opcion.itemId == 1) TipoFiltro.BLANCO_Y_NEGRO else TipoFiltro.SEPIA
                actualizarFoto(posicion)
            }
            true
        }

        menu.show()
    }

    // Menú de vista: número de columnas de la cuadrícula.
    private fun mostrarMenuVista(ancla: View) {
        val menu = PopupMenu(this, ancla)
        menu.menu.add(0, 2, 0, R.string.columnas_2)
        menu.menu.add(0, 3, 1, R.string.columnas_3)
        menu.menu.add(0, 4, 2, R.string.columnas_4)

        menu.setOnMenuItemClickListener { opcion ->
            cambiarColumnas(opcion.itemId)
            true
        }

        menu.show()
    }

    // --- Modo edición ----------------------------------------------------------

    // Abre la barra de edición, siempre que haya una foto seleccionada.
    private fun abrirEdicion() {
        if (adapter.posicionSeleccionada == -1) {
            Toast.makeText(this, R.string.sin_seleccion, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.modoEdicion = true
        aplicarModoEdicion()
    }

    // Cierra la barra de edición y el panel de deslizadores.
    private fun cerrarEdicion() {
        viewModel.modoEdicion = false
        viewModel.deslizadoresVisibles = false
        aplicarModoEdicion()
    }

    // Muestra la barra que corresponda según si se está editando o no.
    private fun aplicarModoEdicion() {
        val editando = viewModel.modoEdicion

        barraPrincipal.visibility = if (editando) View.GONE else View.VISIBLE
        barraEdicion.visibility = if (editando) View.VISIBLE else View.GONE
        panelDeslizadores.visibility =
            if (editando && viewModel.deslizadoresVisibles) View.VISIBLE else View.GONE
    }

    // --- Vista ampliada --------------------------------------------------------

    // Mantener pulsada una foto: la selecciona y la abre a pantalla completa.
    // La barra inferior sigue siendo la normal; para editar se pulsa el botón de editar.
    private fun alMantenerPulsadaFoto(posicion: Int) {
        if (posicion == RecyclerView.NO_POSITION) return

        if (adapter.posicionSeleccionada != posicion) adapter.seleccionar(posicion)
        viewModel.seleccionada = adapter.posicionSeleccionada

        viewModel.vistaGrande = true

        aplicarVistaGrande()
        actualizarControles()
    }

    private fun cerrarVistaGrande() {
        viewModel.vistaGrande = false
        aplicarVistaGrande()
    }

    // Muestra u oculta el panel de la foto ampliada y los botones flotantes.
    private fun aplicarVistaGrande() {
        val ampliada = viewModel.vistaGrande

        panelVistaGrande.visibility = if (ampliada) View.VISIBLE else View.GONE
        findViewById<FloatingActionButton>(R.id.fabAnadir).visibility =
            if (ampliada) View.GONE else View.VISIBLE
        findViewById<FloatingActionButton>(R.id.fabVista).visibility =
            if (ampliada) View.GONE else View.VISIBLE

        refrescarVistaGrande()
    }

    // Vuelve a pintar la foto ampliada con sus ediciones actuales.
    // El cálculo va en segundo plano y se cancela si llega otra petición antes de terminar,
    // que es lo que pasa al arrastrar un deslizador.
    private fun refrescarVistaGrande() {
        if (!viewModel.vistaGrande) return

        val posicion = adapter.posicionSeleccionada
        if (posicion == -1 || posicion >= viewModel.fotos.size) {
            cerrarVistaGrande()
            return
        }

        trabajoVistaGrande?.cancel()
        trabajoVistaGrande = lifecycleScope.launch {
            val imagen = withContext(Dispatchers.Default) { adapter.bitmapProcesado(posicion) }
            if (viewModel.vistaGrande && adapter.posicionSeleccionada == posicion) {
                ivVistaGrande.setImageBitmap(imagen)
            }
        }
    }

    // Repinta una foto en la cuadrícula y, si está abierta, también en la vista ampliada.
    private fun actualizarFoto(posicion: Int) {
        adapter.notifyItemChanged(posicion)
        refrescarVistaGrande()
    }

    // --- Selección -------------------------------------------------------------

    // Se llama al pulsar una foto de la cuadrícula.
    private fun alPulsarFoto(posicion: Int) {
        if (posicion == RecyclerView.NO_POSITION) return

        adapter.seleccionar(posicion)
        viewModel.seleccionada = adapter.posicionSeleccionada

        // Si se ha quedado sin selección, no tiene sentido seguir en modo edición.
        if (adapter.posicionSeleccionada == -1 && viewModel.modoEdicion) cerrarEdicion()

        actualizarControles()
    }

    // Ejecuta una acción solo si hay una foto seleccionada; si no, avisa al usuario.
    // Entra: la acción a ejecutar con la posición y la foto. Sale: nada.
    private fun conFotoSeleccionada(accion: (Int, Foto) -> Unit) {
        val posicion = adapter.posicionSeleccionada
        if (posicion == -1 || posicion >= viewModel.fotos.size) {
            Toast.makeText(this, R.string.sin_seleccion, Toast.LENGTH_SHORT).show()
            return
        }
        accion(posicion, viewModel.fotos[posicion])
    }

    // Ajusta los controles que dependen de que haya o no una foto seleccionada.
    private fun actualizarControles() {
        val posicion = adapter.posicionSeleccionada
        val haySeleccion = posicion != -1 && posicion < viewModel.fotos.size

        actualizandoControles = true
        sbBrillo.isEnabled = haySeleccion
        sbSaturacion.isEnabled = haySeleccion

        if (haySeleccion) {
            val foto = viewModel.fotos[posicion]
            sbBrillo.progress = foto.brillo
            sbSaturacion.progress = foto.saturacion
        } else {
            sbBrillo.progress = 100
            sbSaturacion.progress = 100
        }
        actualizandoControles = false
    }

    // Muestra u oculta el mensaje de collage vacío.
    private fun actualizarEstadoVacio() {
        tvCollageVacio.visibility = if (viewModel.fotos.isEmpty()) View.VISIBLE else View.GONE
    }

    // --- Añadir y eliminar fotos -----------------------------------------------

    // Añade al collage las fotos elegidas, descartando las que ya estaban.
    // Entra: lista de Uris devueltas por el selector. Sale: nada.
    private fun agregarFotos(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val yaEstan = viewModel.fotos.map { it.uriOriginal.toString() }.toMutableSet()
        var agregadas = 0
        var duplicadas = 0

        for (uri in uris) {
            if (yaEstan.contains(uri.toString())) {
                duplicadas++
                continue
            }
            viewModel.fotos.add(Foto(uriOriginal = uri))
            yaEstan.add(uri.toString())
            agregadas++
        }

        if (agregadas > 0) {
            adapter.notifyItemRangeInserted(viewModel.fotos.size - agregadas, agregadas)
        }
        if (duplicadas > 0) {
            Toast.makeText(this, R.string.foto_duplicada, Toast.LENGTH_SHORT).show()
        }

        actualizarEstadoVacio()
    }

    // Elimina del collage la foto de una posición y libera su memoria.
    private fun eliminarFoto(posicion: Int) {
        val foto = viewModel.fotos.removeAt(posicion)

        adapter.liberarCacheDe(foto.uriOriginal)
        BitmapCache.liberar(foto.uriOriginal)

        adapter.limpiarSeleccion()
        adapter.notifyItemRemoved(posicion)
        adapter.notifyItemRangeChanged(posicion, viewModel.fotos.size - posicion)

        viewModel.seleccionada = -1
        cerrarEdicion()
        cerrarVistaGrande()

        actualizarControles()
        actualizarEstadoVacio()
    }

    // Pide confirmación antes de vaciar el collage completo.
    private fun confirmarEliminarTodas() {
        if (viewModel.fotos.isEmpty()) {
            Toast.makeText(this, R.string.collage_vacio, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.confirmar_eliminar_todas_titulo)
            .setMessage(R.string.confirmar_eliminar_todas_mensaje)
            .setPositiveButton(R.string.confirmar_eliminar_boton) { _, _ -> eliminarTodas() }
            .setNegativeButton(R.string.cancelar_boton, null)
            .show()
    }

    // Vacía el collage y libera toda la memoria de imágenes.
    private fun eliminarTodas() {
        val cantidad = viewModel.fotos.size
        viewModel.fotos.clear()

        adapter.limpiarCache()
        BitmapCache.liberarTodo()
        adapter.limpiarSeleccion()
        adapter.notifyItemRangeRemoved(0, cantidad)

        viewModel.seleccionada = -1
        cerrarEdicion()
        cerrarVistaGrande()

        actualizarControles()
        actualizarEstadoVacio()
    }

    // --- Edición ---------------------------------------------------------------

    // Devuelve una foto a su estado original: sin filtro, sin rotación, sin texto.
    private fun restaurarFoto(posicion: Int, foto: Foto) {
        foto.filtro = TipoFiltro.NINGUNO
        foto.rotacion = 0
        foto.brillo = 100
        foto.saturacion = 100
        foto.texto = null

        actualizarFoto(posicion)
        actualizarControles()
    }

    // Pide por diálogo el texto a dibujar sobre la foto seleccionada.
    private fun pedirTexto(posicion: Int, foto: Foto) {
        val campo = EditText(this)
        campo.hint = getString(R.string.dialogo_texto_hint)
        foto.texto?.let { campo.setText(it) }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialogo_texto_titulo)
            .setView(campo)
            .setPositiveButton(R.string.aceptar_boton) { _, _ ->
                val escrito = campo.text.toString().trim()
                foto.texto = escrito.ifEmpty { null }
                actualizarFoto(posicion)
            }
            .setNegativeButton(R.string.cancelar_boton, null)
            .show()
    }

    // Cambia el número de columnas elegido por el usuario.
    private fun cambiarColumnas(columnas: Int) {
        viewModel.columnas = columnas
        gestorCuadricula.spanCount = columnasEfectivas()
    }

    // Columnas que se muestran realmente: en horizontal caben dos más que en vertical.
    // Entra: nada. Sale: el número de columnas a usar en la cuadrícula.
    private fun columnasEfectivas(): Int {
        val apaisado =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (apaisado) viewModel.columnas + 2 else viewModel.columnas
    }

    // --- Guardar y recuperar el estado -----------------------------------------

    // Guarda el collage por si el sistema cierra la aplicación para liberar memoria.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        EstadoCollage.guardar(outState, viewModel.fotos, viewModel.columnas, viewModel.seleccionada)
    }

    // Rehace el collage guardado, si es que se había perdido.
    // Entra: el estado guardado por el sistema. Sale: nada.
    private fun recuperarEstado(estado: Bundle) {
        // Si el ViewModel sigue vivo, el collage no se ha perdido y no hay nada que rehacer.
        if (viewModel.fotos.isNotEmpty()) return

        val recuperado = EstadoCollage.recuperar(estado, contentResolver) ?: return

        viewModel.fotos.addAll(recuperado.fotos)
        viewModel.columnas = recuperado.columnas
        viewModel.seleccionada = recuperado.seleccionada
    }

    // --- Collage final ---------------------------------------------------------

    // Compone la imagen final con las fotos del collage tal y como se ven en pantalla.
    // Entra: nada. Sale: el bitmap del collage. Tarda, así que se llama desde segundo plano.
    private fun componerCollage(): Bitmap =
        GeneradorCollage.generar(viewModel.fotos.size, columnasEfectivas()) { posicion ->
            adapter.bitmapProcesado(posicion)
        }

    // Genera el collage y lo envía a otras aplicaciones.
    private fun compartirCollage() {
        if (viewModel.fotos.isEmpty()) {
            avisar(R.string.collage_vacio)
            return
        }

        lifecycleScope.launch {
            val uriCollage = try {
                withContext(Dispatchers.Default) {
                    GeneradorCollage.prepararParaCompartir(this@MainActivity, componerCollage())
                }
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Sin memoria al componer el collage para compartir", e)
                avisar(R.string.error_memoria)
                return@launch
            } catch (e: IOException) {
                Log.w(TAG, "No se pudo escribir el collage en la caché", e)
                avisar(R.string.error_escritura)
                return@launch
            } catch (e: Exception) {
                Log.w(TAG, "Error inesperado al preparar el collage para compartir", e)
                avisar(R.string.error_guardar)
                return@launch
            }

            val envio = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uriCollage)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(envio, getString(R.string.compartir_titulo)))
        }
    }

    // Genera el collage y lo guarda en la galería del dispositivo.
    private fun guardarCollage() {
        if (viewModel.fotos.isEmpty()) {
            avisar(R.string.collage_vacio)
            return
        }

        lifecycleScope.launch {
            try {
                val guardado = withContext(Dispatchers.Default) {
                    GeneradorCollage.guardarEnGaleria(this@MainActivity, componerCollage())
                }
                avisar(if (guardado) R.string.collage_guardado else R.string.error_guardar)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Sin memoria al componer el collage para guardar", e)
                avisar(R.string.error_memoria)
            } catch (e: SecurityException) {
                Log.w(TAG, "Sin permiso para guardar en la galería", e)
                avisar(R.string.error_permiso)
            } catch (e: IOException) {
                Log.w(TAG, "No se pudo escribir el collage en la galería", e)
                avisar(R.string.error_escritura)
            } catch (e: Exception) {
                Log.w(TAG, "Error inesperado al guardar el collage", e)
                avisar(R.string.error_guardar)
            }
        }
    }

    // Muestra un mensaje breve al usuario.
    // Entra: el texto a mostrar. Sale: nada.
    private fun avisar(mensaje: Int) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }
}
