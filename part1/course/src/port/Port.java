package port;

import com.fasterxml.jackson.databind.ObjectMapper;
import generator.Cargo;
import generator.Schedule;
import generator.Ship;
import parser.WriteToJson;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static generator.Cargo.*;

public class Port {
    private LinkedList<Ship> schedule;
    public static ConcurrentMap<Cargo, CopyOnWriteArrayList<Ship>> mapOfShips = new ConcurrentHashMap<>();
    public static ConcurrentMap<Cargo, CopyOnWriteArrayList<Ship>> mapOfQueues = new ConcurrentHashMap<>();
    public static ConcurrentMap<Cargo, CopyOnWriteArrayList<Ship>> mapOfShipsAfterModeling = new ConcurrentHashMap<>();
    public static CyclicBarrier barrier;
    public static volatile Timer timer;

    public static ConcurrentMap<Cargo, List<Thread>> mapOfThreadCranes = new ConcurrentHashMap<>();
    public static ConcurrentMap<Cargo, List<Crane>> mapOfCranes = new ConcurrentHashMap<>();
    private ConcurrentMap<Cargo, Integer> countOfCranes = new ConcurrentHashMap<>();

    private Map<Cargo, Integer> mapOfFine = new HashMap<>();
    private int totalFine;
    private int fineForWaiting;
    private int fineForExtraStanding;
    private int costOfCranes;

    public static double meanQueueLength;
    private double meanWaitingTime;
    private int maxDelay;
    private double meanDelay;

    public Port(Schedule schedule) {
        this.schedule = schedule.getSchedule();
    }

    public void runPort() throws InterruptedException {
        setAndCalcRandomStat();
        removeExtraShips();
        findOptimal();
    }

    private void findOptimal() throws InterruptedException {
        for (Cargo cargo : Cargo.values()) {
            countOfCranes.put(cargo, 1);
        }

        runCargos();
        calcFine();

        ConcurrentMap<Cargo, Integer> oldFines = new ConcurrentHashMap<>();
        ConcurrentMap<Cargo, Boolean> mapOfFlags = new ConcurrentHashMap<>();
        for (Cargo cargo : Cargo.values()) {
            oldFines.put(cargo, mapOfFine.get(cargo));
            mapOfFlags.put(cargo, false);
        }

        while (!(mapOfFlags.get(BULK) && mapOfFlags.get(CONTAINER) && mapOfFlags.get(LIQUID))) {
            for (Cargo cargo : Cargo.values()) {
                if (!mapOfFlags.get(cargo)) {
                    countOfCranes.put(cargo, countOfCranes.get(cargo) + 1);
                }
            }

            runCargos();
            calcFine();

            for (Cargo cargo : Cargo.values()) {
/*                System.out.println("old fine " + cargo + oldFines.get(cargo));
                System.out.println("new fine " + cargo + mapOfFine.get(cargo));
                System.out.println("number of cranes " + countOfCranes.get(cargo));*/
                if (mapOfFine.get(cargo) <= oldFines.get(cargo)) {
                    oldFines.put(cargo, mapOfFine.get(cargo));
                } else if (!mapOfFlags.get(cargo)){
                    countOfCranes.put(cargo, countOfCranes.get(cargo) - 1);
                    mapOfFlags.put(cargo, true);
                }
            }
        }

        runCargos();
        calcFine();
    }

    private void runCargos() throws InterruptedException {
        resetStat();

        for (Cargo cargo : Cargo.values()) {
            runCargo(cargo);

            for (Thread thread : mapOfThreadCranes.get(cargo)) {
                thread.start();
            }

            for (Thread thread : mapOfThreadCranes.get(cargo)) {
                thread.join();
            }
        }

        for (Cargo cargo : Cargo.values()) {
            for (Ship ship : mapOfShipsAfterModeling.get(cargo)) {
                meanWaitingTime += ship.getUnloadStartTime() - ship.getDateTime();
            }
        }

        meanQueueLength /= 43200;
        meanWaitingTime /= schedule.size();
    }


    private void runCargo(Cargo cargo) throws InterruptedException {
        CopyOnWriteArrayList<Ship> ships = getShipsOfType(cargo);
        mapOfShips.put(cargo, ships);
        mapOfQueues.put(cargo, new CopyOnWriteArrayList<>());
        mapOfCranes.put(cargo, new LinkedList<>());

        ships.sort(Comparator.comparingInt(Ship::getDateTime));

        for (Ship ship : ships) {
            Timer.mapOfFlags.put(ship, true);
        }

        List<Thread> threadCranes = new LinkedList<>();
        for (int i = 0; i < countOfCranes.get(cargo); i++) {
            mapOfCranes.get(cargo).add(new Crane(cargo));
        }

        timer = new Timer();
        barrier = new CyclicBarrier(mapOfCranes.get(cargo).size(), timer);

        for (Crane crane : mapOfCranes.get(cargo)) {
            threadCranes.add(new Thread(crane));
        }

        mapOfThreadCranes.put(cargo, threadCranes);
    }
    
    private void setAndCalcRandomStat() {
        maxDelay = 0;
        for (Ship ship : schedule) {
            int comingTime = (int) (Math.random() * 14 * 1440 - 7 * 1440);
            int standingTime = (int) (Math.random() * 1440);
            ship.setDateTime(ship.getDateTime() + comingTime);
            ship.setExtraStandingTime(standingTime);
            fineForExtraStanding += (standingTime / 60) * 100;
            maxDelay = Math.max(maxDelay, standingTime);
            meanDelay += standingTime;
        }
        meanDelay /= schedule.size();
    }

    private CopyOnWriteArrayList<Ship> getShipsOfType(Cargo cargo){
        CopyOnWriteArrayList<Ship> list = new CopyOnWriteArrayList<>();
        for (Ship ship : schedule) {
            if (ship.getCargoType() == cargo) {
                Ship newShip = new Ship(ship);
                list.add(newShip);
            }
        }
        return list;
    }

    private void removeExtraShips(){
        List<Ship> shipsToRemove = new LinkedList<>();
        for (Ship ship : schedule) {
            if (ship.getDateTime() > 30 * 1440 || ship.getDateTime() <= 0) {
                shipsToRemove.add(ship);
            }
        }

        schedule.removeAll(shipsToRemove);
    }

    private void calcFine() {
        for (Cargo cargo : Cargo.values()) {
            int tempWaitingFine = 0;
            int tempCostOfCranes = 30000 * (countOfCranes.get(cargo) - 1);

            for (Ship ship : mapOfShipsAfterModeling.get(cargo)) {
                tempWaitingFine += ((ship.getUnloadStartTime() - ship.getDateTime()) / 60) * 100;
            }

            mapOfFine.put(cargo, tempWaitingFine + tempCostOfCranes);
            fineForWaiting += tempWaitingFine;
            totalFine += tempWaitingFine;
            costOfCranes += tempCostOfCranes;
        }
        totalFine += fineForExtraStanding;
    }

    private void resetStat() {
        fineForWaiting = 0;
        totalFine = 0;
        costOfCranes = 0;
        for (Cargo cargo : Cargo.values()) {
            mapOfFine.put(cargo, 0);
        }
        meanWaitingTime = 0;
        meanQueueLength = 0;
    }

    public void showStat() {
        CopyOnWriteArrayList<Ship> list = new CopyOnWriteArrayList<>();
        for (Cargo cargo : Cargo.values()) {
            list.addAll(mapOfShipsAfterModeling.get(cargo));
        }
        list.sort(Comparator.comparingInt(Ship::getDateTime));
        for (Ship ship : list) {
            System.out.println(ship.receiveStatForPort());
        }

        System.out.println("NUMBER OF SHIPS: " + list.size());
        System.out.println("COST OF CRANES: " + costOfCranes);
        System.out.println("FINE FOR WAITING QUEUE: " + fineForWaiting);
        System.out.println("FINE FOR EXTRA STANDING: " + fineForExtraStanding);
        System.out.println("TOTAL FINE: " + totalFine);
        System.out.println("MEAN LENGTH OF QUEUE: " + String.format("%.2f", meanQueueLength) + " SHIPS");
        System.out.println("MEAN WAITING TIME: " + Ship.getTimeInString((int) meanWaitingTime));
        System.out.println("MAX DELAY: " + Ship.getTimeInString(maxDelay));
        System.out.println("MEAN DELAY: " + Ship.getTimeInString((int) meanDelay));
        for (Cargo cargo : Cargo.values()) {
            System.out.println("NUMBER OF " + cargo + " CRANES " + countOfCranes.get(cargo));
        }
    }

    public static Schedule parseFromJson() throws IOException {
        Path pathToSchedule = Paths.get("file.json");
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(String.valueOf(pathToSchedule)), Schedule.class);
    }


    public static void main(String[] args) throws InterruptedException, IOException {
        Schedule schedule = new Schedule();
        schedule.generateSchedule();
        WriteToJson.writeToJson(schedule);
        Schedule schedule1 = Port.parseFromJson();
        schedule1.showSchedule();

        Port port = new Port(schedule1);
        System.out.println("Port");
        port.runPort();

        port.showStat();
    }
}
