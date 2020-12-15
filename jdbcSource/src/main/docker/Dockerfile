FROM openjdk:11-jre-slim
RUN mkdir /app
ADD jdbcSource.tar /app
WORKDIR /app
#ENTRYPOINT ["tail", "-f", "/dev/null"]
ENTRYPOINT ["./jdbcsource/bin/jdbcSource"]