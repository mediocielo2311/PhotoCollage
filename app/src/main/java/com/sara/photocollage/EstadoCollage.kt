package com.sara.photocollage

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.util.Log
import org.json.JSONObject

// Guarda y recupera el collage cuando el sistema cierra la aplicación para liberar memoria.
// El ViewModel ya cubre los giros de pantalla; esto cubre el cierre del proceso.
object EstadoCollage {

    private const val CLAVE_FOTOS = "fotos"
    private const val CLAVE_COLUMNAS = "columnas"
    private const val CLAVE_SELECCION = "seleccion"

    // Lo que se devuelve al rehacer un collage guardado.
    data class Recuperado(
        val fotos: List<Foto>,
        val columnas: Int,
        val seleccionada: Int
    )

    // Vuelca el collage al estado que guarda el sistema.
    // Entra: dónde guardarlo y qué guardar. Sale: nada.
    fun guardar(destino: Bundle, fotos: List<Foto>, columnas: Int, seleccionada: Int) {
        val guardadas = ArrayList<String>()

        for (foto in fotos) {
            val datos = JSONObject()
            datos.put("uri", foto.uriOriginal.toString())
            datos.put("filtro", foto.filtro.name)
            datos.put("rotacion", foto.rotacion)
            datos.put("brillo", foto.brillo)
            datos.put("saturacion", foto.saturacion)
            datos.put("texto", foto.texto ?: "")
            guardadas.add(datos.toString())
        }

        destino.putStringArrayList(CLAVE_FOTOS, guardadas)
        destino.putInt(CLAVE_COLUMNAS, columnas)
        destino.putInt(CLAVE_SELECCION, seleccionada)
    }

    // Rehace el collage a partir del estado guardado.
    // Entra: el estado y el acceso a los archivos del dispositivo.
    // Sale: las fotos recuperadas, o null si no había nada guardado.
    fun recuperar(origen: Bundle, resolver: ContentResolver): Recuperado? {
        val guardadas = origen.getStringArrayList(CLAVE_FOTOS) ?: return null

        val fotos = mutableListOf<Foto>()
        for (linea in guardadas) {
            try {
                val datos = JSONObject(linea)
                val uri = Uri.parse(datos.getString("uri"))

                // Tras cerrarse el proceso, el permiso sobre la foto puede haber caducado.
                if (!puedeLeerse(resolver, uri)) continue

                fotos.add(
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
                Log.w("PhotoCollage", "No se pudo recuperar una fotografía del collage", e)
            }
        }

        return Recuperado(
            fotos,
            origen.getInt(CLAVE_COLUMNAS, 3),
            origen.getInt(CLAVE_SELECCION, -1)
        )
    }

    // Comprueba que la aplicación todavía puede leer esa fotografía.
    // Entra: el acceso a archivos y la Uri. Sale: true si se puede abrir.
    private fun puedeLeerse(resolver: ContentResolver, uri: Uri): Boolean = try {
        resolver.openInputStream(uri)?.close()
        true
    } catch (e: Exception) {
        Log.w("PhotoCollage", "La fotografía ya no es accesible: $uri", e)
        false
    }
}
