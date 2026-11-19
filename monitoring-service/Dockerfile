FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY common-domain common-domain
COPY api-gateway api-gateway
COPY data-ingestion-service data-ingestion-service
COPY data-processing-service data-processing-service
COPY data-mining-service data-mining-service
COPY workflow-orchestrator-service workflow-orchestrator-service
COPY results-service results-service
COPY monitoring-service monitoring-service
RUN mvn -B -DskipTests package -pl common-domain,monitoring-service -am

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /workspace/monitoring-service/target/*.jar app.jar
EXPOSE 8086
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD curl -f http://localhost:8086/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
