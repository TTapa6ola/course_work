package main.source;


import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static main.source.Cargo.*;

public class Port {
    private static LinkedList<Ship> schedule;
    public static ConcurrentMap<Cargo, CopyOnWriteArrayList<Ship>> mapOfShips = new ConcurrentHashMap<>();
    public static ConcurrentMap<Cargo, CopyOnWriteArrayList<Ship>> mapOfQueues = new ConcurrentHashMap<>();
    public static ConcurrentMap<Cargo, CopyOnWriteArrayList<Ship>> mapOfShipsAfterModeling = new ConcurrentHashMap<>();
    public static CyclicBarrier barrier;
    public static volatile Timer timer;

    public static Map<Cargo, List<Thread>> mapOfThreadCranes = new HashMap<>();
    public static Map<Cargo, List<Crane>> mapOfCranes = new HashMap<>();
    private static Map<Cargo, Integer> countOfCranes = new HashMap<>();

    private static Map<Cargo, Integer> mapOfFine = new HashMap<>();
    private static int totalFine;
    private static int fineForWaiting;
    private static int fineForExtraStanding;
    private static int costOfCranes;

    public static double meanQueueLength;
    private static double meanWaitingTime;
    private static int maxDelay;
    private static double meanDelay;

    public Port(Schedule schedule) {
        Port.schedule = schedule.getSchedule();
    }

    public static void runPortSpring() throws IOException, InterruptedException {
        parseFromJson();
        runPort();
        putResultToJson();
    }

    public static void runPort() throws InterruptedException {
        setAndCalcRandomStat();
        removeExtraShips();
        findOptimal();
    }

    private static void findOptimal() throws InterruptedException {
        for (Cargo cargo : Cargo.values()) {
            countOfCranes.put(cargo, 1);
        }

        runCargos();
        calcFine();

        Map<Cargo, Integer> oldFines = new HashMap<>();
        Map<Cargo, Boolean> mapOfFlags = new HashMap<>();
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
                /*System.out.println("old fine " + cargo + oldFines.get(cargo));
                System.out.println("new fine " + cargo + mapOfFine.get(cargo));
                System.out.println("number of cranes " + countOfCranes.get(cargo));*/
                if (mapOfFine.get(cargo) <= oldFines.get(cargo)) {
                    oldFines.put(cargo, mapOfFine.get(cargo));
                } else {
                    countOfCranes.put(cargo, countOfCranes.get(cargo) - 1);
                    mapOfFlags.put(cargo, true);
                }
            }
        }

        runCargos();
        calcFine();
    }

    private static void runCargos() throws InterruptedException {
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


    private static void runCargo(Cargo cargo) throws InterruptedException {
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

    private static void setAndCalcRandomStat() {
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

    private static CopyOnWriteArrayList<Ship> getShipsOfType(Cargo cargo){
        CopyOnWriteArrayList<Ship> list = new CopyOnWriteArrayList<>();
        for (Ship ship : schedule) {
            if (ship.getCargoType() == cargo) {
                Ship newShip = new Ship(ship);
                list.add(newShip);
            }
        }
        return list;
    }

    private static void removeExtraShips(){
        List<Ship> shipsToRemove = new LinkedList<>();
        for (Ship ship : schedule) {
            if (ship.getDateTime() > 30 * 1440 || ship.getDateTime() <= 0) {
                shipsToRemove.add(ship);
            }
        }
        for (Ship ship : shipsToRemove) {
            schedule.remove(ship);
        }
    }

    private static void calcFine() {
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

    private static void resetStat() {
        fineForWaiting = 0;
        totalFine = 0;
        costOfCranes = 0;
        for (Cargo cargo : Cargo.values()) {
            mapOfFine.put(cargo, 0);
        }
        meanWaitingTime = 0;
        meanQueueLength = 0;
    }

    public static void parseFromJson() throws IOException {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        RestTemplate restTemplate = builder.build();
        Scanner in = new Scanner(System.in);
        Schedule schedule;

        System.out.println("Generate new schedule?\n yes \t no");
        String answer = in.nextLine();

        if (answer.equals("yes")) {
            String stringSchedule = restTemplate.getForObject("http://localhost:8080/get-schedule", String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            schedule = objectMapper.readValue(stringSchedule, Schedule.class);
        } else {
            try {
                String name = new String();
                while (true) {
                    System.out.println("Input name of file: ");
                    name = in.nextLine();
                    File file = new File(name);
                    if (file.exists()) {
                        break;
                    }
                }
                String url = "http://localhost:8080/get-schedule-by-name?args=" + name;
                schedule = restTemplate.getForObject(url, Schedule.class);
            } catch (RestClientException e) {
                System.out.println("Invalid name of file");
                return;
            }
        }

        Port.schedule = schedule.getSchedule();
    }

    private static void putResultToJson() throws IOException {
        Path path = Paths.get("result.json");

        JsonFactory factory = new JsonFactory();
        JsonGenerator generator = factory.createGenerator(new File(String.valueOf(path)), JsonEncoding.UTF8);

        CopyOnWriteArrayList<Ship> list = new CopyOnWriteArrayList<>();
        for (Cargo cargo : Cargo.values()) {
            list.addAll(mapOfShipsAfterModeling.get(cargo));
        }
        list.sort(Comparator.comparingInt(Ship::getDateTime));
/*
        for (Ship ship : list) {
            System.out.println(ship.receiveStatForPort());
        }
*/

        for (Ship ship : list) {
            generator.writeStartObject();
            generator.writeStringField("name", ship.getName());
            generator.writeStringField("date", Ship.getTimeInString(ship.getDateTime()));
            generator.writeStringField("waiting time",
                    Ship.getTimeInString(ship.getUnloadStartTime() - ship.getDateTime()));
            generator.writeStringField("time of start unloading", Ship.getTimeInString(ship.getUnloadStartTime()));
            generator.writeStringField("time of unloading",
                    Ship.getTimeInString(ship.getUnloadFinishTime() - ship.getUnloadStartTime()));
            generator.writeStringField("cargo", String.valueOf(ship.getCargoType()));
            generator.writeEndObject();
            generator.writeRaw('\n');
        }

        generator.writeRaw('\n');
        generator.writeStartObject();
        generator.writeNumberField("number of ships", list.size());
        generator.writeRaw('\n');
        generator.writeNumberField("cost of cranes", costOfCranes);
        generator.writeRaw('\n');
        generator.writeNumberField("fine for waiting queue", fineForWaiting);
        generator.writeRaw('\n');
        generator.writeNumberField("fine for extra standing", fineForExtraStanding);
        generator.writeRaw('\n');
        generator.writeNumberField("total fine", totalFine);
        generator.writeRaw('\n');
        generator.writeNumberField("mean length of queue", meanQueueLength);
        generator.writeRaw('\n');
        generator.writeStringField("mean waiting time", Ship.getTimeInString((int) meanWaitingTime));
        generator.writeRaw('\n');
        generator.writeStringField("max delay", Ship.getTimeInString(maxDelay));
        generator.writeRaw('\n');
        generator.writeStringField("mean delay", Ship.getTimeInString((int) meanDelay));
        generator.writeRaw('\n');
        for (Cargo cargo : Cargo.values()) {
            generator.writeNumberField("number of " + cargo + " cranes", countOfCranes.get(cargo));
            generator.writeRaw('\n');
        }

        generator.writeEndObject();
        generator.close();

        RestTemplateBuilder builder = new RestTemplateBuilder();
        RestTemplate restTemplate = builder.build();

        restTemplate.postForObject("http://localhost:8080/post-result", Files.readString(path), String.class);
    }
}