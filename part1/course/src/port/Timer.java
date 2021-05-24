package port;

import generator.Ship;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Timer implements Runnable{
    private int time;
    public static ConcurrentMap<Ship, Boolean> mapOfFlags = new ConcurrentHashMap<>();

    public int getTime() {
        return time;
    }

    @Override
    public void run() {
        time++;
        for (Map.Entry<Ship, Boolean> entry : mapOfFlags.entrySet()) {
            entry.setValue(true);
        }
    }
}
