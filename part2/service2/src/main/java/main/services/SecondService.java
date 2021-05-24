package main.services;

import main.source.WriteToJson;
import org.springframework.boot.SpringApplication;

import java.util.Collections;

public class SecondService {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WriteToJson.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", "8080"));
        app.run(args);
    }
}
