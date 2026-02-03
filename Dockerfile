FROM eclipse-temurin:21-jdk

# Pyroscope Application Name and Server Address
ARG PYROSCOPE_APPLICATION_NAME
ARG PYROSCOPE_SERVER_ADDRESS

# Pass ARGs as ENV variables
ENV PYROSCOPE_APPLICATION_NAME=${PYROSCOPE_APPLICATION_NAME}
ENV PYROSCOPE_SERVER_ADDRESS=${PYROSCOPE_SERVER_ADDRESS}

# Application JAR
ARG JAR_FILE=build/libs/*.jar

# Copy the application JAR to the container
COPY ${JAR_FILE} app.jar

# Download and add Pyroscope Java Agent
ADD https://github.com/grafana/pyroscope-java/releases/download/v0.15.2/pyroscope.jar /pyroscope.jar

# Set the entry point
#ENTRYPOINT ["java", "-javaagent:/pyroscope.jar", "-jar", "app.jar"]
ENTRYPOINT ["java", "-javaagent:/pyroscope.jar", "-jar", "app.jar"]