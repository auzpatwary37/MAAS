package optimizerAgent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;
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
	
	private PersonPlanSueModel model;
	private Map<String,Map<String,VariableDetails>>operator = new HashMap<>();
	private Map<String,VariableDetails> variables = new HashMap<>();
	
	private SUEModelOutput flow;
	/**
	 * Variable Details already contain the key as variable name. Maybe the key is not necessary
	 * @param key
	 * @param variable details
	 */
	public void addNewVariable(String key, VariableDetails vd) {
		this.variables.put(key, vd);
	}
	
	public void setupAndRunMetaModel() {
		model = new PersonPlanSueModel(TimeBeans.timeBeans, scenario.getConfig());
		model.populateModel(scenario, fareCalculators, packages);
		LinkedHashMap<String,Double> variables = new LinkedHashMap<>();
		this.variables.values().stream().forEach(vd->{
			variables.put(vd.getVariableName(), vd.getCurrentValue());
		});
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
	
	
	//This assumes the current variable values are already applied to the MaaSPackages
	/**
	 * This function is super complicated. Makes sense to revisit over and over
	 * @return
	 */
	public Map<String,Map<String,Double>> calcApproximateObjectiveGradient() {
		if(this.flow==null)this.setupAndRunMetaModel();
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
					volume = this.flow.getMaaSPackageUsage().get(pacakgeId);
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
								nullPackageGrad = fareGrad.getValue().get(null).get(fl).get(key);
							}catch(Exception e) {//This means either nobody holding that maas package travelled in that time step, or the former
								if(fareGrad.getValue().get(maasPackage.getId())==null)System.out.println("MaaS Package holder did not travel on any fare link in that timeBean");
								else if(fareGrad.getValue().get(maasPackage.getId()).get(fl)==null)System.out.println("The fare link was not used by any maas package holder in that time step");
								else System.out.println("Should investigate. Might be other issues.");
							}
							grad+=(maasPackage.getFullFare().get(fl)-maasPackage.getDiscounts().get(fl))*timeMaasSpecificFareLinkGrad+maasPackage.getFullFare().get(fl)*nullPackageGrad;
							
						}//finish timebean
					}//finish farelinks
					
					grad+=maasPackage.getPackageCost()*model.getPacakgeUserGradient().get(maasPackage.getId()).get(key);
					
				}//finish maaspackage
				//Save the gradient
				operatorGradient.get(operator.getKey()).put(key, grad);
			});
		});
		return operatorGradient;
	}
	
	public Map<String,Double> calcApproximateObjective(){
		if(this.flow==null)this.setupAndRunMetaModel();
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
							nullPackageFlow = timefareLinkFlow.getValue().get(null).get(fl);
						}catch(Exception e) {//This means either nobody holding that maas package travelled in that time step, or the former
							if(timefareLinkFlow.getValue().get(maasPackage.getId())==null)System.out.println("MaaS Package holder did not travel on any fare link in that timeBean");
							else if(timefareLinkFlow.getValue().get(maasPackage.getId()).get(fl)==null)System.out.println("The fare link was not used by any maas package holder in that time bean");
							else System.out.println("Should investigate. Might be other issues.");
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