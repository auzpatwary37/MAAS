package optimizer;

import java.util.Map;

import optimizerAgent.VariableDetails;

public interface Optimizer {
	
	public Map<String,VariableDetails> takeStep(Map<String,Double> gradient);

}
