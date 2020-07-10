package optimizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.population.Plan;

import optimizerAgent.VariableDetails;

public class RandomOptimizer implements Optimizer{
	
	private final String id;
	private Map<String,VariableDetails> variables = new HashMap<>();;
	private double distanceScale = 1;
	private double counter = 0;

	public RandomOptimizer(String id, Map<String,VariableDetails> variables) {
		this.variables = variables;
		this.id = id;
	}
	
	public RandomOptimizer(Plan maasAgentPlan) {
		this.id = maasAgentPlan.getPerson().getId().toString();
		maasAgentPlan.getAttributes().getAsMap().entrySet().forEach(a->{
			if(a.getValue() instanceof VariableDetails) this.variables.put(a.getKey(), (VariableDetails)a.getValue());
		});
	}
	
	@Override
	public Map<String, VariableDetails> takeStep(Map<String, Double> gradient) {//Here assumes gradient = null
		
		this.distanceScale = 1/(counter+1);
		this.variables.values().parallelStream().forEach(vd->{
			
			double current = vd.getCurrentValue();
			Random rnd = new Random();
			int sign = -1;
			if(rnd.nextBoolean()) sign = 1;
			double newvar = current+sign * (vd.getLimit().getSecond() - vd.getLimit().getFirst())*rnd.nextDouble()*this.distanceScale;
			vd.setCurrentValue(newvar);
		});
		this.counter++;
		return this.variables;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public Map<String, VariableDetails> getVarables() {
		return this.variables;
	}

	@Override
	public void reset() {
		this.distanceScale = 1;
		this.counter = 0;
	}
	
	
}
