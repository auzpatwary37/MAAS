package elasticDemand;

import java.net.URL;
import java.util.Map;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

public class ActivityAdditionConfigGroup extends ReflectiveConfigGroup{
	
	public static final String GROUP_NAME = "ActivityAddition";

	public static final String ACTIVITY_TO_INSERT_INPUT_FILE= "ActivityToInsertFile";
	public static final String UNMODIFIABLE_INPUT_FILE= "UnmodifiableActivityFile";
	public static final String ACTIVITY_AGGREGATION_NETWORK_FILE = "TPUSBnet";
	public static final String DRAW_METHOD_FROM_CHOICE_POOL = "Draw method from choice pool";
	public static final String MarginalUtilityOfAttraction = "marginalUtilityOfAttraction";
	public static final String MarginalUtilityOfDurationSway = "marginalUtilityOfDurationSway";
	public static final String MarginalUtilityOfDistanceAndTime = "marginalUtilityOfDistanceAndTime";
	
	
	private String actToInsertFile = null;
	private String unmodifiableActs = null;
	private String TPUSBnetFile = null;
	private String drawMethodFromChoicePool = DrawFromChoice.logitDraw;
	private double marginalUtilityOfAttraction = 1000;//this will be multiplied to the location probability. For now just given a filler
	private double marginalUtilityOfDurationSway = -.0072;//taken as the square of marginal utility of performing
	private double marginalUtilityOfDistanceAndTime = -0.005-.004;//marginal utility of distance + marginal utility of travel per second/free flow travel time per m
	

	public ActivityAdditionConfigGroup() {
		super(ActivityAdditionConfigGroup.GROUP_NAME);
	}
	
	@Override
	public Map<String,String> getComments() {
		final Map<String,String> comments = super.getComments();

		comments.put( ACTIVITY_TO_INSERT_INPUT_FILE , "Activity to insert file. Should be one column csv file. Each row contains one activity type." );
		comments.put( UNMODIFIABLE_INPUT_FILE , "Unmodifiable activities. Should be one column csv file. Each row contains one activity type." );
		comments.put( ACTIVITY_AGGREGATION_NETWORK_FILE , "The network to be used for location aggregation. Typically the TPUSB network for Hong Kong" );
		comments.put(DRAW_METHOD_FROM_CHOICE_POOL, "Choose From "+DrawFromChoice.randomDraw+", "+DrawFromChoice.bestDraw+" or "+DrawFromChoice.logitDraw+". "+DrawFromChoice.logitDraw+" is chosen by default.");
		comments.put(MarginalUtilityOfDurationSway, "taken as the square of marginal utility of performing");
		comments.put(MarginalUtilityOfDistanceAndTime, "marginal utility of distance + marginal utility of travel per second/free flow travel time per m");
		
		return comments;
	}
	
	/* direct access */

	@StringGetter( ACTIVITY_TO_INSERT_INPUT_FILE )
	public String getActToInsertInputFile() {
		return this.actToInsertFile;
	}
	@StringSetter( ACTIVITY_TO_INSERT_INPUT_FILE)
	public void setActToInsertInputFile(final String inputFile) {
		this.actToInsertFile = inputFile;
	}
	
	

	public String getUnmodifiableActInputFile() {
		return this.unmodifiableActs;
	}
	
	public void setUnmodifiableActInputFile(final String inputFile) {
		this.unmodifiableActs = inputFile;
	}
	
	public String getLocationAggregationNetworkFile() {
		return this.TPUSBnetFile;
	}
	public void setLocationAggregationNetworkFile(final String inputFile) {
		this.TPUSBnetFile = inputFile;
	}
	

	public String getDrawMethodFromChoicePool() {
		return this.drawMethodFromChoicePool;
	}

	public void setDrawMethodFromChoicePool(final String choiceMethod) {
		this.drawMethodFromChoicePool = choiceMethod;
	}

	
	public double getMarginalUtilityOfAttraction() {
		return marginalUtilityOfAttraction;
	}

	public void setMarginalUtilityOfAttraction(double marginalUtilityOfAttraction) {
		this.marginalUtilityOfAttraction = marginalUtilityOfAttraction;
	}

	public double getMarginalUtilityOfDurationSway() {
		return marginalUtilityOfDurationSway;
	}

	public void setMarginalUtilityOfDurationSway(double marginalUtilityOfDurationSway) {
		this.marginalUtilityOfDurationSway = marginalUtilityOfDurationSway;
	}

	public double getMarginalUtilityOfDistanceAndTime() {
		return marginalUtilityOfDistanceAndTime;
	}

	public void setMarginalUtilityOfDistanceAndTime(double marginalUtilityOfDistanceAndTime) {
		this.marginalUtilityOfDistanceAndTime = marginalUtilityOfDistanceAndTime;
	}
	
	
}