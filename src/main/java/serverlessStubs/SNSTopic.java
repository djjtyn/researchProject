package serverlessStubs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;


public class SNSTopic {
	
	static boolean clientSet = false;
	static AmazonSNSClient snsClient;
	
	//Method to instantiate a client for communicating with AWS Lambda
	public static void createSNSClient() {
		System.out.println("Creating client...");
		// Credentials required to connect to AWS account
	    AWSCredentials credentials = new BasicSessionCredentials(System.getenv("AWS_ACCESS_KEY_ID"), System.getenv("AWS_SECRET_ACCESS_KEY"), System.getenv("SESSION_TOKEN"));
	    snsClient = (AmazonSNSClient) AmazonSNSClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
	    clientSet = true;
	}
	
	public static void createTopic(String topicName) {
		//Check if this class already has a sns client
		if(!clientSet) {
			createSNSClient();
		} 
		try {
			snsClient.createTopic(new CreateTopicRequest(topicName));
		} catch(Exception e) {
			e.printStackTrace();
			System.out.println("issue creating SNS Topic");
		}	
	}
}
