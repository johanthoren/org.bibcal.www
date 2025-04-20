FROM clojure:lein AS build

WORKDIR /app
COPY . /app

# Print Java version for debugging
RUN java -version && lein --version
RUN lein ring uberjar

FROM clojure:lein

WORKDIR /app
COPY --from=build /app/target/www-*-standalone.jar app.jar

EXPOSE 8080

# General JVM settings that work across versions
CMD ["java", "-Xms256m", "-Xmx384m", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
