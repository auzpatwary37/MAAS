package optimizerAgent;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.replanning.ReplanningContext;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import MaaSPackages.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import optimizer.Adam;
import optimizer.Optimizer;
import optimizer.RandomOptimizer;


public class MaaSOperatorStrategyModule implements PlanStrategyModule{
	
	@Inject
	private @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	
	private int MaaSPacakgeInertia = 5;
	private Scenario scenario;
	

	private timeBeansWrapper timeBeans;

	private Map<String,FareCalculator>fareCalculators;
	
	private static Logger logger = Logger.getLogger(MaaSOperatorStrategyModule.class);
	
	private IntelligentOperatorDecisionEngine decisionEngine;
	private Map<String,VariableDetails> variables = new HashMap<>();
	private Map<String,Optimizer> optimizers = new HashMap<>();
	
	public MaaSOperatorStrategyModule(MaaSPackages packages, Scenario scenario, timeBeansWrapper timeBeans,
			Map<String, FareCalculator> fareCalculators) {
		this.packages = packages;
		this.scenario = scenario;
		this.timeBeans = timeBeans;
		this.fareCalculators = fareCalculators;
	}


	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		logger.info("Entering into the replaning context in MaaSOperatorStrategyModule class.");
 		this.decisionEngine = new IntelligentOperatorDecisionEngine(this.scenario,this.packages,this.timeBeans,this.fareCalculators);
 		this.variables.clear();
 		this.optimizers.clear();
	}

	
	@Override
	public void handlePlan(Plan plan) {
		this.optimizers.put(plan.getPerson().getId().toString(),new Adam(plan));
		Map<String,VariableDetails> variables = this.optimizers.get(plan.getPerson().getId().toString()).getVarables();
		this.variables.putAll(variables);
		this.decisionEngine.addOperatorAgent(plan);
	}
	

	@Override
	public void finishReplanning() {
		if(variables.size()!=0) {
			Map<String,Map<String,Double>>grad =  this.decisionEngine.calcApproximateObjectiveGradient();
			//Map<String,Map<String,Double>>fdgrad =  this.decisionEngine.calcFDGradient();//This line is for testing only. 
			
			if(grad==null)
				logger.debug("Gradient is null. Debug!!!");
			this.optimizers.entrySet().forEach(o->{
				o.getValue().takeStep(grad.get(MaaSUtil.retrieveOperatorIdFromOperatorPersonId(Id.createPersonId(o.getKey()))));
				//o.getValue().takeStep(null);
			});
			Map<String,Double> variableValues = new HashMap<>();
			for(Entry<String, VariableDetails> vd:this.variables.entrySet()) {
				variableValues.put(vd.getKey(), vd.getValue().getCurrentValue());
			}
			MaaSUtil.updateMaaSVariables(packages, variableValues);
			this.decisionEngine = null;
		}
	}

}
