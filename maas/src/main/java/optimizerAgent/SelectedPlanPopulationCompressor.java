package optimizerAgent;

import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;

public class SelectedPlanPopulationCompressor extends PopulationCompressor{

	public SelectedPlanPopulationCompressor() {
		super(null, 1);
	}
	
	public Map<Id<Person>,Person> compressPopulation(Population population) {

		for(Person p:population.getPersons().values()) {
			Plan pl = p.getSelectedPlan();
			p.getPlans().clear();
			p.addPlan(pl);
			this.getFeasiblePlans().put(p.getId(), (List<Plan>)p.getPlans());
			this.compressedPopulation.put(p.getId(),p);
			this.getEquivalentPerson().put(p.getId(), 1.0);
		}
		return super.compressedPopulation;
	}

}
