FROM openjdk:11-jre-slim
RUN mkdir /app
ADD opcuaSource.tar /app
WORKDIR /app
ENTRYPOINT ["./opcuaSource/bin/opcuaSource"]