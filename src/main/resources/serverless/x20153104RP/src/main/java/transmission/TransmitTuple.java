package transmission;

import java.util.HashMap;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;

public class TransmitTuple {

	public void transmitTupleData(HashMap<String, Object> map) {
		try {
			publishToSNSTopic(map);
		} catch (Exception e) {
			System.out.println("Issue with transmitting tuple data");
			e.printStackTrace();
		}
	}
//		String patientIdentifier = (String) map.get("SensorIdentifier");
//		patientIdentifier = patientIdentifier.split("\\:")[0];
//		try {
//			// Get SNS Client
//			PublishRequest request = new PublishRequest(patientIdentifier, "Heartrate:" + map.get("SensorValue"));
//			snsClient.publish(request);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	// }

	public AmazonSNS createSnsClient() {
		//AWSCredentials credentials = new BasicSessionCredentials(System.getenv("accessKey"), System.getenv("secreAccessKey"), System.getenv("sessionToken"));
		AmazonSNS client = AmazonSNSClient.builder().withRegion(Regions.EU_WEST_1).build();
		return client;
	}

	public String createSNSTopic(AmazonSNS client, String topicName) {
		try {
			CreateTopicRequest request = new CreateTopicRequest(topicName);
			CreateTopicResult response = client.createTopic(request);
			return response.getTopicArn();
		} catch(Exception e) {
			e.printStackTrace();
			return "Issue creating topic";
		}
	}

	public void publishToSNSTopic(HashMap<String, Object> map) {
		//Get a SNS client
		AmazonSNS client = createSnsClient();
		//Get the topic arn
		String topicArn = createOrRetrieveTopicARN(client, (String)map.get("snsTopicName"));
		PublishRequest request = new PublishRequest(topicArn, "Testing notification");
		client.publish(request);
	}
	
	public String createOrRetrieveTopicARN(AmazonSNS client, String topicName) {
		CreateTopicResult snsResponse = client.createTopic(topicName);
		return snsResponse.getTopicArn();
	}
}
