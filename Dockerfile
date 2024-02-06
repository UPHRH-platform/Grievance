FROM openjdk:11
MAINTAINER haridas <haridas.kakunje@tarento.com>
ADD target/grievance-0.0.1-SNAPSHOT.jar grievance-0.0.1-SNAPSHOT.jar
#ADD public/emails emails
ENTRYPOINT ["java", "-jar", "/grievance-0.0.1-SNAPSHOT.jar"]
EXPOSE 8088
