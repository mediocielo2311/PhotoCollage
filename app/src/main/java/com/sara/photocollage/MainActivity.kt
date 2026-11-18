package com.sara.photocollage

import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.core.content.FileProvider
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

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
                foto.texto = if (escrito.isEmpty()) null else escrito
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
    // El ViewModel ya cubre los giros de pantalla; esto cubre el cierre del proceso.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val guardadas = ArrayList<String>()
        for (foto in viewModel.fotos) {
            val datos = JSONObject()
            datos.put("uri", foto.uriOriginal.toString())
            datos.put("filtro", foto.filtro.name)
            datos.put("rotacion", foto.rotacion)
            datos.put("brillo", foto.brillo)
            datos.put("saturacion", foto.saturacion)
            datos.put("texto", foto.texto ?: "")
            guardadas.add(datos.toString())
        }

        outState.putStringArrayList(CLAVE_FOTOS, guardadas)
        outState.putInt(CLAVE_COLUMNAS, viewModel.columnas)
        outState.putInt(CLAVE_SELECCION, viewModel.seleccionada)
    }

    // Rehace el collage a partir de lo guardado.
    // Entra: el estado guardado por el sistema. Sale: nada.
    private fun recuperarEstado(estado: Bundle) {
        // Si el ViewModel sigue vivo, el collage no se ha perdido y no hay nada que rehacer.
        if (viewModel.fotos.isNotEmpty()) return

        val guardadas = estado.getStringArrayList(CLAVE_FOTOS) ?: return

        for (linea in guardadas) {
            try {
                val datos = JSONObject(linea)
                val uri = Uri.parse(datos.getString("uri"))

                // Tras cerrarse el proceso, el permiso sobre la foto puede haber caducado.
                if (!puedeLeerse(uri)) continue

                viewModel.fotos.add(
                    Foto(
                        uriOriginal = uri,
                        filtro = TipoFiltro.valueOf(datos.getString("filtro")),
                        rotacion = datos.getInt("rotacion"),
                        brillo = datos.getInt("brillo"),
                        saturacion = datos.getInt("saturacion"),
                        texto = datos.getString("texto").ifEmpty { null }
                    )
                )
            } catch (e: Exception) {
                // Una foto que no se pueda rehacer simplemente no se añade.
            }
        }

        viewModel.columnas = estado.getInt(CLAVE_COLUMNAS, viewModel.columnas)
        viewModel.seleccionada = estado.getInt(CLAVE_SELECCION, -1)
    }

    // Comprueba que la aplicación todavía puede leer esa fotografía.
    // Entra: la Uri de la foto. Sale: true si se puede abrir.
    private fun puedeLeerse(uri: Uri): Boolean = try {
        contentResolver.openInputStream(uri)?.close()
        true
    } catch (e: Exception) {
        false
    }

    // --- Collage final ---------------------------------------------------------

    // Compone en una sola imagen todas las fotos del collage, con sus ediciones.
    // Entra: nada. Sale: el bitmap del collage completo.
    private fun generarCollage(): Bitmap {
        // Nunca más columnas que fotos, para no dejar huecos vacíos en la imagen final.
        val columnas = minOf(columnasEfectivas(), viewModel.fotos.size).coerceAtLeast(1)
        val filas = ceil(viewModel.fotos.size / columnas.toDouble()).toInt().coerceAtLeast(1)
        val ladoCelda = 400

        val collage = Bitmap.createBitmap(
            columnas * ladoCelda,
            filas * ladoCelda,
            Bitmap.Config.ARGB_8888
        )

        val lienzo = Canvas(collage)
        lienzo.drawColor(Color.WHITE)

        for (indice in viewModel.fotos.indices) {
            val columna = indice % columnas
            val fila = indice / columnas
            val destino = Rect(
                columna * ladoCelda,
                fila * ladoCelda,
                (columna + 1) * ladoCelda,
                (fila + 1) * ladoCelda
            )

            // Recorte centrado para que la foto no salga deformada en la celda cuadrada.
            val bitmapFoto = adapter.bitmapProcesado(indice)
            val lado = minOf(bitmapFoto.width, bitmapFoto.height)
            val izquierda = (bitmapFoto.width - lado) / 2
            val arriba = (bitmapFoto.height - lado) / 2
            val origen = Rect(izquierda, arriba, izquierda + lado, arriba + lado)

            lienzo.drawBitmap(bitmapFoto, origen, destino, null)
        }

        return collage
    }

    // Genera el collage, lo guarda en la caché de la app y lo envía a otras aplicaciones.
    private fun compartirCollage() {
        if (viewModel.fotos.isEmpty()) {
            Toast.makeText(this, R.string.collage_vacio, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val collage = generarCollage()

            val carpeta = File(cacheDir, "collage_compartido")
            carpeta.mkdirs()
            val archivo = File(carpeta, "collage_${System.currentTimeMillis()}.png")

            FileOutputStream(archivo).use { salida ->
                collage.compress(Bitmap.CompressFormat.PNG, 100, salida)
            }

            // FileProvider genera una Uri content:// temporal, sin exponer la ruta real.
            val uriCollage = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                archivo
            )

            val envio = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uriCollage)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(envio, getString(R.string.compartir_titulo)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_guardar, Toast.LENGTH_SHORT).show()
        }
    }

    // Genera el collage y lo guarda en la galería del dispositivo.
    private fun guardarCollage() {
        if (viewModel.fotos.isEmpty()) {
            Toast.makeText(this, R.string.collage_vacio, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val collage = generarCollage()

            val datos = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "collage_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoCollage")
                }
            }

            val destino = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, datos)
            if (destino == null) {
                Toast.makeText(this, R.string.error_guardar, Toast.LENGTH_SHORT).show()
                return
            }

            contentResolver.openOutputStream(destino)?.use { salida ->
                collage.compress(Bitmap.CompressFormat.PNG, 100, salida)
            }

            Toast.makeText(this, R.string.collage_guardado, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_guardar, Toast.LENGTH_SHORT).show()
        }
    }

    // Claves con las que se guarda el estado del collage.
    companion object {
        private const val CLAVE_FOTOS = "fotos"
        private const val CLAVE_COLUMNAS = "columnas"
        private const val CLAVE_SELECCION = "seleccion"
    }
}
