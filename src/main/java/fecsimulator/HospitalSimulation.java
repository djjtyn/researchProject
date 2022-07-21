package fecsimulator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Phaser;

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
import serverlessStubs.SNSTopic;

public class HospitalSimulation {
	

	static int oxygenSaturationSensorInitialValue = 95;
	static int respiratoryRateSensorInitialValue = 15;
		
	public static void main(String[] args) {
		Log.printLine("Starting Hopital Simulation");
		String SNSTopicName = "PatientMonitor";
		//Create an SNS Topic prior to the publish of any messages 
		try {
			SNSTopic.createSNSTopic(SNSTopicName);
		} catch(Exception e) {
			e.printStackTrace();
			return;
		}
		startSim(SNSTopicName);
	}
	
	public static void startSim(String SNSTopicName) {
		try {						
			//Instantiate the Cloudsim class 
			try {
				Log.disable();
				int num_user = 1; // number of cloud users
				Calendar calendar = Calendar.getInstance();
				boolean trace_flag = false; // mean trace events
				CloudSim.init(num_user, calendar, trace_flag);
			} catch(Exception e) {
				e.printStackTrace();
				return;
			}
			
			try {
				String appId = "SmartHospital"; // identifier of the application
				int numOfHospitalWings = 10;	
				int numOfpatientsPerWing = 10;	
				FogBroker broker = new FogBroker("broker");
				Application application = createApplication(appId, broker.getId(), SNSTopicName);
				application.setUserId(broker.getId());
				List<FogDevice> fogDevices = new ArrayList<FogDevice>(); //List to store each component
				List<Sensor> sensors = new ArrayList<Sensor>();	//List to store all sensors
				List<Actuator> actuators = new ArrayList<Actuator>();	//List to store all actuators
				ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
				// Create all devices in the FEC environment
				try { 
					createFogDevices(broker.getId(), appId, fogDevices, numOfHospitalWings, actuators, numOfpatientsPerWing, sensors);
				} catch(Exception e) {
					e.printStackTrace();
					return;
				}
				
				//Assign the application modules to their designated fog devices
				try {
					for(FogDevice device : fogDevices){
						//Add the orchestratorModule module to the orchestrator components
						if(device.getName().startsWith("PatientMonitorO")) {
							moduleMapping.addModuleToDevice("orchestratorModule", device.getName());
						}
						
						//Assign modules to each individual PatientMonitor device to allow each monitor to monitor sensor data
						if(device.getName().startsWith("PatientMonitor-")){
							moduleMapping.addModuleToDevice("heartRateModule", device.getName());	//Attach module to read heart rate sensor data
							moduleMapping.addModuleToDevice("bloodPressureModule", device.getName());	//Attach module to read blood pressure sensor data
							//moduleMapping.addModuleToDevice("o2SatModule", device.getName());	//Attach module to read blood pressure sensor data
							//moduleMapping.addModuleToDevice("respRateModule", device.getName());	//Attach module to read respiratory rate sensor data
						}
					}
				} catch(Exception e) {
					e.printStackTrace();
					return;
				}
				
				boolean CLOUD = false;
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
				return;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("serial")
	private static Application createApplication(String appId, int userId, String SNSTopicName){	
		
		Application application = Application.createApplication(appId, userId);
		application.addAppModule("heartRateModule", 10);	//AppModule to monitor patient heart rate
		application.addAppModule("bloodPressureModule", 10);	//AppModule to monitor patient blood pressure
		application.addOrchestratorAppModule("orchestratorModule", 10, SNSTopicName);	//AppModule to monitor determine serverless function/FEC environment resources - also takes SNSTpic Name to enable message publishing to correct topic
		//application.addAppModule("o2SatModule", 10);	//AppModule to monitor patient o2 saturation
		//application.addAppModule("respRateModule", 10);	//AppModule to monitor patient respiratory rate
		//application.addAppModule("patientVitalsModule", 10);
//		//application.addAppModule("triggerAlert", 10);
		
		
		application.addAppEdge("heartRate", "heartRateModule", 50, 50, "heartRate", Tuple.UP, AppEdge.SENSOR);	//Heart rate sensor -> heartRateModule: heartRate tuple communication
		application.addAppEdge("heartRateModule", "orchestratorModule", 50, 50, "heartRateData", Tuple.UP, AppEdge.MODULE);
		//application.addAppEdge("heartRateModule", "PATIENTMONITORDISPLAY", 100, 28, 100, "heartRateData", Tuple.DOWN, AppEdge.ACTUATOR);
		//application.addAppEdge("heartRateModule", "alarm", 100, 28, 100, "heartRateData", Tuple.UP, AppEdge.ACTUATOR);
		//application.addAppEdge("heartRateModule", "patientVitalsModule", 1000, 20000, "processedHeartRateData", Tuple.DOWN, AppEdge.MODULE);

		application.addAppEdge("bloodPressure", "bloodPressureModule", 50, 50, "bloodPressure", Tuple.UP, AppEdge.SENSOR);	//Blood pressure sensor -> bloodPressureModule: BLOODPRESSURE tuple communication
		application.addAppEdge("bloodPressureModule", "orchestratorModule", 50, 50, "bloodPressureData", Tuple.UP, AppEdge.MODULE);
		//    	application.addAppEdge("bloodPressureModule", "PATIENTMONITORDISPLAY", 100, 28, 100, "bloodPressureData", Tuple.DOWN, AppEdge.ACTUATOR);	
//		
//    	application.addAppEdge("o2Saturation", "o2SatModule", 1000, 20000, "o2Saturation", Tuple.UP, AppEdge.SENSOR);	//o2 Saturation sensor -> o2SatModule: O2SATURATION tuple communication
//    	application.addAppEdge("o2SatModule", "orchestratorModule", 2000, 2000, "o2SaturationData", Tuple.UP, AppEdge.MODULE);
    	
    	//application.addAppEdge("respiratoryRate", "respRateModule", 1000, 20000, "respiratoryRate", Tuple.UP, AppEdge.SENSOR);	//Respiratory rate sensor -> respRateModule: RESPIRATORYRATE tuple communication
    	//application.addAppEdge("respRateModule", "orchestratorModule", 2000, 2000, "respiratoryRateData", Tuple.UP, AppEdge.MODULE);
//		//Communication between individual patient monitors and hospital wing master monitor
//
//		
		// Application module Input/Outputs
		application.addTupleMapping("heartRateModule", "heartRate", "heartRateData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateData
		application.addTupleMapping("orchestratorModule", "heartRateData", "heartRateData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM
    	
		application.addTupleMapping("bloodPressureModule", "bloodPressure", "bloodPressureData", new FractionalSelectivity(1.0)); // bloodPressureModule(BLOODPRESSURE) returns BLOODPRESURESTREAM
    	application.addTupleMapping("orchestratorModule", "bloodPressureData", "bloodPressureData", new FractionalSelectivity(1.0));
    	
//    	application.addTupleMapping("o2SatModule", "o2Saturation", "o2SaturationData", new FractionalSelectivity(1.0)); // o2SatModule(O2SATURATION) returns O2SATURATIONSTREAM
//    	application.addTupleMapping("orchestratorModule", "o2SaturationData", "o2SaturationData", new FractionalSelectivity(1.0));
    	
    	//application.addTupleMapping("respRateModule", "respiratoryRate", "respiratoryRateData", new FractionalSelectivity(1.0)); // respRateModule(RESPIRATORYRATE) returns RESPIRATORYRATESTREAM
    	//application.addTupleMapping("orchestratorModule", "respiratoryRateData", "respiratoryRateData", new FractionalSelectivity(1.0));
    	
    	//application.addTupleMapping("heartRateModule", "heartRateRawData", "processedHeartRateData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM
		//application.addTupleMapping("patientVitalsModule", "processedHeartRateData", "heartData", new FractionalSelectivity(1.0)); // heartRateModule(heartRate) returns heartRateSTREAM



		 
		// Application loops
		final AppLoop heartRateMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("heartRate");
				add("heartRateModule");
				add("orchestratorModule");
				//add("PATIENTMONITORDISPLAY");
			}
		});
		final AppLoop bloodPressureMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("bloodPressure");
				add("bloodPressureModule");
				add("orchestratorModule");
				//add("PATIENTMONITORDISPLAY");
			}
		});
		final AppLoop o2SatMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("o2Saturation");
				add("o2SatModule");
				add("orchestratorModule");
			}
		});
		final AppLoop respRateMonitorLoop = new AppLoop(new ArrayList<String>() {
			{
				add("respiratoryRate");
				add("respRateModule");
				add("orchestratorModule");
			}
		});
		
		
		
		
		List<AppLoop> loops = new ArrayList<AppLoop>(){
			{
				add(heartRateMonitorLoop);
				add(bloodPressureMonitorLoop);
				//add(o2SatMonitorLoop);
				//add(respRateMonitorLoop);
			}
		};
		
		application.setLoops(loops);
		return application;
	}
	
	//Method to create all fog devices for the simulation
	private static void createFogDevices(int userId, String appId, List<FogDevice> fogDevices, int numOfHospitalWings, List<Actuator> actuators, int numOfpatientsPerWing, List<Sensor> sensors) {
		//Create Cloud Device at level 0
		FogDevice cloud = createFogDevice("cloud", "cloud", 0);	//createFogDevice method required device type and its level as arguments
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		
		//Create proxy-server device at level 1
		FogDevice proxy = createFogDevice("proxy-server","Pi3BPlus", 1);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		
		//Loop through each hospital wing and assign patient monitor
		String wingIdentifier;
		
		//All sensors will need an initial value which will be assigned using array values to allow simulation duplications
		int[] heartrateSensorInitialValues = new int[numOfpatientsPerWing];
		int[] bloodPressureSensorInitialValues = new int[numOfpatientsPerWing];
		
		//Implement hospital wing with specific identifiers and sensor initial values for each
		for(int i=0;i<numOfHospitalWings;i++){
			switch(i) {
			case 0: 
				wingIdentifier = "WingA";
				heartrateSensorInitialValues = new int[]{35,45,55,65,75,85,95,105,115,40};
				bloodPressureSensorInitialValues = new int[] {66,79,73,115,78,84,70,73,134,104};
				break;
			case 1:	
				wingIdentifier = "WingB";
				heartrateSensorInitialValues = new int[]{50,82,22,80,70,90,92,68,70,46};
				bloodPressureSensorInitialValues = new int[] {131,112,120,117,91,98,68,51,95,82};
				break;
			case 2:	
				wingIdentifier = "WingC";
				heartrateSensorInitialValues = new int[]{80,68,45,110,105,100,68,79,84,37};
				bloodPressureSensorInitialValues = new int[] {59,68,112,73,85,86,87,106,128,66};
				break;
			case 3:	
				wingIdentifier = "WingD";
				heartrateSensorInitialValues = new int[]{90,58,64,28,70,90,92,68,70,46};
				bloodPressureSensorInitialValues = new int[] {79,110,94,116,92,82,121,53,71,118};
				break;
			case 4:	
				wingIdentifier = "WingE";
				heartrateSensorInitialValues = new int[]{50,73,81,94,88,77,90,60,90,33};
				bloodPressureSensorInitialValues = new int[] {105,75,84,118,77,119,58,94,109,117};
				break;
			case 5:	
				wingIdentifier = "WingF";
				heartrateSensorInitialValues = new int[]{80,63,90,75,80,70,77,82,75,68};
				bloodPressureSensorInitialValues = new int[] {129,61,99,87,57,107,109,57,137,99};
				break;
			case 6:	
				wingIdentifier = "WingG";
				heartrateSensorInitialValues = new int[]{70,83,81,74,78,87,95,68,96,43};
				bloodPressureSensorInitialValues = new int[] {95,83,97,86,106,103,101,112,78,73};
				break;
			case 7:	
				wingIdentifier = "WingH";
				heartrateSensorInitialValues = new int[]{70,73,80,92,96,93,88,70,70,83};
				bloodPressureSensorInitialValues = new int[] {117,114,102,68,102,67,66,65,50,53};
				break;
			case 8:	
				wingIdentifier = "WingI";
				heartrateSensorInitialValues = new int[]{78,76,85,74,78,74,80,50,48,63};
				bloodPressureSensorInitialValues = new int[] {121,54,97,65,67,88,102,63,110,110};
				break;
			case 9:	
				wingIdentifier = "WingJ";
				heartrateSensorInitialValues = new int[]{50,73,81,94,88,77,90,60,90,33};
				bloodPressureSensorInitialValues = new int[] {50,122,114,86,62,77,56,66,54,121};
				break;
			default: wingIdentifier = "Invalid Wing";
					break;
			}
			addHospitalWing(wingIdentifier, userId, appId, proxy.getId(), fogDevices, actuators, numOfpatientsPerWing, sensors, heartrateSensorInitialValues, bloodPressureSensorInitialValues);
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
				peList.add(new Pe(0, new PeProvisionerOverbooking(2800))); 
				PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(1000), new BwProvisionerOverbooking(bw), storage, peList, new StreamOperatorScheduler(peList), new FogLinearPowerModel(107.339, 83.4333));
				hostList.add(host);
				FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);
				FogDevice fogDevice = new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList), storageList, 10, 10000, 10000, 0, 0.01);
				fogDevice.setLevel(level);
				return fogDevice;
			}
		} catch(Exception e) {
			//System.out.println("There was an issue creating the " + deviceType + " device at level " + level + " with name: " + nodeName);
			e.printStackTrace();
			return null;
		}
		return null;
	}
	
	private static FogDevice addHospitalWing(String id, int userId, String appId, int parentId, 
			List<FogDevice> fogDevices, List<Actuator> actuators, int numOfpatientsPerWing, List<Sensor> sensors, 
			int[] heartRateSensorInitialValues, int[] bloodPressureSensorInitialValues){
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
		FogDevice patientMonitorOrchestrator = createFogDevice("PatientMonitorOrchestrator-"+id, "Pi3BPlus", 4);
		patientMonitorOrchestrator.setParentId(patientMonitorMaster.getId());
		patientMonitorOrchestrator.setUplinkLatency(2);
		fogDevices.add(patientMonitorOrchestrator);
		
		//Create an actuator to emit alarm sounds
		Actuator alarm = new Actuator("alarm-" + id, userId, appId, "alarm");
		alarm.setGatewayDeviceId(patientMonitorMaster.getId());
		alarm.setLatency(1.0); 
		actuators.add(alarm);	
		
		//Instantiate patient monitor devices with initial sensor values from arrays
		for(int i=0;i<numOfpatientsPerWing;i++){
			String patientMonitorUnitId = "PatientMonitor-"+ (i+1) + ":" + id;
			FogDevice patientMonitor = addPatientMonitor(patientMonitorUnitId, userId, appId, patientMonitorOrchestrator.getId(), actuators, sensors, heartRateSensorInitialValues[i], bloodPressureSensorInitialValues[i]);
			patientMonitor.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
			fogDevices.add(patientMonitor);
		}
//		//Create an actuator for viewing all patient monitor units
//		Actuator patientMonitorDisplay = new Actuator(id+"-patientMasterdisplay", userId, appId, "PATIENTMONITORMASTERDISPLAY"); 
//		patientMonitorDisplay.setGatewayDeviceId(patientMonitorMaster.getId());
//		patientMonitorDisplay.setLatency(1.0); 
//		actuators.add(patientMonitorDisplay);
		return router;
	}
	
	private static FogDevice addPatientMonitor(String id, int userId, String appId, int parentId, List<Actuator>actuators, List<Sensor> sensors, int heartRateSensorInitialValue, int bloodPressureSensorInitialValue)	{
		// Patient monitors will be raspberry Pi components at level 5
		FogDevice patientMonitor = createFogDevice(id,"Pi3BPlus",5);
		patientMonitor.setParentId(parentId);
		
		addPatientMonitorSensors(id, userId, appId, patientMonitor.getId(), sensors, heartRateSensorInitialValue, bloodPressureSensorInitialValue);
		
		// Patient monitors will have a display to output their sensor data
		Actuator patientMonitorDisplay = new Actuator(id+"-display", userId, appId, "PATIENTMONITORDISPLAY");
		patientMonitorDisplay.setGatewayDeviceId(patientMonitor.getId());
		patientMonitorDisplay.setLatency(1.0); 
		actuators.add(patientMonitorDisplay);	
		return patientMonitor;
	}
	
	//Method to add sensors. 
	private static void addPatientMonitorSensors(String id, int userId, String appId, int parentId, List<Sensor> sensors, int heartRateSensorInitialValue, int bloodPressureSensorInitialValue) {
		int monitorSensorTransmissionTime = 1;
		//Patient Monitor Sensors with their initial values set using array index values retrieved by addHospitalWing invoking function
		Sensor heartRateSensor = new Sensor(id+"-hrSensor", "heartRate", userId, appId, new DeterministicDistribution(monitorSensorTransmissionTime), heartRateSensorInitialValue);
		heartRateSensor.setGatewayDeviceId(parentId);
		heartRateSensor.setLatency(1.0);
		sensors.add(heartRateSensor);
		
		
		Sensor bloodPressureSensor = new Sensor(id+"-bpSensor", "bloodPressure", userId, appId, new DeterministicDistribution(monitorSensorTransmissionTime), bloodPressureSensorInitialValue);
		bloodPressureSensor.setGatewayDeviceId(parentId);
		bloodPressureSensor.setLatency(1.0);
		bloodPressureSensorInitialValue+=20;
		sensors.add(bloodPressureSensor);
		
//		Sensor o2SaturationSensor = new Sensor(id+"-o2Sensor", "o2Saturation", userId, appId, new DeterministicDistribution(monitorSensorTransmissionTime), oxygenSaturationSensorInitialValue);
//		o2SaturationSensor.setGatewayDeviceId(parentId);
//		o2SaturationSensor.setLatency(1.0);
//		sensors.add(o2SaturationSensor);
		
//		Sensor respiratoryRateSensor = new Sensor(id+"-rrSensor", "respiratoryRate", userId, appId, new DeterministicDistribution(monitorSensorTransmissionTime), respiratoryRateSensorInitialValue);
//		respiratoryRateSensor.setGatewayDeviceId(parentId);
//		respiratoryRateSensor.setLatency(1.0);
//		sensors.add(respiratoryRateSensor);
//				
	}
}