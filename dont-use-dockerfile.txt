# Args to be passed with docker build 
# ARG DOCKER_REGISTRY=nexus-docker-private-group.ossim.io
# ARG BASE_IMAGE_TAG=release
#Base image
FROM adoptopenjdk/openjdk13-openj9:jdk-13.0.2_8_openj9-0.18.0-alpine-slim
#Copy relevent files
COPY ./src/main/resources /app/resources
COPY ./build/classes/groovy/main /app/classes
COPY src/main/jib /app
COPY ./build/libs/unzip-and-ingest-*-all.jar /app
#Create Enviromental Variables
ENV JAVA_APP_DIR='/app'
ENV JAVA_CLASSPATH=/app/classpath/*:/app/libs/*
#Expose port 8080
EXPOSE 8080
#Allow file to be executable
RUN chmod 755 /app/run_java.sh
#change user
USER 1001:1001
#Set default upon startup
CMD /app/run_java.sh
