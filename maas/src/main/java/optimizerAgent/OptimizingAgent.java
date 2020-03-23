package optimizerAgent;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.Tuple;

public abstract class OptimizingAgent implements Person{
	
	protected Person person;
	protected final String type;
	
	public OptimizingAgent(Person person, String type, Map<String, Double> variableIntialValue, Map<String, Tuple<Double,Double>> variableRange) {
		this.person = person;
		this.type = type;	
		this.variableIntialValue = variableIntialValue;
		this.variableRange = variableRange;
		
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
	
	public Plan createAttributedPlan() {
		Plan plan = PopulationUtils.createPlan();
		//make attribute per variable
		for(Entry<String, Tuple<Double, Double>> variable:this.variableRange.entrySet()) {
			plan.getAttributes().putAttribute(variable.getKey(),new VariableDetails(variable.getKey(),new Tuple<Double,Double>(variable.getValue().getFirst(),variable.getValue().getSecond()),this.variableIntialValue.get(variable.getKey())));
		}
		this.person.addPlan(plan);
		this.person.setSelectedPlan(plan);
		return plan;
	}
	
	
}
