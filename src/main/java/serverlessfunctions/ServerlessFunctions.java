package serverlessfunctions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.auth.AWSCredentials;

import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;

public class ServerlessFunctions implements RequestHandler<Object, String> {
	
	
	public String handleRequest(Object input, Context context) {
		context.getLogger().log("Input: " + input);
		String output = "Hello, " + input + "!";
		return output;
	}

	
}
