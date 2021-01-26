FROM openjdk:11-jre-slim
RUN mkdir /app
ADD jdbcSource.tar /app
WORKDIR /app
ENTRYPOINT ["./jdbcSource/bin/jdbcSource"]