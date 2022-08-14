package fecsimulator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

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
import ifogsim.policy.AppModuleAllocationPolicy;
import ifogsim.scheduler.StreamOperatorScheduler;
import ifogsim.utils.FogLinearPowerModel;
import ifogsim.utils.FogUtils;
import ifogsim.utils.TimeKeeper;
import ifogsim.utils.distribution.DeterministicDistribution;
import serverlessStubs.Cloudwatch;
import serverlessStubs.EnvVariables;
import serverlessStubs.SNSTopic;

public class HospitalSimulation {
	
	//Global variable so fog devices can determine if serverless functions have been integrated
	public static boolean useServerless;
	//Global variable to allow simulation start time to be retrieved from controller class at end of simulation
	public static Date startTimeDate;
	//Track latency of each simulation run to determine average
	public static double  loopexecutionTime;
	
	public static void main(String[] args) {
		Log.printLine("Starting Hopital Simulation");
		startSim();	
	}

	public static void startSim() {
		Log.disable();
		try {		

			// Instantiate the Cloudsim class
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; 
			CloudSim.init(num_user, calendar, trace_flag);		
			String appId = "SmartHospital";
			int numOfHospitalWings = 0;
			//Prompt user to determine how many hospital wings to use in the simulation
			do {
				numOfHospitalWings = determineAmountofHospitalWings();
			} while (numOfHospitalWings <1 || numOfHospitalWings>5);
			int numOfpatientsPerWing = 10;
			String snsTopicName = "PatientMonitor";
			
			//Determine if serverless integration is to apply and if so create an SNS topic
			setUseServerless();	
			if(useServerless) {
				createSNSTopic(snsTopicName);
			} 
			
			//Create Array lists to store environment devices
			List<FogDevice> fogDevices = new ArrayList<FogDevice>(); // List to store all fog devices
			List<Sensor> sensors = new ArrayList<Sensor>(); // List to store all sensors
			List<Actuator> actuators = new ArrayList<Actuator>(); // List to store all actuators
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId(), snsTopicName);	//Takes appIdentifier, broker identifier and SNS topic name for orchestrator module
			createFogDevices(broker.getId(), appId, fogDevices, numOfHospitalWings, actuators, numOfpatientsPerWing, sensors);	//Create all environment components
			Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
			//Application is submitted with details of its fogdevices, sensors, actuators, application modules/communications and a mapping of each module to its fog device
			controller.submitApplication(application, new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, mapApplicationModules(fogDevices)));	
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			CloudSim.startSimulation();
			CloudSim.stopSimulation();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}
	
	@SuppressWarnings("resource")
	public static int determineAmountofHospitalWings() {
		Scanner sc = new Scanner(System.in);
		System.out.println("Please enter an amount of hopsital wings between 1 and 5 to integrate into the simulation");
		return sc.nextInt();
	}
	
	
	//Allow user to set if servrless environment is to be integrated into FEC simulation
	public static void setUseServerless() {
		//Method invoked will return true if user inputs they want ot use servlerss environment, false otherwise
		useServerless = useServerlessEnvironment();
	}
	
	public static boolean getUseServerless() {
		return useServerless;
	}

	//Returns true if user wants to use serverless environment, false otherwise
	@SuppressWarnings("resource")
	private static boolean useServerlessEnvironment() {
		Scanner sc = new Scanner(System.in);			// Scanner to accept user input	
		//Prompt user for input
		System.out.println("Do you want to integrate serverless functions into the simulation?[y/n]");
		char userChoice = sc.next().charAt(0);
		if(userChoice == 'y') { 
			//Set all environment variables needed for AWS access
			setEnvironmentVariables();
			//Get date to use for cloudwatch lower time boundary
			startTimeDate = Cloudwatch.getCurrentDateTime();
			return true;
		}
		else if(userChoice == 'n') {
			return false;
		} else {
			System.out.println("You havent input a valid character. Only y or n are accepted");
			return useServerlessEnvironment();
		}	
	}
	
	
	//Method to set variables to be used as environment variables in EnvVariables class
	@SuppressWarnings("resource")
	public static void setEnvironmentVariables() {
		try {
			System.out.println("***NCI ACCESS***\n AWS Crdentials can be found by logging in at: https://ncirl.awsapps.com/start#/");
			Scanner sc = new Scanner(System.in);
			System.out.println("What is your AWS access key ID?");
			String accessKey = sc.nextLine();
			System.out.println("What is your AWS secret access key?");
			String secretKey = sc.next();
			System.out.println("What is your AWS session token?");
			String sessionToken = sc.next();
			EnvVariables.setEnvironmentVariables(accessKey, secretKey, sessionToken);	
		}catch (Exception e) {
			System.out.println("Error setting environment variables");
			e.printStackTrace();
		}
	}
	
	
	//Method to create an SNS topic
	public static void createSNSTopic(String snsTopicName) {
		try {
			SNSTopic.createSNSTopic(snsTopicName);
		} catch(Exception e) {
			System.out.println("Error creating SNS topic");
			e.printStackTrace();
		}
	}
	
	private static Application createApplication(String appId, int brokerId, String SNSTopicName) {
		Application application = Application.createApplication(appId, brokerId);
		try {
			createAllApplicationModuleDetails(application, SNSTopicName);	
			createAllApplicationLoops(application);
		} catch (Exception e) {
			System.out.println("Error creating Application");
			e.printStackTrace();
		}
		return application;
	}
	
	private static void createAllApplicationModuleDetails(Application application, String SNSTopicName) {
		//Add application modules that are used by all sensors
		application.addOrchestratorAppModule("orchestratorModule",10, SNSTopicName);
		application.addAppModule("patientMonitorMasterModule",10 );
		try {			
			createAppModuleFlow(application, "heartRate"); // Set application module, edges and tuple mappings for heart rate sensors
			createAppModuleFlow(application, "bloodPressure"); // Set application module, edges and tuple mappings for blood pressure sensors
			createAppModuleFlow(application, "o2Saturation"); // Set application module, edges and tuple mappings for o2Saturation sensors
			createAppModuleFlow(application, "respiratoryRate"); // Set application module, edges and tuple mappings for respiratory rate sensors
		} catch (Exception e) {
			System.out.println("Error creating application modules");
			e.printStackTrace();
		}
		
	}

	private static void createAppModuleFlow(Application application, String module) {
		try {
			String collectionModule = module+"Module";
			String collectionModuleOutput = module+"ModuleOut";
			String orchestratorOutput = module+"OrchestratorOut";
			String masterMonitorOutput = module+"MasterMonitorOut";
			application.addAppModule(collectionModule, 10);
			application.addAppEdge(module, collectionModule, 50, 50, module, Tuple.UP, AppEdge.SENSOR);
			application.addTupleMapping(collectionModule, module, collectionModuleOutput, new FractionalSelectivity(1.0));
			application.addAppEdge(collectionModule, "orchestratorModule", 50, 50, collectionModuleOutput, Tuple.UP, AppEdge.MODULE);
			application.addTupleMapping("orchestratorModule", collectionModuleOutput, orchestratorOutput, new FractionalSelectivity(1.0));
			application.addAppEdge("orchestratorModule","patientMonitorMasterModule", 50, 50, orchestratorOutput, Tuple.UP, AppEdge.MODULE);
			application.addTupleMapping("patientMonitorMasterModule", orchestratorOutput, masterMonitorOutput, new FractionalSelectivity(1.0));
			application.addAppEdge("patientMonitorMasterModule","monitorMasterDisplay", 50, 1000, masterMonitorOutput, Tuple.DOWN, AppEdge.ACTUATOR);
		}catch (Exception e ) {
			System.out.println("Error creating application module flow");
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("serial")
	private static void createAllApplicationLoops(Application application) {
		List<AppLoop> loops = new ArrayList<AppLoop>(){{
			add(createAppLoop("heartRate"));
			add(createAppLoop("bloodPressure"));
			add(createAppLoop("o2Saturation"));
			add(createAppLoop("respiratoryRate"));
			add(createAppLoop("orchToActuator"));
		}};
		application.setLoops(loops);
	}

	private static ModuleMapping mapApplicationModules(List<FogDevice> fogDevices) {
		// Assign the application modules to their designated fog devices
		try {
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
			for (FogDevice device : fogDevices) {
				// Assign modules to each individual PatientMonitor device to allow each monitorto monitor sensor data
				if (device.getName().startsWith("PatientMonitor-")) {
					moduleMapping.addModuleToDevice("heartRateModule", device.getName()); // Attach module to read heart rate sensor data
					moduleMapping.addModuleToDevice("bloodPressureModule", device.getName()); // Attach module to read blood pressure sensor data
					moduleMapping.addModuleToDevice("o2SaturationModule", device.getName()); // Attach module to read o2 saturation sensor data
					moduleMapping.addModuleToDevice("respiratoryRateModule", device.getName()); // Attach module to read respiratory rate sensor data
					moduleMapping.addModuleToDevice("orchestratorModule", device.getName()); // Attach orchestrator module to each patient monitor device
				}
				if (device.getName().startsWith("patientMonitorMaster")) {
					moduleMapping.addModuleToDevice("patientMonitorMasterModule", device.getName());
					//moduleMapping.addModuleToDevice("patientMonitorMasterBloodModule", device.getName());
				}
			}
			return moduleMapping;
		} catch (Exception e) {
			System.out.println("Error mapping application modules");
			e.printStackTrace();
			return null;
		}
	}

	// This method will be used to create AppLoops (Only working for heart rate at the moment
	@SuppressWarnings("serial")
	private static AppLoop createAppLoop(String loopType) {
		if(loopType.equals("orchToActuator")) {
			AppLoop loop = new AppLoop(new ArrayList<String>() {
				// Each loop will contain its argument value, Module appended to the argument
				// value and orchestrator module
				{
					add("orchestratorModule");
					add("patientMonitorMasterModule");
					add("monitorMasterDisplay");
				}
			});	
			return loop;
		} else {
			AppLoop loop = new AppLoop(new ArrayList<String>() {
				{
					add(loopType);
					add(loopType + "Module");
					add("orchestratorModule");
				}
			});
			return loop;
		}
	}

	// Method to create all fog devices for the simulation
	private static void createFogDevices(int userId, String appId, List<FogDevice> fogDevices, int numOfHospitalWings,
			List<Actuator> actuators, int numOfpatientsPerWing, List<Sensor> sensors) {
		// Create Cloud Device at level 0
		FogDevice cloud = createFogDevice("cloud", "cloud", 0); // createFogDevice method required device type and its															// level as arguments
		cloud.setParentId(-1);
		fogDevices.add(cloud);

		// Create proxy-server device at level 1
		FogDevice proxy = createFogDevice("proxy-server", "Pi3BPlus", 1);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		// Loop through each hospital wing and assign patient monitor
		String wingIdentifier;
		

		// All sensors will need an initial value which will be assigned using array values to allow simulation duplications
		int[] heartrateSensorInitialValues = new int[numOfpatientsPerWing];
		int[] bloodPressureSensorInitialValues = new int[numOfpatientsPerWing];
		int[] o2SaturationSensorInitialValues = new int[numOfpatientsPerWing];
		int[] respiratoryrateSensorInitialValues = new int[numOfpatientsPerWing];

		// Implement hospital wing with specific identifiers and sensor initial values for each
		for (int i = 0; i < numOfHospitalWings; i++) {
			switch (i) {
			case 0:
				wingIdentifier = "WingA";
				heartrateSensorInitialValues = new int[] {82, 45, 55, 65, 75, 85, 95, 105, 115, 40};
				bloodPressureSensorInitialValues = new int[] {66, 79, 73, 115, 78, 84, 70, 73, 134, 104};
				o2SaturationSensorInitialValues = new int[] {98, 99, 99, 96, 93, 94, 93, 99, 97, 100};
				respiratoryrateSensorInitialValues = new int[] {13, 14, 14, 16, 15, 13, 15, 13, 14, 13};
				break;
			case 1:	
				wingIdentifier = "WingB";
				heartrateSensorInitialValues = new int[]{50,82,22,80,70,90,92,68,70,46};
				bloodPressureSensorInitialValues = new int[] {131,112,120,117,91,98,68,51,95,82};
				o2SaturationSensorInitialValues = new int[] {98,97,98,95,93,95,96,97,100,99};
				respiratoryrateSensorInitialValues = new int[]{16,12,15,16,15,11,12,12,10,15};
				break;
			case 2:	
				wingIdentifier = "WingC";
				heartrateSensorInitialValues = new int[]{80,68,45,110,105,100,68,79,84,37};
				bloodPressureSensorInitialValues = new int[] {59,68,112,73,85,86,87,106,128,66};
				o2SaturationSensorInitialValues = new int[]{87,96,99,100,86,96,95,92,85,100};
				respiratoryrateSensorInitialValues = new int[]{13,14,12,15,12,13,15,16,14,13};
				break;
			case 3:	
				wingIdentifier = "WingD";
				heartrateSensorInitialValues = new int[]{90,58,64,28,70,90,92,68,70,46};
				bloodPressureSensorInitialValues = new int[] {79,110,94,116,92,82,121,53,71,118};
				o2SaturationSensorInitialValues = new int[] {95,91,93,86,91,96,93,89,87,100};
				respiratoryrateSensorInitialValues = new int[]{14,12,18,12,14,14,12,16,17,12};
				break;
			case 4:	
				wingIdentifier = "WingE";
				heartrateSensorInitialValues = new int[]{50,73,81,94,88,77,90,60,90,33};
				bloodPressureSensorInitialValues = new int[] {105,75,84,118,77,119,58,94,109,117};
				o2SaturationSensorInitialValues = new int[] {90,93,99,89,91,89,90,96,99,92};
				respiratoryrateSensorInitialValues = new int[]{18,17,16,17,17,18,17,17,12,17};
				break;
			default:
				wingIdentifier = "Invalid Wing";
				break;
			}
			addHospitalWing(wingIdentifier, userId, appId, proxy.getId(), fogDevices, actuators, numOfpatientsPerWing,
					sensors, heartrateSensorInitialValues, bloodPressureSensorInitialValues,
					o2SaturationSensorInitialValues, respiratoryrateSensorInitialValues);
		}
	}

	private static void addHospitalWing(String id, int userId, String appId, int parentId,
			List<FogDevice> fogDevices, List<Actuator> actuators, int numOfpatientsPerWing, List<Sensor> sensors,
			int[] heartRateSensorInitialValues, int[] bloodPressureSensorInitialValues,
			int[] o2SaturationSensorInitialValues, int[] respiratoryrateSensorInitialValues) {
		
		try {
			// Create router device for each hospital wing at level 2
			FogDevice router = createFogDevice("router-" + id, "Pi3BPlus", 2);
			router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
			router.setParentId(parentId);
			fogDevices.add(router);
			
			// PATIENT MONITOR COMPONENTS
			// Create PatientMonitor Master Components at level 3
			FogDevice patientMonitorMaster = createFogDevice("patientMonitorMaster-" + id, "Pi3BPlus", 3);
			patientMonitorMaster.setUplinkLatency(2);
			patientMonitorMaster.setParentId(router.getId());
			fogDevices.add(patientMonitorMaster);
			
			// Instantiate patient monitor devices with initial sensor values from arrays
			for (int i = 0; i < numOfpatientsPerWing; i++) {
				String patientMonitorUnitId = "PatientMonitor-" + (i + 1) + ":" + id;
				FogDevice patientMonitor = addPatientMonitor(patientMonitorUnitId, userId, appId, actuators, sensors, 
						heartRateSensorInitialValues[i], bloodPressureSensorInitialValues[i], 
						o2SaturationSensorInitialValues[i], respiratoryrateSensorInitialValues[i]);
				patientMonitor.setUplinkLatency(2); // latency of connection between patient monitor and patient monitor
				patientMonitor.setParentId(patientMonitorMaster.getId());
				fogDevices.add(patientMonitor);
			}
	
			// Create an actuator to display sensor info of all wing patients in a master display
			Actuator patientMonitorMasterDisplay = new Actuator("MonitorMasterDisplay-" + id, userId, appId,
					"monitorMasterDisplay");
			patientMonitorMasterDisplay.setGatewayDeviceId(patientMonitorMaster.getId());
			patientMonitorMasterDisplay.setLatency(1.0);
			actuators.add(patientMonitorMasterDisplay);
		} catch (Exception e) {
			System.out.println("Error creating hospital wing devices: " + id);
			e.printStackTrace();
		}
	}

	private static FogDevice addPatientMonitor(String id, int userId, String appId, List<Actuator> actuators, List<Sensor> sensors,
			int heartRateSensorInitialValue, int bloodPressureSensorInitialValue, int o2SaturationSensorInitialValue,
			int respiratoryrateSensorInitialValue) {
		// Patient monitors will be raspberry Pi components at level 4
		FogDevice patientMonitor = createFogDevice(id, "Pi3BPlus", 4);
		addPatientMonitorSensors(id, userId, appId, patientMonitor.getId(), sensors, heartRateSensorInitialValue,
				bloodPressureSensorInitialValue, o2SaturationSensorInitialValue, respiratoryrateSensorInitialValue);
		return patientMonitor;
	}

	// Patient Monitor Sensors with their initial values set using array index
	// values retrieved by addHospitalWing invoking function
	private static void addPatientMonitorSensors(String id, int userId, String appId, int parentId,
			List<Sensor> sensors, int heartRateSensorInitialValue, int bloodPressureSensorInitialValue,
			int o2SaturationSensorInitialValue, int respiratoryrateSensorInitialValue) {
		int monitorSensorTransmissionTime = 1;

		// Create heart rate sensor
		try {
			Sensor heartRateSensor = new Sensor(id + "-hrSensor", "heartRate", userId, appId,
					new DeterministicDistribution(monitorSensorTransmissionTime), heartRateSensorInitialValue);
			heartRateSensor.setGatewayDeviceId(parentId);
			heartRateSensor.setLatency(1.0);
			sensors.add(heartRateSensor);
		} catch (Exception e) {
			System.out.println("Issue creating heart rate sensor");
			e.printStackTrace();
		}

		// Create blood pressure sensor
		try {
			Sensor bloodPressureSensor = new Sensor(id + "-bpSensor", "bloodPressure", userId, appId,
					new DeterministicDistribution(monitorSensorTransmissionTime), bloodPressureSensorInitialValue);
			bloodPressureSensor.setGatewayDeviceId(parentId);
			bloodPressureSensor.setLatency(1.0);
			sensors.add(bloodPressureSensor);
		} catch (Exception e) {
			System.out.println("Issue creating blood pressure sensor");
			e.printStackTrace();
		}
//
//		// Create o2Saturation sensor
		try {
			Sensor o2SaturationSensor = new Sensor(id + "-o2Sensor", "o2Saturation", userId, appId,
					new DeterministicDistribution(monitorSensorTransmissionTime), o2SaturationSensorInitialValue);
			o2SaturationSensor.setGatewayDeviceId(parentId);
			o2SaturationSensor.setLatency(1.0);
			sensors.add(o2SaturationSensor);
		} catch (Exception e) {
			System.out.println("Issue creating o2Saturation sensor");
			e.printStackTrace();
		}
//
//		// Create respiratoryRate sensor
		try {
			Sensor respiratoryRateSensor = new Sensor(id + "-rrSensor", "respiratoryRate", userId, appId,
					new DeterministicDistribution(monitorSensorTransmissionTime), respiratoryrateSensorInitialValue);
			respiratoryRateSensor.setGatewayDeviceId(parentId);
			respiratoryRateSensor.setLatency(1.0);
			sensors.add(respiratoryRateSensor);
		} catch (Exception e) {
			System.out.println("Issue creating respiratoryRate sensor");
			e.printStackTrace();
		}
	}
	
	//Method that can create devices with either cloud or Raspberry Pi3BPlus specifications
	private static FogDevice createFogDevice(String nodeName, String deviceType, int level) {
		try {
			// Default iFogSim provided variable values
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
			if (deviceType == "cloud") {
				// Cloud variable values will use default values provided by iFogSim
				peList.add(new Pe(0, new PeProvisionerOverbooking(44800)));
				PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(40000),
						new BwProvisionerOverbooking(bw), storage, peList, new StreamOperatorScheduler(peList),
						new FogLinearPowerModel(16 * 103, 16 * 83.25));
				hostList.add(host);
				FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone,
						cost, costPerMem, costPerStorage, costPerBw);
				FogDevice fogDevice = new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList),
						storageList, 10, 100, 10000, 0, 0.0);
				fogDevice.setLevel(level);
				return fogDevice;
			}
			if (deviceType == "Pi3BPlus") {
				peList.add(new Pe(0, new PeProvisionerOverbooking(2800)));
				PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(1000), new BwProvisionerOverbooking(bw),
						storage, peList, new StreamOperatorScheduler(peList),
						new FogLinearPowerModel(107.339, 83.4333));
				hostList.add(host);
				FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone,
						cost, costPerMem, costPerStorage, costPerBw);
				FogDevice fogDevice = new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList),
						storageList, 10, 10000, 10000, 0, 0.01);
				fogDevice.setLevel(level);
				return fogDevice;
			}
		} catch (Exception e) {
			System.out.println("There was an issue creating the " + deviceType + " device at level " + level + " with name: " + nodeName);
			e.printStackTrace();
		}
		//Return null if unable to create device
		return null;
	}
}