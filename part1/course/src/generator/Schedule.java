package generator;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.LinkedList;

@JsonAutoDetect
public class Schedule {
    @JsonDeserialize(as = LinkedList.class)
    public LinkedList<Ship> schedule = new LinkedList<>();

    public void generateSchedule(){
        int numberOfShips = (int) (Math.random() * 20 + 80);
        for (int i = 0; i < numberOfShips; i++) {
            Ship ship = Ship.generateShip();
            ship.setName(Integer.toString(i+1));
            schedule.add(ship);
        }
    }

    public void showSchedule(){
        for (Ship ship : schedule) {
            System.out.println(ship.tosString());
        }
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

