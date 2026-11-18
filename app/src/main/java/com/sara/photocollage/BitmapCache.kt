package com.sara.photocollage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

// Guarda en memoria la versión decodificada y reducida de cada foto original.
// Evita releer el archivo del disco en cada repintado y limita el tamaño de los
// bitmaps en memoria, que de otro modo serían de varios megapíxeles cada uno.
object BitmapCache {

    private const val LADO_MAXIMO = 720

    private val originales = mutableMapOf<String, Bitmap>()

    // Devuelve el bitmap original (reducido) de una foto, decodificándolo solo la primera vez.
    fun original(context: Context, uri: Uri): Bitmap {
        val clave = uri.toString()
        originales[clave]?.let { return it }

        val bitmap = decodificarReducido(context, uri)
        originales[clave] = bitmap
        return bitmap
    }

    // Saca de memoria el original de una foto concreta.
    fun liberar(uri: Uri) {
        originales.remove(uri.toString())
    }

    // Saca de memoria todos los originales.
    fun liberarTodo() {
        originales.clear()
    }

    // Decodifica la imagen reduciéndola para que su lado mayor no pase de LADO_MAXIMO.
    private fun decodificarReducido(context: Context, uri: Uri): Bitmap {
        // Primera pasada: leer solo las dimensiones, sin cargar los píxeles.
        val opcionesMedida = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { entrada ->
            BitmapFactory.decodeStream(entrada, null, opcionesMedida)
        }

        // Calcular el factor de reducción (siempre potencia de 2, como pide inSampleSize).
        var muestreo = 1
        val ladoMayor = maxOf(opcionesMedida.outWidth, opcionesMedida.outHeight)
        while (ladoMayor / (muestreo * 2) >= LADO_MAXIMO) {
            muestreo *= 2
        }

        // Segunda pasada: cargar ya reducida.
        val opcionesCarga = BitmapFactory.Options().apply { inSampleSize = muestreo }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { entrada ->
            BitmapFactory.decodeStream(entrada, null, opcionesCarga)
        }

        return bitmap ?: throw IllegalStateException("No se ha podido leer la imagen")
    }
}
