package singlePlanAlgo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;


/**
 * 
 * @author Ashraf
 *TODO:Should add specific time per package as well
 *
 */
public class MAASPackage {
	
	private final String operatorId;
	private String id;
	private List<Id<TransitLine>> transitLines = new ArrayList<>();
	private Map<Id<TransitLine>,Double> discounts = new HashMap<>();
	private int maxTaxiTrip;
	private double packageCost;
	private double packageExpairyTime;
	
	public MAASPackage(String Id,String operatorId,List<Id<TransitLine>> TransitLines,int maxTaxiTrip,double packageCost, double packageExpTime) {
		this.id=Id;
		this.transitLines = TransitLines;
		this.maxTaxiTrip=maxTaxiTrip;
		this.packageCost=packageCost;
		this.packageExpairyTime=packageExpTime;
		this.operatorId=operatorId;
	}
	
	public MAASPackage(String Id,String operatorId) {
		this.id = Id;
		this.operatorId=operatorId;
	}

	public void addTransitLine(Id<TransitLine> lineId, boolean totalDiscounted, double discount) {
		if(totalDiscounted) {
			this.transitLines.add(lineId);
		}else {
			this.discounts.put(lineId, discount);
		}
	}
	
//	public void addTransitLine() {
//		
//	}
	
	public void addALLTransitLine(TransitSchedule ts, double defaultDiscount ) {
		this.transitLines.addAll(ts.getTransitLines().keySet());
		for(Id<TransitLine> tl:ts.getTransitLines().keySet()) {
			this.discounts.put(tl, defaultDiscount);
		}
	}
	
	public List<Id<TransitLine>> getTransitLines() {
		return transitLines;
	}

	public void setTransitLines(List<Id<TransitLine>> transitLines) {
		this.transitLines = transitLines;
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

	public Map<Id<TransitLine>, Double> getDiscounts() {
		return discounts;
	}

	public void setDiscounts(Map<Id<TransitLine>, Double> discounts) {
		this.discounts = discounts;
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
	
}
