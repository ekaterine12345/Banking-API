FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN addgroup --system banking && adduser --system --ingroup banking banking

COPY --from=build /app/build/libs/*.jar app.jar

USER banking

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
