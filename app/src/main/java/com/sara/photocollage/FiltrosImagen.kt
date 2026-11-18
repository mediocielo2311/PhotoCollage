package com.sara.photocollage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface

// Filtros implementados en Kotlin manipulando directamente los píxeles.
// Cada función recibe un bitmap y devuelve uno NUEVO: el que entra nunca se modifica,
// que es lo que permite restaurar siempre la imagen original.
object FiltrosImagen {

    // Convierte a escala de grises usando luminosidad perceptual.
    fun blancoYNegro(origen: Bitmap): Bitmap {
        val ancho = origen.width
        val alto = origen.height
        val pixeles = IntArray(ancho * alto)
        origen.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)

        for (i in pixeles.indices) {
            val pixel = pixeles[i]
            val alfa = (pixel shr 24) and 0xFF
            val rojo = (pixel shr 16) and 0xFF
            val verde = (pixel shr 8) and 0xFF
            val azul = pixel and 0xFF

            val gris = (0.299 * rojo + 0.587 * verde + 0.114 * azul).toInt().coerceIn(0, 255)

            pixeles[i] = (alfa shl 24) or (gris shl 16) or (gris shl 8) or gris
        }

        return bitmapDesdePixeles(pixeles, ancho, alto)
    }

    // Aplica la matriz de conversión sepia clásica.
    fun sepia(origen: Bitmap): Bitmap {
        val ancho = origen.width
        val alto = origen.height
        val pixeles = IntArray(ancho * alto)
        origen.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)

        for (i in pixeles.indices) {
            val pixel = pixeles[i]
            val alfa = (pixel shr 24) and 0xFF
            val rojo = (pixel shr 16) and 0xFF
            val verde = (pixel shr 8) and 0xFF
            val azul = pixel and 0xFF

            val nuevoRojo = (0.393 * rojo + 0.769 * verde + 0.189 * azul).toInt().coerceIn(0, 255)
            val nuevoVerde = (0.349 * rojo + 0.686 * verde + 0.168 * azul).toInt().coerceIn(0, 255)
            val nuevoAzul = (0.272 * rojo + 0.534 * verde + 0.131 * azul).toInt().coerceIn(0, 255)

            pixeles[i] = (alfa shl 24) or (nuevoRojo shl 16) or (nuevoVerde shl 8) or nuevoAzul
        }

        return bitmapDesdePixeles(pixeles, ancho, alto)
    }

    // Suma o resta luz a cada canal. progreso: 0..200, 100 = sin cambios.
    fun brillo(origen: Bitmap, progreso: Int): Bitmap {
        if (progreso == 100) return origen

        val ancho = origen.width
        val alto = origen.height
        val pixeles = IntArray(ancho * alto)
        origen.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)

        val ajuste = (progreso - 100) * 255 / 100

        for (i in pixeles.indices) {
            val pixel = pixeles[i]
            val alfa = (pixel shr 24) and 0xFF
            val rojo = (((pixel shr 16) and 0xFF) + ajuste).coerceIn(0, 255)
            val verde = (((pixel shr 8) and 0xFF) + ajuste).coerceIn(0, 255)
            val azul = ((pixel and 0xFF) + ajuste).coerceIn(0, 255)

            pixeles[i] = (alfa shl 24) or (rojo shl 16) or (verde shl 8) or azul
        }

        return bitmapDesdePixeles(pixeles, ancho, alto)
    }

    // Acerca o aleja cada color de su gris equivalente. progreso: 0..200, 100 = sin cambios.
    fun saturacion(origen: Bitmap, progreso: Int): Bitmap {
        if (progreso == 100) return origen

        val ancho = origen.width
        val alto = origen.height
        val pixeles = IntArray(ancho * alto)
        origen.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)

        val factor = progreso / 100.0

        for (i in pixeles.indices) {
            val pixel = pixeles[i]
            val alfa = (pixel shr 24) and 0xFF
            val rojo = (pixel shr 16) and 0xFF
            val verde = (pixel shr 8) and 0xFF
            val azul = pixel and 0xFF

            val gris = 0.299 * rojo + 0.587 * verde + 0.114 * azul

            val nuevoRojo = (gris + (rojo - gris) * factor).toInt().coerceIn(0, 255)
            val nuevoVerde = (gris + (verde - gris) * factor).toInt().coerceIn(0, 255)
            val nuevoAzul = (gris + (azul - gris) * factor).toInt().coerceIn(0, 255)

            pixeles[i] = (alfa shl 24) or (nuevoRojo shl 16) or (nuevoVerde shl 8) or nuevoAzul
        }

        return bitmapDesdePixeles(pixeles, ancho, alto)
    }

    // Gira la imagen los grados indicados en sentido horario.
    fun rotar(origen: Bitmap, grados: Int): Bitmap {
        if (grados % 360 == 0) return origen

        val matriz = Matrix().apply { postRotate(grados.toFloat()) }
        return Bitmap.createBitmap(origen, 0, 0, origen.width, origen.height, matriz, true)
    }

    // Dibuja un texto sobre la imagen, en la parte inferior izquierda.
    fun conTexto(origen: Bitmap, texto: String): Bitmap {
        val resultado = origen.copy(Bitmap.Config.ARGB_8888, true)
        val lienzo = Canvas(resultado)

        val pincel = Paint().apply {
            color = Color.WHITE
            textSize = resultado.width / 10f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 2f, 2f, Color.BLACK)
        }

        lienzo.drawText(texto, resultado.width * 0.05f, resultado.height * 0.92f, pincel)
        return resultado
    }

    // Crea un bitmap nuevo a partir del array de píxeles ya modificado.
    private fun bitmapDesdePixeles(pixeles: IntArray, ancho: Int, alto: Int): Bitmap {
        val resultado = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        resultado.setPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
        return resultado
    }
}
