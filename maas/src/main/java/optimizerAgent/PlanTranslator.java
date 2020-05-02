package optimizerAgent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;
import com.google.inject.Inject;
/**
 * Bind this class as event handler in the module
 * @author Ashraf
 *
 *This is currently a very poor implementation (using existing code)
 *Write some handler to create a plan rather than using the existing code to translate. (April 2020)
 */
public class PlanTranslator implements PersonEntersVehicleEventHandler{
	
	private Set<Id<Person>> personHandled = Collections.synchronizedSet(new HashSet<Id<Person>>());
	private Map<String,Tuple<Double,Double>> timeBean;
	
	
	private Scenario scenario;
	
	
	public PlanTranslator(Map<String,Tuple<Double,Double>> timeBean, Scenario scenario) {
		this.timeBean=timeBean;
		this.scenario = scenario;
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {// tap into the plan
		
		Id<Person> id = event.getPersonId();
		if(!personHandled.contains(id)){
			
			Person person;
			if((person = scenario.getPopulation().getPersons().get(id))!=null) {
			personHandled.add(id);
			Plan plan = scenario.getPopulation().getPersons().get(id).getSelectedPlan();
			
			if(plan.getAttributes().getAsMap().get(SimpleTranslatedPlan.SimplePlanAttributeName)==null) {//never before seen plan
				SimpleTranslatedPlan simpleplan = new SimpleTranslatedPlan(timeBean, plan, scenario);//translate the plan
				plan.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, simpleplan);//put the translation inside the plan's attribute
				
				
			}
			}
		}
		
	}
	
	public void reset() {
		this.personHandled.clear();
	}
	

}
