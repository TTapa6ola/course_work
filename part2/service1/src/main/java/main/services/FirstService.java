package main.services;

import main.source.Schedule;
import org.springframework.boot.SpringApplication;

import java.util.Collections;

public class FirstService {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Schedule.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", "8090"));
        app.run(args);
    }
}
