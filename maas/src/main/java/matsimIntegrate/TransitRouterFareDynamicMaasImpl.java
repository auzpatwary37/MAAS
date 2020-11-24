package matsimIntegrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import maasPackagesV2.MaaSPackages;
import dynamicTransitRouter.FareDynamicTransitTimeAndDisutility;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.TransitRouterNetworkHR;
import dynamicTransitRouter.costs.MultiNodeAStarEucliean;
import dynamicTransitRouter.costs.StopStopTime;
import dynamicTransitRouter.costs.VehicleOccupancy;
import dynamicTransitRouter.costs.WaitingTime;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import optimizerAgent.MaaSUtil;
import running.RunUtils;
import transitCalculatorsWithFare.FareLink;
import transitCalculatorsWithFare.FareTransitRouterConfig;

public class TransitRouterFareDynamicMaasImpl extends TransitRouterFareDynamicImpl{

	@Inject
	public TransitRouterFareDynamicMaasImpl(final Scenario scenario, final WaitingTime waitTime, 
			final StopStopTime stopStopTime, final VehicleOccupancy vehicleOccupancy, 
			final Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc, 
			@Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages mps){
		super(scenario);
		TransitRouterNetworkTravelTimeAndDisutility ttCalculator = new FareDynamicTransitTimeAndDisutilityMaaS((FareTransitRouterConfig) tConfig, 
				waitTime, stopStopTime, vehicleOccupancy, fareCals, tdc,
				new PreparedTransitSchedule(scenario.getTransitSchedule()), mps);
		setFareCalculatorAndDijkstra(ttCalculator);
	}
	
	@Override
	public List<Leg> convertPathToLegList( double departureTime, Path p, Coord fromCoord, Coord toCoord, Person person) {
		List<Leg> legs = new ArrayList<Leg>();
		Leg leg;
		double walkDistance, walkWaitTime, travelTime = 0;
		Route walkRoute;
		Coord coord = fromCoord;
		TransitRouteStop stop = null;
		double time = departureTime;
		boolean lastLegIsInSystem = false;
		Id<TransitStopFacility> enterSystemStopFacility = null;
		
		for (Link link : p.links) {
			TransitRouterNetworkHR.TransitRouterNetworkLink l = (TransitRouterNetworkHR.TransitRouterNetworkLink) link;
			if(l.route!=null) {
				//in line link
				double ttime = ttCalculator.getLinkTravelTime(l, time, person, null);
				travelTime += ttime;
				time += ttime;
			}
			else if(l.fromNode.route!=null) {
				//inside link (egress link)
				leg = PopulationUtils.createLeg(TransportMode.pt);
				@SuppressWarnings("deprecation")
				ExperimentalTransitRoute ptRoute = new ExperimentalTransitRoute(stop.getStopFacility(), l.fromNode.line, l.fromNode.route, l.fromNode.stop.getStopFacility());
				leg.setRoute(ptRoute);
				leg.setTravelTime(travelTime);
				legs.add(leg);
				travelTime = 0;
				stop = l.fromNode.stop;
				coord = l.fromNode.stop.getStopFacility().getCoord();
				
				//Add a FareLink
				String mode = l.fromNode.route.getTransportMode();
				String fareLinkType = (mode.equals("train") || mode.equals("LR"))?FareLink.NetworkWideFare:FareLink.InVehicleFare;
				
				List<FareLink> fareLinkList = (List<FareLink>) person.getSelectedPlan().getAttributes().getAttribute(FareLink.FareLinkAttributeName);  //TODO: It may have some information on the old one, need to fix
				if( fareLinkList == null) {
					fareLinkList = new ArrayList<FareLink>();
					person.getSelectedPlan().getAttributes().putAttribute(FareLink.FareLinkAttributeName, fareLinkList);
				}
				FareLink f = null;
				if(fareLinkType.equals(FareLink.NetworkWideFare)) {
					if(lastLegIsInSystem) { //Replace the old link by new fare link with new stop facility
						//if(fareLinkList.size() > 0) {
							FareLink oldF = fareLinkList.remove(fareLinkList.size()-1);
							f = new FareLink(fareLinkType, null, null, oldF.getBoardingStopFacility(), 
									l.fromNode.stop.getStopFacility().getId(), mode);
//						}else {
//							f = new FareLink(fareLinkType, null, null, enterSystemStopFacility, 
//									l.fromNode.stop.getStopFacility().getId(), mode);							
//						}
					}else {
						f = new FareLink(fareLinkType, null, null, enterSystemStopFacility, 
							l.fromNode.stop.getStopFacility().getId(), mode);
					}
				}else {
					f = new FareLink(fareLinkType,l.fromNode.line.getId(), l.fromNode.route.getId(), enterSystemStopFacility, 
							l.fromNode.stop.getStopFacility().getId(), mode);
				}
				fareLinkList.add(f);
//				if(fareLinkList.size()>1) {
//					System.out.println("Debug");
//				}
				if(fareLinkType.equals(FareLink.NetworkWideFare)) {
					lastLegIsInSystem = true;
				}
				
			}
			else if(l.toNode.route!=null) {
				//wait link (boarding link)
				leg = PopulationUtils.createLeg(TransportMode.transit_walk);
				walkDistance = CoordUtils.calcEuclideanDistance(coord, l.toNode.stop.getStopFacility().getCoord()); 
				walkWaitTime = walkDistance/tConfig.getBeelineWalkSpeed()/*+ttCalculator.getLinkTravelTime(l, time+walkDistance/this.config.getBeelineWalkSpeed(), person, null)*/;
				walkRoute = RouteUtils.createGenericRouteImpl(stop==null?null:stop.getStopFacility().getLinkId(), l.toNode.stop.getStopFacility().getLinkId());
				walkRoute.setDistance(walkDistance);
				leg.setRoute(walkRoute);
				leg.setTravelTime(walkWaitTime);
				legs.add(leg);
				stop = l.toNode.stop;
				time += walkWaitTime;
				enterSystemStopFacility = stop.getStopFacility().getId();
			}
			
		}
		Object o = person.getSelectedPlan().getAttributes().getAttribute("fareLink");
		leg = PopulationUtils.createLeg(TransportMode.transit_walk);
		walkDistance = CoordUtils.calcEuclideanDistance(coord, toCoord); 
		walkWaitTime = walkDistance/tConfig.getBeelineWalkSpeed();
		walkRoute = RouteUtils.createGenericRouteImpl(stop==null?null:stop.getStopFacility().getLinkId(), null);
		walkRoute.setDistance(walkDistance);
		leg.setRoute(walkRoute);
		leg.setTravelTime(walkWaitTime);
		legs.add(leg);
		return legs;
	}


}
