package optimizerAgent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import singlePlanAlgo.MaaSDataLoader;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;

/**
 * This class is responsible for making an intelligent decision on operator
 * @author ashraf
 *
 */
public class IntelligentOperatorDecisionEngine {
	
	@Inject
	private Scenario scenario;
	
	@Inject
	private timeBeansWrapper TimeBeans;
	
	@Inject
	private Map<String,FareCalculator> fareCalculators;
	
	@Inject
	private @Named("MaaSPackages") MaaSPackages packages;
	
	private PopulationCompressor populationCompressor = null;
	
	private static final Logger logger = Logger.getLogger(IntelligentOperatorDecisionEngine.class);
	
	private PersonPlanSueModel model;
	private Map<String,Map<String,VariableDetails>>operator = new HashMap<>();
	private Map<String,VariableDetails> variables = new HashMap<>();
	
	private SUEModelOutput flow;
	
	private String type = null;
	/**
	 * Variable Details already contain the key as variable name. Maybe the key is not necessary
	 * @param key
	 * @param variable details
	 */
	public void addNewVariable(String key, VariableDetails vd) {
		this.variables.put(key, vd);
	}
	
	
	public IntelligentOperatorDecisionEngine(Scenario scenario, MaaSPackages packages, timeBeansWrapper timeBeans, Map<String, FareCalculator> fareCalculators, PopulationCompressor populationCompressor, String type ) {
		this.scenario = scenario;
		this.TimeBeans = timeBeans;
		this.packages = packages;
		this.fareCalculators = fareCalculators;
		this.populationCompressor = populationCompressor;
		this.type = type;
	}
	

	
	
	public void setupAndRunMetaModel(LinkedHashMap<String,Double> variables) {
		model = new PersonPlanSueModel(TimeBeans.timeBeans, scenario.getConfig());
		model.setPopulationCompressor(populationCompressor);
		model.populateModel(scenario, fareCalculators, packages);
		this.flow = model.performAssignment(scenario.getPopulation(),variables);
		this.model.setCreateLinkIncidence(false);
	}
	
	public void runMetamodel(LinkedHashMap<String,Double> variables) {
		this.flow = model.performAssignment(scenario.getPopulation(),variables);
	}
	
	
	public void addOperatorAgent(Plan maaSOperatorPlan) {
		Person agent = maaSOperatorPlan.getPerson();
		String operator = (MaaSUtil.retrieveOperatorIdFromOperatorPersonId(agent.getId()));
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

		if(type.equals(MaaSDataLoader.typeOperator)) {
			return this.calcOperatorObjectiveGrad(variables);
		}else if(type.equals(MaaSDataLoader.typePlatform)) {
			return this.calcPlatformObjGrad(variables);
		}else {
			return null;
		}

	}
	
	/**
	 * This assumes the packages are made out of different modes and the integrator is a platform who only benefits from the package price.
	 * However, the platform pays the company a fraction of the original price of the discounted fare links 
	 * @param variables
	 * @return
	 */
	private Map<String,Map<String,Double>> calcPlatformObjGrad(LinkedHashMap<String,Double> variables) {
//		if(this.flow==null) {
//			this.setupAndRunMetaModel(variables);
//		}else {
//			this.runMetamodel(variables);
//		}
		
		//this.setupAndRunMetaModel(variables);
		if(model!=null) {
			this.runMetamodel(variables);
		}else {
			this.setupAndRunMetaModel(variables);
		}
		//System.out.println(this.scenario.getNetwork().getLinks().get(this.scenario.getNetwork().getLinks().keySet().toArray()[0]).getClass());
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		
		this.operator.entrySet().forEach(operator->{
			Double alpha = variables.get(MaaSUtil.createPlanformReimbursementVariableName(operator.getKey()));
			if(alpha==null)alpha = 1.;
			
			operatorGradient.put(operator.getKey(),new HashMap<>());
			operator.getValue().entrySet().forEach(var->{//for one variable
				
				String key = var.getKey();
				String pacakgeId = MaaSUtil.retrievePackageId(key);//packageId of that variable
				double volume = 0;
				double a = this.packages.getMassPackages().get(pacakgeId).getReimbursementRatio();
				if(MaaSUtil.ifFareLinkVariableDetails(key)) {//if the variable is a fare link variable
					FareLink farelink = new FareLink(MaaSUtil.retrieveFareLink(key));
					for(Entry<String, Map<String, Map<String, Double>>> timeFl:this.flow.getMaaSSpecificFareLinkFlow().entrySet()) volume+=timeFl.getValue().get(pacakgeId).get(farelink.toString());//subtract froom gradient
					volume=volume*-1*a;
				}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(key)) {
					if(this.flow.getMaaSPackageUsage().get(pacakgeId)==null) {
						volume = 0;
					}else {
						volume = this.flow.getMaaSPackageUsage().get(pacakgeId);
					}
				}
				
				double grad = volume;
				for(MaaSPackage maasPackage:this.packages.getMassPackagesPerOperator().get(operator.getKey())) {//maasPackage belonging to the operator
					for(String fl:maasPackage.getFareLinks().keySet()) {//fare link in that maas package
						for(Entry<String, Map<String, Map<String, Map<String, Double>>>> fareGrad:model.getFareLinkGradient().entrySet()) {//timeBeans
							double timeMaasSpecificFareLinkGrad = 0;
							//double nullPackageGrad = 0;
							try {//The try catch block is necessary as we created the incidence matrix based on usage rather than exhaustive enumeration. 
								//So, there can be null values for any specific keys maasPackage and fareLink and evem for timeBeans in case of flow
								timeMaasSpecificFareLinkGrad = fareGrad.getValue().get(maasPackage.getId()).get(fl).get(key);//The fare link can be not used at a timeBean by anyone belonging to that specific maas pacakge  
								//nullPackageGrad = fareGrad.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName).get(fl).get(key);//check
							}catch(Exception e) {//This means either nobody holding that maas package travelled in that time step, or the former
								if(fareGrad.getValue().get(maasPackage.getId())==null) {//case1
									//logger.debug("MaaS Package holder did not travel on any fare link in that timeBean");
								}
								else if(fareGrad.getValue().get(maasPackage.getId()).get(fl)==null) {//case 2
									//logger.debug("The fare link was not used by any maas package holder in that time step");
								}
								else if(fareGrad.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName)==null) {//case3
									//logger.debug("MaaS Package holder did not travel on any fare link in that timeBean");
								}else if(fareGrad.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName).get(fl)==null) {//case4
									//logger.debug("The fare link was not used by any maas package holder in that time step");
								}else //case5
									logger.debug("Should investigate. Might be other issues.");
							}
							grad+=-1*a*maasPackage.getDiscounts().get(fl)*timeMaasSpecificFareLinkGrad;
							
						}//finish timebean
					}//finish farelinks
					
					if(model.getPacakgeUserGradient().containsKey(maasPackage.getId())) {
						grad+=maasPackage.getPackageCost()*model.getPacakgeUserGradient().get(maasPackage.getId()).get(key);
					}else {
						grad+=0;// The package was not used in any of the plans
					}
					
				}//finish maaspackage
				//Save the gradient
				operatorGradient.get(operator.getKey()).put(key, grad);
			});
		});
		return operatorGradient;
	}
	
	public Map<String,Double> calcPlatformObjective(LinkedHashMap<String,Double> variables){
		if(model==null) {
			this.setupAndRunMetaModel(variables);
		}
		Map<String,Double>operatorObjective = new HashMap<>();
		this.operator.entrySet().forEach(o->{
			double revenue = 0;
			
			for(MaaSPackage maasPackage:this.packages.getMassPackagesPerOperator().get(o.getKey())) {//maasPackage belonging to the operator
				for(String fl:maasPackage.getFareLinks().keySet()) {//fare link in that maas package
					for(Entry<String, Map<String, Map<String, Double>>> timefareLinkFlow:flow.getMaaSSpecificFareLinkFlow().entrySet()) {//timeBeans
						double flow = 0;
						double nullPackageFlow = 0;
						try {//The try catch block is necessary as we created the incidence matrix based on usage rather than exhaustive enumeration. 
							//So, there can be null values for any specific keys maasPackage and fareLink and evem for timeBeans in case of flow
							flow = timefareLinkFlow.getValue().get(maasPackage.getId()).get(fl);//get flow in that fare link at a time step with maas package 
							//nullPackageFlow = timefareLinkFlow.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName).get(fl);
						}catch(Exception e) {//This means either nobody holding that maas package travelled in that time step, or the former
							if(timefareLinkFlow.getValue().get(maasPackage.getId())==null) {
								//logger.debug("MaaS Package holder did not travel on any fare link in that timeBean");
							}
							else if(timefareLinkFlow.getValue().get(maasPackage.getId()).get(fl)==null) {
								//logger.debug("The fare link was not used by any maas package holder in that time bean");
							}
							else {
								logger.debug("Should investigate. Might be other issues.");//The noMass is not present? 
							}
						}
						revenue -= maasPackage.getDiscounts().get(fl)*flow*maasPackage.getReimbursementRatio();
						
					}//finish timebean
				}//finish farelinks
			}
			operatorObjective.put(o.getKey(),revenue);
		});
		return operatorObjective;
	}
	
	public Map<String,Map<String,Double>> calcPlatformFDGrad(LinkedHashMap<String,Double> variables){
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		double c = 0.0005;
		Map<String,Double> operatorObj = calcPlatformObjective(variables);
		for(Entry<String, Map<String, VariableDetails>> o:this.operator.entrySet()) {
			operatorGradient.put(o.getKey(), new HashMap<>());
			for(String var:o.getValue().keySet()) {
				LinkedHashMap<String,Double>vars = new LinkedHashMap<>(variables);
				vars.compute(var,(k,v)->v+c);
				this.runMetamodel(vars);
				Map<String,Double> newObj = this.calcPlatformObjective(vars);
				operatorGradient.get(o.getKey()).put(var, (newObj.get(o.getKey())-operatorObj.get(o.getKey()))/c);
			}
		}
		
		return operatorGradient;
	}
	
	private Map<String,Map<String,Double>> calcOperatorObjectiveGrad(LinkedHashMap<String,Double> variables){
//		this.setupAndRunMetaModel(variables);
		if(model!=null) {
			this.runMetamodel(variables);
		}else {
			this.setupAndRunMetaModel(variables);
		}
		//System.out.println(this.scenario.getNetwork().getLinks().get(this.scenario.getNetwork().getLinks().keySet().toArray()[0]).getClass());
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		this.operator.entrySet().forEach(operator->{
			operatorGradient.put(operator.getKey(),new HashMap<>());
			operator.getValue().entrySet().forEach(var->{//for one variable
				
				String key = var.getKey();
				String pacakgeId = MaaSUtil.retrievePackageId(key);//packageId of that variable
				double volume = 0;
				if(MaaSUtil.ifFareLinkVariableDetails(key)) {//if the variable is a fare link variable
					FareLink farelink = new FareLink(MaaSUtil.retrieveFareLink(key));
					for(Entry<String, Map<String, Map<String, Double>>> timeFl:this.flow.getMaaSSpecificFareLinkFlow().entrySet()) volume+=timeFl.getValue().get(pacakgeId).get(farelink.toString());//subtract froom gradient
					volume=volume*-1;
				}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(key)) {
					if(this.flow.getMaaSPackageUsage().get(pacakgeId)==null) {
						volume = 0;
					}else {
						volume = this.flow.getMaaSPackageUsage().get(pacakgeId);
					}
				}
				
				double grad = volume;
				for(MaaSPackage maasPackage:this.packages.getMassPackagesPerOperator().get(operator.getKey())) {//maasPackage belonging to the operator
					for(String fl:maasPackage.getFareLinks().keySet()) {//fare link in that maas package
						for(Entry<String, Map<String, Map<String, Map<String, Double>>>> fareGrad:model.getFareLinkGradient().entrySet()) {//timeBeans
							double timeMaasSpecificFareLinkGrad = 0;
							double nullPackageGrad = 0;
							try {//The try catch block is necessary as we created the incidence matrix based on usage rather than exhaustive enumeration. 
								//So, there can be null values for any specific keys maasPackage and fareLink and evem for timeBeans in case of flow
								timeMaasSpecificFareLinkGrad = fareGrad.getValue().get(maasPackage.getId()).get(fl).get(key);//The fare link can be not used at a timeBean by anyone belonging to that specific maas pacakge  
								nullPackageGrad = fareGrad.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName).get(fl).get(key);//check
							}catch(Exception e) {//This means either nobody holding that maas package travelled in that time step, or the former
								if(fareGrad.getValue().get(maasPackage.getId())==null) {//case1
									//logger.debug("MaaS Package holder did not travel on any fare link in that timeBean");
								}
								else if(fareGrad.getValue().get(maasPackage.getId()).get(fl)==null) {//case 2
									//logger.debug("The fare link was not used by any maas package holder in that time step");
								}
								else if(fareGrad.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName)==null) {//case3
									//logger.debug("MaaS Package holder did not travel on any fare link in that timeBean");
								}else if(fareGrad.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName).get(fl)==null) {//case4
									//logger.debug("The fare link was not used by any maas package holder in that time step");
								}else //case5
									logger.debug("Should investigate. Might be other issues.");
							}
							grad+=(maasPackage.getFullFare().get(fl)-maasPackage.getDiscounts().get(fl))*timeMaasSpecificFareLinkGrad+maasPackage.getFullFare().get(fl)*nullPackageGrad;
							
						}//finish timebean
					}//finish farelinks
					
					if(model.getPacakgeUserGradient().containsKey(maasPackage.getId())) {
						grad+=maasPackage.getPackageCost()*model.getPacakgeUserGradient().get(maasPackage.getId()).get(key);
					}else {
						grad+=0;// The package was not used in any of the plans
					}
					
				}//finish maaspackage
				//Save the gradient
				operatorGradient.get(operator.getKey()).put(key, grad);
			});
		});
		return operatorGradient;
	}
	
	public Map<String,Map<String,Double>> calcOperatorFDGradient(LinkedHashMap<String,Double> variables){
		Map<String,Map<String,Double>>operatorGradient = new HashMap<>();
		
		Map<String,Double> y0 = this.calcApproximateObjective(variables);
		
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
		if(this.type.equals(MaaSDataLoader.typeOperator)) {
			return this.calcOperatorFDGradient(variables);
		}else if(this.type.equals(MaaSDataLoader.typePlatform)) {
			return this.calcPlatformFDGrad(variables);
		}
		return null;
	}
	
	public Map<String,Double> calcApproximateObjective(){
		LinkedHashMap<String,Double> variables = new LinkedHashMap<>();
		this.variables.values().stream().forEach(vd->{
			variables.put(vd.getVariableName(), vd.getCurrentValue());
		});

		if(this.type.equals(MaaSDataLoader.typeOperator)) {
			return this.calcApproximateObjective(variables);
		}else if(this.type.equals(MaaSDataLoader.typePlatform)) {
			return this.calcPlatformObjective(variables);
		}
		return null;
	}
	
	public Map<String,Double> calcApproximateObjective(LinkedHashMap<String,Double>variables){
		//this.setupAndRunMetaModel(variables);
		if(model==null) {
			this.setupAndRunMetaModel(variables);
		}
		Map<String,Double> operatorObj = new HashMap<>();
		
		this.operator.keySet().forEach(o->{
			double obj = 0;
			for(MaaSPackage maasPackage:this.packages.getMassPackagesPerOperator().get(o)) {//maasPackage belonging to the operator
				for(String fl:maasPackage.getFareLinks().keySet()) {//fare link in that maas package
					for(Entry<String, Map<String, Map<String, Double>>> timefareLinkFlow:flow.getMaaSSpecificFareLinkFlow().entrySet()) {//timeBeans
						double flow = 0;
						double nullPackageFlow = 0;
						try {//The try catch block is necessary as we created the incidence matrix based on usage rather than exhaustive enumeration. 
							//So, there can be null values for any specific keys maasPackage and fareLink and evem for timeBeans in case of flow
							flow = timefareLinkFlow.getValue().get(maasPackage.getId()).get(fl);//get flow in that fare link at a time step with maas package 
							nullPackageFlow = timefareLinkFlow.getValue().get(MaaSUtil.nullMaaSPacakgeKeyName).get(fl);
						}catch(Exception e) {//This means either nobody holding that maas package travelled in that time step, or the former
							if(timefareLinkFlow.getValue().get(maasPackage.getId())==null) {
								//logger.debug("MaaS Package holder did not travel on any fare link in that timeBean");
							}
							else if(timefareLinkFlow.getValue().get(maasPackage.getId()).get(fl)==null) {
								//logger.debug("The fare link was not used by any maas package holder in that time bean");
							}
							else {
								logger.debug("Should investigate. Might be other issues.");//The noMass is not present? 
							}
						}
						obj+=(maasPackage.getFullFare().get(fl)-maasPackage.getDiscounts().get(fl))*flow+maasPackage.getFullFare().get(fl)*nullPackageFlow;
						
					}//finish timebean
				}//finish farelinks
				
				obj+=maasPackage.getPackageCost()*this.flow.getMaaSPackageUsage().get(maasPackage.getId());
				
			}
			operatorObj.put(o, obj);
		});
		
		return operatorObj;
	}

}
