# PhotoCollage

Aplicación Android desarrollada en Kotlin con Android Studio para el examen práctico de 2º DAM.

## Descripción

PhotoCollage permite seleccionar varias fotografías de la galería del dispositivo, visualizarlas en
una composición tipo collage y editarlas de forma individual: aplicar filtros, ajustar brillo y
saturación, rotarlas, añadir texto o restaurar la imagen original. El collage final se puede
compartir con otras aplicaciones o guardar en la galería del dispositivo, y la propia app permite
consultar después los collages guardados.

## Interfaz

La pantalla principal se organiza en tres zonas:

- **Título** con el nombre de la aplicación.
- **Zona central**: la cuadrícula con las fotografías del collage. Si está vacío, se muestra un
  mensaje indicándolo.
- **Barra inferior de acciones**, con siete iconos: editar, eliminar la foto seleccionada, eliminar
  todas, restaurar la imagen original, compartir, guardar el collage y ver los collages guardados.

Sobre la cuadrícula hay dos **botones flotantes**: el grande abre el selector de fotos y el pequeño
despliega el menú de distribución de la cuadrícula.

Una pulsación sobre una fotografía la selecciona y la resalta con un borde. **Manteniéndola
pulsada** se abre a pantalla completa sobre la cuadrícula, sin salir de la pantalla principal.

Al pulsar **editar** con una fotografía seleccionada, la barra inferior se sustituye por la **barra
de edición**: brillo y saturación, filtros, rotar 90º, añadir texto, restaurar original y cerrar.
Si la fotografía está abierta a pantalla completa, los cambios se ven ampliados y en tiempo real.
El botón atrás del dispositivo cierra primero la edición y después la vista ampliada.

## Funcionalidades

### Obligatorias

| Apartado | Implementación |
|---|---|
| Interfaz principal | Título, cuadrícula central y todas las acciones exigidas, con una disposición que se adapta a distintos tamaños de pantalla y a las dos orientaciones. |
| Selección de imágenes | Photo Picker del sistema, con selección múltiple (hasta 20 por tanda), sin permitir fotos repetidas y con aviso si se intenta repetir una. |
| Visualización del collage | `RecyclerView` con `GridLayoutManager`. Las celdas son cuadradas y las fotos se recortan de forma centrada para no deformarse. |
| Selección y eliminación | Pulsar una foto la resalta con un borde de color; volver a pulsarla la deselecciona. Solo puede haber una seleccionada. Se puede eliminar la seleccionada o vaciar el collage, esto último previa confirmación con `AlertDialog`. |
| Filtros | Blanco y negro, sepia y restauración de la imagen original, aplicados sobre la foto seleccionada y visibles de inmediato. Si no hay ninguna seleccionada se muestra un mensaje informativo. |
| Rotación y brillo | Rotación acumulativa de 90º en sentido horario y ajuste de brillo con `SeekBar` en tiempo real, con el valor central (100) como brillo original. |
| Compartir | Generación de la imagen final del collage y envío mediante `Intent` con `FileProvider`. |

### Ampliaciones voluntarias

- **Filtro de saturación**, con `SeekBar` y también en tiempo real.
- **Texto sobre las fotografías**, introducido mediante un diálogo.
- **Guardar el collage** en la galería del dispositivo, en la carpeta `Pictures/PhotoCollage`.
- **Cambiar la distribución** de la cuadrícula: 2, 3 o 4 columnas en vertical, y dos columnas más
  automáticamente al girar a horizontal.

### Funcionalidad adicional

- **Galería de collages guardados**: una segunda pantalla que consulta a `MediaStore` los collages
  creados por la aplicación y permite verlos a pantalla completa sin salir de ella. Manteniendo
  pulsado un collage se entra en modo selección, con la opción de seleccionar todos y de
  eliminarlos del dispositivo. La galería es siempre un reflejo de lo que hay realmente guardado:
  no mantiene copias propias, por lo que no puede desincronizarse de la galería del móvil.

## Estructura del código

| Archivo | Responsabilidad |
|---|---|
| `MainActivity.kt` | Pantalla principal: eventos de las barras y los botones flotantes, selección, menús y diálogos. |
| `FotosViewModel.kt` | Estado del collage (fotos, columnas, selección y modo edición); lo conserva al girar la pantalla. |
| `Foto.kt` | Modelo de datos: Uri original de la fotografía y su estado de edición. |
| `FotosAdapter.kt` | Adaptador de la cuadrícula: pinta cada celda ya editada y cachea los resultados procesados. |
| `FiltrosImagen.kt` | Filtros implementados en Kotlin manipulando directamente los píxeles. |
| `BitmapCache.kt` | Decodifica y reduce cada imagen original una sola vez, y libera memoria al eliminar fotos. |
| `GaleriaActivity.kt` | Pantalla de collages guardados: consulta a `MediaStore` y vista a pantalla completa. |
| `CollagesAdapter.kt` | Adaptador de la cuadrícula de collages guardados. |

### Tratamiento de las imágenes

Los filtros están implementados íntegramente en Kotlin, recorriendo el array de píxeles de la
imagen con `getPixels` y `setPixels`; no se utiliza ninguna aplicación ni librería externa. El
blanco y negro usa luminosidad perceptual, el sepia la matriz de conversión clásica, el brillo suma
o resta luz a cada canal y la saturación acerca o aleja cada color de su gris equivalente.

La imagen original nunca se modifica: cada operación genera un bitmap nuevo a partir del original,
que se conserva en memoria, por lo que restaurar una fotografía es inmediato. Las imágenes se
decodifican reducidas (720 px de lado máximo) para limitar el consumo de memoria, y se liberan de
la caché al eliminar las fotos del collage.

## Permisos

La aplicación utiliza el **Photo Picker** de Android, que da acceso únicamente a las fotografías
que el usuario selecciona de forma explícita. Por ese motivo no solicita permisos de acceso al
almacenamiento. Para compartir el collage se emplea un `FileProvider`, que genera una Uri temporal
sin exponer rutas de archivo reales.

## Instrucciones para ejecutar el proyecto

1. Clonar el repositorio o descargarlo como ZIP.
2. Abrir la carpeta del proyecto desde Android Studio (`File > Open`).
3. Esperar a que Gradle termine de sincronizar las dependencias.
4. Conectar un dispositivo Android con Android 7.0 (API 24) o superior, o iniciar un emulador.
5. Pulsar el botón **Run**.

## Capturas de pantalla

### Interfaz y selección de fotografías

**Pantalla inicial con el collage vacío**

![Pantalla inicial con el collage vacío](capturas/01_vacio.png)

**Selección múltiple de fotografías con el Photo Picker del sistema**

![Selección múltiple de fotografías](capturas/02_selector.png)

**Collage en cuadrícula**

![Collage en cuadrícula](capturas/03_cuadricula.png)

**Fotografía seleccionada, resaltada con un borde**

![Fotografía seleccionada](capturas/04_seleccion.png)

**Mensaje informativo al operar sin ninguna fotografía seleccionada**

![Mensaje informativo sin selección](capturas/05_sin_seleccion.png)

**Fotografía abierta a pantalla completa manteniéndola pulsada**

![Fotografía a pantalla completa](capturas/06_vista_ampliada.png)

### Edición

**Barra de edición con el menú de filtros**

![Barra de edición](capturas/07_barra_edicion.png)

**Filtro blanco y negro**

![Filtro blanco y negro](capturas/08_blanco_negro.png)

**Filtro sepia**

![Filtro sepia](capturas/09_sepia.png)

**Fotografía restaurada a su estado original**

![Fotografía restaurada](capturas/10_restaurada.png)

**Rotación de 90º**

![Rotación de 90 grados](capturas/11_rotada.png)

**Panel de brillo y saturación**

![Panel de brillo y saturación](capturas/12_panel_brillo.png)

**Brillo modificado en tiempo real**

![Brillo modificado](capturas/13_brillo.png)

**Saturación modificada**

![Saturación modificada](capturas/16_saturacion.png)

**Texto sobre una fotografía**

![Texto sobre una fotografía](capturas/17_texto.png)

### Eliminar y compartir

**Confirmación antes de eliminar todas las fotografías**

![Confirmación de eliminar todas](capturas/14_eliminar_todas.png)

**Compartir el collage con otras aplicaciones**

![Compartir el collage](capturas/15_compartir.png)

### Distribución de la cuadrícula y adaptación a la pantalla

**Dos columnas**

![Dos columnas](capturas/18_columnas_2.png)

**Cuatro columnas**

![Cuatro columnas](capturas/19_columnas_4.png)

**Dos columnas en horizontal**

![Dos columnas en horizontal](capturas/20_horizontal_2.png)

**Cuatro columnas en horizontal**

![Cuatro columnas en horizontal](capturas/21_horizontal_4.png)

### Galería de collages guardados

**Collages guardados en el dispositivo**

![Galería de collages guardados](capturas/22_galeria.png)

**Selección múltiple para eliminar collages**

![Eliminar collages](capturas/23_borrado_collages.png)

## Autora

Sara — 2º DAM, CEU FP.
