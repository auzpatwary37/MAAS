package optimizerAgent;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.StartupListener;

import com.google.inject.Inject;

public class PlanTranslationControlerListener implements IterationStartsListener, StartupListener{
	
	@Inject 
	private timeBeansWrapper timeBeansWrapped;
	
	@Inject
	private EventsManager manager;
	
	private PlanTranslator planTranslator;
	
	
	@Override
	public void notifyStartup(StartupEvent event) {
		this.planTranslator = new PlanTranslator(timeBeansWrapped.timeBeans, event.getServices().getScenario());
		manager.addHandler(planTranslator);
	}
	
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		this.planTranslator.reset();
	}



	

}
