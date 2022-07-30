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

	public static void main(String[] args) {
		Log.printLine("Starting Hopital Simulation");
		// Create an SNS Topic prior to the publish of any messages
//		try {
//			SNSTopic.createSNSTopic("PatientMonitor");
//		} catch(Exception e) {
//			e.printStackTrace();
//			return;
//		}

//		try {
//		SNSTopic.subscribeToTopic();
//	} catch(Exception e) {
//		e.printStackTrace();
//		return;
//	}
		startSim("Test");
	}

	public static void startSim(String SNSTopicName) {
		try {
			// Instantiate the Cloudsim class
			try {
				Log.disable();
				int num_user = 1; // number of cloud users
				Calendar calendar = Calendar.getInstance();
				boolean trace_flag = false; // mean trace events
				CloudSim.init(num_user, calendar, trace_flag);
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}

			try {
				String appId = "SmartHospital"; // identifier of the application
				int numOfHospitalWings = 1;
				int numOfpatientsPerWing = 1;
				FogBroker broker = new FogBroker("broker");
				Application application = createApplication(appId, broker.getId(), SNSTopicName);
				application.setUserId(broker.getId());
				List<FogDevice> fogDevices = new ArrayList<FogDevice>(); // List to store each component
				List<Sensor> sensors = new ArrayList<Sensor>(); // List to store all sensors
				List<Actuator> actuators = new ArrayList<Actuator>(); // List to store all actuators
				ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
				// Create all devices in the FEC environment
				try {
					createFogDevices(broker.getId(), appId, fogDevices, numOfHospitalWings, actuators,
							numOfpatientsPerWing, sensors);
					// Assign the application modules to their designated fog devices
					moduleMapping = mapApplicationModules(moduleMapping, fogDevices);
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
				boolean CLOUD = false;
				Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

				controller.submitApplication(application, (CLOUD)
						? (new ModulePlacementMapping(fogDevices, application, moduleMapping))
						: (new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

				TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
				CloudSim.startSimulation();
				CloudSim.stopSimulation();

			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("serial")
	private static Application createApplication(String appId, int userId, String SNSTopicName) {
		Application application = Application.createApplication(appId, userId);
		
		//Add application modules that are used by all sensors
		application.addOrchestratorAppModule("orchestratorModule",10, SNSTopicName);
		application.addAppModule("patientMonitorMasterModule", 10);

		createAppModuleFlow(application, "heartRate"); // Set application module, edges and tuple mappings for heart rate sensors
		createAppModuleFlow(application, "bloodPressure"); // Set application module, edges and tuple mappings for blood pressure sensors
		createAppModuleFlow(application, "o2Saturation"); // Set application module, edges and tuple mappings for o2Saturation sensors
		createAppModuleFlow(application, "respiratoryRate"); // Set application module, edges and tuple mappings for respiratory rate sensors

		// All loops added to this list are created through invoking the createAppLoop method with a relevant loop argument
		List<AppLoop> loops = new ArrayList<AppLoop>() {
			{
				add(createAppLoop("heartRate"));
				add(createAppLoop("bloodPressure"));
				add(createAppLoop("o2Saturation"));
				add(createAppLoop("respiratoryRate"));
			}
		};
		application.setLoops(loops);
		return application;
	}

	private static void createAppModuleFlow(Application application, String module) {
		application.addAppModule(module + "Module", 10);
		// Mapping for the sensor to its value collection module
		application.addAppEdge(module, module + "Module", 50, 50, module, Tuple.UP, AppEdge.SENSOR);
		// Mapping for collection module to orchestrator module
		application.addAppEdge(module + "Module", "orchestratorModule", 50, 50, module + "Data", Tuple.UP, AppEdge.MODULE);
		//application.addAppEdge("orchestratorModule", "patientMonitorMasterModule",50, 50, module+"Data", Tuple.UP, AppEdge.MODULE);
		//application.addAppEdge("patientMonitorMasterModule", "monitorMasterDisplay",50, 50, module+"Data", Tuple.DOWN, AppEdge.ACTUATOR);
		// Application module Input/Outputs
		application.addTupleMapping(module + "Module", module, module + "Data", new FractionalSelectivity(1.0));
		application.addTupleMapping("orchestratorModule", module+"Data",module+"Data", new FractionalSelectivity(1.0));
		// application.addTupleMapping("patientMonitorMasterModule", module+"Data", module+"Data", new FractionalSelectivity(1.0));
	}

	private static ModuleMapping mapApplicationModules(ModuleMapping moduleMapping, List<FogDevice> fogDevices) {
		// Assign the application modules to their designated fog devices
		try {
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
				}
			}
			return moduleMapping;
		} catch (Exception e) {
			System.out.println("Error mapping application modules");
			e.printStackTrace();
			return null;
		}
	}

	// This method will be used to create AppLoops
	private static AppLoop createAppLoop(String loopType) {
		@SuppressWarnings("serial")
		AppLoop loop = new AppLoop(new ArrayList<String>() {
			// Each loop will contain its argument value, Module appended to the argument
			// value and orchestrator module
			{
				add(loopType);
				add(loopType + "Module");
				add("orchestratorModule");
				//add("patientMonitorMasterModule");
				// add("monitorMasterDisplay");
			}
		});
		return loop;
	}

	// Method to create all fog devices for the simulation
	private static void createFogDevices(int userId, String appId, List<FogDevice> fogDevices, int numOfHospitalWings,
			List<Actuator> actuators, int numOfpatientsPerWing, List<Sensor> sensors) {
		// Create Cloud Device at level 0
		FogDevice cloud = createFogDevice("cloud", "cloud", 0); // createFogDevice method required device type and its
																// level as arguments
		cloud.setParentId(-1);
		fogDevices.add(cloud);

		// Create proxy-server device at level 1
		FogDevice proxy = createFogDevice("proxy-server", "Pi3BPlus", 1);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);

		// Loop through each hospital wing and assign patient monitor
		String wingIdentifier;

		// All sensors will need an initial value which will be assigned using array
		// values to allow simulation duplications
		int[] heartrateSensorInitialValues = new int[numOfpatientsPerWing];
		int[] bloodPressureSensorInitialValues = new int[numOfpatientsPerWing];
		int[] o2SaturationSensorInitialValues = new int[numOfpatientsPerWing];
		int[] respiratoryrateSensorInitialValues = new int[numOfpatientsPerWing];

		// Implement hospital wing with specific identifiers and sensor initial values
		// for each
		for (int i = 0; i < numOfHospitalWings; i++) {
			switch (i) {
			case 0:
				wingIdentifier = "WingA";
				heartrateSensorInitialValues = new int[] { 35, 45, 55, 65, 75, 85, 95, 105, 115, 40 };
				bloodPressureSensorInitialValues = new int[] { 66, 79, 73, 115, 78, 84, 70, 73, 134, 104 };
				o2SaturationSensorInitialValues = new int[] { 98, 99, 99, 96, 93, 94, 93, 99, 97, 100 };
				respiratoryrateSensorInitialValues = new int[] { 13, 14, 14, 16, 15, 13, 15, 13, 14, 13 };
				break;
//			case 1:	
//				wingIdentifier = "WingB";
//				heartrateSensorInitialValues = new int[]{50,82,22,80,70,90,92,68,70,46};
//				bloodPressureSensorInitialValues = new int[] {131,112,120,117,91,98,68,51,95,82};
			// o2SaturationSensorInitialValues = new int[] {98,97,98,95,93,95,96,97,100,99};
			// respiratoryrateSensorInitialValues = new int[]
			// {16,12,15,16,15,11,12,12,10,15};
//				break;
//			case 2:	
//				wingIdentifier = "WingC";
//				heartrateSensorInitialValues = new int[]{80,68,45,110,105,100,68,79,84,37};
//				bloodPressureSensorInitialValues = new int[] {59,68,112,73,85,86,87,106,128,66};
			// o2SaturationSensorInitialValues = new int[]
			// {87,96,99,100,86,96,95,92,85,100};
			// respiratoryrateSensorInitialValues = new int[]
			// {13,14,12,15,12,13,15,16,14,13};
//				break;
//			case 3:	
//				wingIdentifier = "WingD";
//				heartrateSensorInitialValues = new int[]{90,58,64,28,70,90,92,68,70,46};
//				bloodPressureSensorInitialValues = new int[] {79,110,94,116,92,82,121,53,71,118};
			// o2SaturationSensorInitialValues = new int[] {95,91,93,86,91,96,93,89,87,100};
			// respiratoryrateSensorInitialValues = new int[]
			// {14,12,18,12,14,14,12,16,17,12};
//				break;
//			case 4:	
//				wingIdentifier = "WingE";
//				heartrateSensorInitialValues = new int[]{50,73,81,94,88,77,90,60,90,33};
//				bloodPressureSensorInitialValues = new int[] {105,75,84,118,77,119,58,94,109,117};
			// o2SaturationSensorInitialValues = new int[] {90,93,99,89,91,89,90,96,99,92};
			// respiratoryrateSensorInitialValues = new int[]
			// {18,17,16,17,17,18,17,17,12,17};
//				break;
//			case 5:	
//				wingIdentifier = "WingF";
//				heartrateSensorInitialValues = new int[]{80,63,90,75,80,70,77,82,75,68};
//				bloodPressureSensorInitialValues = new int[] {129,61,99,87,57,107,109,57,137,99};
			// o2SaturationSensorInitialValues = new int[]
			// {100,95,98,93,100,92,93,94,97,97};
			// respiratoryrateSensorInitialValues = new int[]
			// {14,12,13,13,12,15,14,12,12,12};
//				break;
//			case 6:	
//				wingIdentifier = "WingG";
//				heartrateSensorInitialValues = new int[]{70,83,81,74,78,87,95,68,96,43};
//				bloodPressureSensorInitialValues = new int[] {95,83,97,86,106,103,101,112,78,73};
			// o2SaturationSensorInitialValues = new int[] {99,94,99,98,99,96,95,96,95,96};
			// respiratoryrateSensorInitialValues = new int[]
			// {14,15,14,17,15,18,13,17,15,17};
//				break;
//			case 7:	
//				wingIdentifier = "WingH";
//				heartrateSensorInitialValues = new int[]{70,73,80,92,96,93,88,70,70,83};
//				bloodPressureSensorInitialValues = new int[] {117,114,102,68,102,67,66,65,50,53};
			// o2SaturationSensorInitialValues = new int[] {96,95,98,97,100,95,99,95,95,96};
			// respiratoryrateSensorInitialValues = new int[] {13,15,14,9,13,9,13,16,14,14};
//				break;
//			case 8:	
//				wingIdentifier = "WingI";
//				heartrateSensorInitialValues = new int[]{78,76,85,74,78,74,80,50,48,63};
//				bloodPressureSensorInitialValues = new int[] {121,54,97,65,67,88,102,63,110,110};
			// o2SaturationSensorInitialValues = new int[]
			// {99,95,100,98,97,99,99,100,96,97};
			// respiratoryrateSensorInitialValues = new int[]
			// {14,14,12,14,14,13,14,13,12,15};
//				break;
//			case 9:	
//				wingIdentifier = "WingJ";
//				heartrateSensorInitialValues = new int[]{50,73,81,94,88,77,90,60,90,33};
//				bloodPressureSensorInitialValues = new int[] {50,122,114,86,62,77,56,66,54,121};
			// o2SaturationSensorInitialValues = new int[] {93,99,99,93,96,95,97,98,96,95};
			// respiratoryrateSensorInitialValues = new int[]
			// {15,13,12,12,13,13,15,12,15,15};
//				break;
			default:
				wingIdentifier = "Invalid Wing";
				break;
			}
			addHospitalWing(wingIdentifier, userId, appId, proxy.getId(), fogDevices, actuators, numOfpatientsPerWing,
					sensors, heartrateSensorInitialValues, bloodPressureSensorInitialValues,
					o2SaturationSensorInitialValues, respiratoryrateSensorInitialValues);
		}
	}

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
			// System.out.println("There was an issue creating the " + deviceType + " device
			// at level " + level + " with name: " + nodeName);
			e.printStackTrace();
			return null;
		}
		return null;
	}

	private static FogDevice addHospitalWing(String id, int userId, String appId, int parentId,
			List<FogDevice> fogDevices, List<Actuator> actuators, int numOfpatientsPerWing, List<Sensor> sensors,
			int[] heartRateSensorInitialValues, int[] bloodPressureSensorInitialValues,
			int[] o2SaturationSensorInitialValues, int[] respiratoryrateSensorInitialValues) {
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

		// Create an actuator to display sensor info of all wing patients in a master
		// display
		Actuator patientMonitorMasterDisplay = new Actuator("MonitorMasterDisplay-" + id, userId, appId,
				"monitorMasterDisplay");
		patientMonitorMasterDisplay.setGatewayDeviceId(patientMonitorMaster.getId());
		patientMonitorMasterDisplay.setLatency(1.0);
		actuators.add(patientMonitorMasterDisplay);

		// Instantiate patient monitor devices with initial sensor values from arrays
		for (int i = 0; i < numOfpatientsPerWing; i++) {
			String patientMonitorUnitId = "PatientMonitor-" + (i + 1) + ":" + id;
			FogDevice patientMonitor = addPatientMonitor(patientMonitorUnitId, userId, appId,
					patientMonitorMaster.getId(), actuators, sensors, heartRateSensorInitialValues[i],
					bloodPressureSensorInitialValues[i], o2SaturationSensorInitialValues[i],
					respiratoryrateSensorInitialValues[i]);
			patientMonitor.setUplinkLatency(2); // latency of connection between patient monitor and patient monitor
												// master is 2 ms
			fogDevices.add(patientMonitor);
		}
		return router;
	}

	private static FogDevice addPatientMonitor(String id, int userId, String appId, int parentId,
			List<Actuator> actuators, List<Sensor> sensors, int heartRateSensorInitialValue,
			int bloodPressureSensorInitialValue, int o2SaturationSensorInitialValue,
			int respiratoryrateSensorInitialValue) {
		// Patient monitors will be raspberry Pi components at level 4
		FogDevice patientMonitor = createFogDevice(id, "Pi3BPlus", 4);
		patientMonitor.setParentId(parentId);
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
		}

		// Create blood pressure sensor
		try {
			Sensor bloodPressureSensor = new Sensor(id + "-bpSensor", "bloodPressure", userId, appId,
					new DeterministicDistribution(monitorSensorTransmissionTime), bloodPressureSensorInitialValue);
			bloodPressureSensor.setGatewayDeviceId(parentId);
			bloodPressureSensor.setLatency(1.0);
			sensors.add(bloodPressureSensor);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Issue creating blood pressure sensor");
		}

		// Create o2Saturation sensor
		try {
			Sensor o2SaturationSensor = new Sensor(id + "-o2Sensor", "o2Saturation", userId, appId,
					new DeterministicDistribution(monitorSensorTransmissionTime), o2SaturationSensorInitialValue);
			o2SaturationSensor.setGatewayDeviceId(parentId);
			o2SaturationSensor.setLatency(1.0);
			sensors.add(o2SaturationSensor);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Issue creating o2Saturation sensor");
		}

		// Create respiratoryRate sensor
		try {
			Sensor respiratoryRateSensor = new Sensor(id + "-rrSensor", "respiratoryRate", userId, appId,
					new DeterministicDistribution(monitorSensorTransmissionTime), respiratoryrateSensorInitialValue);
			respiratoryRateSensor.setGatewayDeviceId(parentId);
			respiratoryRateSensor.setLatency(1.0);
			sensors.add(respiratoryRateSensor);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Issue creating respiratoryRate sensor");
		}
	}
}