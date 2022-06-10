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
					moduleMapping.addModuleToDevice("heartRateModule", device.getName());	//Attach module to read heart rate sensor data
					moduleMapping.addModuleToDevice("bloodPressureModule", device.getName());	//Attach module to read blood pressure sensor data
					moduleMapping.addModuleToDevice("o2SatModule", device.getName());
					
					
					//moduleMapping.addModuleToDevice("monitorSinglePatientVitals", device.getName());
					
//					moduleMapping.addModuleToDevice("bloodPressureModule", device.getName());
//					moduleMapping.addModuleToDevice("o2SatModule", device.getName());
//					moduleMapping.addModuleToDevice("respRateModule", device.getName());
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
		FogDevice cloud = createFogDevice("cloud", "cloud", 0);	//createFogDevice method required device type and its level as arguments
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		
		//Create proxy-server device at level 1
		FogDevice proxy = createFogDevice("proxy-server","Pi3BPlus", 1);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		
		//Loop through each hospital wing and assign patient monitor and smart bin components
		String wingIdentifier;
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
	
	private static FogDevice createFogDevice(String nodeName, String deviceType, int level) {
		try {
			//Default iFogSim provided variable values
			List<Pe> peList = new ArrayList<Pe>();
			int hostId = FogUtils.generateEntityId();
			long storage = 1000000; // host storage
			int bw = 10000;
			List<Host> hostList = new ArrayList<Host>();
			String arch = "x86"; // system architecture
			String os = "Linux"; // operating system
			String vmm = "Xen";
			double time_zone = 10.0; // time zone this resource located
			double cost = 3.0; // the cost of using processing in this resource
			double costPerMem = 0.05; // the cost of using memory in this resource
			double costPerStorage = 0.001; // the cost of using storage in this resource
			double costPerBw = 0.0; // the cost of using bw in this resource
			LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN devices by now
		
			// Check which device type to be created and instantiante a FogDevice for it
			if(deviceType == "cloud") {
				//Cloud variable values will use default values provided by iFogSim
				peList.add(new Pe(0, new PeProvisionerOverbooking(44800))); 
				PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(40000), new BwProvisionerOverbooking(bw), storage, peList, new StreamOperatorScheduler(peList), new FogLinearPowerModel(16*103, 16*83.25));
				hostList.add(host);
				FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);
				FogDevice fogDevice = new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList), storageList, 10, 100, 10000, 0, 0.0);
				fogDevice.setLevel(level);
				return fogDevice;
			}
			if(deviceType == "Pi3BPlus") {
				//Proxy variable values will use default values provided by iFogSim
				peList.add(new Pe(0, new PeProvisionerOverbooking(2800))); 
				PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(4000), new BwProvisionerOverbooking(bw), storage, peList, new StreamOperatorScheduler(peList), new FogLinearPowerModel(107.339, 83.4333));
				hostList.add(host);
				FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);
				FogDevice fogDevice = new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList), storageList, 10, 10000, 10000, 0, 0.01);
				fogDevice.setLevel(level);
				return fogDevice;
			}
		} catch(Exception e) {
			System.out.println("There was an issue creating the " + deviceType + " device at level " + level);
			e.printStackTrace();
			return null;
		}
		return null;
	}
	
	private static FogDevice addHospitalWing(String id, int userId, String appId, int parentId){
		//Create router device for each hospital wing at level 2
		FogDevice router = createFogDevice("router-"+id, "Pi3BPlus", 2);
		router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
		router.setParentId(parentId);
		fogDevices.add(router);
		
		//PATIENT MONITOR COMPONENTS
		//Create PatientMonitor Master Components at level 3
		FogDevice patientMonitorMaster = createFogDevice("patientMonitorMaster-"+id, "Pi3BPlus", 3);
		patientMonitorMaster.setUplinkLatency(2);
		patientMonitorMaster.setParentId(router.getId());
		fogDevices.add(patientMonitorMaster);	
		
		//Create PatientMonitor Orchestrator at level 4 as parent for each patient monitor device
		FogDevice patientMonitorOrchestrator = createFogDevice("patientMonitorOrchestator-"+id, "Pi3BPlus", 4);
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
//		//Create an actuator for viewing all patient monitor units
//		Actuator patientMonitorDisplay = new Actuator(id+"-patientMasterdisplay", userId, appId, "PATIENTMONITORMASTERDISPLAY"); 
//		patientMonitorDisplay.setGatewayDeviceId(patientMonitorMaster.getId());
//		patientMonitorDisplay.setLatency(1.0); 
//		actuators.add(patientMonitorDisplay);
		
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
	
	private static void addPatientMonitorSensors(String id, int userId, String appId, int parentId) {
		//Patient Monitor Sensors
		Sensor heartRateSensor = new Sensor(id+"-hrSensor", "heartRate", userId, appId, new DeterministicDistribution(5));
		heartRateSensor.setGatewayDeviceId(parentId);
		heartRateSensor.setLatency(1.0);
		sensors.add(heartRateSensor);
		
		Sensor bloodPressureSensor = new Sensor(id+"-bpSensor", "bloodPressure", userId, appId, new DeterministicDistribution(5));
		bloodPressureSensor.setGatewayDeviceId(parentId);
		bloodPressureSensor.setLatency(1.0);
		sensors.add(bloodPressureSensor);
		
		Sensor o2SaturationSensor = new Sensor(id+"-o2Sensor", "o2Saturation", userId, appId, new DeterministicDistribution(5));
		o2SaturationSensor.setGatewayDeviceId(parentId);
		o2SaturationSensor.setLatency(1.0);
		sensors.add(o2SaturationSensor);
//		
//		Sensor respiratoryRateSensor = new Sensor(id+"-rrSensor", "RESPIRATORYRATE", userId, appId, new DeterministicDistribution(5));
//		respiratoryRateSensor.setGatewayDeviceId(parentId);
//		respiratoryRateSensor.setLatency(1.0);
//		sensors.add(respiratoryRateSensor);
//		
		
	}
	
	private static FogDevice addPatientMonitor(String id, int userId, String appId, int parentId)	{
		// Patient monitors will be raspberry Pi components at level 5
		FogDevice patientMonitor = createFogDevice(id,"Pi3BPlus",5);
		patientMonitor.setParentId(parentId);
		addPatientMonitorSensors(id, userId, appId, patientMonitor.getId());
		
		// Patient monitors will have a display to output their sensor data
		Actuator patientMonitorDisplay = new Actuator(id+"-display", userId, appId, "PATIENTMONITORDISPLAY");
		patientMonitorDisplay.setGatewayDeviceId(patientMonitor.getId());
		patientMonitorDisplay.setLatency(1.0); 
		actuators.add(patientMonitorDisplay);	
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
		application.addAppModule("bloodPressureModule", 10);	//AppModule to monitor patient blood pressure
		application.addAppModule("o2SatModule", 10);	//AppModule to monitor patient o2 saturation
////		application.addAppModule("respRateModule", 10);	//AppModule to monitor patient respiratory rate
		//application.addAppModule("patientVitalsModule", 10);
//		//application.addAppModule("triggerAlert", 10);
		
		
		application.addAppEdge("heartRate", "heartRateModule", 1000, 20000, "heartRate", Tuple.UP, AppEdge.SENSOR);	//Heart rate sensor -> heartRateModule: heartRate tuple communication
		application.addAppEdge("heartRateModule", "PATIENTMONITORDISPLAY", 100, 28, 100, "heartRateData", Tuple.DOWN, AppEdge.ACTUATOR);
		//application.addAppEdge("heartRateModule", "patientVitalsModule", 1000, 20000, "processedHeartRateData", Tuple.DOWN, AppEdge.MODULE);

		application.addAppEdge("bloodPressure", "bloodPressureModule", 1000, 20000, "bloodPressure", Tuple.UP, AppEdge.SENSOR);	//Blood pressure sensor -> bloodPressureModule: BLOODPRESSURE tuple communication
    	application.addAppEdge("bloodPressureModule", "PATIENTMONITORDISPLAY", 100, 28, 100, "bloodPressureData", Tuple.DOWN, AppEdge.ACTUATOR);	
		
    	application.addAppEdge("o2Saturation", "o2SatModule", 1000, 20000, "o2Saturation", Tuple.UP, AppEdge.SENSOR);	//o2 Saturation sensor -> o2SatModule: O2SATURATION tuple communication
    	application.addAppEdge("o2SatModule", "PATIENTMONITORDISPLAY", 100, 28, 100, "o2SaturationData", Tuple.DOWN, AppEdge.ACTUATOR);
    	
    	//application.addAppEdge("RESPIRATORYRATE", "respRateModule", 1000, 20000, "RESPIRATORYRATE", Tuple.UP, AppEdge.SENSOR);	//Respiratory rate sensor -> respRateModule: RESPIRATORYRATE tuple communication
//		
//		//Communication between individual patient monitors and hospital wing master monitor
//
//		
		// Application module Input/Outputs
		application.addTupleMapping("heartRateModule", "heartRate", "heartRateData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM
    	application.addTupleMapping("bloodPressureModule", "bloodPressure", "bloodPressureData", new FractionalSelectivity(1.0)); // bloodPressureModule(BLOODPRESSURE) returns BLOODPRESURESTREAM
    	application.addTupleMapping("o2SatModule", "o2Saturation", "o2SaturationData", new FractionalSelectivity(1.0)); // o2SatModule(O2SATURATION) returns O2SATURATIONSTREAM
    	//application.addTupleMapping("heartRateModule", "heartRateRawData", "processedHeartRateData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM
		//application.addTupleMapping("patientVitalsModule", "processedHeartRateData", "heartData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM


////		application.addTupleMapping("respRateModule", "RESPIRATORYRATE", "RESPIRATORYRATESTREAM", new FractionalSelectivity(1.0)); // respRateModule(RESPIRATORYRATE) returns RESPIRATORYRATESTREAM
		 
		// Application loops
		final AppLoop heartRateMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("heartRate");
				add("heartRateModule");
				add("PATIENTMONITORDISPLAY");
			}
		});
		final AppLoop bloodPressureMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("bloodPressure");
				add("bloodPressureModule");
				add("PATIENTMONITORDISPLAY");
			}
		});
		final AppLoop o2SatMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("o2Saturation");
				add("o2SatModule");
				add("PATIENTMONITORDISPLAY");
			}
		});
		
		List<AppLoop> loops = new ArrayList<AppLoop>(){
			{
				add(heartRateMonitorLoop);
				add(bloodPressureMonitorLoop);
				add(o2SatMonitorLoop);
			}
		};
		
		application.setLoops(loops);
		return application;
	}
}
