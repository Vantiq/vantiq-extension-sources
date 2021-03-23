FROM openjdk:11-jre-slim
RUN mkdir /app
ADD CSVSource.tar /app
WORKDIR /app
ENTRYPOINT ["./CSVSource/bin/CSVSource"]