FROM openjdk:11-jre-slim
RUN mkdir /app
ADD FTPClientSource.tar /app
WORKDIR /app
ENTRYPOINT ["./FTPClientSource/bin/FTPClientSource"]