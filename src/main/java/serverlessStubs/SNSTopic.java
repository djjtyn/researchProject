package serverlessStubs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;


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
			System.out.println("SNS topic created");
		} catch(Exception e) {
			e.printStackTrace();
			System.out.println("Issue creating SNS Topic");
		}	
	}
}
