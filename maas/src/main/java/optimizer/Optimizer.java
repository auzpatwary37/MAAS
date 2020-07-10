package optimizer;

import java.util.Map;

import optimizerAgent.VariableDetails;

public interface Optimizer {
	
	public Map<String,VariableDetails> takeStep(Map<String,Double> gradient);
	public String getId();
	public Map<String,VariableDetails> getVarables();
	/**
	 * Should reset the optimizer's inner variables
	 */
	public void reset();
}
