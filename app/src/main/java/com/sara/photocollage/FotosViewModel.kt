package com.sara.photocollage

import androidx.lifecycle.ViewModel

// Mantiene el estado del collage fuera de la Activity.
// Al vivir en un ViewModel, todo esto sobrevive a los cambios de configuración
// (por ejemplo, girar el dispositivo) sin que se pierda el trabajo del usuario.
class FotosViewModel : ViewModel() {

    val fotos = mutableListOf<Foto>()    // fotos del collage, en orden
    var columnas = 3                      // columnas elegidas por el usuario (en vertical)
    var seleccionada = -1                 // posición de la foto seleccionada, -1 si ninguna
    var modoEdicion = false               // true si la barra de edición está abierta
    var deslizadoresVisibles = false      // true si el panel de brillo/saturación está abierto
    var vistaGrande = false               // true si la foto seleccionada se ve a pantalla completa
}
