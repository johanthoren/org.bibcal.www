FROM clojure:openjdk-17-tools-deps-slim-buster AS build

WORKDIR /app
COPY . /app

RUN apt-get update && apt-get install -y leiningen
RUN lein ring uberjar

FROM openjdk:17-slim-buster

WORKDIR /app
COPY --from=build /app/target/www-*-standalone.jar app.jar

EXPOSE 8080

ENV PORT=8080

CMD ["java", "-Dring.server.host=0.0.0.0", "-Dring.server.port=8080", "-jar", "app.jar"]