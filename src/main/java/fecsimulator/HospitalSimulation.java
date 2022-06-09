package fecsimulator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import cloudsim.Host;
import cloudsim.Log;
import cloudsim.Pe;
import cloudsim.Storage;
import cloudsim.core.CloudSim;
import cloudsim.power.PowerHost;
import cloudsim.provisioners.RamProvisionerSimple;
import cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import ifogsim.application.AppEdge;
import ifogsim.application.AppLoop;
import ifogsim.application.Application;
import ifogsim.application.selectivity.FractionalSelectivity;
import ifogsim.entities.Actuator;
import ifogsim.entities.FogBroker;
import ifogsim.entities.FogDevice;
import ifogsim.entities.FogDeviceCharacteristics;
import ifogsim.entities.Sensor;
import ifogsim.entities.Tuple;
import ifogsim.placement.Controller;
import ifogsim.placement.ModuleMapping;
import ifogsim.placement.ModulePlacementEdgewards;
import ifogsim.placement.ModulePlacementMapping;
import ifogsim.policy.AppModuleAllocationPolicy;
import ifogsim.scheduler.StreamOperatorScheduler;
import ifogsim.utils.FogLinearPowerModel;
import ifogsim.utils.FogUtils;
import ifogsim.utils.TimeKeeper;
import ifogsim.utils.distribution.DeterministicDistribution;

public class HospitalSimulation {
	
	
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>(); //List to store each component
	static List<Sensor> sensors = new ArrayList<Sensor>();	//List to store all sensors
	static List<Actuator> actuators = new ArrayList<Actuator>();	//List to store all actuators
	static int numOfHospitalWings = 3;	//Hospital is designed to have 3 different wings(WingA, WingB & WingC)
	static int numOfpatientsPerWing = 10;
	static int numOfBinsPerWing = 2;
	static int numOfParkingSpacesPerWing = 20;
	
	private static boolean CLOUD = false;
	
	public static void main(String[] args) {
		Log.printLine("Starting Hopital Simulation");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			
			CloudSim.init(num_user, calendar, trace_flag);
			String appId = "SmartHospital"; // identifier of the application
			FogBroker broker = new FogBroker("broker");
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			//Invoke method for the creation of fog devices 
			createFogDevices(broker.getId(), appId);
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
			
			//Assign each application module to its fog device
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("PatientMonitor-")){
					//Assign modules to each individual PatientMonitor device to allow each monitor to monitor sensor data
					moduleMapping.addModuleToDevice("patientVitalsModule", device.getName());
					
					
					//moduleMapping.addModuleToDevice("monitorSinglePatientVitals", device.getName());
					
//					moduleMapping.addModuleToDevice("bloodPressureModule", device.getName());
//					moduleMapping.addModuleToDevice("o2SatModule", device.getName());
//					moduleMapping.addModuleToDevice("respRateModule", device.getName());
				} else if (device.getName().startsWith("heartMonitor-")) {
					moduleMapping.addModuleToDevice("heartRateModule", device.getName());
				}
			}
			if(CLOUD){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("object_detector", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("object_tracker", "cloud"); // placing all instances of Object Tracker module in the Cloud
			}
			Controller controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	//Method to create all fog devices for the simulation
	private static void createFogDevices(int userId, String appId) {
		//Create Cloud Device at level 0
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		//Create proxy device at level 1
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		String wingIdentifier;
		//Loop through each hospital wing and assign patient monitor and smart bin components
		for(int i=0;i<numOfHospitalWings;i++){
			//Switch Statement to assign hospital wing identifier value
			switch(i) {
			case 0: wingIdentifier = "WingA";
					break;
			case 1:	wingIdentifier = "WingB";
					break;
			case 2:	wingIdentifier = "WingC";
					break;
			default: wingIdentifier = "Invalid Wing";
					break;
			}
			//Method to assign specific devices to each hospital wing
			addHospitalWing(wingIdentifier, userId, appId, proxy.getId());
		}
	}
	
	private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
	
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage, peList, new StreamOperatorScheduler(peList), new FogLinearPowerModel(busyPower, idlePower));

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}
	
	private static FogDevice addHospitalWing(String id, int userId, String appId, int parentId){
		//Create router device for each hospital wing at level 2
		FogDevice router = createFogDevice("router-"+id, 2800, 4000, 10000, 10000, 2, 0.0, 107.339, 83.4333);
		router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
		router.setParentId(parentId);
		fogDevices.add(router);
		
		//PATIENT MONITOR COMPONENTS
		//Create PatientMonitor Master Components at level 3
		FogDevice patientMonitorMaster = createFogDevice("patientMonitorMaster-"+id, 2800, 4000, 10000, 10000, 3, 0.0, 107.339, 83.4333);
		patientMonitorMaster.setUplinkLatency(2);
		patientMonitorMaster.setParentId(router.getId());
		fogDevices.add(patientMonitorMaster);	
		//Create PatientMonitor Orchestrator at level 4 as parent for each patient monitor device
		FogDevice patientMonitorOrchestrator = createFogDevice("patientMonitorOrchestator-"+id, 2800, 4000, 10000, 10000, 4, 0.0, 107.339, 83.4333);
		patientMonitorOrchestrator.setParentId(patientMonitorMaster.getId());
		patientMonitorOrchestrator.setUplinkLatency(2);
		fogDevices.add(patientMonitorOrchestrator);
		//Instantiate patient monitor devices
		for(int i=0;i<numOfpatientsPerWing;i++){
			String patientMonitorUnitId = "PatientMonitor-"+ (i+1) + ":" + id;
			FogDevice patientMonitor = addPatientMonitor(patientMonitorUnitId, userId, appId, patientMonitorOrchestrator.getId());
			patientMonitor.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
			fogDevices.add(patientMonitor);
		}
		//Create an actuator for viewing all patient monitor units
		Actuator patientMonitorDisplay = new Actuator(id+"-patientMasterdisplay", userId, appId, "PATIENTMONITORMASTERDISPLAY"); 
		patientMonitorDisplay.setGatewayDeviceId(patientMonitorMaster.getId());
		patientMonitorDisplay.setLatency(1.0); 
		actuators.add(patientMonitorDisplay);
		
//		//BIN COMPONENTS
//		//Create bin Master Components at level 3
//		FogDevice binMaster = createFogDevice("binMaster-"+id, 2800, 4000, 10000, 10000, 3, 0.0, 107.339, 83.4333);
//		binMaster.setUplinkLatency(2);
//		binMaster.setParentId(router.getId());
//		fogDevices.add(binMaster);	
//		//Create bin Orchestrator at level 4 as parent for each bin device
//		FogDevice binOrchestrator = createFogDevice("binOrchestator-"+id, 2800, 4000, 10000, 10000, 4, 0.0, 107.339, 83.4333);
//		binOrchestrator.setParentId(binMaster.getId());
//		binOrchestrator.setUplinkLatency(2);
//		//Create the specified amount of bins per area
//		for(int i=0;i<numOfBinsPerWing;i++){
//			String binId = id+"-Bin-"+(i+1);
//			FogDevice bin = addBin(binId, userId, appId, binOrchestrator.getId());
//			bin.setUplinkLatency(2); 
//			fogDevices.add(bin);
//		}
		return router;
	}
	
	
	private static void createHeartMonitor(String id, int userId, String appId, int parentId) {
		FogDevice heartMonitor = createFogDevice(id,2800, 1000, 10000, 10000, 6, 0, 87.53, 82.44);
		heartMonitor.setParentId(parentId);
		heartMonitor.setUplinkLatency(2);
		fogDevices.add(heartMonitor);
		// Each patient monitor will receive data from heart rate, blood pressure, o2 saturation and respiratory rate sensors
		Sensor heartRateSensor = new Sensor(id+"-hrSensor", "heartRate", userId, appId, new DeterministicDistribution(5));
		heartRateSensor.setGatewayDeviceId(heartMonitor.getId());
		heartRateSensor.setLatency(1.0);
		sensors.add(heartRateSensor);
		// Patient monitors will have a display to output their sensor data
		Actuator patientMonitorDisplay = new Actuator(id+"-display", userId, appId, "PATIENTMONITORDISPLAY");
		patientMonitorDisplay.setGatewayDeviceId(parentId);
		patientMonitorDisplay.setLatency(1.0); 
		actuators.add(patientMonitorDisplay);	
	}
	
	private static FogDevice addPatientMonitor(String id, int userId, String appId, int parentId)	{
		// Patient monitors will be raspberry Pi components at level 5
		FogDevice patientMonitor = createFogDevice(id,2800, 1000, 10000, 10000, 5, 0, 87.53, 82.44);
		createHeartMonitor("heartMonitor-" + id, userId, appId, patientMonitor.getId());
		patientMonitor.setParentId(parentId);
		
//		Sensor bloodPressureSensor = new Sensor(id+"-bpSensor", "BLOODPRESSURE", userId, appId, new DeterministicDistribution(5));
//		bloodPressureSensor.setGatewayDeviceId(patientMonitor.getId());
//		bloodPressureSensor.setLatency(1.0);
//		sensors.add(bloodPressureSensor);
//		Sensor o2SaturationSensor = new Sensor(id+"-o2Sensor", "O2SATURATION", userId, appId, new DeterministicDistribution(5));
//		o2SaturationSensor.setGatewayDeviceId(patientMonitor.getId());
//		o2SaturationSensor.setLatency(1.0);
//		sensors.add(o2SaturationSensor);
//		Sensor respiratoryRateSensor = new Sensor(id+"-rrSensor", "RESPIRATORYRATE", userId, appId, new DeterministicDistribution(5));
//		respiratoryRateSensor.setGatewayDeviceId(patientMonitor.getId());
//		respiratoryRateSensor.setLatency(1.0);
//		sensors.add(respiratoryRateSensor);
		return patientMonitor;

	}
	
//	private static FogDevice addBin(String id, int userId, String appId, int parentId)	{
//		// BIns will be raspberry Pi components (Raspberry Pi Zero) at level 5
//		FogDevice bin = createFogDevice(id,500, 1000, 10000, 10000, 5, 0, 87.53, 82.44);
//		bin.setParentId(parentId);
//		Sensor binSensor = new Sensor(id+"-Sensor", "BIN", userId, appId, new DeterministicDistribution(5));
//		binSensor.setGatewayDeviceId(bin.getId());
//		binSensor.setLatency(1.0);
//		sensors.add(binSensor);
//		
//		// Bins will have a display to output their sensor data
//		Actuator binStatusDisplay = new Actuator(id+"-display", userId, appId, "BINSTATUSDISPLAY");
//		binStatusDisplay.setGatewayDeviceId(parentId);
//		binStatusDisplay.setLatency(1.0); 
//		actuators.add(binStatusDisplay);
//		return bin;
//	}
	
	private static Application createApplication(String appId, int userId){	
		Application application = Application.createApplication(appId, userId);
		
		application.addAppModule("heartRateModule", 10);	//AppModule to monitor patient heart rate
////		application.addAppModule("bloodPressureModule", 10);	//AppModule to monitor patient blood pressure
////		application.addAppModule("o2SatModule", 10);	//AppModule to monitor patient o2 saturation
////		application.addAppModule("respRateModule", 10);	//AppModule to monitor patient respiratory rate
		application.addAppModule("patientVitalsModule", 10);
//		//application.addAppModule("triggerAlert", 10);
//		
		//Communication between sensors and app modules
		application.addAppEdge("heartRate", "heartRateModule", 1000, 20000, "heartRate", Tuple.UP, AppEdge.SENSOR);	//Heart rate sensor -> heartRateModule: heartRate tuple communication
		//application.addAppEdge("heartRateModule", "patientVitalsModule", 1000, 20000, "processedHeartRateData", Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge("heartRateModule", "PATIENTMONITORDISPLAY", 100, 28, 100, "heartRateRawData", Tuple.DOWN, AppEdge.ACTUATOR);
////		application.addAppEdge("BLOODPRESSURE", "bloodPressureModule", 1000, 20000, "BLOODPRESSURE", Tuple.UP, AppEdge.SENSOR);	//Blood pressure sensor -> bloodPressureModule: BLOODPRESSURE tuple communication
////		application.addAppEdge("O2SATURATION", "o2SatModule", 1000, 20000, "O2SATURATION", Tuple.UP, AppEdge.SENSOR);	//o2 Saturation sensor -> o2SatModule: O2SATURATION tuple communication
////		application.addAppEdge("RESPIRATORYRATE", "respRateModule", 1000, 20000, "RESPIRATORYRATE", Tuple.UP, AppEdge.SENSOR);	//Respiratory rate sensor -> respRateModule: RESPIRATORYRATE tuple communication
//		
//		//Communication between individual patient monitors and hospital wing master monitor
//
//		
		// Application module Input/Outputs
		application.addTupleMapping("heartRateModule", "heartRate", "heartRateRawData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM
		application.addTupleMapping("heartRateModule", "heartRateRawData", "processedHeartRateData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM
		//application.addTupleMapping("patientVitalsModule", "processedHeartRateData", "heartData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM
////		application.addTupleMapping("bloodPressureModule", "BLOODPRESSURE", "BLOODPRESURESTREAM", new FractionalSelectivity(1.0)); // bloodPressureModule(BLOODPRESSURE) returns BLOODPRESURESTREAM
////		application.addTupleMapping("o2SatModule", "O2SATURATION", "O2SATURATIONSTREAM", new FractionalSelectivity(1.0)); // o2SatModule(O2SATURATION) returns O2SATURATIONSTREAM
////		application.addTupleMapping("respRateModule", "RESPIRATORYRATE", "RESPIRATORYRATESTREAM", new FractionalSelectivity(1.0)); // respRateModule(RESPIRATORYRATE) returns RESPIRATORYRATESTREAM
		 
		// Application loops
		final AppLoop heartRateMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("heartRate");
				add("heartRateModule");
				//add("patientVitalsModule");
				//add("PATIENTMONITORDISPLAY");
			}
		});
		
		List<AppLoop> loops = new ArrayList<AppLoop>(){
			{
				add(heartRateMonitorLoop);
			}
		};
		
		application.setLoops(loops);
		return application;
	}
}
