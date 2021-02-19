FROM openjdk:11-jre-slim
RUN mkdir /app
ADD testConnector.tar /app
WORKDIR /app
ENTRYPOINT ["./testConnector/bin/testConnector"]