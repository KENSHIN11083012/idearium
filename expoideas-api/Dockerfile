# syntax=docker/dockerfile:1
# API de Expoideas. Compila con el JDK y corre solo con el JRE, como usuario sin
# privilegios. Las pruebas corren en la integración continua, no aquí.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Dependencias primero: esta capa se reutiliza mientras el pom no cambie.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/expoideas-api-*.jar /app.jar


FROM eclipse-temurin:25-jre

# Parches de seguridad del sistema que la imagen base todavía no trae.
RUN apt-get update \
    && apt-get -y upgrade \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system expoideas \
    && useradd --system --gid expoideas --home-dir /app --no-create-home expoideas \
    && mkdir -p /app /data/files \
    && chown expoideas:expoideas /data/files

WORKDIR /app
COPY --from=build /app.jar app.jar

# El montón de Java usa hasta el 60 % de la memoria del contenedor. El resto es
# para lo que la JVM gasta fuera del montón (clases cargadas, hilos, código
# compilado): con un porcentaje mayor, el contenedor llegaría a su límite antes
# que el montón al suyo y Docker lo mataría sin aviso.
ENV SPRING_PROFILES_ACTIVE=prod \
    FILES_DIR=/data/files \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"

# Los archivos subidos deben sobrevivir a una actualización de la imagen.
VOLUME /data/files

# Qué versión es esta imagen: la etiqueta con la que la publica la integración
# continua (el commit). Va al final para no invalidar las capas de arriba.
ARG APP_VERSION=local
ENV APP_VERSION=$APP_VERSION
LABEL org.opencontainers.image.title="Idearium API" \
      org.opencontainers.image.revision=$APP_VERSION

USER expoideas
EXPOSE 8080

# La imagen no trae curl: la comprobación usa bash contra el endpoint de readiness.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'

ENTRYPOINT ["java", "-jar", "app.jar"]
