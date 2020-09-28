package optimizerAgent;

import java.util.Random;
import java.util.stream.Collectors;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.withinday.controller.ExecutedPlansService;
import org.matsim.withinday.controller.ExecutedPlansServiceImpl;

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
	private int MaaSPacakgeOptimizationStartingCounter = 50;
	private int MaaSPacakgeInertia = 25;
	private Scenario scenario;
	private int maxCounter = 10;
	private OutputDirectoryHierarchy controlerIO;

	private int currentMatsimIteration = 0;
	private timeBeansWrapper timeBeans;

	private Map<String,FareCalculator>fareCalculators;
	
	
	
	private static Logger logger = Logger.getLogger(MaaSOperatorStrategyModule.class);
	
	private IntelligentOperatorDecisionEngine decisionEngine;
	private Map<String,VariableDetails> variables = new HashMap<>();
	private Map<String,Optimizer> optimizers = new HashMap<>();
	//private ExecutedPlansService executedPlans;
	
	public MaaSOperatorStrategyModule(MaaSPackages packages, Scenario scenario, timeBeansWrapper timeBeans,
			Map<String, FareCalculator> fareCalculators,ExecutedPlansServiceImpl executedPlans, OutputDirectoryHierarchy controlerIO) {
		this.packages = packages;
		this.scenario = scenario;
		this.timeBeans = timeBeans;
		this.fareCalculators = fareCalculators;
		this.controlerIO = controlerIO;
		//this.executedPlans = executedPlans;
	}


	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		this.currentMatsimIteration = replanningContext.getIteration();
		logger.info("Entering into the replaning context in MaaSOperatorStrategyModule class.");
 		this.decisionEngine = new IntelligentOperatorDecisionEngine(this.scenario,this.packages,this.timeBeans,this.fareCalculators);
 		this.variables.clear();
 		this.optimizers.clear();
	}

	
	@Override
	public void handlePlan(Plan plan) {
		if(this.currentMatsimIteration%this.MaaSPacakgeInertia==0) {
			String operatorId =  plan.getPerson().getId().toString();
			if(!this.optimizers.containsKey(operatorId))this.optimizers.put(operatorId,new Adam(plan));
			Map<String,VariableDetails> variables = this.optimizers.get(plan.getPerson().getId().toString()).getVarables();
			this.variables.putAll(variables);
			this.decisionEngine.addOperatorAgent(plan);
		}
	}
	

	@Override
	public void finishReplanning() {
		if(this.currentMatsimIteration>=this.MaaSPacakgeOptimizationStartingCounter && this.currentMatsimIteration%this.MaaSPacakgeInertia==0) {
		//this.takeSingleStep();//use either this or the following 
		this.takeOptimizedStep();
		}
//		else {
//			for(Plan plan:this.plans) {
//				Plan newPlan = executedPlans.getExecutedPlans().get( plan.getPerson().getId() ) ;
//				Gbl.assertNotNull( newPlan ) ;
//				PopulationUtils.copyFromTo(newPlan, plan);
//			}
//		}
	}
	
	
	private void takeSingleStep() {
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
	
	private void takeOptimizedStep() {
		try {
			String fileLoc = this.controlerIO.getIterationFilename(this.currentMatsimIteration, "optimDetails.csv");

			FileWriter fw = new FileWriter(new File(fileLoc));
			fw.append("optimIter");
			List<String> vName = new ArrayList<>();
			for(String s:this.variables.keySet()) {
				fw.append(","+s+"_gradient");
				vName.add(s);
			}
			for(String s:vName) {
				fw.append(","+s);
			}
			fw.append("\n");
			if(variables.size()!=0) {
				for(int counter = 0;counter<=maxCounter;counter++) {
					fw.append(counter+"");
					Map<String,Map<String,Double>>grad =  this.decisionEngine.calcApproximateObjectiveGradient();
					//Map<String,Map<String,Double>>fdgrad =  this.decisionEngine.calcFDGradient();//This line is for testing only. 

					if(grad==null)
						logger.debug("Gradient is null. Debug!!!");
					
					for(String s:vName) {
						fw.append(","+grad.get(s));
					}
					
					this.optimizers.entrySet().forEach(o->{
						o.getValue().takeStep(grad.get(MaaSUtil.retrieveOperatorIdFromOperatorPersonId(Id.createPersonId(o.getKey()))));//This step 
						//already decides the new variable values and replace the old one with the new values. As, the same variable details instances
						//are used in decision engine and also here, the change should be broadcasted automatically. (Make a check if possible)Ashraf July 11, 2020
						//o.getValue().takeStep(null);
					});
					Map<String,Double> variableValues = new HashMap<>();
					for(Entry<String, VariableDetails> vd:this.variables.entrySet()) {
						variableValues.put(vd.getKey(), vd.getValue().getCurrentValue());
					}
					MaaSUtil.updateMaaSVariables(packages, variableValues);
					for(String s:vName) {
						fw.append(","+variableValues.get(s));
					}
					fw.append("\n");
					fw.flush();
				}
				this.decisionEngine = null;
				this.optimizers.entrySet().forEach(o->o.getValue().reset());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		

}
