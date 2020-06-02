package optimizerAgent;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.population.PopulationUtils;

import com.google.inject.Inject;

public class PlanTranslationControlerListener implements IterationStartsListener, StartupListener, BeforeMobsimListener{
	
	@Inject 
	private timeBeansWrapper timeBeansWrapped;

	
	@Inject
	private Scenario scenario;
	
	
	@Inject
	PlanTranslationControlerListener(){
		
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {

	}
	
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		for(Person p:scenario.getPopulation().getPersons().values()) {
			if(!PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				if(p.getSelectedPlan().getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName)==null) {
					Plan plan = p.getSelectedPlan();
					plan.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, new SimpleTranslatedPlan(timeBeansWrapped.timeBeans, plan, scenario));
				}
			}
		}
	}



	

}
