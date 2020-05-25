package optimizerAgent;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.Tuple;

public abstract class OptimizingAgent implements Person{
	
	protected Person person;
	protected final String type;
	protected  Activity dummyAct;
	
	/**
	 * The type will be set as the subpopulation name for the agents
	 * @param person
	 * @param type
	 * @param variableIntialValue
	 * @param variableRange
	 */
	public OptimizingAgent(Person person, String type,Activity dummyAct, Map<String, Double> variableIntialValue, Map<String, Tuple<Double,Double>> variableRange) {
		this.person = person;
		this.type = type;	
		this.dummyAct = dummyAct;
		this.variableIntialValue = variableIntialValue;
		this.variableRange = variableRange;
		this.createAttributedPlan();
	}
	
	protected Map<String, Double> variableIntialValue = new HashMap<>();
	protected Map<String, Tuple<Double,Double>> variableRange = new HashMap<>();
	public abstract double getFunctionalScoreApproximation();
	public abstract Map<String,Double> getScoreGradientApproximation();

	public Person getPerson() {
		return person;
	}

	public String getType() {
		return type;
	}

	public Map<String, Double> getVariableIntialValue() {
		return variableIntialValue;
	}

	public Map<String, Tuple<Double, Double>> getVariableRange() {
		return variableRange;
	}
	
	@SuppressWarnings("deprecation")
	public Plan createAttributedPlan() {
		Plan plan = PopulationUtils.createPlan();
		//make attribute per variable
		for(Entry<String, Tuple<Double, Double>> variable:this.variableRange.entrySet()) {
			plan.getAttributes().putAttribute(variable.getKey(),new VariableDetails(variable.getKey(),new Tuple<Double,Double>(variable.getValue().getFirst(),variable.getValue().getSecond()),this.variableIntialValue.get(variable.getKey())));
		}
	
		//Add a dummy activity
		plan.addActivity(dummyAct);
		this.person.addPlan(plan);
		this.person.setSelectedPlan(plan);
		PopulationUtils.putSubpopulation(person, this.type);
		return plan;
	}
	
	
}
