package matsimIntegrate;

import java.util.Map;

import javax.inject.Inject;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;

import dynamicTransitRouter.FareDynamicTransitTimeAndDisutility;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.TransitRouterNetworkHR;
import dynamicTransitRouter.costs.MultiNodeAStarEucliean;
import dynamicTransitRouter.costs.StopStopTime;
import dynamicTransitRouter.costs.VehicleOccupancy;
import dynamicTransitRouter.costs.WaitingTime;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import running.RunUtils;
import transitCalculatorsWithFare.FareTransitRouterConfig;

public class TransitRouterFareDynamicMaasImpl extends TransitRouterFareDynamicImpl{

	@Inject
	public TransitRouterFareDynamicMaasImpl(final Scenario scenario, final WaitingTime waitTime, 
			final StopStopTime stopStopTime, final VehicleOccupancy vehicleOccupancy, 
			final Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc){
		super(scenario);
		TransitRouterNetworkTravelTimeAndDisutility ttCalculator = new FareDynamicTransitTimeAndDisutilityMaaS((FareTransitRouterConfig) tConfig, 
				waitTime, stopStopTime, vehicleOccupancy, fareCals, tdc,
				new PreparedTransitSchedule(scenario.getTransitSchedule()));
		setFareCalculatorAndDijkstra(ttCalculator);
	}

}
