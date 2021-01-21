FROM openjdk:11-jre-slim
RUN mkdir /app
ADD udpSource.tar /app
WORKDIR /app
ENTRYPOINT ["./udpSource/bin/udpSource"]