# 1단계: 빌드
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# 2단계: 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 mathmap
COPY --from=build /app/target/mathmap.jar app.jar
USER mathmap
ENV MATHMAP_SECURE_COOKIE=true
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
