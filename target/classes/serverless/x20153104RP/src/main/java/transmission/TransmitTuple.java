package transmission;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.MessageAttributeValue;

public class TransmitTuple {

	public void transmitTupleData(HashMap<String, Object> map) {
		try {
			publishToSNSTopic(map);
		} catch (Exception e) {
			System.out.println("Issue with transmitting tuple data");
			e.printStackTrace();
		}
	}

	public AmazonSNS createSnsClient() {
		AmazonSNS client = AmazonSNSClient.builder().withRegion(Regions.EU_WEST_1).build();
		return client;
	}

	
	//Need to include message attributes
	public void publishToSNSTopic(HashMap<String, Object> map) {
		//Get a SNS client
		AmazonSNS client = createSnsClient();
		//Get the topic arn using the topic name which the orchestrator module has a record of
		String topicArn = createOrRetrieveTopicARN(client, (String)map.get("snsTopicName"));
		//Create a String value from sensor value
		String sensorValue = (String)map.get("SensorValue");
		//Publish a message with the sensor value and identifiers for emitting sensor and sensor type
		PublishRequest request = new PublishRequest(topicArn,sensorValue).withMessageAttributes(createMessageAttribute(map));
		client.publish(request);
	}
	
	public String createOrRetrieveTopicARN(AmazonSNS client, String topicName) {
		CreateTopicResult snsResponse = client.createTopic(topicName);
		return snsResponse.getTopicArn();
	}
	
	//This method allows attribute key value pairs to be published for each message 
	public static Map<String, MessageAttributeValue> createMessageAttribute(HashMap<String, Object> map) {
		Map<String, MessageAttributeValue> attributeMap = new HashMap<>();
		//Create key/pair values for the sensorIdentifier and sensor type
		attributeMap.put("SensorIdentifier", createMessageAttribute(map.get("SensorIdentifier")));
		attributeMap.put("SensorType", createMessageAttribute(map.get("SensorType")));
		return attributeMap;
	}
	
	//This method will allow message attributes to be created
	public static MessageAttributeValue createMessageAttribute(Object attributeType) {
		String attributeTypeString = (String)attributeType;
		MessageAttributeValue attribute= new MessageAttributeValue();
		attribute.setDataType("String");
		attribute.setStringValue(attributeTypeString);
		return attribute;
	}
}
