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
	
	public static void transmitTupleData(String sensorIdentifier, String sensorType, int sensorValue, String snsTopicName) {
		try {
			//Create and retrieve a hashmap containing sensor info
			HashMap<String, Object> sensorInfo = createMapping(sensorIdentifier, sensorType, sensorValue, snsTopicName);
			// Credentials required to connect to AWS account
		    AWSCredentials credentials = new BasicSessionCredentials(System.getenv("AWS_ACCESS_KEY_ID"), System.getenv("AWS_SECRET_ACCESS_KEY"), System.getenv("SESSION_TOKEN"));
			//	Instantiate a client allowing access to AWS Lambda
			AWSLambda client = createLambdaClient(credentials);
			//Pass in credentials so SNS client can use same credentials as Lambda client
			InvokeRequest request = createLambdaRequest(sensorInfo);	
			client.invoke(request);
		} catch(Exception e) {
			e.printStackTrace();
		}

	}
	
	//Method to instantiate a client for communicating with AWS Lambda
	private static AWSLambda createLambdaClient(AWSCredentials credentials) {
		AWSLambda client = AWSLambdaClientBuilder.standard()
				.withRegion(Regions.EU_WEST_1)
				.withCredentials(new AWSStaticCredentialsProvider(credentials))
				.build();
		return client;
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