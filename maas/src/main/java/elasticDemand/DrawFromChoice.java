package elasticDemand;

import java.util.Map;

import org.matsim.api.core.v01.Id;

public interface DrawFromChoice<T> {

	public static String randomDraw = "randomDraw";
	public static String bestDraw = "bestDraw";
	public static String logitDraw = "logitDraw";
	
	
	public Id<T> draw(Map<Id<T>,Double> Score);
	

}
