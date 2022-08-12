//This class is used to allow AWS Lambda functions to be invoked

package serverlessStubs;

import java.util.HashMap;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.google.gson.Gson;

public class LambdaInvoke {
	
	//Variable to allow Lambda client to only require one instantitation
	private static AWSLambda client;

	
	//Setter & Getter for client
	public static void setClient(AWSLambda clientSetter) {
		client = clientSetter;
	}
	
	public static AWSLambda getClient() {
		//If the client has already been set, retrieve it
		if (client != null) {
			return client;
		} else {
			//If the client hasn't yet been set, instantiate and set
			AWSLambda client = createLambdaClient();
			setClient(client);
			return client;
		}
		
	}
	
	public static void transmitTupleData(String sensorIdentifier, String sensorType, int sensorValue, String snsTopicName) {
		try {
			//Instantiate a Lambda client by either setting or getting the global scoped client
			AWSLambda client = getClient();
			//Create and retrieve a hashmap containing sensor info
			HashMap<String, Object> sensorInfo = createMapping(sensorIdentifier, sensorType, sensorValue, snsTopicName);
			InvokeRequest request = createLambdaRequest(sensorInfo);	
			client.invoke(request);
		} catch(Exception e) {
			e.printStackTrace();
		}

	}
	
	//Method to instantiate a client for communicating with AWS Lambda
	private static AWSLambda createLambdaClient() {
		try {
			AWSCredentials credentials = new BasicSessionCredentials(EnvVariables.getAWSAccessKey(), EnvVariables.getAWSSecretKey(), EnvVariables.getAWSSessionToken());
			AWSLambda client = AWSLambdaClientBuilder.standard()
					.withRegion(Regions.EU_WEST_1)
					.withCredentials(new AWSStaticCredentialsProvider(credentials))
					.build();
			return client;
		}catch(Exception e) {
			System.out.println("Error creating Lambda Client");
			return null;
		}
	}
	
	//Mehod to instantiate a payload to send to a AWS Lambda function
	private static InvokeRequest createLambdaRequest(HashMap<String, Object> sensorInfo) {
		// Credentials required to connect to AWS account
		InvokeRequest request = new InvokeRequest()
				.withFunctionName("TransmitTupleData")
				.withPayload(new Gson().toJson(sensorInfo))
				.withInvocationType(InvocationType.Event);
		return request;
	}
		
	public static HashMap<String, Object> createMapping(String sensorIdentifier , String sensorType, int sensorValue, String snsTopicName) {
		HashMap<String, Object> mapping = new HashMap<>();
		mapping.put("SensorIdentifier", sensorIdentifier);
		mapping.put("SensorType", sensorType);
		mapping.put("SensorValue", sensorValue);
		mapping.put("snsTopicName", snsTopicName);
		return mapping;
	}
}
