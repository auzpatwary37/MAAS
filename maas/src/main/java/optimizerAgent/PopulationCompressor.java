package optimizerAgent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class PopulationCompressor {
	
	private Map<Id<Person>,Double> equivalentPerson = new HashMap<>();
	private final Network odNetwork;//make this final
	private Set<String>uniquePersonId = new HashSet<>();
	private Map<Id<Person>,List<Plan>> feasiblePlans = new HashMap<>();
	private BiMap<String,Id<Person>> personId = HashBiMap.create();
	private final int maxPlanSize;
	private Map<Id<Person>,Person> compressedPopulation = new HashMap<>();
	
	public PopulationCompressor(Network odNetwork, int maxPlanSize) {
		this.odNetwork = odNetwork;
		this.maxPlanSize = maxPlanSize;
	}
	
	private String createUniquePersonId(Person person) {
		String id = PopulationUtils.getSubpopulation(person);
		for(PlanElement pe:person.getSelectedPlan().getPlanElements()){
			if(pe instanceof Activity) {
				id=id+NetworkUtils.getNearestNode(this.odNetwork,((Activity)pe).getCoord()).getId().toString()+"___";
			}
		}
		return id;
	}
	
	
	public Map<Id<Person>,Person> compressPopulation(Population population) {
		for(Person p:population.getPersons().values()) {
			String pid = null;
			if(!this.uniquePersonId.contains(pid = this.createUniquePersonId(p))){//new person
				this.uniquePersonId.add(pid);
				this.feasiblePlans.put(p.getId(),(List<Plan>)p.getAttributes().getAttribute(MaaSUtil.uniqueMaaSIncludedPlanAttributeName));
				equivalentPerson.put(p.getId(), 1.);
				this.personId.put(pid, p.getId());
				this.compressedPopulation.put(p.getId(), p);
			}else {//person already exists
				Id<Person> eqPId = this.personId.get(pid); 
				this.equivalentPerson.compute(eqPId, (k,v)->v=v+1);
				List<Plan> plans = (List<Plan>)p.getAttributes().getAttribute(MaaSUtil.uniqueMaaSIncludedPlanAttributeName);
				List<Plan>oldPlans = this.feasiblePlans.get(eqPId);
				for(Plan newpl:plans) {
					boolean unique = true;
					for(Plan pl: oldPlans) {
						if(MaaSUtil.planEquals(pl, newpl)) {
							unique = false;
							break;
						}
					}
					if(unique) {
						oldPlans.add(newpl);
					}
				}
				if(oldPlans.size()>this.maxPlanSize) {
					MaaSUtil.sortPlan(oldPlans);
					oldPlans.removeAll(oldPlans.subList(maxPlanSize, oldPlans.size()));
				}
			}
		}
		System.out.println("Finished population compression.");
		return this.compressedPopulation;
	}

	public Map<Id<Person>, Double> getEquivalentPerson() {
		return equivalentPerson;
	}

	public Network getOdNetwork() {
		return odNetwork;
	}

	public Map<Id<Person>, List<Plan>> getFeasiblePlans() {
		return feasiblePlans;
	}

	public int getMaxPlanSize() {
		return maxPlanSize;
	}

	public Map<Id<Person>, Person> getCompressedPopulation() {
		return compressedPopulation;
	}

	public void reset() {
		this.equivalentPerson = new HashMap<>();
		this.uniquePersonId = new HashSet<>();
		this.feasiblePlans = new HashMap<>();
		this.personId = HashBiMap.create();
		this.compressedPopulation = new HashMap<>();
	}
	
}
