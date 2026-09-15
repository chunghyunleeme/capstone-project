FROM gradle:8-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle :app:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/app/build/libs/app-all.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]