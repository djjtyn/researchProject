package serverlessStubs;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.AmazonSNSException;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.SetSubscriptionAttributesRequest;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;


public class SNSTopic {
	
	//Method to instantiate and return client for communicating with AWS SNS
	public static AmazonSNS createSNSClient() {
	    AWSCredentials credentials = new BasicSessionCredentials(System.getenv("AWS_ACCESS_KEY_ID"), System.getenv("AWS_SECRET_ACCESS_KEY"), System.getenv("SESSION_TOKEN"));
		AmazonSNS client = AmazonSNSClient.builder().withRegion(Regions.EU_WEST_1).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
		return client;
	}
	
	public static void createSNSTopic(String topicName) {
		try {
			System.out.println("Attempting to create SNS topic with name: " + topicName + "...");
			//Get the SNS client and create a topic named as method argument
			AmazonSNS client = createSNSClient();
			client.createTopic(new CreateTopicRequest(topicName));
		} catch(AmazonSNSException e) {
			e.getErrorMessage();
			System.out.println("Issue creating SNS Topic");
		}	
	}
	
	public static SetTopicAttributesRequest setTopicAttributes(String topicArn, String attribute, String value) {
		try {
			SetTopicAttributesRequest request = new SetTopicAttributesRequest();
			request.setTopicArn(topicArn);
			request.setAttributeName(attribute);
			
			return request;
		} catch(Exception e) {
			System.out.println("Failed here");
			e.printStackTrace();
			return null;
		}
	}
	
	
	public static String createOrRetrieveTopicARN(AmazonSNS client, String topicName) {
		CreateTopicResult snsResponse = client.createTopic(topicName);
		return snsResponse.getTopicArn();
	}
	
	
	//This method allows SNS messages to be published with message attributes
	public static void publishMessage() {
		try {
			AmazonSNS client = createSNSClient();
			String topicArn = createOrRetrieveTopicARN(client, "PatientMonitor");
			PublishRequest request = new PublishRequest(topicArn, "Testing notification with seperate functions").withMessageAttributes(createMessageAttribute("Testing"));
			client.publish(request);
			System.out.println("Message Published!!");
		} catch(Exception e) {
			System.out.println("Unable to publish message");
		}
	}
	
	public static Map<String, MessageAttributeValue> createMessageAttribute(String monitorId) {
		Map<String, MessageAttributeValue> attributeMap = new HashMap<>();
		MessageAttributeValue attribute= new MessageAttributeValue();
		attribute.setDataType("String");
		attribute.setStringValue(monitorId);	
		attributeMap.put("Attrib", attribute);
		return attributeMap;
	}
	
	
	public static void subscribeToTopic() {
		AmazonSNS client = createSNSClient();
		SubscribeRequest request = new SubscribeRequest(createOrRetrieveTopicARN(client, "PatientMonitor"), "email", "djjtynan@gmail.com");
		request.setReturnSubscriptionArn(true);
		SubscribeResult response = client.subscribe(request);
		client.setSubscriptionAttributes(setFilterPolicy(response.getSubscriptionArn()));
	}
	
	public static SetSubscriptionAttributesRequest setFilterPolicy(String subArn) {
		System.out.println("SubscriptionARN is" + subArn);
		SetSubscriptionAttributesRequest request = new SetSubscriptionAttributesRequest();
		request.setSubscriptionArn(subArn);
		String attributeName = "Attrib";
		request.setAttributeName(attributeName);
		return request;
	}
}
