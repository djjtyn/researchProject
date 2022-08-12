FROM maven:3.5.2-jdk-8-alpine AS MAVEN_TOOL_CHAIN
COPY pom.xml /tmp/pom.xml
COPY src /tmp/src
WORKDIR /tmp/
ENV _JAVA_OPTIONS=-Xmx1400m
RUN mvn package
CMD ["java", "-cp", "target/fecsimulator-0.0.1-SNAPSHOT.jar", "fecsimulator.HospitalSimulation"]