# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
# Convert CRLF to LF in case of Windows line endings in mvnw
RUN sed -i 's/\r$//' mvnw
RUN chmod +x ./mvnw
# Download dependencies to cache them
RUN ./mvnw dependency:go-offline -B
# Copy source code and build
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
