package optimizerAgent;

import java.util.Map;

public class packUsageStat{
	public final Map<String,Double> packagesSold;//packages Sold 
	public final Map<String,Double> selfPackageTrip;// package trips owned by the fareLink operators. 
	public final Map<String,Double> packageTrip;//package trips owned by the package operator
	public final Map<String,Double> totalTrip;
	public final Map<String,Double> revenue;
	public packUsageStat(Map<String, Double> totalTrip, Map<String, Double> selfPackageTrip, Map<String, Double> revenue, Map<String, Double> packagesSold, Map<String, Double> packageTrip) {
		this.packagesSold = packagesSold;
		this.selfPackageTrip = selfPackageTrip;
		this.packageTrip = packageTrip;
		this.totalTrip = totalTrip;
		this.revenue = revenue;
		
	}
}
