# Stage 1: Build the application
FROM maven:3.9-amazoncorretto-17 AS builder
# 1. สร้างไฟล์ settings.xml กำหนดค่า Proxy สำหรับ Maven
RUN mkdir -p /root/.m2 && echo '<settings>\
    <proxies>\
    <proxy>\
    <id>http-proxy</id>\
    <active>true</active>\
    <protocol>http</protocol>\
    <host>localhost</host>\
    <port>3128</port>\
    </proxy>\
    </proxies>\
    </settings>' > /root/.m2/settings.xml
WORKDIR /app
COPY pom.xml .
# 2. ดาวน์โหลด Dependencies (ผ่าน settings.xml โดยไม่ต้องใส่ -D proxy ในคำสั่ง)
RUN mvn dependency:resolve -B
# 3. บิลด์โปรเจกต์
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the minimal runtime image
FROM amazoncorretto:17-alpine

ENV http_proxy=http://localhost:3128
ENV https_proxy=http://localhost:3128
ENV HTTP_PROXY=http://localhost:3128
ENV HTTPS_PROXY=http://localhost:3128

WORKDIR /app

# Add a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built jar from the builder stage
COPY --from=builder /app/target/gateway-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "app.jar"]
CMD java -Dhttps.proxyHost=localhost -Dhttps.proxyPort=3128 -Dhttp.proxyHost=localhost -Dhttp.proxyPort=3128 -Dhttp.nonProxyHosts="localhost|127.0.0.1|vault|hiroshi-nlu|chatservice|chatservice2|host.docker.internal|ppgpt-mcp" -Dhttps.nonProxyHosts="localhost|127.0.0.1|vault|hiroshi-nlu|chatservice|chatservice2|host.docker.internal|ppgpt-mcp" -jar app.jar