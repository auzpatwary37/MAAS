package optimizerAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import maasPackagesV2.MaaSPackage;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitTransferLink;

public class CNLTransitRouteMaaS extends CNLTransitRoute{
	
	public boolean debugSwitch = true;
	
	public CNLTransitRouteMaaS(ArrayList<CNLTransitTransferLink> transferLinks, ArrayList<CNLTransitDirectLink> dlinks,
			Scenario scenario, TransitSchedule ts, double routeWalkingDistance, String routeId) {
		super(transferLinks, dlinks, scenario, ts, routeWalkingDistance, routeId);
	}

	public CNLTransitRouteMaaS(CNLTransitRoute tr, Scenario scenario, TransitSchedule ts) {
		super((List<CNLTransitTransferLink>)(List<?>)tr.getTransitTransferLinks(), (List<CNLTransitDirectLink>)(List<?>)tr.getTransitDirectLinks(), 
				scenario , ts, tr.getRouteWalkingDistance(), tr.getTrRouteId().toString());
	}
	
	/**
	 * Not complete yet. Use with caution
	 */
	@Override
	public double getFare(TransitSchedule ts, Map<String, FareCalculator> farecalc, Map<String, Object>additionalDataContainer) {
		this.routeFare=0;
		for(FareLink s:super.FareEntryAndExit) {
			if(s.getType().equals(FareLink.NetworkWideFare)) {
				this.routeFare+=farecalc.get(s.getMode()).getFares(null, null, s.getBoardingStopFacility(), s.getAlightingStopFacility()).get(0);
			}else {
				this.routeFare+=farecalc.get(s.getMode()).getFares(s.getTransitRoute(), s.getTransitLine(), s.getBoardingStopFacility(), s.getAlightingStopFacility()).get(0);
			}
			MaaSPackage maas = (MaaSPackage)additionalDataContainer.get(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			//double discount = maas.getDiscounts().get(s.toString());
			if(maas!=null && maas.getDiscounts().get(s.toString())!=null)this.routeFare-=maas.getDiscounts().get(s.toString());
		}
		//System.out.println();
		if(Double.isNaN(routeFare)) {
			System.out.println("Debug");
		}
		return this.routeFare;
		
	}
	
	
}
