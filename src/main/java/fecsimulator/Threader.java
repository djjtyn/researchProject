package fecsimulator;

import java.time.Instant;
import java.util.concurrent.Phaser;


public class Threader extends Thread {
	
//	private Phaser phaser;
	
//	
//	public Threader(String name, Phaser phaser) {
//		this.phaser = phaser;
//		phaser.register();
//		setName(name);
//	}
	
	@Override
	public void run() {
		try {
			System.out.println(this.getName() + " started at " + Instant.now());
			HospitalSimulation hs = new HospitalSimulation();
			hs.startSim();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

}
