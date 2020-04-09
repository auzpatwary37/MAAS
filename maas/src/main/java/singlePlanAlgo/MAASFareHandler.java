package singlePlanAlgo;

import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Population;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import dynamicTransitRouter.fareCalculators.FareCalculator;

//TODO Talk to enoch and incorporate personId here. 

public class MAASFareHandler implements FareCalculator{
	
	private MAASPackages maasPackages;
	private Population population;
	private FareCalculator fareCalculator;
	
	
	
	
	
	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		
		double fare = this.fareCalculator.getMinFare(routeId, lineId, fromStopId,  toStopId);
		return fare;
	}
	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId) {
		double fare = this.fareCalculator.getMinFare(routeId, lineId, fromStopId);
		return fare;
	}
	@Override
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence) {
		// TODO Auto-generated method stub
		return 0;
	}
	public void setFareFactor(double fareFactor) {
		// TODO Auto-generated method stub
		
	} 
	
	
	

}
