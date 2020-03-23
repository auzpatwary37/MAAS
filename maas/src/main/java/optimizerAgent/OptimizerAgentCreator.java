package optimizerAgent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Customizable;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.scenario.CustomizableUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import singlePlanAlgo.MAASPackage;
import singlePlanAlgo.MAASPackages;

/**
 * For now we only try the MAAS optimizer agents
 * I will create somesort of interface later after I get more insight into the whole thing
 * 
 * @author Ashraf
 *
 */
public class OptimizerAgentCreator {
	private Population population;
	private MAASPackages maasPackages;
	private final String agentSurname = "MAASAgent";

	
	public OptimizerAgentCreator(Population population, MAASPackages maasPackages) {
		this.population = population;
		this.maasPackages = maasPackages;
	}
	
	private void createAndAgents() {
		for(Entry<String, Set<MAASPackage>> operator:this.maasPackages.getMassPackagesPerOperator().entrySet()) {
			//create one agent per operator
			PopulationFactory popFac = this.population.getFactory();
			Person person = popFac.createPerson(Id.createPersonId(operator.getKey()+agentSurname));
			Plan plan = popFac.createPlan();
			
			plan.setType("MAASPlan");
			plan.setPerson(person);
			//plan.getAttributes().putAttribute("variableName", value)
			
		}
	}
}

class MAASPlanKeyWords{
	public final String MAASPlanType = "MAASPlan";
	public final String variableNoString = "numberOfVariable";
	
	
}

/**
 * Maybe not needed
 * @author Ashraf
 *
 */
class MAASPlan implements Plan{
	
	private Person person;
	private Map<String, MAASPackages> maasPakages = new HashMap<>();
	private double score;
	private final String planType = "MAASPlan";
	private Customizable customizableDelegate;
	private final Attributes attributes = new Attributes();

	@Override
	public Map<String, Object> getCustomAttributes() {
		//Attributes att = new Attributes();
		if (this.customizableDelegate == null) {
			this.customizableDelegate = CustomizableUtils.createCustomizable();
		}
		return this.customizableDelegate.getCustomAttributes();
	}

	@Override
	public void setScore(Double score) {
		this.score = score;
		
	}

	@Override
	public Double getScore() {
		
		return this.score;
	}

	@Override
	public Attributes getAttributes() {
		return this.attributes;
	}

	@Override
	public List<PlanElement> getPlanElements() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addLeg(Leg leg) {
		// This plan will not have any leg
		
	}

	@Override
	public void addActivity(Activity act) {
		// this plan will not have any activity
		
	}

	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setType(String type) {
		// this method will not do anything 
		
	} 
	
	@Override
	public String toString() {
		String scoreString = "undefined";
		if (this.getScore() != null) {
			scoreString = this.getScore().toString();
		}
		String personIdString = "undefined" ;
		if ( this.getPerson() != null ) {
			personIdString = this.getPerson().getId().toString() ;
		}

		return "[score=" + scoreString + "]" +
//				"[selected=" + PersonUtils.isSelected(this) + "]" +
				"[nof_acts_legs=" + getPlanElements().size() + "]" +
				"[type=" + this.planType + "]" +
				"[personId=" + personIdString + "]" ;
	}
	@Override
	public Person getPerson() {
		return this.getPerson();
	}

	@Override
	public void setPerson(Person person) {
		this.person = person;
		
	}
	
	
	
}
