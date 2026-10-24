# One image with the web app and the API. Spring Boot serves the built web app as static files,
# so everything runs on port 8080. Build: docker build -t chargingprices .  Run: docker run -p 8080:8080 chargingprices

# 1. Build the web app
FROM node:24-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# 2. Build the back end with the web app inside
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /backend
COPY backend/ ./
COPY --from=web /web/dist/ src/main/resources/static/
RUN ./gradlew bootJar --no-daemon --console=plain

# 3. Run on a small JRE image, as a non-root user
FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=backend /backend/build/libs/chargingprices-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
