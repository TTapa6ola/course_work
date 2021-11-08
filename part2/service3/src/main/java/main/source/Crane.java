package main.source;

import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Crane implements Runnable {
    private final Cargo cargoType;
    private final double productivity;
    private volatile boolean isBusy;
    private Ship ship;

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
            } else {
                if (ship.semaphore.availablePermits() == 0) {
                    ship.semaphore.release();
                    this.ship = null;
                    isBusy = false;
                } else {
                    if (ship.getExtraStandingTime() >= 0) {
                        ship.setExtraStandingTime(ship.getExtraStandingTime() - 1);
                    } else {
                        releaseShip(this.ship);
                    }
                }
            }
        }
    }

    synchronized private void captureShip(Ship ship) throws InterruptedException {
        ship.setUnloadStartTime(Port.timer.getTime());
        ship.semaphore.acquire();
        this.ship = ship;
        isBusy = true;
    }

    synchronized private void releaseShip(Ship ship) {
        ship.setUnloadFinishTime(Port.timer.getTime());

        if (ship.semaphore.availablePermits() < 2) {
            ship.semaphore.release();
        }

        this.ship = null;
        isBusy = false;
    }

    @Override
    public void run() {
        try {
            while (Port.timer.getTime() < 43200) {
                Port.meanQueueLength += Port.mapOfQueues.get(cargoType).size();

                if (isBusy) {
                    unloadShip();
                } else {
                    workWithQueue();
                }

                for (Ship ship : Port.mapOfShips.get(cargoType)) {
                    if (ship.getDateTime() == Port.timer.getTime()) {
                        if (unloadLogic(ship)) {
                            break;
                        }
                    }
                }


                if (!isBusy) {
                    for (Ship ship : Port.mapOfShips.get(cargoType)) {
                        if (ship.getDateTime() < Port.timer.getTime()) {
                            if (ship.semaphore.availablePermits() == 1 && ship.getQuantity() > 0
                                    && !Port.mapOfQueues.get(cargoType).contains(ship)
                                    && Timer.mapOfFlags.get(ship)) {
                                Timer.mapOfFlags.put(ship, false);
                                ship.semaphore.acquire();
                                ship.setQuantity((ship.getQuantity() - productivity / 60));
                                ship.semaphore.release();
                                break;
                            }
                        }
                    }
                }

                Port.barrier.await();
            }

            for (Ship ship : Port.mapOfShips.get(cargoType)) {
                if (ship.getExtraStandingTime() > 0 || ship.getQuantity() > 0) {
                    if (ship.getUnloadStartTime() == 0) {
                        ship.setUnloadStartTime(Port.timer.getTime());
                    }
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
        synchronized (Port.mapOfQueues)
        {
            for (Ship ship : Port.mapOfQueues.get(cargoType)) {
                if (unloadLogic(ship)) {
                    shipsToRemove.add(ship);
                }
            }
            Port.mapOfQueues.get(cargoType).removeAll(shipsToRemove);
        }
    }

    synchronized private boolean unloadLogic(Ship ship) {
        try {
            if (isBusy) {
                boolean allCranesAreBusy = true;
                for (Crane crane : Port.mapOfCranes.get(cargoType)){
                    if (!crane.isBusy()) {
                        allCranesAreBusy = false;
                        break;
                    }
                }

                synchronized (Port.mapOfQueues)
                {
                    if (!Port.mapOfQueues.get(cargoType).contains(ship) && allCranesAreBusy
                            && ship.semaphore.availablePermits() == 2) {
                        Port.mapOfQueues.get(cargoType).add(ship);
                    }
                }
            } else {
                synchronized (ship) {
                    if (ship.semaphore.availablePermits() == 2) {
                        captureShip(ship);
                        unloadShip();
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

