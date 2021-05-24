package main.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Scanner;

@SpringBootApplication
@RestController
public class WriteToJson {
    @GetMapping("get-schedule")
    public static File writeToJson(String args) throws IOException {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        RestTemplate restTemplate = builder.build();
        String stringSchedule = restTemplate.getForObject("http://localhost:8090/generate-schedule", String.class);

        ObjectMapper objectMapper = new ObjectMapper();
        LinkedList scheduleList = objectMapper.readValue(stringSchedule, LinkedList.class);
        Schedule schedule = new Schedule();
        schedule.setSchedule(scheduleList);

        addEntry(schedule);

        Path path = args == null ? Paths.get("schedule.json") : Paths.get(args);

        ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
        Writer jsonSchedule = new FileWriter(String.valueOf(path));
        objectWriter.writeValue(jsonSchedule, schedule);

        return new File(String.valueOf(path));
    }

    @GetMapping(value = "/get-schedule-by-name", params = {"args"})
    public static Schedule getScheduleByName(@RequestParam String args) throws IOException {
        File file = new File(args);

        if (!file.exists()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid name of file");
        }

        Path path = Paths.get(args);

        ObjectMapper mapper = new ObjectMapper();
        Schedule schedule = mapper.readValue(new File(String.valueOf(path)), Schedule.class);

        return schedule;
    }

    @PostMapping("/post-result")
    public static void postResult(@RequestBody String result) throws IOException {
        Path path = Paths.get("result.json");

        ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
        Writer writer = new FileWriter(String.valueOf(path));
        objectWriter.writeValue(writer, result);
    }

    public static void addEntry(Schedule schedule){
        while(true) {
            System.out.println("Enter new ship?\n yes \t no");
            Scanner scanner = new Scanner(System.in);
            String answer = scanner.nextLine();
            if (answer.equals("yes")) {
                System.out.println("Enter name");
                String name = scanner.nextLine();
                System.out.println("Enter cargo type");
                String cargo = scanner.nextLine();
                Cargo cargo_type = switch (cargo) {
                    case "BULK" -> Cargo.BULK;
                    case "LIQUID" -> Cargo.LIQUID;
                    case "CONTAINER" -> Cargo.CONTAINER;
                    default -> throw new IllegalStateException("Unexpected value: " + cargo);
                };
                System.out.println("Enter date");
                int date = scanner.nextInt();
                System.out.println("Enter quantity");
                int quantity = scanner.nextInt();
                Ship ship = new Ship(date, name, cargo_type, quantity);
                schedule.addEntry(ship);
            } else {
                break;
            }
        }
    }
}
