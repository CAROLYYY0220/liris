# Demo WEKA + Spring Boot

Resumen rápido
- Proyecto Java Spring Boot que usa modelos entrenados con WEKA para mostrar predicciones (aprobado/reprobado) a partir de los datasets ya incluidos.
- Los recursos WEKA (.arff y .model) se empaquetan dentro del JAR en `classpath:/weka/`.

Estructura relevante
- `src/main/java/com/liris/demo/` — código Java (incluye `WekaController` y `DemoApplication`).
- `src/main/resources/templates/` — plantillas Thymeleaf (ej. `prediccionesView.html`).
- `weka/` — datasets y modelos: `student-mat.arff`, `student-por.arff`, `modeloRF.model`, `modeloRF_por.model`.

Requisitos
- Java 21 (JDK instalado y `java` en PATH).
- Maven (se puede usar el wrapper incluido `mvnw.cmd`).

Build y ejecución (PowerShell en Windows)

1) Empaquetar (salta tests si quieres):
```powershell
.\mvnw.cmd -U package -DskipTests
```

2) Ejecutar el JAR (usa puerto 8081 para evitar conflicto con 8080):
```powershell
java -jar target\demo-0.0.1-SNAPSHOT.jar --server.port=8081
```

3) Alternativa (ejecutar con Spring Boot desde Maven):
```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

Endpoints
- `GET /predicciones` → muestra las predicciones para los datasets incluidos (student-mat y student-por).
- (Opcional) puedes crear una redirección `/` hacia `/predicciones` si quieres un índice simple.

Notas técnicas importantes
- Recursos WEKA dentro del JAR: el controlador carga ARFF y modelos usando `getResourceAsStream("/weka/..." )`. Si el JAR no incluye `src/main/resources` o la carpeta `weka`, Thymeleaf no encontrará las plantillas y la aplicación lanzará un error `TemplateInputException`.
- Si el puerto está ocupado, cambia `--server.port=<PUERTO>` al lanzar el JAR o ajusta `src/main/resources/application.properties`.
- Si el JAR está en uso, detenlo antes de volver a empaquetar (PowerShell):
```powershell
Get-Process -Name java | Where-Object { $_.Path -like '*demo-0.0.1-SNAPSHOT.jar*' } | Stop-Process -Force
```

Archivos modificados / puntos de interés
- `pom.xml`: se ajustó la sección de `<resources>` para incluir la carpeta `weka` y asegurar que `src/main/resources` se empaquete.
- `WekaController.java`: ahora carga `.arff` y `.model` desde `classpath:/weka` vía `getResourceAsStream` y usa `DataSource(InputStream)` y `SerializationHelper.read(InputStream)`.
- `src/main/resources/templates/prediccionesView.html`: plantilla Thymeleaf usada para mostrar las predicciones.

Sugerencias para mejorar la claridad (opciones que puedo implementar):
- Mejorar la plantilla `prediccionesView.html` para mostrar un resumen primero (número de instancias, conteo aprobado/reprobado), y luego tablas plegables por dataset.
- Añadir una página índice `/` que redirija a `/predicciones`.
- Generar un CSV con las predicciones en la ruta `/predicciones/download` para descargar resultados.

¿Quieres que implemente alguna de estas mejoras ahora? Si prefieres, primero solo organizo el README con más detalle (ej.: incluir ejemplos de respuesta HTML) o actualizo la plantilla para presentar la información de forma más entendible.

---
Si quieres que haga cambios ahora: dime si prefieres (elige una o varias):
- "README sólo" → solo mejorar y ampliar la documentación (ya creado el archivo base).
- "Mejorar plantilla" → reestructuro `prediccionesView.html` para mostrar resumen, tablas y estilos sencillos.
- "Añadir / redirigir" → creo un pequeño controlador que haga `@GetMapping("/")` → `redirect:/predicciones`.

Respuesta rápida: ¿qué prefieres que haga ahora?