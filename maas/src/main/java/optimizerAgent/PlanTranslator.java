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
 */
public class PlanTranslator implements PersonEntersVehicleEventHandler{
	
	private Set<Id<Person>> personHandled = Collections.synchronizedSet(new HashSet<Id<Person>>());
	private Map<String,Tuple<Double,Double>> timeBean;
	
	@Inject
	private Scenario scenario;
	
	@Inject
	public PlanTranslator(Map<String,Tuple<Double,Double>> timeBean) {
		this.timeBean=timeBean;
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		Id<Person> id = event.getPersonId();
		if(!personHandled.contains(id)){
			personHandled.add(id);
			Plan plan = scenario.getPopulation().getPersons().get(id).getSelectedPlan();
			if(plan.getAttributes().getAsMap().get(SimpleTranslatedPlan.SimplePlanAttributeName)==null) {//never before seen plan
				SimpleTranslatedPlan delegate = new SimpleTranslatedPlan(timeBean, plan, scenario);//translate the plan
				plan.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, delegate);//put the translation inside the plan's attribute
			}
		}
		
	}

	

}
