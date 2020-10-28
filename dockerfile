ARG DOCKER_REGISTRY=nexus-docker-private-group.ossim.io
ARG BASE_IMAGE_TAG=release

# Jib uses distroless java as the default base image
FROM adoptopenjdk/openjdk13-openj9:jdk-13.0.2_8_openj9-0.18.0-alpine-slim

# Multiple copy statements are used to break the app into layers, allowing for faster rebuilds after small changes
# COPY dependencyJars /app/libs
# COPY snapshotDependencyJars /app/libs
# COPY projectDependencyJars /app/libs
COPY ./src/main/resources /app/resources
COPY ./build/classes/groovy/main /app/classes


# Jib's extra directory ("src/main/jib" by default) is used to add extra, non-classpath files
COPY src/main/jib /app

#Copy UnzipAndIngest
COPY ./build/libs/unzip-and-ingest-0.2-all.jar /app/libs/


ENV JAVA_APP_DIR='/app'
ENV JAVA_APP_JAR=/app/libs/unzip-and-ingest-0.2-all.jar 
ENV JAVA_CLASSPATH=/app/classpath/*:/app/libs/*
EXPOSE 8080
RUN chmod 755 /app/run_java.sh
USER 1001:1001
CMD /app/run_java.sh
