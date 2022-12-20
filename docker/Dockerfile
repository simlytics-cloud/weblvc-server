FROM gcr.io/distroless/java17-debian11:nonroot
COPY web-lvc-server_2.13-1.0.1-SNAPSHOT-allinone.jar /weblvcserver.jar
WORKDIR /
EXPOSE 8443
ENTRYPOINT ["java", "-cp", "/weblvcserver.jar:/:/config", "-jar", "/weblvcserver.jar"]