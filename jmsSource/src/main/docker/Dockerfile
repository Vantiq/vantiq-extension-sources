FROM openjdk:11-jre-slim
RUN mkdir /app
ADD jmsSource.tar /app
WORKDIR /app
ENTRYPOINT ["./jmsSource/bin/jmsSource"]