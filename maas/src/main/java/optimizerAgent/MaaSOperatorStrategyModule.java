package optimizerAgent;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import MaaSPackages.MaaSPackages;
import optimizer.Adam;
import optimizer.Optimizer;


public class MaaSOperatorStrategyModule implements PlanStrategyModule{
	
	@Inject
	private @Named("MaaSPackages") MaaSPackages packages;
	
	private IntelligentOperatorDecisionEngine decisionEngine;
	private Map<String,VariableDetails> variables = new HashMap<>();
	private Map<String,Optimizer> optimizers = new HashMap<>();
	
	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		this.decisionEngine = new IntelligentOperatorDecisionEngine();
	}

	
	@Override
	public void handlePlan(Plan plan) {
		this.optimizers.put(MaaSUtil.retrieveOperatorIdFromOperatorPersonId(plan.getPerson().getId()),new Adam(plan));
		Map<String,VariableDetails> variables = this.optimizers.get(plan.getPerson().getId().toString()).getVarables();
		this.variables.putAll(variables);
		this.decisionEngine.addOperatorAgent(plan);
	}
	

	@Override
	public void finishReplanning() {
		Map<String,Map<String,Double>>grad =  this.decisionEngine.calcApproximateObjectiveGradient();
		this.optimizers.entrySet().forEach(o->{
			o.getValue().takeStep(grad.get(o.getKey()));
		});
		Map<String,Double> variableValues = new HashMap<>();
		for(Entry<String, VariableDetails> vd:this.variables.entrySet()) {
			variableValues.put(vd.getKey(), vd.getValue().getCurrentValue());
		}
		MaaSUtil.updateMaaSVariables(packages, variableValues);
	}

}
