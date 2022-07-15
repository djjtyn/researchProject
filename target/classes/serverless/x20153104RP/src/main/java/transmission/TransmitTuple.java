package transmission;

import java.util.HashMap;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;

public class TransmitTuple {
	public void transmitTupleData(HashMap<String, Object> map) {
		String patientIdentifier = (String) map.get("SensorIdentifier");
		patientIdentifier = patientIdentifier.split("\\:")[0];
		try {
			// Get SNS Client
			AmazonSNS snsClient = createSnsClient();
			PublishRequest request = new PublishRequest(patientIdentifier, "Heartrate:" + map.get("SensorValue"));
			snsClient.publish(request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public AmazonSNS createSnsClient() {
		AWSCredentials credentials = new BasicSessionCredentials(System.getenv("SNS_AWS_ACCESS_KEY_ID"), System.getenv("SNS_AWS_SECRET_ACCESS_KEY"), System.getenv("SNS_SESSION_TOKEN"));
		AmazonSNS client = AmazonSNSClient.builder().withRegion(Regions.EU_WEST_1)
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
		return client;

	}
}
