package matsimIntegrate;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.pt.router.CustomDataManager;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import com.google.common.util.concurrent.AtomicDouble;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import dynamicTransitRouter.FareDynamicTransitTimeAndDisutility;
import dynamicTransitRouter.RouteHelper;
import dynamicTransitRouter.TransitStop;
import dynamicTransitRouter.TransitRouterNetworkHR.TransitRouterNetworkLink;
import dynamicTransitRouter.costs.StopStopTime;
import dynamicTransitRouter.costs.VehicleOccupancy;
import dynamicTransitRouter.costs.WaitingTime;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareTransitRouterConfig;

public class FareDynamicTransitTimeAndDisutilityMaaS extends FareDynamicTransitTimeAndDisutility{

	private final MaaSPackages mps;
	//private static AtomicDouble busFareSaved = new AtomicDouble();
	//private static AtomicDouble trainFareSaved = new AtomicDouble();
	//private static AtomicDouble ferryFareSaved = new AtomicDouble();
	
	public FareDynamicTransitTimeAndDisutilityMaaS(FareTransitRouterConfig config, WaitingTime waitTime,
			StopStopTime stopStopTime, VehicleOccupancy vehicleOccupancy, Map<String, FareCalculator> fareCalculators,
			TransferDiscountCalculator tdc, PreparedTransitSchedule preparedTransitSchedule, MaaSPackages mps) {
		super(config, waitTime, stopStopTime, vehicleOccupancy, fareCalculators, tdc, preparedTransitSchedule);
		this.mps = mps;
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * The part of the disutility of the fare is calculated here. Time is not passed as the fare is now
	 * assumed to be constant.
	 * 
	 * @param wrapped The transfer link concerned.
	 * @param person 
	 * @param vehicle
	 * @param dataManager
	 * @return
	 */
	@Override
	protected double getLinkTravelFareDisutility(final TransitRouterNetworkLink wrapped, final Person person,
			double time, final CustomDataManager dataManager) {
		double fare = 0.0;
		double fareDiff = 0.0; // The fare between alight in previous node with in current node
		
		//As all of these will be putting in a new route helper anyways, rewriting to flow better JLo
		RouteHelper fromNodeHelper = (RouteHelper) dataManager.getFromNodeCustomData();
		RouteHelper newHelper = null;
		boolean isFirstTrip = false;
		if(fromNodeHelper==null) {
			isFirstTrip = true; //If it is the first node, it is a first trip.
		} else
			newHelper = fromNodeHelper.clone();

		String attributeName = (String) person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		MaaSPackage mp = mps.getMassPackages().get(attributeName);
		
		// If it is a transfer link, we will calculate the transfer disutility for the transfer.
		if (wrapped.getRoute() == null) {
			if (wrapped.toNode.getRoute() != null) {  // A boarding link
				fare = getBoardingLinkFare(wrapped, isFirstTrip, fromNodeHelper, newHelper, time);
				//TODO: Add the discount based on the fare link.
				if(fare<Double.MAX_VALUE && attributeName!= null && attributeName.equals("bus") && wrapped.toNode.getRoute().getTransportMode().equals("bus")) {
					//busFareSaved.getAndAdd(fare);
					fare = 0;
				}else if(fare<Double.MAX_VALUE && attributeName!= null && attributeName.equals("train") && wrapped.toNode.getRoute().getTransportMode().equals("train")) {
					//trainFareSaved.getAndAdd(fare);
					fare = 0;
				}else if(fare<Double.MAX_VALUE && attributeName!= null && attributeName.equals("ferry") && wrapped.toNode.getRoute().getTransportMode().equals("ferry")) {
					//ferryFareSaved.getAndAdd(fare);
					fare = 0;
				}
				
				if(isFirstTrip) {
					Id<TransitLine> toLineId = wrapped.toNode.line.getId();
					TransitStop fromStop = wrapped.toNode.tStop;
					String toTransportMode = wrapped.toNode.getRoute().getTransportMode();
					newHelper = new RouteHelper(toLineId, fromStop, toTransportMode, time);	
				}
			
			} else if (wrapped.fromNode.getRoute() != null) {  // Egress link
				TransitRoute route = wrapped.fromNode.getRoute();
				newHelper.egress(route.getTransportMode(), wrapped.fromNode.getLine().getId(), route.getId(), time);
				
			} else {  // Transfer link, so just pass the data
				if(isFirstTrip) //To disallow transfers at start node(s)
					return Double.MAX_VALUE;
			}
		} else {
			fareDiff = getTravelFareDiff(wrapped, newHelper);
			if(fareDiff<Double.MAX_VALUE && attributeName!= null && attributeName.equals("bus") && wrapped.getRoute().getTransportMode().equals("bus")) {
				//busFareSaved.getAndAdd(fareDiff);
				fareDiff = 0;
			}else if(fareDiff<Double.MAX_VALUE && attributeName!= null && attributeName.equals("train") && wrapped.getRoute().getTransportMode().equals("train")) {
				//trainFareSaved.getAndAdd(fareDiff);
				fareDiff = 0;
			}else if(fareDiff<Double.MAX_VALUE && attributeName!= null && attributeName.equals("ferry") && wrapped.getRoute().getTransportMode().equals("ferry")) {
				//ferryFareSaved.getAndAdd(fareDiff);
				fareDiff = 0;
			}
		}
		if (fare < 0) {
			throw new RuntimeException("The fare is negative, not valid!");	
		} else if (fareDiff < 0) {
			throw new RuntimeException("The fare difference is negative for longer travel, not valid!");
		}
		//store the helper to toNode and return value
		newHelper.addChargedFare(fare+fareDiff);
		dataManager.setToNodeCustomData(newHelper);
		return (fare + fareDiff) * config.getMarginalUtilityOfMoney(); // The return value is definitely negative
	}
//	
//	public static double getBusFareSaved() {
//		return busFareSaved.doubleValue();
//	}
//
//	public static double getTrainFareSaved() {
//		return trainFareSaved.doubleValue();
//	}
//
//	public static double getFerryFareSaved() {
//		return ferryFareSaved.doubleValue();
//	}
//
//	/**
//	 * This function resets the fare saved, would be called before iteration.
//	 */
//	public static void resetFareSaved() {
//		busFareSaved = new AtomicDouble();
//		trainFareSaved = new AtomicDouble();
//		ferryFareSaved = new AtomicDouble();
//	}

}
