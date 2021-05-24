package port;

import generator.Cargo;
import generator.Ship;


import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Crane implements Runnable {
    private final Cargo cargoType;
    private final double productivity;
    private boolean isBusy;
    private Ship ship;
    private static ConcurrentMap<Cargo, Lock> lockers = new ConcurrentHashMap<>();;
    static {
        for (Cargo cargo : Cargo.values()) {
            lockers.put(cargo,  new ReentrantLock());
        }
    }

    public Crane(Cargo cargoType) {
        this.cargoType = cargoType;
        productivity = switch (cargoType) {
            case BULK -> 600;
            case LIQUID -> 720;
            case CONTAINER -> 120;
        };
        this.ship = null;
        isBusy = false;
    }

    public boolean isBusy()
    {
        return isBusy;
    }

    public void unloadShip() {
        synchronized (ship) {
            if (ship.getQuantity() > 0) {
                ship.setQuantity((ship.getQuantity() - productivity / 60));
                //System.out.println("Quantity " + ship.getQuantity() + "Extra Standing time " + Ship.getTimeInString((int)extraStandingTime));
            } else {
                if (ship.semaphore.availablePermits() == 0) {
                    ship.semaphore.release();
                    this.ship = null;
                } else {
                    if (ship.getExtraStandingTime() > 0) {
                        ship.setExtraStandingTime(ship.getExtraStandingTime() - 1);
                        //System.out.println("Quantity " + ship.getQuantity() + "Extra Standing time " + Ship.getTimeInString((int)extraStandingTime));
                    } else {
                        releaseShip(this.ship);
                    }
                }
            }
        }
    }

    private void captureShip(Ship ship) throws InterruptedException {
        if (ship.semaphore.availablePermits() == 2) {
            ship.setUnloadStartTime(Port.timer.getTime());
        }
        ship.semaphore.acquire();
        this.ship = ship;
        isBusy = true;
        //System.out.println("Ship " + ship.getName() +  " is captured");
        //System.out.println("Time " + Ship.getTimeInString(Port.timer.getTime()) + " Arrival time " + Ship.getTimeInString(Port.timer.getTime()));
    }

    private void releaseShip(Ship ship) {
        //System.out.println("Ship " + ship.getName() +  " is released at " + Ship.getTimeInString(Port.timer.getTime()));
        ship.setUnloadFinishTime(Port.timer.getTime());
        ship.semaphore.release();
        this.ship = null;
        isBusy = false;
    }

    @Override
    public void run() {
        System.out.println("Crane running");
        try {
            while (Port.timer.getTime() < 43200) {
                lockers.get(cargoType).lock();
                Port.meanQueueLength += Port.mapOfQueues.get(cargoType).size();

                if (isBusy) {
                    unloadShip();
                } else {
                    workWithQueue();
                }

                for (Ship ship : Port.mapOfShips.get(cargoType)) {
                    if (ship.getDateTime() == Port.timer.getTime()) {
                        if (unloadLogic(ship)) break;
                    }
                }

                synchronized (Port.mapOfQueues) {
                for (Ship ship : Port.mapOfShips.get(cargoType)) {
                    if (ship.getDateTime() < Port.timer.getTime()) {
                            if (ship.semaphore.availablePermits() == 1 && ship.getQuantity() > 0
                                    && !Port.mapOfQueues.get(cargoType).contains(ship) && !isBusy
                                    && Timer.mapOfFlags.get(ship)) {
                                Timer.mapOfFlags.put(ship, false);
                                ship.semaphore.acquire();
                                ship.setQuantity((ship.getQuantity() - productivity / 60));
                                ship.semaphore.release();
                                break;
                                //System.out.println("Quantity " + ship.getQuantity());
                            }
                        }
                    }
                }

                lockers.get(cargoType).unlock();
                Port.barrier.await();
            }

            for (Ship ship : Port.mapOfShips.get(cargoType)) {
                if (ship.getExtraStandingTime() > 0) {
                    captureShip(ship);
                    releaseShip(ship);
                }
            }

            CopyOnWriteArrayList<Ship> list = new CopyOnWriteArrayList<>();
            for (Ship ship : Port.mapOfShips.get(cargoType)) {
                Ship ship1 = new Ship(ship);
                list.add(ship1);
            }

            Port.mapOfShipsAfterModeling.put(cargoType, list);
        } catch(InterruptedException | BrokenBarrierException e){
            e.printStackTrace();
        }
    }

    private void workWithQueue(){
        LinkedList<Ship> shipsToRemove = new LinkedList<>();
        synchronized (Port.mapOfQueues) {
            for (Ship ship : Port.mapOfQueues.get(cargoType)) {
                if (unloadLogic(ship)) {
                    shipsToRemove.add(ship);
                }
            }

            for (Ship ship : shipsToRemove) {
                Port.mapOfQueues.get(cargoType).remove(ship);
            }
        }
    }

    private boolean unloadLogic(Ship ship) {
        try {
            if (isBusy) {
                boolean allCranesAreBusy = true;
                for (Crane crane : Port.mapOfCranes.get(cargoType)){
                    if (!crane.isBusy()) {
                        allCranesAreBusy = false;
                        break;
                    }
                }

                synchronized (Port.mapOfQueues) {
                    if (!Port.mapOfQueues.get(cargoType).contains(ship) && allCranesAreBusy
                            && ship.semaphore.availablePermits() == 2) {
                        Port.mapOfQueues.get(cargoType).add(ship);
                        //System.out.println("Ship " + ship.getName() + " put in queue at " + Ship.getTimeInString(Port.timer.getTime()));
                    }
                }
            } else {
                synchronized (ship) {
                    if (ship.semaphore.availablePermits() == 2) {
                        captureShip(ship);
                        unloadShip();
                        return true;
                    } else if (ship.semaphore.availablePermits() == 1 && ship.getQuantity() > 0) {
                        ship.semaphore.acquire();
                        ship.setQuantity((ship.getQuantity() - productivity / 60));
                        ship.semaphore.release();
                        //System.out.println("Quantity " + ship.getQuantity());
                        return true;
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }
}

