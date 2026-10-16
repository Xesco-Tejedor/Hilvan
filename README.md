# Hilván

Aplicación de escritura narrativa asistida por IA. Parte de una frase o una imagen y construye un relato por fragmentos, con controles de azar, tono y continuidad. Permite dirigir la historia, guardar personajes y generar ilustraciones de escenas.

[Abrir la aplicación](https://xesco-tejedor.github.io/Hilvan/) · [Repositorio](https://github.com/Xesco-Tejedor/Hilvan)

## Qué puedes hacer

- Empezar con una premisa escrita, dictada por micrófono o una imagen.
- Escuchar cada fragmento leído en voz alta, con voz elegible y entonación según puntuación y diálogo.
- Combinar géneros y elegir longitud y persona narrativa.
- Ajustar seis controles: azar creativo, giros de trama, tono, ritmo, descripción y fidelidad al canon.
- Continuar con una indicación propia o uno de los caminos propuestos.
- Usar un dado de emergencia de 20 caras para introducir una variación.
- Empezar un relato nuevo con el botón **Nuevo** de la barra inferior, que pide confirmación para no perder el actual.
- Editar fragmentos y pedir otra versión del último.
- Mantener un reparto reutilizable y una biblia de personajes y hechos.
- Ilustrar escenas y exportar el relato a HTML, PDF mediante impresión, Markdown o JSON.

## Cómo usarla

1. Abre la aplicación en un navegador con conexión a internet.
2. Escribe una frase en **Punto de partida**, pulsa **🎙 Dictar** para decirla en voz alta (en navegadores compatibles, como Chrome) o pulsa **O parte de una imagen** para seleccionar un archivo. Puedes elegir varios géneros y personajes de tu reparto.
3. Ajusta los controles, la longitud aproximada del fragmento y la persona narrativa. Pulsa **Empezar relato**.
4. Lee el resultado o pulsa **🔊 Escuchar** en un fragmento para oírlo; el mismo botón (**⏹ Detener**) para la lectura. Para avanzar, escribe qué debería ocurrir o elige un camino propuesto y pulsa **Continuar**. Los cambios de azar y estilo se aplican al siguiente fragmento.
5. Si quieres romper una situación estancada, usa el dado de emergencia. La tirada provoca una nueva intervención narrativa.
6. Revisa la biblia y el aspecto de los personajes. El reparto queda disponible para otros relatos en ese navegador.
7. Pulsa **Ilustrar** en un fragmento para pedir una imagen. Puedes regenerarla o cambiar su semilla visual.
8. Exporta antes de empezar otro relato: pulsa **🌱 Nuevo** en la barra inferior (o **Empezar otro relato** al final de la página) y confirma. Esa acción elimina el relato actual de ese navegador, aunque mantiene el reparto.

## Exportación y recuperación

- **HTML:** página con el texto y las imágenes disponibles.
- **PDF:** abre la versión de impresión; elige guardar como PDF en el diálogo del navegador. Puede ser necesario permitir ventanas emergentes.
- **Markdown:** texto y biblia. Las ilustraciones se describen, no se incrustan como archivos de imagen.
- **JSON:** copia del relato, reparto e imágenes disponibles, que puede recuperarse con **Importar JSON**. Guarda esta copia para continuar en otro dispositivo.

## Voz: dictado y lectura

El dictado usa el reconocimiento de voz del navegador (Web Speech API). Funciona en Chrome y derivados; en otros navegadores el botón no aparece. Chrome envía el audio a sus servidores para transcribirlo: la aplicación no graba ni conserva audio. Requiere conceder el permiso de micrófono a la página.

La lectura en voz alta usa las voces instaladas en el dispositivo o las del navegador (también Web Speech API). Si el navegador no la admite, el botón Escuchar no aparece. Puedes elegir la voz española en el selector «Voz de lectura», sobre el relato. La lectura modula pausas, tono y ritmo según la puntuación y el diálogo para que suene menos plana, pero la calidad final depende de la voz instalada: en Android conviene activar la voz de alta calidad en Ajustes → Sistema → Idiomas → Conversión de texto a voz. Ninguna voz gratuita de navegador equivale a una narración profesional.

## IA, conexión y privacidad

El texto se solicita a través de un servicio intermedio configurado en la aplicación. El modo automático intenta primero Gemini y después OpenRouter en determinados fallos; también puedes seleccionar un proveedor. Las peticiones con imagen usan Gemini.

Las ilustraciones se solicitan a Pollinations. Los datos necesarios para generar texto e imágenes se envían a servicios externos: no introduzcas información confidencial ni imágenes que no quieras compartir con ellos.

La configuración actual usa servicios o modelos de nivel gratuito, sujetos a cuotas, disponibilidad y cambios. El contador de la app es local y no refleja necesariamente todas las peticiones de la cuenta o de otras apps. Una generación puede fallar o tardar; no hay disponibilidad garantizada.

## Almacenamiento y límites

El relato actual y el reparto se guardan en el navegador; las imágenes usan IndexedDB. No hay sincronización automática entre dispositivos. Borrar los datos del sitio puede eliminar estos contenidos. Conserva una copia JSON.

La IA puede introducir contradicciones o resultados poco adecuados. La fidelidad al canon y la continuidad visual son objetivos de generación, no garantías. Revisa y edita el resultado; si una ilustración cambia demasiado un personaje, ajusta su descripción o regenérala.

## Ejecutarla en local

La interfaz es una página HTML con CSS y JavaScript, sin paso de compilación:

```bash
git clone https://github.com/Xesco-Tejedor/Hilvan.git
cd Hilvan
python3 -m http.server 8000
```

Abre `http://localhost:8000`. La IA depende de los servicios configurados: una copia propia necesita revisar su disponibilidad y permisos. El repositorio no instala un backend propio.
