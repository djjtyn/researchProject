package serverlessStubs;

//This class contains methods to interact with SNS for SNS topic creation, fog device subscribing

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.AmazonSNSException;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SetSubscriptionAttributesRequest;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;


public class SNSTopic {
	
	//Method to instantiate and return client for communicating with AWS SNS
	public static AmazonSNS createSNSClient() {
	    AWSCredentials credentials = new BasicSessionCredentials(EnvVariables.getAWSAccessKey(), EnvVariables.getAWSSecretKey(), EnvVariables.getAWSSessionToken());
		AmazonSNS client = AmazonSNSClient.builder().withRegion(Regions.EU_WEST_1).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
		return client;
	}
	
	//Create an sns topic with the name of argument value
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
		
	//This method returns the ARN value for a topic using topic name
	public static String createOrRetrieveTopicARN(AmazonSNS client, String topicName) {
		CreateTopicResult snsResponse = client.createTopic(topicName);
		return snsResponse.getTopicArn();
	}
		
	//Due to fog devices being simulated there is no physical endpoint set up for subscriptions so this simulation feature will not be available
	public static void subscribeToTopic() {
		AmazonSNS client = createSNSClient();
		SubscribeRequest request = new SubscribeRequest(createOrRetrieveTopicARN(client, "PatientMonitor"), "email", "fogDevice");
		request.setReturnSubscriptionArn(true);
		SubscribeResult response = client.subscribe(request);
		client.setSubscriptionAttributes(setFilterPolicy(response.getSubscriptionArn()));
	}
	
	//This method allows topic subscribers to only receive but since simulated fog devices dont have physical endpoint this simulation feature will not be available
	public static SetSubscriptionAttributesRequest setFilterPolicy(String subArn) {
		SetSubscriptionAttributesRequest request = new SetSubscriptionAttributesRequest();
		request.setSubscriptionArn(subArn);
		request.setAttributeName("AttributeKey");
		request.setAttributeValue("AttributeValue");
		return request;
	}
}
