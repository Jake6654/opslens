FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY . .
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

# run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
# java -jar app.jar it runs app automatically
ENTRYPOINT ["java", "-jar", "app.jar"]