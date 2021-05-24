package parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import generator.Cargo;
import generator.Schedule;
import generator.Ship;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class WriteToJson {
    public static void writeToJson(Schedule schedule) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        addEntry(schedule);
        objectMapper.writeValue(new File("./file.json"), schedule);
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

    public static void main(String[] args) throws IOException {
        Schedule schedule1 = new Schedule();
        schedule1.generateSchedule();
        WriteToJson.writeToJson(schedule1);
    }
}
