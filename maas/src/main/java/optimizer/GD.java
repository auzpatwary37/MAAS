package optimizer;

import java.util.Map;

import org.matsim.core.utils.collections.Tuple;

import optimizerAgent.VariableDetails;

public class GD implements Optimizer{
	
	private double alpha = .001;
	private int counter;
	private final String id;
	private Map<String,VariableDetails> variables;

	public GD(String id, Map<String,VariableDetails> variables) {
		this.variables = variables;
		this.id = id;
	}
	
	public GD(String id, Map<String,VariableDetails> variables,double alpha) {
		this.variables = variables;
		this.alpha = alpha;
		this.id = id;
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

}
