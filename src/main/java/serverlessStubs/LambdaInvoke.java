//This class is used to allow the simulation orchestrator components to invoke severless functions
package serverlessStubs;

import java.nio.charset.Charset;

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
	
	
	// Credentials required to connect to AWS account
    final static AWSCredentials credentials = new BasicSessionCredentials(System.getenv("AWS_ACCESS_KEY_ID"), System.getenv("AWS_SECRET_ACCESS_KEY"), System.getenv("SESSION_TOKEN"));
	public static void invokeLambda(String functionName, String sensorValue) {
		System.out.println(functionName + " is trying to execute.");
		
		//Choose which function to invoke and what data to pass as its parameter argument
		InvokeRequest request = new InvokeRequest()
				.withFunctionName(functionName)
				.withPayload(new Gson().toJson(sensorValue))
				.withInvocationType(InvocationType.RequestResponse);
		

		AWSLambda client = AWSLambdaClientBuilder.standard()
				.withRegion(Regions.EU_WEST_1)
				.withCredentials(new AWSStaticCredentialsProvider(credentials))
				.build();
		
		//Invoke the aws lambda function 
		InvokeResult result = client.invoke(request);
		
		String resultStr = new String(result.getPayload().array(), Charset.forName("UTF-8"));
		System.out.println("Result is: " + resultStr);
		
	}
}
