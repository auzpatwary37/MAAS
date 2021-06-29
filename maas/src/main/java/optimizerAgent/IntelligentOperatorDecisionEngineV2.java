package optimizerAgent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Set;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import singlePlanAlgo.MaaSDataLoaderV2;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import optimizerAgent.*;
import com.google.common.collect.*;
/**
 * This class is responsible for making an intelligent decision on operator
 * @author ashraf
 *
 */
public class IntelligentOperatorDecisionEngineV2 {
	
	@Inject
	private Scenario scenario;
	
	@Inject
	private timeBeansWrapper TimeBeans;
	
	@Inject
	private Map<String,FareCalculator> fareCalculators;
	
	@Inject
	private @Named("MaaSPackages") MaaSPackages packages;
	
	private PopulationCompressor populationCompressor = null;
	
	private static final Logger logger = Logger.getLogger(IntelligentOperatorDecisionEngineV2.class);
	private boolean newPop = false;
	private PersonPlanSueModel model;
	private Map<String,Map<String,VariableDetails>>operator = new HashMap<>();
	private Map<String,VariableDetails> variables = new HashMap<>();
	private boolean ifCalculateFull = false;
	private SUEModelOutput flow;
	private packUsageStat modelStat=null;
	
	private String type = null;
	/**
	 * Variable Details already contain the key as variable name. Maybe the key is not necessary
	 * @param key
	 * @param variable details
	 */
	public void addNewVariable(String operatorId, String key, VariableDetails vd) {
		this.variables.put(key, vd);
		if(!this.operator.containsKey(operatorId)) {
			this.operator.put(operatorId, new HashMap<>());
		}
		this.operator.get(operatorId).put(key, vd);
	}
	
	
	public PersonPlanSueModel getModel() {
		return model;
	}


	public SUEModelOutput getFlow() {
		return flow;
	}


	public IntelligentOperatorDecisionEngineV2(Scenario scenario, MaaSPackages packages, timeBeansWrapper timeBeans, Map<String, FareCalculator> fareCalculators, PopulationCompressor populationCompressor, String type ) {
		this.scenario = scenario;
		this.TimeBeans = timeBeans;
		this.packages = packages;
		this.fareCalculators = fareCalculators;
		this.populationCompressor = populationCompressor;
		this.type = type;
	}
	

	
	
	public boolean isIfCalculateFull() {
		return ifCalculateFull;
	}


	public void setIfCalculateFull(boolean ifCalculateFull) {
		this.ifCalculateFull = ifCalculateFull;
	}


	public void setupAndRunMetaModel(LinkedHashMap<String,Double> variables) {
		modelStat = null;
		model = new PersonPlanSueModel(TimeBeans.timeBeans, scenario.getConfig());
		model.setPopulationCompressor(populationCompressor);
		model.populateModel(scenario, fareCalculators, packages);
		this.flow = model.performAssignment(scenario.getPopulation(),variables);
		this.model.setCreateLinkIncidence(false);
	}
	
	public void runMetamodel(LinkedHashMap<String,Double> variables) {
		modelStat = null;
		if(newPop) {
			this.flow = model.performAssignment(scenario.getPopulation(),variables);
		}else {
			this.flow = model.performAssignment(variables);
		}
	}
	
	
	public void addOperatorAgent(Plan maaSOperatorPlan) {
		Person agent = maaSOperatorPlan.getPerson();
		String operator = agent.getId().toString();
		Map<String,VariableDetails> var = new HashMap<>();
		maaSOperatorPlan.getAttributes().getAsMap().entrySet().forEach(a->{
			if(a.getValue() instanceof VariableDetails) var.put(a.getKey(), (VariableDetails)a.getValue());
		});
		this.operator.put(operator, var);
		this.variables.putAll(var);
	}
	
	
	public Map<String,Map<String,Double>> calcApproximateObjectiveGradient() {
		LinkedHashMap<String,Double> variables = new LinkedHashMap<>();
		this.variables.values().stream().forEach(vd->{
			variables.put(vd.getVariableName(), vd.getCurrentValue());
		});
		return this.calcApproximateObjectiveGradient(variables);
	}
	
	//This assumes the current variable values are already applied to the MaaSPackages
	/**
	 * Make this parallel. 
	 * This function is super complicated. Makes sense to revisit over and over
	 * @return
	 */
	public Map<String,Map<String,Double>> calcApproximateObjectiveGradient(LinkedHashMap<String,Double> variables) {

		if(type.equals(MaaSDataLoaderV2.typeOperator)) {
			return this.calcOperatorObjectiveGrad(variables);
		}else if(type.equals(MaaSDataLoaderV2.typeGovt)) {
			return this.calcGovtObjectiveGrad(variables);
		}else if(type.equals(MaaSDataLoaderV2.typeGovtTT)){
			return this.calcSeperatedGovtObjectiveGrad(variables);
		}else if(type.equals(MaaSDataLoaderV2.typeGovtTU)) {
			return this.calcGovtTUCombinedObjectiveGrad(variables);
		}
		return null;
	}
	
	
	
	
	
	
	private Map<String, Map<String, Double>> calcGovtTUCombinedObjectiveGrad(LinkedHashMap<String, Double> variables) {
//		this.setupAndRunMetaModel(variables);
		if(model!=null) {
			this.runMetamodel(variables);
		}else {
			this.setupAndRunMetaModel(variables);
		}
		ObjectiveAndGradientCalculator.measureAverreageTLWaitingTime(model, flow, "test/ averageWaitTime.csv");
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
		
		Map<String,Map<String,Double>> revGrad = null;
		if(!this.ifCalculateFull) {
			revGrad = ObjectiveAndGradientCalculator.calcRevenueObjectiveGradient(model, flow, variables, this.operator, packages, fareCalculators);
		}else {
			this.modelStat = ObjectiveAndGradientCalculator.fastCalculateRevenueObjectiveAndGradient(model, flow, variables, this.operator, packages, fareCalculators);
			revGrad = modelStat.getGrad();
			double govtObj = this.modelStat.getObjective().get("Govt");
			modelStat.GovtSepRevenue = govtObj;
			if(govtObj>0) {
				Map<String,Double>additionalGovtGrad = new HashMap<>();
				revGrad.get("Govt").entrySet().forEach(g->{
					additionalGovtGrad.put(g.getKey(),-1*g.getValue()*govtObj);
				});
				modelStat.additionalGovtGrad = additionalGovtGrad;
			
			}
		}
		Map<String,Map<String,Double>> tuGrad = new HashMap<>();
		
		Tuple<Map<String,Double>,Double> tuTuple = ObjectiveAndGradientCalculator.calcTotalSystemUtilityGradientAndObjective(model);
		tuGrad.put("Govt", tuTuple.getFirst());
		if(this.modelStat!=null) {
			this.modelStat.getObjective().compute("Govt", (k,v)->v=v+tuTuple.getSecond());
			this.modelStat.totalSystemUtility = tuTuple.getSecond();
		}
		
		for(Entry<String,Map<String,Double>> operator:tuGrad.entrySet()) {
			for(Entry<String,Double> grad:operator.getValue().entrySet()) {
				revGrad.get(operator.getKey()).compute(grad.getKey(), (k,v)->v=v+grad.getValue()/vom);
			}
		}
		if(this.modelStat!=null)this.modelStat.setGrad(revGrad);
		return revGrad;
	}


	private Map<String,Map<String,Double>> calcOperatorObjectiveGrad(LinkedHashMap<String,Double> variables){
//		this.setupAndRunMetaModel(variables);
		if(model!=null) {
			this.runMetamodel(variables);
		}else {
			this.setupAndRunMetaModel(variables);
		}
		if(!this.ifCalculateFull) {
			return ObjectiveAndGradientCalculator.calcRevenueObjectiveGradient(model, flow, variables, this.operator, packages, fareCalculators);
		}else {
			this.modelStat = ObjectiveAndGradientCalculator.fastCalculateRevenueObjectiveAndGradient(model, flow, variables, this.operator, packages, fareCalculators);
			return modelStat.getGrad();
		}
	}
	
	public Map<String,Map<String,Double>> calcGovtObjectiveGrad(LinkedHashMap<String,Double> variables){
		
//		this.setupAndRunMetaModel(variables);
		if(model!=null) {
			this.runMetamodel(variables);
		}else {
			this.setupAndRunMetaModel(variables);
		}
		ObjectiveAndGradientCalculator.measureAverreageTLWaitingTime(model, flow, "test/ averageWaitTime.csv");
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		double vot_car = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithCar_NAME).getOrCreateModeParams("car").getMarginalUtilityOfTraveling()/3600;
		double vot_transit = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()/3600;
		double vot_wait = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtlOfWaitingPt_utils_hr()/3600;
		double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
		
		Map<String,Map<String,Double>> revGrad = null;
		if(!this.ifCalculateFull) {
			revGrad = ObjectiveAndGradientCalculator.calcRevenueObjectiveGradient(model, flow, variables, this.operator, packages, fareCalculators);
		}else {
			this.modelStat = ObjectiveAndGradientCalculator.fastCalculateRevenueObjectiveAndGradient(model, flow, variables, this.operator, packages, fareCalculators);
			revGrad = modelStat.getGrad();
		}
		Map<String,Map<String,Double>> ttGrad = null;
		
		if(!this.ifCalculateFull) {
			ttGrad = ObjectiveAndGradientCalculator.calcTotalSystemTravelTimeGradient(model, flow, variables, operator, vot_car, vot_transit, vot_wait, vom);
		}else {
			Tuple<Map<String, Map<String, Double>>, Double> a = ObjectiveAndGradientCalculator.calcCompleteTotalSystemTravelTimeGradient(model, flow, variables, operator, vot_car, vot_transit, vot_wait, vom);
			ttGrad = a.getFirst();
			this.modelStat.totalSystemTravelTime = a.getSecond();
			this.modelStat.getObjective().compute("Govt", (k,v)->v=v+a.getSecond());
		}
		
		for(Entry<String,Map<String,Double>> operator:ttGrad.entrySet()) {
			for(Entry<String,Double> grad:operator.getValue().entrySet()) {
				revGrad.get(operator.getKey()).compute(grad.getKey(), (k,v)->v=v+grad.getValue());
			}
		}
		
		if(this.modelStat!=null)this.modelStat.setGrad(revGrad);
		return revGrad;
	}
	
	public Map<String,Map<String,Double>> calcSeperatedGovtObjectiveGrad(LinkedHashMap<String,Double> variables){
		
//		this.setupAndRunMetaModel(variables);
		if(model!=null) {
			this.runMetamodel(variables);
		}else {
			this.setupAndRunMetaModel(variables);
		}
		ObjectiveAndGradientCalculator.measureAverreageTLWaitingTime(model, flow, "test/ averageWaitTime.csv");
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		double vot_car = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithCar_NAME).getOrCreateModeParams("car").getMarginalUtilityOfTraveling()/3600;
		double vot_transit = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()/3600;
		double vot_wait = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtlOfWaitingPt_utils_hr()/3600;
		double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
		//Map<String,Map<String,Double>> revGrad = ObjectiveAndGradientCalculator.calcRevenueObjectiveGradient(model, flow, variables, this.operator, packages, fareCalculators);
		Map<String,Map<String,Double>> ttGrad = null;
		if(!this.ifCalculateFull) {
			ttGrad = ObjectiveAndGradientCalculator.calcTotalSystemTravelTimeGradient(model, flow, variables, operator, vot_car, vot_transit, vot_wait, vom);
		}else {
			ttGrad = ObjectiveAndGradientCalculator.calcCompleteTotalSystemTravelTimeGradient(model, flow, variables, operator, vot_car, vot_transit, vot_wait, vom).getFirst();
		}
		//Map<String,Map<String,Double>> totalGrad = new HashMap<>(revGrad);
		
//		for(Entry<String,Map<String,Double>> operator:ttGrad.entrySet()) {
//			for(Entry<String,Double> grad:operator.getValue().entrySet()) {
//				totalGrad.get(operator.getKey()).compute(grad.getKey(), (k,v)->v=v+grad.getValue());
//			}
//		}
		return ttGrad;
	}
	
	
	
	public Map<String,Map<String,Double>> calcFDGradient(LinkedHashMap<String,Double> variables){
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		
		//Map<String,Double> y0 = this.calcApproximateObjective(variables);
		
		this.operator.entrySet().forEach(operator->{
			operator.getValue().entrySet().forEach(var->{
				variables.compute(var.getKey(), (k,v)->v=v+.0025);
				this.runMetamodel(variables);
				Map<String,Double> yplus = this.calcApproximateObjective(variables);
				variables.compute(var.getKey(), (k,v)->v=v-.005);
				this.runMetamodel(variables);
				Map<String,Double> yminus = this.calcApproximateObjective(variables);
				yplus.entrySet().forEach(op->{
					if(operatorGradient.get(op.getKey())==null)operatorGradient.put(op.getKey(), new HashMap<>());
					double grad = 1./(2*.0025)*(yplus.get(op.getKey())-yminus.get(op.getKey()));
					operatorGradient.get(op.getKey()).put(var.getKey(), grad);
				});
			});
		});
		return operatorGradient;
	}
	public Map<String,Map<String,Double>> calcFDGradient(){
		LinkedHashMap<String,Double> variables = new LinkedHashMap<>();
		this.variables.values().stream().forEach(vd->{
			variables.put(vd.getVariableName(), vd.getCurrentValue());
		});
		return this.calcFDGradient(variables);
	}
	
	
	public Map<String,Double> calcApproximateObjective(){
		LinkedHashMap<String,Double> variables = new LinkedHashMap<>();
		this.variables.values().stream().forEach(vd->{
			variables.put(vd.getVariableName(), vd.getCurrentValue());
		});
		if(this.modelStat!=null)return modelStat.getObjective();
		return this.calcApproximateObjective(variables);
	}
	
	public Map<String,Double> calcApproximateObjective(LinkedHashMap<String,Double> variables){

		if(this.type.equals(MaaSDataLoaderV2.typeOperator)) {
			return this.calcApproximateOperatorObjective(variables);
		}else if(this.type.equals(MaaSDataLoaderV2.typeGovt)) {
			return this.calcApproximateGovtObjective(variables);
		}else if(this.type.equals(MaaSDataLoaderV2.typeGovtTT)) {
			double tt =  this.calcApproximateGovtTTObjective(variables);
			return this.operator.keySet().stream().collect(Collectors.toMap(k->k, k->tt)); 
		
		}
		return null;
	}
	
	public Map<String,Double> calcApproximateOperatorObjective(LinkedHashMap<String,Double>variables){
		//this.setupAndRunMetaModel(variables);
		if(model==null) {
			this.setupAndRunMetaModel(variables);
		}
		if(!this.ifCalculateFull) {
			return ObjectiveAndGradientCalculator.calcRevenueObjective(flow, operator.keySet(), packages, fareCalculators);
		}else {
			return ObjectiveAndGradientCalculator.calcCompleteRevenueObjective(flow, operator.keySet(), packages, fareCalculators);
		}
	}
	
	public Map<String,Double> calcApproximateGovtObjective(LinkedHashMap<String,Double>variables){
		//this.setupAndRunMetaModel(variables);
		if(model==null) {
			this.setupAndRunMetaModel(variables);
		}
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		double vot_car = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithCar_NAME).getOrCreateModeParams("car").getMarginalUtilityOfTraveling()/3600;
		double vot_transit = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()/3600;
		double vot_wait = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtlOfWaitingPt_utils_hr()/3600;
		double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
		
		Map<String,Double> revObj = null;
		if(!this.ifCalculateFull) {
			revObj = ObjectiveAndGradientCalculator.calcRevenueObjective(flow, operator.keySet(), packages, fareCalculators);
		}else {
			revObj = ObjectiveAndGradientCalculator.calcCompleteRevenueObjective(flow, operator.keySet(), packages, fareCalculators);
		}
		
		double ttObj = ObjectiveAndGradientCalculator.calcTotalSystemTravelTime(model, flow, vot_car, vot_transit, vot_wait, vom);
		if(!this.ifCalculateFull) {
			for(String l:revObj.keySet()){
				revObj.compute(l, (k,v)->v=v+ttObj);
			}
		}else {
			revObj.compute("Govt", (k,v)->v=v+ttObj);
		}
		logger.info("Travel time Objective = "+ttObj);
		logger.info("RevenueObjective = "+revObj.toString());
		return revObj;
	}
	
	public double calcApproximateGovtTTObjective(LinkedHashMap<String,Double>variables){
		//this.setupAndRunMetaModel(variables);
		if(model==null) {
			this.setupAndRunMetaModel(variables);
		}
		String PersonChangeWithCar_NAME = "person_TCSwithCar";
		String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
		
		String PersonFixed_NAME = "trip_TCS";
		String GVChange_NAME = "person_GV";
		String GVFixed_NAME = "trip_GV";
		double vot_car = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithCar_NAME).getOrCreateModeParams("car").getMarginalUtilityOfTraveling()/3600;
		double vot_transit = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling()/3600;
		double vot_wait = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtlOfWaitingPt_utils_hr()/3600;
		double vom = scenario.getConfig().planCalcScore().getOrCreateScoringParameters(PersonChangeWithoutCar_NAME).getMarginalUtilityOfMoney();
		//Map<String,Double> revObj = ObjectiveAndGradientCalculator.calcRevenueObjective(flow, operator.keySet(), packages, fareCalculators);
		double ttObj = ObjectiveAndGradientCalculator.calcTotalSystemTravelTime(model, flow, vot_car, vot_transit, vot_wait, vom);
	//	revObj.keySet().forEach(l->revObj.compute(l, (k,v)->v=v+ttObj));
		logger.info("Travel time Objective = "+ttObj);
	//	logger.info("RevenueObjective = "+revObj.toString());
		return ttObj;
	}
	
	public BiMap<String,String> getSimpleVariableKey(){
		return this.model.getSimpleVarKeys();
	}
	/**
	 * 
	 * @return
	 */
	public packUsageStat getPackageUsageStat() {
		if(this.modelStat==null) {
			this.modelStat =  ObjectiveAndGradientCalculator.calcPackUsageStat(this.flow, this.packages, this.fareCalculators);
		}
		return this.modelStat;
	}

}
