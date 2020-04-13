package optimizerAgent;

import java.util.Map;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import singlePlanAlgo.MAASPackages;

/**
 * This class will hold the essential information required for retrieving the fare information of a fare payment 
 * 
 * for train i.e. MTR this will include only the mode boarding stop and alighting stop
 * 
 * for bus and other transit modes, this class will include the transit line route boarding and alighting stop and 
 * 
 * @author ashraf
 *
 */

//TODO: add any other parameter that is necessary for fare extraction

public class FareLink {
	
	//Any transit mode with network wide payment
	public static String Train = "Train";
	
	//Any transit mode with vehicel wide payment 
	public static String otherMode = "Other";
	
	
	public static String seperator = "_";
	
	private final String mode;
	private Id<TransitLine> transitLine = null;
	private Id<TransitRoute> tranitRoute = null;
	private final Id<TransitStopFacility> boardingStopFacility;
	private final Id<TransitStopFacility> alightingStopFacility;
	
	
	public FareLink(String mode,Id<TransitLine> transitLine, Id<TransitRoute> transitRoute, Id<TransitStopFacility> boardingStopFacility, Id<TransitStopFacility> alightingStopFacility) {
		this.mode = mode;
		this.transitLine = transitLine;
		this.tranitRoute = transitRoute;
		this.alightingStopFacility = alightingStopFacility;
		this.boardingStopFacility = boardingStopFacility;
		
		if(this.mode.equals(FareLink.otherMode) && (this.transitLine==null || this.tranitRoute==null)) {
			throw new IllegalArgumentException("Transit line id or transit route id cannot be null for transit a vehicle specific transit mode!!!");
		}
		
	}


	public Id<TransitLine> getTransitLine() {
		return transitLine;
	}


	public void setTransitLine(Id<TransitLine> transitLine) {
		this.transitLine = transitLine;
	}


	public Id<TransitRoute> getTranitRoute() {
		return tranitRoute;
	}


	public void setTranitRoute(Id<TransitRoute> tranitRoute) {
		this.tranitRoute = tranitRoute;
	}


	public String getMode() {
		return mode;
	}


	public Id<TransitStopFacility> getBoardingStopFacility() {
		return boardingStopFacility;
	}


	public Id<TransitStopFacility> getAlightingStopFacility() {
		return alightingStopFacility;
	}

	@Override
	public String toString() {
		return mode+seperator+this.transitLine+seperator+this.tranitRoute+seperator+this.boardingStopFacility+seperator+this.alightingStopFacility;
	}
	
	
	// Enoch please implement this function
	public double getFare(Map<String,FareCalculator> fareCalcultors, MAASPackages packages) {
		return 0;
	}
}
