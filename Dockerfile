FROM node:24-alpine AS web-builder

WORKDIR /app/src/main/web
COPY src/main/web/package*.json ./
RUN npm ci
COPY src/main/web ./
COPY src/main/resources/test-suites /app/src/main/resources/test-suites
RUN npm run build

FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=web-builder /app/target/generated-resources ./target/generated-resources

RUN mvn package -DskipTests -Dplugin.prettier.skip=true

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/otp-sentinel-*.jar app.jar

EXPOSE 8080
# Mount the deployment configuration at /app/config.kdl when starting the container.
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["/app/config.kdl"]
