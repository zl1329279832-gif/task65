FROM openjdk:8-jre-slim

LABEL maintainer="springbootniyfl"

WORKDIR /app

COPY target/springbootniyfl-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
