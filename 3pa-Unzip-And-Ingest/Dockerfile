FROM adoptopenjdk/openjdk13-openj9:jdk-13.0.2_8_openj9-0.18.0-alpine-slim
COPY build/libs/3pa-Unzip-And-Ingest-*-all.jar 3pa-Unzip-And-Ingest.jar
EXPOSE 8080
CMD ["java", "-Dcom.sun.management.jmxremote", "-Xmx128m", "-XX:+IdleTuningGcOnIdle", "-Xtune:virtualized", "-jar", "3pa-Unzip-And-Ingest.jar"]
