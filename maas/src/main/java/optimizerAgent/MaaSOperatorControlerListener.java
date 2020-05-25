package optimizerAgent;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.population.PopulationUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;

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
				String operatorId = p.getId().toString().replace(MaaSUtil.MaaSOperatorSubscript, "");
				for(MaaSPackage m:packages.getMassPackagesPerOperator().get(operatorId)) {
					double cost =  ((VariableDetails)p.getAttributes().getAttribute(m.getId()+MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript)).getCurrentValue();
					m.setPackageCost(cost);//We only load the cost for now
				}
			}
		});
	}

}
