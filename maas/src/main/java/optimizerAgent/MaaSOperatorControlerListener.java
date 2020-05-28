package optimizerAgent;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.population.PopulationUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import transitCalculatorsWithFare.FareLink;

public class MaaSOperatorControlerListener implements BeforeMobsimListener{

	@Inject 
	private Scenario scenario;

	@Inject
	private @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	
	@Inject
	public MaaSOperatorControlerListener(){
		
	};
	
	
	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {// This class will load the decided price variables in the MaaSPackages.
		scenario.getPopulation().getPersons().values().forEach(p->{
			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {//this is a maas operator
				
				for(Object a:p.getSelectedPlan().getAttributes().getAsMap().values()) {
					if(a instanceof VariableDetails) {
						VariableDetails var = (VariableDetails)a;
						String key = var.getVariableName();
						String packageId = MaaSUtil.retrievePackageId(key);
						String fareLink = null;
						if(MaaSUtil.ifFareLinkVariableDetails(key)) {
							 fareLink = MaaSUtil.retrieveFareLink(key);
							 packages.getMassPackages().get(packageId).setDiscountForFareLink(new FareLink(fareLink), var.getCurrentValue());
						}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(key)) {
							 packages.getMassPackages().get(packageId).setPackageCost(var.getCurrentValue());
						}
					}
				}
			}
		});
	}

}
