package serverlessStubs;


//This class will allow environment variables for AWS access to be set using cmd
public class EnvVariables {
	public static String AWS_ACCESS_KEY_ID;
	public static String AWS_SECRET_ACCESS_KEY;
	public static String SESSION_TOKEN;
	
	public static void setEnvironmentVariables(String access_key, String secret_access_key, String session_token) {
		AWS_ACCESS_KEY_ID = access_key;
		AWS_SECRET_ACCESS_KEY = secret_access_key;
		SESSION_TOKEN = session_token;
	}
	
	public static String getAWSAccessKey() {
		return AWS_ACCESS_KEY_ID;
	}
	
	public static String getAWSSecretKey() {
		return AWS_SECRET_ACCESS_KEY;
	}
	
	public static String getAWSSessionToken() {
		return SESSION_TOKEN;
	}
	
	
	
	

}
