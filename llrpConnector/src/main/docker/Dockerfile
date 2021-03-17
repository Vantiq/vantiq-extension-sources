FROM openjdk:11-jre-slim
RUN mkdir /app
ADD llrpConnector.tar /app
WORKDIR /app
ENTRYPOINT ["./llrpConnector/bin/llrpConnector"]