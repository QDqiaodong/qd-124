ARG DOCKER_REGISTRY=

FROM ${DOCKER_REGISTRY}maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -s /root/.m2/settings.xml compile -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -B --no-transfer-progress

COPY src ./src
RUN mvn -s /root/.m2/settings.xml clean package -Dmaven.test.skip=true -B --no-transfer-progress

FROM ${DOCKER_REGISTRY}eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

EXPOSE 8108

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
