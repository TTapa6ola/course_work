package main.source;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedList;

@SpringBootApplication
@RestController
@JsonAutoDetect
public class Schedule {
    @JsonDeserialize(as = LinkedList.class)
    public LinkedList<Ship> schedule = new LinkedList<>();

    @GetMapping("/generate-schedule")
    public LinkedList<Ship> generateSchedule(){
        int numberOfShips = (int) (Math.random() * 20 + 80);
        for (int i = 0; i < numberOfShips; i++) {
            Ship ship = Ship.generateShip();
            ship.setName(Integer.toString(i+1));
            schedule.add(ship);
        }

        return this.schedule;
    }

    public void showSchedule(){
        for (Ship ship : schedule) {
            System.out.println(ship.tosString());
        }
    }

    public void setSchedule(LinkedList<Ship> schedule) {
        this.schedule = schedule;
    }

    public void addEntry(Ship ship){
        schedule.addLast(ship);
    }

    public LinkedList<Ship> getSchedule() {
        return schedule;
    }

    public static void main(String[] args) {
        Schedule schedule = new Schedule();
        schedule.showSchedule();
    }
}