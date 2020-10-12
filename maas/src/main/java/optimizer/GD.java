package optimizer;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;

import optimizerAgent.VariableDetails;

public class GD implements Optimizer{
	
	private double alpha = .001;
	private int counter;
	private final String id;
	private Map<String,VariableDetails> variables= new HashMap<>();

	public GD(String id, Map<String,VariableDetails> variables) {
		this.variables = variables;
		this.id = id;
	}
	
	public GD(String id, Map<String,VariableDetails> variables,double alpha) {
		this.variables = variables;
		this.alpha = alpha;
		this.id = id;
	}
	
	public GD(Plan maasAgentPlan) {
		this.id = maasAgentPlan.getPerson().getId().toString();
		maasAgentPlan.getAttributes().getAsMap().entrySet().forEach(a->{
			if(a.getValue() instanceof VariableDetails) this.variables.put(a.getKey(), (VariableDetails)a.getValue());
		});
	}
	
	@Override
	public Map<String, VariableDetails> takeStep(Map<String, Double> gradient) {
		counter = counter+1;
		
		gradient.entrySet().parallelStream().forEach(g->{
			double var = this.variables.get(g.getKey()).getCurrentValue() + this.alpha*g.getValue();
			Tuple<Double,Double> limit = this.variables.get(g.getKey()).getLimit();
			if(var<limit.getFirst()) var = limit.getFirst();
			else if (var>limit.getSecond()) var = limit.getSecond();
			this.variables.get(g.getKey()).setCurrentValue(var);
		});
		
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
		this.counter = 0;
	}
	
	

}
