package singlePlanAlgo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

public class MAASPackages {
	
	private Map<String,MAASPackage> massPackages=new HashMap<>();
	private Map<String,Set<MAASPackage>> massPackagesPerOperator=new HashMap<>();
	public final String maasPriceVariableName = "MAAS_Price";
	public final String maasTaxiTripVariableName = "MAAS_Taxi";
	
	
	public Map<String,List<MAASPackages>> packages= new HashMap<>();
	
	
	public MAASPackages() {
		
	}
	
	public MAASPackages(TransitSchedule ts,boolean freePerMode, double defaultCost, int freeTaxiTrip) {
		Map<String, MAASPackage> packages = new HashMap<>();
		for(Entry<Id<TransitLine>, TransitLine> d:ts.getTransitLines().entrySet()) {
			String mode = d.getValue().getRoutes().get(new ArrayList<>(d.getValue().getRoutes().keySet()).get(0)).getTransportMode();
			if(packages.containsKey(mode)) {
				
			}else {
				packages.put(mode, new MAASPackage(mode,"1"));
				packages.get(mode).setMaxTaxiTrip(freeTaxiTrip);
				packages.get(mode).setPackageCost(defaultCost);
				packages.get(mode).setPackageExpairyTime(24*3600.);
			}
		}
		this.massPackages=packages;
	}
	
	public MAASPackages(Map<String, MAASPackage> packages) {
		this();
		for(MAASPackage maas:packages.values()) {
			this.addMAASPacakge(maas);
		}		
	}
	
	public void addMAASPacakge(MAASPackage maas) {
		this.massPackages.put(maas.getId(), maas);
		if(this.massPackagesPerOperator.containsKey(maas.getOperatorId())) {
			this.massPackagesPerOperator.get(maas.getOperatorId()).add(maas);
		}else {
			this.massPackagesPerOperator.put(maas.getOperatorId(), new HashSet<>());
			this.massPackagesPerOperator.get(maas.getOperatorId()).add(maas);
		}
	}

	public Map<String, MAASPackage> getMassPackages() {
		return massPackages;
	}

	public Map<String, Set<MAASPackage>> getMassPackagesPerOperator() {
		return massPackagesPerOperator;
	}
	
	
	
	
	
}
