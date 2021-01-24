package matsimIntegrate;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.pt.router.CustomDataManager;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import com.google.common.util.concurrent.AtomicDouble;

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
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
import transitCalculatorsWithFare.FareLink;
import transitCalculatorsWithFare.FareTransitRouterConfig;

public class FareDynamicTransitTimeAndDisutilityMaaS extends FareDynamicTransitTimeAndDisutility{

	private final MaaSPackages mps;
	private static AtomicDouble busFareSaved = new AtomicDouble();
	private static AtomicDouble trainFareSaved = new AtomicDouble();
	private static AtomicDouble ferryFareSaved = new AtomicDouble();
	
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
		} else newHelper = fromNodeHelper.clone();

		String attributeName = (String) person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		MaaSPackage mp = mps.getMassPackages().get(attributeName);
		
		// If it is a transfer link, we will calculate the transfer disutility for the transfer.
		if (wrapped.getRoute() == null) {
			if (wrapped.toNode.getRoute() != null) {  // A boarding link
				fare = getBoardingLinkFare(wrapped, isFirstTrip, fromNodeHelper, newHelper, time);
				//TODO: Make the discount based on the fare link.
				if(attributeName!=null && fare < Double.MAX_VALUE) {
					fare = 0; //As we have no information about the minimum fare, the min fare is always 0.
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
		} else { //Travel link
			if(attributeName == null){ // If no package
				fareDiff = getTravelFareDiff(wrapped, newHelper);
			}else { //If with a package
				fareDiff = getTravelFareDiffMAAS(wrapped, newHelper, mp);
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
	
	protected double getTravelFareDiffMAAS(final TransitRouterNetworkLink wrapped, RouteHelper newHelper, 
			MaaSPackage mp) {
		// Travel Link 	(i.e.If it is not a transfer link)
		Id<TransitRoute> routeId = wrapped.route.getId();
		Id<TransitLine> lineId = wrapped.line.getId();
		String transportMode = wrapped.route.getTransportMode();
		
		FareCalculator currFareCal = getFareCalculator(transportMode);

		TransitStop fromStop = wrapped.fromNode.tStop;
		TransitStop toStop = wrapped.toNode.tStop;

		TransitStop startStop = newHelper.entryStop;
		double previousFare = 0;
		double previousFareWithoutPackage = 0;
		String fareLinkType = (transportMode.equals("train") || transportMode.equals("LR"))?FareLink.NetworkWideFare:FareLink.InVehicleFare;
		if(startStop.getFacilityId() != fromStop.getFacilityId() || startStop.getOccurrence() != fromStop.getOccurrence()) {
			FareLink from_fl;
			if(fareLinkType.equals(FareLink.NetworkWideFare)) { //The fare link from start to 'from'
				from_fl = new FareLink(fareLinkType, null, null, startStop.getFacilityId(), 
					fromStop.getFacilityId(), transportMode);
			}else {
				from_fl = new FareLink(fareLinkType, lineId, routeId, 
						startStop.getFacilityId(), fromStop.getFacilityId(), transportMode);
			}
			previousFareWithoutPackage = currFareCal.getFare(routeId, lineId, startStop.getFacilityId(), startStop.getOccurrence(),
					fromStop.getFacilityId(), fromStop.getOccurrence());
			if(startStop.getFacilityId()!=fromStop.getFacilityId())
				previousFare = previousFareWithoutPackage - mp.getDiscountForFareLink(from_fl);
			else
				previousFare = 0; //XXX: It is completely wrong, but should work.
		}
		
		//Obtain the to fare link
		FareLink to_fl;
		if(fareLinkType.equals(FareLink.NetworkWideFare)) {
			to_fl = new FareLink(fareLinkType, null, null, startStop.getFacilityId(), 
					toStop.getFacilityId(), transportMode);
		}else {
			to_fl = new FareLink(fareLinkType,lineId, routeId, 
					startStop.getFacilityId(), toStop.getFacilityId(), transportMode);
		}
		double toFareWithoutPackage = currFareCal.getFare(routeId, lineId, startStop.getFacilityId(), startStop.getOccurrence(),
				toStop.getFacilityId(), toStop.getOccurrence()) ;
		double toFare = toFareWithoutPackage - mp.getDiscountForFareLink(to_fl);
		
		if(toFare < 0) {
			toFare = 0;
		}
		
		double fareDiff = toFare - previousFare;
		double normalFareDiff = toFareWithoutPackage - previousFareWithoutPackage;
		
		if(wrapped.getRoute().getTransportMode().equals("bus")) {
			busFareSaved.getAndAdd(normalFareDiff- fareDiff);
		}else if(wrapped.getRoute().getTransportMode().equals("train")) {
			trainFareSaved.getAndAdd(normalFareDiff- fareDiff);
		}else if(wrapped.getRoute().getTransportMode().equals("ferry")) {
			ferryFareSaved.getAndAdd(normalFareDiff- fareDiff);
		}
		
		if (transportMode.equals(RouteHelper.MTRMode) || transportMode.equals(RouteHelper.LRMode)) {
			// To avoid going backward, for train
			if (newHelper.isStationVisited(toStop.getFacilityId())) {
				return Double.MAX_VALUE;
			} else if (fareDiff < 0) {
				//this could happen in MTR e.g. you can cross harbour more than 1 way 
				return Double.MAX_VALUE;
			}
			if(fareDiff!=0) {
				System.nanoTime();
			}
			
			//Deal with the helper
			newHelper.addStation(fromStop);
			//check if all the discount is realised
			if(transportMode.equals(RouteHelper.LRMode))	//from LR to MTR discount is checked inside the discount calculator JLo
				fareDiff = newHelper.realiseUnrealisedLRFare(fareDiff);
			else
				fareDiff = newHelper.realiseUnrealisedDiscount(fareDiff);
		} else	//check if all the discount is realised
			fareDiff = newHelper.realiseUnrealisedDiscount(fareDiff);
		if(fareDiff<0) {
			System.out.println();
		}
		return fareDiff;
	}
}
