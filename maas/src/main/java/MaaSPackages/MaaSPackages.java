package MaaSPackages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.FareLink;

public class MaaSPackages{
	
	private Map<String,MaaSPackage> massPackages=new HashMap<>();
	private Map<String,Set<MaaSPackage>> massPackagesPerOperator=new HashMap<>();
	public final String maasPriceVariableName = "MAAS_Price";
	public final String maasTaxiTripVariableName = "MAAS_Taxi";
	private Map<String,String> fareLinkToOperatorMap = new HashMap<>();//This Assumes fare links are unique to operators, this might not be true
	//TODO: fix this issue.
	
	/**
	 * Creating empty packages
	 */
	public MaaSPackages() {
		
	}
	/**
	 * This is an easy method to create MAAS packages per mode in transit
	 * @param ts transitSchedule
	 * @param freePerMode if the mode is discounted then false if fully free then true
	 * @param defaultCost Price per packages
	 * @param freeTaxiTrip How many taxi trip to give away
	 */
	public MaaSPackages(TransitSchedule ts,boolean freePerMode, double defaultCost, int freeTaxiTrip, Map<String,FareCalculator> fareCalculators,double defaultDiscount, boolean fullDiscounted) {
		Map<String, MaaSPackage> packages = new HashMap<>();
		int operatorId = 1;
		packages.put("train", new MaaSPackage("train", Integer.toString(operatorId), defaultCost, freeTaxiTrip));
		packages.get("train").addTransitMode(ts, "train", fareCalculators, defaultDiscount, fullDiscounted);
		for(Entry<Id<TransitLine>, TransitLine> d:ts.getTransitLines().entrySet()) {
			String mode = d.getValue().getRoutes().get(new ArrayList<>(d.getValue().getRoutes().keySet()).get(0)).getTransportMode();
			if(!mode.equals("train")) {
			if(!packages.containsKey(mode)){
				operatorId++;
				packages.put(mode, new MaaSPackage(mode, Integer.toString(operatorId), defaultCost, freeTaxiTrip));
				packages.get(mode).setPackageExpairyTime(24*3600.);
				
			}
			packages.get(mode).addTransitLine(d.getValue(), fareCalculators, defaultDiscount, fullDiscounted);
			}
		}
		//this.massPackages=packages;
		packages.values().stream().forEach((maas)->this.addMaaSPacakge(maas));
	}
	
	public MaaSPackages(Map<String, MaaSPackage> packages) {
		this();
		for(MaaSPackage maas:packages.values()) {
			this.addMaaSPacakge(maas);
		}		
	}
	
	public void addMaaSPacakge(MaaSPackage maas) {
		this.massPackages.put(maas.getId(), maas);
		if(this.massPackagesPerOperator.containsKey(maas.getOperatorId())) {
			this.massPackagesPerOperator.get(maas.getOperatorId()).add(maas);
		}else {
			this.massPackagesPerOperator.put(maas.getOperatorId(), new HashSet<>());
			this.massPackagesPerOperator.get(maas.getOperatorId()).add(maas);
		}
		this.fareLinkToOperatorMap.putAll(maas.getFareLinks().keySet().stream().collect(Collectors.toMap(a->a, a->maas.getOperatorId())));
	}

	public Map<String, MaaSPackage> getMassPackages() {
		return massPackages;
	}

	public Map<String, Set<MaaSPackage>> getMassPackagesPerOperator() {
		return massPackagesPerOperator;
	}
	
	
	public String getOperatorId(FareLink fl) {
		return this.fareLinkToOperatorMap.get(fl.toString());
	}
	
	public void removeMaaSPackage(MaaSPackage pac) {
		this.massPackages.remove(pac);
		this.massPackagesPerOperator.get(pac.getOperatorId()).remove(pac);
		if(this.massPackagesPerOperator.get(pac.getOperatorId()).isEmpty())this.massPackagesPerOperator.remove(pac.getOperatorId());
	}
	
	
}
