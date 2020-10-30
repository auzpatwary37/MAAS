package MaaSPackages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.FareLink;





/**
 * 
 *@author Ashraf
 *TODO:Should add specific time per package as well
 *Should the maas package be fare link specific? It makes more sense.
 *Fare links can have Id we can distribute the fares accordingly 
 *it would be too much work to add this in the current format. Maybe we will try this in another Class? Ashraf, May 2020
 *
 */
public class MaaSPackage {
	/**
	 * This is the operator id that this maas packages belongs to.
	 */
	private final String operatorId;
	/**
	 * This is the maas package ID.
	 */
	private String id;
	/**
	 * The list of fare links that this maas package contains or controls
	 */
	private Map<String,FareLink> fareLinks = new HashMap<>();
	/**
	 * Discounts for each of these fare links
	 */
	private Map<String,Double> discounts = new HashMap<>();//farelink key->discount
	
	/**
	 * FullFare for each of these fare links
	 */
	private Map<String,Double> fullFare = new HashMap<>();//farelink key->fare
	/**
	 * The maximum number of free taxi trip. Maybe later add discount to this as well. 
	 */
	private int maxTaxiTrip;
	/**
	 * The cost or price  of the package 
	 */
	private double packageCost;
	/**
	 * This is how after many days the package has to be renewed. Maybe used later
	 */
	private double packageExpairyTime;
	
	/**
	 * This is to keep track of the multi operator maas package. 
	 * This variable keeps the fare links that belong to the operator.
	 */
	private Set<String> selfFareLinks = new HashSet<>();
	
	private double reimbursementRatio = 1;
	
	public MaaSPackage(String Id,String operatorId,Map<String,FareLink> fareLinks,  Map<String,Double> discountedFare, Map<String,Double> fullFare, int maxTaxiTrip, double packageCost, double packageExpTime) {
		this.id=Id;
		this.fareLinks = fareLinks;
		this.discounts = discountedFare;
		this.fullFare = fullFare;
		this.maxTaxiTrip=maxTaxiTrip;
		this.packageCost=packageCost;
		this.packageExpairyTime=packageExpTime;
		this.operatorId=operatorId;
	}
	
	public MaaSPackage(String Id,String operatorId, double packageCost,int maxTaxiTrip) {
		this.id = Id;
		this.operatorId=operatorId;
		this.packageCost = packageCost;
		this.maxTaxiTrip = maxTaxiTrip;
	}

	/**
	 * this function by default assumes the provided fare link belong to the operator. To add a 
	 * fare link that does not belong to the operator, add a boolean flag in the argument
	 * @param fareLink
	 * @param discount
	 * @param fullFare
	 */
	public void addFareLink(FareLink fareLink, double discount, double fullFare) {
		this.fareLinks.put(fareLink.toString(), fareLink);
		this.discounts.put(fareLink.toString(), discount);
		this.fullFare.put(fareLink.toString(), fullFare);
		this.selfFareLinks.add(fareLink.toString());
	}
	
	/**
	 * 
	 * @param fareLink
	 * @param discount
	 * @param fullFare
	 * @param selfFareLink
	 */
	public void addFareLink(FareLink fareLink, double discount, double fullFare, boolean selfFareLink) {
		this.fareLinks.put(fareLink.toString(), fareLink);
		this.discounts.put(fareLink.toString(), discount);
		this.fullFare.put(fareLink.toString(), fullFare);
		this.selfFareLinks.add(fareLink.toString());
		if(selfFareLink)this.selfFareLinks.add(fareLink.toString());
	}
	
	/**
	 * Convenient method to add a farelink belonging to all routes in a transit line 
	 * In vehicle fare payment scheme is assumed.
	 * @param tl
	 * @param fareCalculators
	 * @param defaultDiscount
	 * @param fullDiscounted
	 */
	public void addTransitLine(TransitLine tl, Map<String, FareCalculator> fareCalculators, double defaultDiscount, boolean fullDiscounted) {
		for(TransitRoute tr:tl.getRoutes().values()) {
			List<TransitStopFacility> stops = new ArrayList<>();
			tr.getStops().stream().forEach((stop)->stops.add(stop.getStopFacility()));
			for(int i=0;i<stops.size()-1;i++) {
				for(int j=i+1;j<stops.size();j++) {
					if(stops.get(i).getId().equals(stops.get(j).getId())) continue;//Do not want to go through cyclic routes' same stop od pairs 
					FareLink fl = new FareLink(FareLink.InVehicleFare,tl.getId(),tr.getId(),stops.get(i).getId(),stops.get(j).getId(),tr.getTransportMode());
					double fullFare = fareCalculators.get(tr.getTransportMode()).getFares(tr.getId(), tl.getId(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					double discount = defaultDiscount;
					if(fullDiscounted) discount = fullFare;
					this.addFareLink(fl, discount, fullFare);
				}
			}
		}
	}
	
	/**
	 * Convenient method to add a farelink belonging to all routes in a transit line 
	 * In vehicle fare payment scheme is assumed. Added a boolean flag for indicating if the line belong to the operator
	 * @param tl
	 * @param fareCalculators
	 * @param defaultDiscount
	 * @param fullDiscounted
	 */
	public void addTransitLine(TransitLine tl, Map<String, FareCalculator> fareCalculators, double defaultDiscount, boolean fullDiscounted, boolean selfFareLinks) {
		for(TransitRoute tr:tl.getRoutes().values()) {
			List<TransitStopFacility> stops = new ArrayList<>();
			tr.getStops().stream().forEach((stop)->stops.add(stop.getStopFacility()));
			for(int i=0;i<stops.size()-1;i++) {
				for(int j=i+1;j<stops.size();j++) {
					if(stops.get(i).getId().equals(stops.get(j).getId())) continue;//Do not want to go through cyclic routes' same stop od pairs 
					FareLink fl = new FareLink(FareLink.InVehicleFare,tl.getId(),tr.getId(),stops.get(i).getId(),stops.get(j).getId(),tr.getTransportMode());
					double fullFare = fareCalculators.get(tr.getTransportMode()).getFares(tr.getId(), tl.getId(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					double discount = defaultDiscount;
					if(fullDiscounted) discount = fullFare;
					this.addFareLink(fl, discount, fullFare, selfFareLinks);
				}
			}
		}
	}
	/**
	 * Convenient method to add all farelinks belonging to a transit mode (tr.getmode)
	 * Network wide fare payment is assumed
	 * @param ts
	 * @param mode
	 * @param fareCalculators
	 * @param defaultDiscount
	 * @param fullDiscounted
	 */
	public void addTransitMode(TransitSchedule ts, String mode, Map<String, FareCalculator> fareCalculators, double defaultDiscount, boolean fullDiscounted) {
		List<TransitStopFacility> stops = new ArrayList<>();
		for(TransitLine tl:ts.getTransitLines().values()) {
			for(TransitRoute tr:tl.getRoutes().values()) {
				if(tr.getTransportMode().equals(mode)) {
				tr.getStops().stream().forEach((stop)->{
					if(!stops.contains(stop.getStopFacility())) {
						stops.add(stop.getStopFacility());
						}
					});
				}
			}
		}
		for(int i=0;i<stops.size();i++) {
			for(int j=0;j<stops.size();j++) {
				if(i!=j) {
					FareLink fl = new FareLink(FareLink.NetworkWideFare,null,null,stops.get(i).getId(),stops.get(j).getId(),mode);
					double fullFare = fareCalculators.get(mode).getFares(null, null, fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					double discount = defaultDiscount;
					if(fullDiscounted) discount = fullFare;
					this.addFareLink(fl, discount, fullFare);
				}
			}
		}
	}
	
	
	/**
	 * Convenient method to add all farelinks belonging to a transit mode (tr.getmode)
	 * Network wide fare payment is assumed
	 * This variant adds a boolean flag to indicate if the fare links belong to the operator. 
	 * @param ts
	 * @param mode
	 * @param fareCalculators
	 * @param defaultDiscount
	 * @param fullDiscounted
	 */
	public void addTransitMode(TransitSchedule ts, String mode, Map<String, FareCalculator> fareCalculators, double defaultDiscount, boolean fullDiscounted, boolean selfFareLinks) {
		List<TransitStopFacility> stops = new ArrayList<>();
		for(TransitLine tl:ts.getTransitLines().values()) {
			for(TransitRoute tr:tl.getRoutes().values()) {
				if(tr.getTransportMode().equals(mode)) {
				tr.getStops().stream().forEach((stop)->{
					if(!stops.contains(stop.getStopFacility())) {
						stops.add(stop.getStopFacility());
						}
					});
				}
			}
		}
		for(int i=0;i<stops.size();i++) {
			for(int j=0;j<stops.size();j++) {
				if(i!=j) {
					FareLink fl = new FareLink(FareLink.NetworkWideFare,null,null,stops.get(i).getId(),stops.get(j).getId(),mode);
					double fullFare = fareCalculators.get(mode).getFares(null, null, fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
					double discount = defaultDiscount;
					if(fullDiscounted) discount = fullFare;
					this.addFareLink(fl, discount, fullFare, selfFareLinks);
				}
			}
		}
	}
	/**
	 * Clones the existing MaaSPackage with a new ID
	 * @param id the new ID
	 * @return
	 */
	public MaaSPackage clone(String id) {
		 MaaSPackage m = new MaaSPackage(id,this.operatorId,this.packageCost,this.maxTaxiTrip);
		 m.setFareLinks(new HashMap<>(this.fareLinks));
		 m.setDiscounts(new HashMap<>(this.discounts));
		 m.setFullFare(new HashMap<>(this.fullFare));
		 m.setSelfFareLinks(new HashSet<>(this.selfFareLinks));
		 return m;
	}
	

	public double getDiscountForFareLink(FareLink fl) {
		Double discount = 0.;
		if((discount = this.getDiscounts().get(fl.toString()))==null) {
			discount = 0.;
		}
		return discount;
	}
	
	
	//--------------------------GetterSetter-----------------------------------------
	

	
	
	public Map<String, FareLink> getFareLinks() {
		return fareLinks;
	}

	public Set<String> getSelfFareLinks() {
		return selfFareLinks;
	}

	public void setSelfFareLinks(Set<String> selfFareLinks) {
		this.selfFareLinks = selfFareLinks;
	}

	public void setFareLinks(Map<String, FareLink> fareLinks) {
		this.fareLinks = fareLinks;
	}

	public Map<String, Double> getDiscounts() {
		return discounts;
	}

	public void setDiscounts(Map<String, Double> discounts) {
		this.discounts = discounts;
	}
	
	public void setDiscountForFareLink(FareLink fareLink, double discount) {
		if(!this.discounts.containsKey(fareLink.toString()))throw new IllegalArgumentException("The fare link "+fareLink.toString()+" does not belong to this package!!!");
		this.discounts.put(fareLink.toString(), discount);
	}
	

	public Map<String, Double> getFullFare() {
		return fullFare;
	}

	public void setFullFare(Map<String, Double> fullFare) {
		this.fullFare = fullFare;
	}

	public void setId(String id) {
		this.id = id;
	}

	public int getMaxTaxiTrip() {
		return maxTaxiTrip;
	}

	public void setMaxTaxiTrip(int maxTaxiTrip) {
		this.maxTaxiTrip = maxTaxiTrip;
	}

	public String getId() {
		return id;
	}

	

	public double getPackageCost() {
		return packageCost;
	}

	public void setPackageCost(double packageCost) {
		this.packageCost = packageCost;
	}

	public double getPackageExpairyTime() {
		return packageExpairyTime;
	}

	public void setPackageExpairyTime(double packageExpairyTime) {
		this.packageExpairyTime = packageExpairyTime;
	}

	public String getOperatorId() {
		return operatorId;
	}

	public double getReimbursementRatio() {
		return reimbursementRatio;
	}

	public void setReimbursementRatio(double reimbursementRatio) {
		this.reimbursementRatio = reimbursementRatio;
	}
	
	
}
