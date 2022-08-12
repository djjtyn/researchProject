package serverlessStubs;

//This class is used to retrieve durations for Lambda functions between simulation start and end

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;



public class Cloudwatch {
	
 	//Wont need Main method
//	public static void main(String [] args ) {
//		double totalDuration = getDuration();	
//		System.out.println("Lambda Total Duration: " + totalDuration);
//	} 
	
	public static double getDuration(Date startDateTime) {
		AmazonCloudWatch cw = createClient();	//Returns Cloudwatch client
		//Create request to aggregate all lambda functions invoked from simulation start time
		GetMetricStatisticsRequest request = createStatisticsRequest(startDateTime);
		// Sum all lambda function duration values and return the result
		double totalLambdaDuration = sumLambdaDurations(cw.getMetricStatistics(request));
		return totalLambdaDuration;
	}
		
	//Method to create a Cloudwatch client
	public static AmazonCloudWatch createClient() {
		AWSCredentials credentials = new BasicSessionCredentials(EnvVariables.getAWSAccessKey(), EnvVariables.getAWSSecretKey(), EnvVariables.getAWSSessionToken());
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.standard()
			    .withRegion(Regions.EU_WEST_1)
				.withCredentials(new AWSStaticCredentialsProvider(credentials))
				.build();
		return cw;		
	}
	
	//Method to create metric statistics request to sum all lambda durations
	public static GetMetricStatisticsRequest createStatisticsRequest(Date startDateTime) {
		GetMetricStatisticsRequest request = new GetMetricStatisticsRequest();
		 request.setStartTime(startDateTime);
		 request.setNamespace("AWS/Lambda");
		 request.setMetricName("Duration");
		 request.setDimensions(createDimensions());	//Returns dimension collection with lambda identifier
		 request.setStatistics(createStatisticQuery());	//Returns statistics query to sum all durations
		 request.setPeriod(60);
		 request.setEndTime(getCurrentDateTime()); 
		 return request;
	}
	
	//Method to allow the correct lambda to be set for receiving metrics
	public static Collection<Dimension> createDimensions(){
		Collection<Dimension> dimensions = new ArrayList();
		Dimension dimension = new Dimension();
		dimension.setName("FunctionName");
		dimension.setValue("TransmitTupleData");
		dimensions.add(dimension);
		return dimensions;
	}
	
	//Method which provides Summing ability for a metric
	public static Collection<String> createStatisticQuery() {
		Collection<String> stats = new ArrayList();
		stats.add("Sum");
		return stats;
	}
	
	//Method to allow dates to be set for CLoudwatch metrics to be collected
	public static Date getCurrentDateTime() {
		Calendar calendar = Calendar.getInstance();
		return calendar.getTime();
	}
	
	//Method to sum each lambda duration with each other
	public static double sumLambdaDurations(GetMetricStatisticsResult result) {
		double totalDuration = 0;
		//Traverse each lambda function and sum each duration
		 for(Datapoint dp: result.getDatapoints()) {
			 totalDuration+=dp.getSum();
		 }
		 return totalDuration;
	 }
}

