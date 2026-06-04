FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY .mvn /workspace/.mvn
COPY pom.xml /workspace/pom.xml
COPY backend/pom.xml /workspace/backend/pom.xml
RUN mvn -s .mvn/settings.xml -gs .mvn/settings.xml -q -pl backend -am dependency:go-offline

COPY backend/src /workspace/backend/src
RUN mvn -s .mvn/settings.xml -gs .mvn/settings.xml -q -pl backend -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
