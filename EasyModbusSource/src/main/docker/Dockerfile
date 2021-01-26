FROM openjdk:11-jre-slim
RUN mkdir /app
ADD EasyModbusSource.tar /app
WORKDIR /app
ENTRYPOINT ["./EasyModbusSource/bin/EasyModbusSource"]