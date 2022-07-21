package ifogsim.entities;

import java.util.ArrayList;
import java.util.Random;

import cloudsim.UtilizationModelFull;
import cloudsim.core.CloudSim;
import cloudsim.core.SimEntity;
import cloudsim.core.SimEvent;
import ifogsim.application.AppEdge;
import ifogsim.application.AppLoop;
import ifogsim.application.Application;
import ifogsim.utils.*;
import ifogsim.utils.distribution.Distribution;

public class Sensor extends SimEntity{
	
	private int gatewayDeviceId;
	private GeoLocation geoLocation;
	private long outputSize;
	private String appId;
	private int userId;
	private String tupleType;
	private String sensorName;
	private String destModuleName;
	private Distribution transmitDistribution;
	private int controllerId;
	private Application app;
	private double latency;
	
	//Values set at -1 to represent that sensor values haven't been set yet
	private int initialHeartRateSensorValue;
	//boolean initialSensorValue = true;
	
	
	
	private int transmissionStartDelay = Config.TRANSMISSION_START_DELAY;
	
	public Sensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation, 
			Distribution transmitDistribution, int cpuLength, int nwLength, String tupleType, String destModuleName) {
		super(name);
		this.setAppId(appId);
		this.gatewayDeviceId = gatewayDeviceId;
		this.geoLocation = geoLocation;
		this.outputSize = 3;
		this.setTransmitDistribution(transmitDistribution);
		setUserId(userId);
		setDestModuleName(destModuleName);
		setTupleType(tupleType);
		setSensorName(sensorName);
		setLatency(latency);
	}
	
	public Sensor(String name, int userId, String appId, int gatewayDeviceId, double latency, GeoLocation geoLocation, 
			Distribution transmitDistribution, String tupleType) {
		super(name);
		this.setAppId(appId);
		this.gatewayDeviceId = gatewayDeviceId;
		this.geoLocation = geoLocation;
		this.outputSize = 3;
		this.setTransmitDistribution(transmitDistribution);
		setUserId(userId);
		setTupleType(tupleType);
		setSensorName(sensorName);
		setLatency(latency);
	}
	
	/**
	 * This constructor is called from the code that generates PhysicalTopology from JSON
	 * @param name
	 * @param tupleType
	 * @param string 
	 * @param userId
	 * @param appId
	 * @param transmitDistribution
	 */
	public Sensor(String name, String tupleType, int userId, String appId, Distribution transmitDistribution, int initialSensorVal) {
		super(name);
		this.setAppId(appId);
		this.setTransmitDistribution(transmitDistribution);
		setTupleType(tupleType);
		setSensorName(tupleType);
		setUserId(userId);
		this.setInitialHeartRateSensorValue(initialSensorVal);
	}
	
	public Sensor(String name, String tupleType, int userId, String appId, Distribution transmitDistribution) {
		super(name);
		this.setAppId(appId);
		this.setTransmitDistribution(transmitDistribution);
		setTupleType(tupleType);
		setSensorName(tupleType);
		setUserId(userId);
	}
	
	//int loop = 0;
	
	public void transmit(){
		AppEdge _edge = null;
		for(AppEdge edge : getApp().getEdges()){
			if(edge.getSource().equals(getTupleType()))
				_edge = edge;
		}
		long cpuLength = (long) _edge.getTupleCpuLength();
		long nwLength = (long) _edge.getTupleNwLength();
		Tuple tuple = new Tuple(getAppId(), FogUtils.generateTupleId(), Tuple.UP, cpuLength, 1, nwLength, outputSize, 
				new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
		tuple.setUserId(getUserId());
		
		//Allow Lambda functions to retrieve sensor identifiers 
		tuple.setTupleType(getTupleType());
		tuple.setSensorSourceName(this.getName());
		
		//Allow each sensor to transmit a value
		int sensorValue = generateRandomSensorData();
		tuple.setTupleValue(sensorValue);	//Manually set so runtime duration can be assessed on ec2 to prevent interference caused by other local running resources
		
		tuple.setDestModuleName(_edge.getDestination());
		tuple.setSrcModuleName(getSensorName());
		Logger.debug(getName(), "Sending tuple with tupleId = "+tuple.getCloudletId());

		tuple.setDestinationDeviceId(getGatewayDeviceId());
		//System.out.println(tuple.getSrcModuleName() + " sending " + getTupleType() + " to " + tuple.getDestModuleName());
		int actualTupleId = updateTimings(getSensorName(), tuple.getDestModuleName());
		tuple.setActualTupleId(actualTupleId);
		
		send(gatewayDeviceId, getLatency(), FogEvents.TUPLE_ARRIVAL,tuple); 
	}
	
	protected int updateTimings(String src, String dest){
		Application application = getApp();
		for(AppLoop loop : application.getLoops()){
			if(loop.hasEdge(src, dest)){
				
				int tupleId = TimeKeeper.getInstance().getUniqueId();
				if(!TimeKeeper.getInstance().getLoopIdToTupleIds().containsKey(loop.getLoopId()))
					TimeKeeper.getInstance().getLoopIdToTupleIds().put(loop.getLoopId(), new ArrayList<Integer>());
				TimeKeeper.getInstance().getLoopIdToTupleIds().get(loop.getLoopId()).add(tupleId);
				TimeKeeper.getInstance().getEmitTimes().put(tupleId, CloudSim.clock());
				return tupleId;
			}
		}
		return -1;
	}
	
	@Override
	public void startEntity() {
		send(gatewayDeviceId, CloudSim.getMinTimeBetweenEvents(), FogEvents.SENSOR_JOINED, geoLocation);
		send(getId(), getTransmitDistribution().getNextValue() + transmissionStartDelay, FogEvents.EMIT_TUPLE);
	}

	@Override
	public void processEvent(SimEvent ev) {
		switch(ev.getTag()){
		case FogEvents.TUPLE_ACK:
			//transmit(transmitDistribution.getNextValue());
			break;
		case FogEvents.EMIT_TUPLE:
			transmit();
			send(getId(), getTransmitDistribution().getNextValue(), FogEvents.EMIT_TUPLE);
			break;
		}
			
	}

	@Override
	public void shutdownEntity() {
		
	}

	public int getGatewayDeviceId() {
		return gatewayDeviceId;
	}

	public void setGatewayDeviceId(int gatewayDeviceId) {
		this.gatewayDeviceId = gatewayDeviceId;
	}

	public GeoLocation getGeoLocation() {
		return geoLocation;
	}

	public void setGeoLocation(GeoLocation geoLocation) {
		this.geoLocation = geoLocation;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public String getTupleType() {
		return tupleType;
	}

	public void setTupleType(String tupleType) {
		this.tupleType = tupleType;
	}

	public String getSensorName() {
		return sensorName;
	}

	public void setSensorName(String sensorName) {
		this.sensorName = sensorName;
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getDestModuleName() {
		return destModuleName;
	}

	public void setDestModuleName(String destModuleName) {
		this.destModuleName = destModuleName;
	}

	public Distribution getTransmitDistribution() {
		return transmitDistribution;
	}

	public void setTransmitDistribution(Distribution transmitDistribution) {
		this.transmitDistribution = transmitDistribution;
	}

	public int getControllerId() {
		return controllerId;
	}

	public void setControllerId(int controllerId) {
		this.controllerId = controllerId;
	}

	public Application getApp() {
		return app;
	}

	public void setApp(Application app) {
		this.app = app;
	}

	public Double getLatency() {
		return latency;
	}

	public void setLatency(Double latency) {
		this.latency = latency;
	}

	protected long getOutputSize(){return this.outputSize;}

	public void setTransmissionStartDelay(int transmissionStartDelay) {
		this.transmissionStartDelay = transmissionStartDelay;
	}

	public int getTransmissionStartDelay() {
		return transmissionStartDelay;
	}
	
	public void setInitialHeartRateSensorValue(int initialValue) {
		this.initialHeartRateSensorValue = initialValue;
	}
	
	public int getInitialHeartRateSensorValue() {
		return this.initialHeartRateSensorValue;
	}
	

	
	//This method will be used for generating random sensor data to be transmit 
	private int generateRandomSensorData() {
		Random random = new Random();
		//generate a random number to represent fluctuations of a patients initial sensor data(Range of 5 above/below intitial sensor data)
		int randomNumber = random.nextInt((this.getInitialHeartRateSensorValue() + 5) + 1 - (this.getInitialHeartRateSensorValue() - 5)) + (this.getInitialHeartRateSensorValue() - 5);
		//Ensure that the random number is >== before transmitting it to avoid sending invalid sensor values
		if(randomNumber < 0) {
			randomNumber = 0;
		}
		return randomNumber;
		}
	}
