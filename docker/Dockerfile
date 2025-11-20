FROM mcr.microsoft.com/azure-cli:latest as builder

FROM eclipse-temurin:17-jdk-jammy

COPY --from=builder /usr /usr
COPY --from=builder /home /home
COPY --from=builder /lib /lib
COPY --from=builder /bin /bin
COPY --from=builder /etc /etc

RUN apt-get update && apt-get install -y maven curl git && rm -rf /var/lib/apt/lists/*

RUN az --version && java -version && mvn -version

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update && apt-get install -y curl python3 python3-pip && rm -rf /var/lib/apt/lists/*
RUN curl -sL https://aka.ms/InstallAzureCLIDeb | bash
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/azure-resource-manager-dashboard-1.0.0.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
