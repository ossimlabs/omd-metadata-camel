ARG DOCKER_REGISTRY=nexus-docker-private-group.ossim.io
ARG BASE_IMAGE_TAG=release

# Jib uses distroless java as the default base image
FROM adoptopenjdk/openjdk13-openj9:jdk-13.0.2_8_openj9-0.18.0-alpine-slim

# Multiple copy statements are used to break the app into layers, allowing for faster rebuilds after small changes
# COPY dependencyJars /app/libs
# COPY snapshotDependencyJars /app/libs
# COPY projectDependencyJars /app/libs
COPY ./src/main/resources /app/resources
COPY ./src/main/groovy/io/ossim/unzipAndIngest /app/classes

# Jib's extra directory ("src/main/jib" by default) is used to add extra, non-classpath files
COPY src/main/jib /

#Copy UnzipAndIngest
COPY ./build/libs/unzip-and-ingest-0.2.jar /

# Jib's default entrypoint when container.entrypoint is not set
ENTRYPOINT ["java",
    jib.container.jvmFlags, 
    "-cp", 
    "/app/resources:/app/classes:/app/libs/*", 
    jib.container.mainClass
    ]
CMD [jib.container.args]

    containerizingMode = 'packaged'
    container {
        // Run as userid 1001 added so entrypoint doesn't run as root user.
        // Userid 1001 is common for other Omar applications and NFS mounts.
        user = '1001:1001'
        environment = [
            JAVA_APP_DIR: '/app', 
            JAVA_MAIN_CLpASS: mainClassName,
            JAVA_CLASSPATH: '/app/classpath/*:/app/libs/*'
        ]
        ports = ['8080']    
        creationTime = 'USE_CURRENT_TIMESTAMP'
        entrypoint = ['/app/run_java.sh']
    }        