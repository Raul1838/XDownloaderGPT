# X GIF Downloader

Aplicación Android nativa (Kotlin) que recibe la URL de un post público de X, localiza su medio mediante la API pública de vxtwitter, descarga el vídeo MP4 que X utiliza para representar los GIF y lo convierte a un archivo `.gif` en **Descargas**.

## Compilar

- Android Studio reciente + JDK 17.
- Abrir la carpeta del proyecto y ejecutar `:app:assembleDebug`.
- O subir el proyecto a GitHub y ejecutar **Actions → Build APK → Run workflow**. El APK se publica como artefacto `XGifDownloader-debug`.

## Límites conocidos

- Solo funciona con posts públicos y medios que el servicio de resolución pueda devolver. No accede a cuentas privadas ni evita restricciones de X.
- Depende de `api.vxtwitter.com`, un servicio de terceros no oficial; puede cambiar o limitar peticiones.
- Convierte hasta los primeros 8 segundos, con un máximo de 80 fotogramas y ancho/alto máximo de 480 px para mantener el tamaño razonable.
- La conversión puede generar archivos grandes y el resultado puede diferir del GIF original en calidad o velocidad.

Uso responsable: descarga únicamente contenido que tengas derecho a guardar y respeta los términos de X y los derechos de sus autores.
