package singlePlanAlgo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.Maps;

public class VehicleDistanceTravelled implements TransitDriverStartsEventHandler, LinkEnterEventHandler, LinkLeaveEventHandler,
									VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler{
	
	private final Map<Id<Link>, Double> linkDistanceList;
	private Set<Id<Vehicle>> transitVehicles = new HashSet<>();
	private double travelDist_km = 0;
	private double ptTravelDist_km = 0;
	
	private double travelTime_s = 0;
	private double ptTravelTime_s = 0;
	
	private Map<Id<Vehicle>, Double> vehicleEnterLinkTime = new HashMap<>();
	
	public VehicleDistanceTravelled(Network net) {
		this.linkDistanceList = Maps.transformValues(net.getLinks(), e -> e.getLength());
	}
	
	@Override
	public void reset(int iteration) {
		travelDist_km = 0;
		ptTravelDist_km = 0;
		travelTime_s = 0;
		ptTravelTime_s = 0;
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		// Add the distance travelled for every non-pt travel.
		leaveLink(event.getVehicleId(), event.getLinkId(), event.getTime());
	}
	
	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		leaveLink(event.getVehicleId(), event.getLinkId(), event.getTime());
	}
	
	/**
	 * Add the distnace and time, and clear the vehicle from vehicleEnterTime
	 * @param vehicleId
	 * @param linkId
	 * @param currTime
	 */
	private void leaveLink(Id<Vehicle> vehicleId, Id<Link> linkId, double currTime) {
		if(!transitVehicles.contains(vehicleId)) {
			travelDist_km += (linkDistanceList.get(linkId) / 1000);
			travelTime_s += (currTime - vehicleEnterLinkTime.get(vehicleId));
		}else {
			ptTravelDist_km += (linkDistanceList.get(linkId) / 1000);
			ptTravelTime_s += (currTime - vehicleEnterLinkTime.get(vehicleId));
		}
		vehicleEnterLinkTime.remove(vehicleId);
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		vehicleEnterLinkTime.put(event.getVehicleId(), event.getTime());
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		vehicleEnterLinkTime.put(event.getVehicleId(), event.getTime());
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		// Add the transit vehicle sets
		transitVehicles.add(event.getVehicleId());
	}
	
	public double getTravelDist() {
		return this.travelDist_km;
	}
	
	public double getPTTravelDist() {
		return this.ptTravelDist_km;
	}
	
	public double getTravelTime() {
		return this.travelTime_s;
	}
	
	public double getPTTravelTime() {
		return this.ptTravelTime_s;
	}

}
