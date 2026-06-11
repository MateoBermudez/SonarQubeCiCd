# Pipeline CI/CD con Jenkins, Tomcat y SonarQube

Proyecto de demostración de un flujo de **integración y despliegue continuo (CI/CD)** para una aplicación Java/Maven, con análisis automático de calidad de código y notificaciones por correo según el resultado.

---

## Tecnologías utilizadas

| Tecnología | Rol en el proyecto |
|---|---|
| **Java 17 + Maven** | Lenguaje y gestor de dependencias/build de la aplicación web (genera un `.war`) |
| **Jenkins** | Orquestador del pipeline: detecta cambios, compila, despliega y notifica |
| **Apache Tomcat 9** | Servidor de aplicaciones donde se despliega el `.war` (servidor de pruebas) |
| **SonarQube** | Analiza el código fuente y evalúa su calidad según reglas configurables |
| **GitHub** | Repositorio remoto del código fuente, dispara el pipeline mediante polling |
| **Docker / Docker Compose** | Contenedores que alojan Jenkins, Tomcat y SonarQube de forma aislada y reproducible |
| **Gmail SMTP** | Envío de correos de notificación (éxito/fallo) |

---

## Arquitectura general

```
GitHub (código Java/Maven)
       │  polling cada minuto (cron: * * * * *)
       ▼
   Jenkins ────────────────────────────┐
       │ mvn clean package             │
       ▼                                │
   Tomcat (servidor de pruebas)     SonarQube
       │                                │
   App desplegada              Análisis de calidad
                                          │
                                 Quality Gate (Reliability Rating)
                                          │
                                ┌─────────┴─────────┐
                            ✅ Pasa              ❌ Falla
                          Correo éxito          Correo fallo
```

---

## Componente 1: Aplicación Java (Maven)

Una aplicación web simple empaquetada como `.war`, compuesta por:

- **`pom.xml`**: define el proyecto Maven, sus dependencias (Servlet API) y los plugins necesarios (`maven-war-plugin` para empaquetar, `sonar-maven-plugin` para el análisis).
- **`HelloServlet.java`**: un servlet simple que responde en `/hello`.
- **`index.jsp`**: página de inicio de la aplicación.
- **`web.xml`**: configuración estándar de la aplicación web.

**Para qué sirve:** es el "producto" que el pipeline construye, analiza y despliega. Su simplicidad permite enfocarse en demostrar el flujo CI/CD y no en la complejidad del código.

---

## Componente 2: Jenkins

**Rol:** es el cerebro del pipeline. Se ejecuta en un contenedor Docker y orquesta todo el flujo mediante un **Jenkinsfile** (pipeline declarativo) ubicado en la raíz del repositorio.

### Configuración clave

- **Plugins instalados:** Maven Integration, Git, Deploy to container, SonarQube Scanner, Blue Ocean, Email Extension.
- **Maven Tool:** configurado en *Global Tool Configuration* para que el pipeline pueda ejecutar `mvn`.
- **SonarQube Server:** configurado en *System* con la URL del contenedor (`http://sonarqube:9000`) y un token de autenticación generado en SonarQube.
- **Credenciales de Tomcat:** usuario/contraseña (`admin`/`admin123`) guardadas como `Username with password` para que Jenkins pueda desplegar el `.war` vía el *Tomcat Manager*.
- **SMTP (Gmail):** configurado con `smtp.gmail.com`, puerto 465 (SSL) y una **App Password** de Google, para el envío de correos.
- **Trigger:** `pollSCM('* * * * *')` — revisa el repositorio de GitHub cada minuto y dispara el pipeline si detecta cambios nuevos (commits).

### Etapas del pipeline (Jenkinsfile)

1. **Checkout** → descarga el código desde GitHub.
2. **Build** → compila y empaqueta la app con `mvn clean package`.
3. **Deploy a Tomcat** → sube el `.war` al Tomcat de pruebas usando el plugin *Deploy to container*.
4. **Análisis SonarQube** → ejecuta `mvn sonar:sonar`, enviando los resultados al servidor de SonarQube.
5. **Quality Gate** → espera el resultado del análisis (`waitForQualityGate`); si la regla configurada no se cumple, el pipeline se marca como fallido.
6. **Post (success/failure)** → envía un correo distinto según el resultado del Quality Gate.

---

## Componente 3: Apache Tomcat

**Rol:** actúa como **servidor de pruebas** donde se despliega automáticamente la aplicación compilada (`mi-app.war`).

### Configuración relevante

- **`tomcat-users.xml`**: define el usuario `admin` con los roles `manager-gui` y `manager-script`, necesarios para que Jenkins pueda desplegar remotamente vía la API del *Manager*.
- **`context.xml`** (en `webapps/manager/META-INF/`): por defecto el *Manager* de Tomcat solo acepta conexiones desde `localhost`. Se sobreescribió con un `Valve` que permite el acceso desde cualquier IP (`allow=".*"`), necesario porque Jenkins y Tomcat corren en contenedores distintos.
- Se construyó con un **Dockerfile** propio para copiar el *Manager* (que en la imagen oficial no viene habilitado por defecto) y aplicar la configuración de `context.xml` de forma confiable durante el build de la imagen.

**Para qué sirve:** simula un entorno de "producción/pruebas" real donde la aplicación queda accesible en `http://localhost:8090/mi-app`.

---

## Componente 4: SonarQube

**Rol:** analiza estáticamente el código fuente en busca de bugs, vulnerabilidades, code smells, duplicaciones y cobertura, y determina si el código cumple con un estándar de calidad definido (**Quality Gate**).

### Configuración relevante

- **Proyecto `mi-app`**: creado manualmente, con un **token de autenticación** generado para que Jenkins pueda enviar los resultados del análisis.
- **Webhook**: configurado en `Administration → Configuration → Webhooks` apuntando a `http://jenkins:8080/sonarqube-webhook/`, para que SonarQube notifique a Jenkins cuando termina de procesar el análisis (esto es lo que permite que `waitForQualityGate` no se quede esperando indefinidamente).
- **Quality Gate personalizado ("ReglaMateo")**: se creó una condición sobre **Overall Code**:
    - **Métrica:** Reliability Rating
    - **Operador:** is worse than
    - **Valor:** A

  Esto significa: *si el código tiene al menos un bug de confiabilidad (rating distinto de A), el Quality Gate falla*.

- El Quality Gate debe **asignarse explícitamente al proyecto** (`Project Settings → Quality Gate`), de lo contrario usa el por defecto ("Sonar way").

**Para qué sirve:** es el "filtro de calidad" del pipeline. Determina automáticamente si el código recién compilado es apto para continuar (correo de éxito) o debe corregirse antes de avanzar (correo de fallo).

---

## ¿Por qué se eligió la regla "Reliability Rating"?

El profesor dejó libre la elección de la regla. Se escogió **Reliability Rating is worse than A** porque:

- Es **fácil de provocar y revertir**: basta un pequeño cambio en el código (ej. comentar un `<title>`, o no validar un valor `null`) para hacer fallar o pasar la regla.
- Es **visualmente clara** en el dashboard de SonarQube: se ve exactamente qué archivo y línea generó el problema.
- Refleja un **caso de uso real**: en un pipeline profesional no debería permitirse desplegar código con bugs conocidos.
- No requiere configuración adicional (a diferencia de *Coverage*, que necesitaría pruebas unitarias, o *Duplications*, que requeriría código repetido).

---

## El rol del `index.jsp` en las pruebas

El archivo `index.jsp` es la **página principal** de la aplicación (HTML + JSP). Se usó como punto de prueba para el demo en lugar del `.java` porque las reglas de *Reliability* sobre HTML son muy predecibles y visuales:

- Comentar la línea `<title>...</title>` hace que SonarQube reporte automáticamente:
    - *"Add a `<title>` tag to this page"* (Reliability, Medium)
    - *"Add 'lang' and/or 'xml:lang' attributes..."* (si también se quita `lang`)

Esto permite mostrar en vivo el ciclo completo: **comentar una línea → push → Jenkins detecta el cambio → SonarQube detecta el bug → Quality Gate falla → llega el correo de fallo**. Y al revertir el cambio, se repite el ciclo mostrando el correo de éxito.

> Nota: `<title>` define el texto que aparece en la **pestaña del navegador** (no es visible dentro de la página), mientras que `<h1>` es el encabezado visible en el cuerpo del documento. Por eso, al comentar el `<title>`, la página sigue viéndose igual pero SonarQube detecta el problema.

---

## Flujo completo de demostración

1. Se realiza un cambio en el código (ej. comentar `<title>` en `index.jsp`).
2. Se hace `git push` al repositorio en GitHub.
3. Jenkins detecta el cambio en máximo 1 minuto (polling SCM).
4. Jenkins ejecuta automáticamente: checkout → build con Maven → deploy a Tomcat → análisis con SonarQube → evaluación del Quality Gate.
5. Si el Quality Gate **falla** (Reliability peor que A): se envía un correo indicando que la regla no se cumplió.
6. Se corrige el código (se descomenta el `<title>`), se hace push de nuevo.
7. Jenkins repite el flujo automáticamente y, al pasar el Quality Gate, se envía un correo de éxito.

---

## Resumen de puertos y accesos

| Servicio | URL | Credenciales |
|---|---|---|
| Jenkins | http://localhost:8080 | Usuario admin creado en setup |
| Tomcat | http://localhost:8090 | `admin` / `admin123` |
| App desplegada | http://localhost:8090/mi-app | — |
| SonarQube | http://localhost:9000 | `admin` / (cambiada en primer login) |