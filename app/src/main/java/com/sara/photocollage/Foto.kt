package com.sara.photocollage

import android.net.Uri

// Filtros de color que puede tener una fotografía.
enum class TipoFiltro {
    NINGUNO,
    BLANCO_Y_NEGRO,
    SEPIA
}

// Representa una fotografía del collage.
// Guarda la Uri original intacta y, aparte, el estado de edición aplicado sobre ella.
// Así la imagen original nunca se modifica y se puede restaurar en cualquier momento.
data class Foto(
    val uriOriginal: Uri,          // ubicación de la foto en el dispositivo
    var filtro: TipoFiltro = TipoFiltro.NINGUNO,
    var rotacion: Int = 0,         // grados acumulados en sentido horario
    var brillo: Int = 100,         // 0..200, 100 = brillo original
    var saturacion: Int = 100,     // 0..200, 100 = saturación original
    var texto: String? = null      // texto opcional dibujado sobre la foto
)
