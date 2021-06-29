package optimizerAgent;

import java.util.Map;

public class packUsageStat{
	public Map<String,Double> additionalGovtGrad = null;//This is to enforce negative revenue
	public final Map<String,Double> packagesSold;//packages Sold 
	public final Map<String,Double> selfPackageTrip;// package trips owned by the fareLink operators. 
	public final Map<String,Double> packageTrip;//package trips owned by the package operator
	public Double totalSystemTravelTime;
	public Double totalSystemUtility;
	public final Map<String,Double> totalTrip;
	public final Map<String,Double> revenue;
	private Map<String,Map<String,Double>> grad;
	private Map<String,Double> objective;
	public Double GovtSepRevenue;
	public packUsageStat(Map<String, Double> totalTrip, Map<String, Double> selfPackageTrip, Map<String, Double> revenue, Map<String, Double> packagesSold, Map<String, Double> packageTrip) {
		this.packagesSold = packagesSold;
		this.selfPackageTrip = selfPackageTrip;
		this.packageTrip = packageTrip;
		this.totalTrip = totalTrip;
		this.revenue = revenue;
		
	}
	public Map<String, Map<String, Double>> getGrad() {
		return grad;
	}
	public void setGrad(Map<String, Map<String, Double>> grad) {
		this.grad = grad;
	}
	public Map<String, Double> getObjective() {
		return objective;
	}
	public void setObjective(Map<String, Double> objective) {
		this.objective = objective;
	}
	
}
