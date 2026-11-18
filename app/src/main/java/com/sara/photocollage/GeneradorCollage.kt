package com.sara.photocollage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

// Se encarga de la imagen final del collage: componerla, guardarla en la galería
// del dispositivo y prepararla para compartirla con otras aplicaciones.
object GeneradorCollage {

    private const val LADO_CELDA = 400
    private const val CARPETA_GALERIA = "Pictures/PhotoCollage"
    private const val CARPETA_CACHE = "collage_compartido"

    // Compone en una sola imagen todas las fotos del collage.
    // Entra: cuántas fotos hay, en cuántas columnas se ven y cómo obtener la imagen
    // ya editada de cada una. Sale: el bitmap del collage completo.
    fun generar(totalFotos: Int, columnasVisibles: Int, imagenDe: (Int) -> Bitmap): Bitmap {
        // Nunca más columnas que fotos, para no dejar huecos vacíos en la imagen final.
        val columnas = minOf(columnasVisibles, totalFotos).coerceAtLeast(1)
        val filas = ceil(totalFotos / columnas.toDouble()).toInt().coerceAtLeast(1)

        val collage = Bitmap.createBitmap(
            columnas * LADO_CELDA,
            filas * LADO_CELDA,
            Bitmap.Config.ARGB_8888
        )

        val lienzo = Canvas(collage)
        lienzo.drawColor(Color.WHITE)

        for (indice in 0 until totalFotos) {
            val columna = indice % columnas
            val fila = indice / columnas
            val destino = Rect(
                columna * LADO_CELDA,
                fila * LADO_CELDA,
                (columna + 1) * LADO_CELDA,
                (fila + 1) * LADO_CELDA
            )

            // Recorte centrado para que la foto no salga deformada en la celda cuadrada.
            val foto = imagenDe(indice)
            val lado = minOf(foto.width, foto.height)
            val izquierda = (foto.width - lado) / 2
            val arriba = (foto.height - lado) / 2
            val origen = Rect(izquierda, arriba, izquierda + lado, arriba + lado)

            lienzo.drawBitmap(foto, origen, destino, null)
        }

        return collage
    }

    // Guarda el collage en la galería del dispositivo.
    // Entra: el collage ya compuesto. Sale: true si se ha podido guardar.
    fun guardarEnGaleria(context: Context, collage: Bitmap): Boolean {
        val datos = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nombreNuevo())
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, CARPETA_GALERIA)
            }
        }

        val resolver = context.contentResolver
        val destino = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, datos)
            ?: return false

        val salida = resolver.openOutputStream(destino) ?: return false
        salida.use { collage.compress(Bitmap.CompressFormat.PNG, 100, it) }

        return true
    }

    // Escribe el collage en la caché de la aplicación y devuelve una Uri compartible.
    // FileProvider genera una Uri content:// temporal, sin exponer la ruta real del archivo.
    // Entra: el collage ya compuesto. Sale: la Uri que se envía a otras apps.
    fun prepararParaCompartir(context: Context, collage: Bitmap): Uri {
        val carpeta = File(context.cacheDir, CARPETA_CACHE)
        carpeta.mkdirs()

        val archivo = File(carpeta, nombreNuevo())
        FileOutputStream(archivo).use { salida ->
            collage.compress(Bitmap.CompressFormat.PNG, 100, salida)
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    }

    // Nombre único para cada collage, basado en el momento en que se crea.
    private fun nombreNuevo(): String = "collage_${System.currentTimeMillis()}.png"
}
