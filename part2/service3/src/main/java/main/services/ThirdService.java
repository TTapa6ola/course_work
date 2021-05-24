package main.services;

import main.source.Port;

import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;

public class ThirdService {
    public static void main(String[] args) throws BrokenBarrierException, InterruptedException, IOException {
        Port.runPortSpring();
    }
}
