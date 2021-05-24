package main.source;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.Semaphore;

public class Ship {
    private int dateTime;
    private String name;
    private Cargo cargoType;
    private double quantity;
    private int unloadStartTime;
    private int unloadFinishTime;
    private int extraStandingTime;

    @JsonIgnore
    public Semaphore semaphore = new Semaphore(2);

    public int getUnloadStartTime() {
        return unloadStartTime;
    }

    public String getName() {
        return name;
    }

    public void setUnloadStartTime(int unloadStartTime) {
        this.unloadStartTime = unloadStartTime;
    }

    public int getUnloadFinishTime() {
        return unloadFinishTime;
    }

    public void setUnloadFinishTime(int unloadFinishTime) {
        this.unloadFinishTime = unloadFinishTime;
    }

    public int getExtraStandingTime() {
        return extraStandingTime;
    }

    public void setExtraStandingTime(int extraStandingTime) {
        this.extraStandingTime = extraStandingTime;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public int getDateTime() {
        return dateTime;
    }

    public Ship(int date_time, String name, Cargo cargoType, int quantity) {
        this.dateTime = date_time;
        this.name = name;
        this.cargoType = cargoType;
        this.quantity = quantity;
    }

    public Ship(int date_time, Cargo cargoType, int quantity) {
        this.dateTime = date_time;
        this.cargoType = cargoType;
        this.quantity = quantity;
    }

    public Ship(Ship ship) {
        this.dateTime = ship.dateTime;
        this.name = ship.name;
        this.cargoType = ship.cargoType;
        this.quantity = ship.quantity;
        this.unloadStartTime = ship.unloadStartTime;
        this.unloadFinishTime = ship.unloadFinishTime;
        this.extraStandingTime = ship.extraStandingTime;
        this.semaphore = ship.semaphore;
    }

    public Ship() {};

    public void setDateTime(int dateTime) {
        this.dateTime = dateTime;
    }

    public double getQuantity() {
        return quantity;
    }

    public Cargo getCargoType() {
        return cargoType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static Ship generateShip() {
        int time = (int) (Math.random() * 1440 * 30);
        int cargo = (int) (Math.random() * 3);
        Cargo cargo_type = switch (cargo) {
            case 0 -> Cargo.BULK;
            case 1 -> Cargo.CONTAINER;
            case 2 -> Cargo.LIQUID;
            default -> Cargo.BULK;
        };
        int mult = (cargo_type == Cargo.CONTAINER) ? 5000 : 25000;

        int quantity = (int) (Math.random() * mult);

        return new Ship(time, cargo_type, quantity);
    }

    public static String getTimeInString(int date_time){
        return date_time / 1440 + ":" + (date_time % 1440) / 60 + ":" + (date_time % 1440) % 60;
    }

    public String tosString() {
        String time = getTimeInString(dateTime);
        String cargo = switch (cargoType) {
            case BULK -> " BULK ";
            case LIQUID -> " LIQUID ";
            case CONTAINER -> " CONTAINER ";
        };
        int productivity = switch (cargoType) {
            case BULK -> 600;
            case LIQUID -> 720;
            case CONTAINER -> 120;
        };
        String standingTime = getTimeInString((int) (quantity / productivity * 60));
        return "DATE: " + time + "\tNAME: " + name + "\t" + cargo + quantity + " STANDING TIME: " + standingTime;
    }

    public String receiveStatForPort(){
        String time = getTimeInString(dateTime);
        int waitingTime =   unloadStartTime - dateTime;
        int trueUnloadingTime = unloadFinishTime - unloadStartTime;
        String trueUnloadingTimeString = getTimeInString(trueUnloadingTime);
        String waiting_time = getTimeInString(waitingTime);
        String start_unload_time = getTimeInString(dateTime + waitingTime);
        return "NAME: " + name + " DATE: " + time + " WAITING TIME: " +  waiting_time +
                " UNLOAD STARTED AT: " + start_unload_time + " AND LASTS: " + trueUnloadingTimeString + " CARGO " + cargoType + " end at " + getTimeInString(unloadFinishTime);
    }
}
