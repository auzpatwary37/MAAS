package optimizerAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitTransferLink;

public class CNLTransitRouteMaaS extends CNLTransitRoute{
	
	
	
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
		for(FareLink s:super.FareEntryAndExit) {
			if(s.getType().equals(FareLink.NetworkWideFare)) {
				this.routeFare+=farecalc.get(s.getMode()).getFares(null, null, s.getBoardingStopFacility(), s.getAlightingStopFacility()).get(0);
			}else {
				this.routeFare+=farecalc.get(s.getMode()).getFares(s.getTransitRoute(), s.getTransitLine(), s.getBoardingStopFacility(), s.getAlightingStopFacility()).get(0);
			}
			//TODO: finish implementing MaaS embedded fare. 
		}
		return this.routeFare;
		
	}
	
	
}
