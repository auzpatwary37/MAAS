package optimizerAgent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.Vehicles;

import com.google.inject.Inject;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import singlePlanAlgo.MAASPackages;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SignalFlowReductionGenerator;

/**
 * 
 * @author Ashraf
 * 
 * Writing down the initial thought 
 * 
 * This model is very simple SUE for plans of each person 
 * In that sense this model is a probability based model 
 * 
 * Basically there will be person level network loading, where, we use the similar model parameters. 
 * 
 * What happens to the activities???
 * 
 * Should we model the activities as well??
 * 
 * Lets say we do
 * 
 * So utility of a plan = utility of each activity and disutility of all the travel legs. 
 * 
 * There should still be a network and transit links map.
 * 
 *  but instead of od pairs, there will be plans. So the model will not scale as well as a trip based model
 *  
 *  pseudo code:
 *  
 *  Assignment Loop:
 *  {
 *  for each person { Will run in parallel
 *  	calculate plan probability{
 *  		for each activity{
 *  			calculate activity utility
 *  		}
 *  		
 *  		for each leg{
 * 				calculate leg Dis-utility (Use last iterations travel time for both car and transit)
 *  	
 *  		} 
 *  		
 *  		Perform simple logit to get the split (What about path size??) What should be a good measure of similarity between two plans
 *  		
 *  		based on the split calculate network and transit flow
 *  	}
 *  	
 *  }
 *  update link and transit flow
 *  }
 *  
 *  Functions Necessary;
 *  
 *  PerformTransitOverlay: Same funciton should be usable 
 *  
 *  Perform SUE: performs the outer loop	
 *  
 *  Person NL: Performs person specific calculation// Should be thread safe
 *  
 *  CalcActivityUtility: Should be pretty much straight forward implementation. Must be thread safe. The parameters should contain all activity parameters. Implementation will follow from MATSim's own activity utility function
 *  
 *  CalcTripUtility: This should be separated using mode (MAAS plan can be incorporated here) must be thread safe
 *  
 *  CalcPlanLinkTransitLinkIncidece: this will calculate the incidence relation from plan. I this really necessary? This can calculated per plan
 *  
 *  updateLinkFlow: 
 * 
 */
public class PersonPlanSueModel {
	
	
	//TODO: add sub-population support
	//
	
	Logger logger = Logger.getLogger(PersonPlanSueModel.class);
	
	private Population population;
	private Map<String,Tuple<Double,Double>>timeBeans;
	private Map<String,FareCalculator> farecalculators;
	private MAASPackages maasPakages;
	private Scenario scenario; 
	private TransitSchedule ts;
	private Map<String,AnalyticalModelNetwork> networks = new ConcurrentHashMap<>();;
	private Map<String,Map<Id<TransitLink>,TransitLink>> transitLinks = new ConcurrentHashMap<>();
	private LinkedHashMap<String,Double> Params = new LinkedHashMap<>();
	private LinkedHashMap<String,Double> AnalyticalModelInternalParams = new LinkedHashMap<>();
	private LinkedHashMap<String,Tuple<Double,Double>> AnalyticalModelParamsLimit=new LinkedHashMap<>();
	
	private double alphaMSA=1.9;//parameter for decreasing MSA step size
	private double gammaMSA=.1;//parameter for decreasing MSA step size
	
	//other Parameters for the Calibration Process
	private double tollerance= 1;
	private double tolleranceLink=.1;
	private int maxIter = 500;
	
	//Used Containers
	private List<Double> beta=new ArrayList<>(); //This is related to weighted MSA of the SUE
	private List<Double> error=new ArrayList<>();
	private int consecutiveErrorIncrease = 0;
	
	
	//This are needed for output generation 
	
	protected Map<String,Map<Id<Link>,Double>> outputLinkTT=new ConcurrentHashMap<>();
	protected Map<String,Map<Id<TransitLink>,Double>> outputTrLinkTT=new ConcurrentHashMap<>();
	private Map<String,Map<Id<Link>,Double>> totalPtCapacityOnLink=new HashMap<>();
	protected Map<String,Map<String,Double>>MTRCount=new ConcurrentHashMap<>();
	
	//Internal Parameters of the model
	public static final String BPRalphaName="BPRalpha";
	public static final String BPRbetaName="BPRbeta";
	public static final String PlanMiuName="Miu";
	public static final String TransferalphaName="Transferalpha";
	public static final String TransferbetaName="Transferbeta";
	
	
	
	//Constructor
	@Inject
	public PersonPlanSueModel(Map<String, Tuple<Double, Double>> timeBean,Config config) {
		this.timeBeans=timeBean;
		//this.defaultParameterInitiation(null);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			
			//For result recording
			outputLinkTT.put(timeBeanId, new HashMap<>());
			outputTrLinkTT.put(timeBeanId, new HashMap<>());
			this.totalPtCapacityOnLink.put(timeBeanId, new HashMap<>());
			this.MTRCount.put(timeBeanId, new ConcurrentHashMap<>());
		}
		if(config==null) config = ConfigUtils.createConfig();
		this.defaultParameterInitiation(config);
		logger.info("Model created.");
	}
	
	
	private void defaultParameterInitiation(Config config){
		//Loads the Internal default parameters 
		
		this.AnalyticalModelInternalParams.put(CNLSUEModel.LinkMiuName, 0.008);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.ModeMiuName, 0.01);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRalphaName, 0.15);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRbetaName, 4.);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferalphaName, 0.5);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferbetaName, 1.);
		this.loadAnalyticalModelInternalPamamsLimit();
		
		//Loads the External default Parameters
		if(config==null) {
			config=ConfigUtils.createConfig();
		}
		

		this.Params.put(CNLSUEModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
		this.Params.put(CNLSUEModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfDistance());
		this.Params.put(CNLSUEModel.MarginalUtilityofMoneyName,config.planCalcScore().getMarginalUtilityOfMoney());
		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateModeParams("car").getMonetaryDistanceRate());
		this.Params.put(CNLSUEModel.MarginalUtilityofTravelptName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfTraveling());
		this.Params.put(CNLSUEModel.MarginalUtilityOfDistancePtName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfDistance());
		this.Params.put(CNLSUEModel.MarginalUtilityofWaitingName,config.planCalcScore().getMarginalUtlOfWaitingPt_utils_hr());
		this.Params.put(CNLSUEModel.UtilityOfLineSwitchName,config.planCalcScore().getUtilityOfLineSwitch());
		this.Params.put(CNLSUEModel.MarginalUtilityOfWalkingName, config.planCalcScore().getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostWalkName, config.planCalcScore().getOrCreateModeParams("walk").getMonetaryDistanceRate());
		this.Params.put(CNLSUEModel.ModeConstantPtname,config.planCalcScore().getOrCreateModeParams("pt").getConstant());
		this.Params.put(CNLSUEModel.ModeConstantCarName,config.planCalcScore().getOrCreateModeParams("car").getConstant());
		this.Params.put(CNLSUEModel.MarginalUtilityofPerformName, config.planCalcScore().getPerforming_utils_hr());
		this.Params.put(CNLSUEModel.CapacityMultiplierName, 1.0);
	}
	
	public void setDefaultParameters(LinkedHashMap<String,Double> params) {
		for(String s:params.keySet()) {
			this.Params.put(s, params.get(s));
		}
	}
	
	
	protected void loadAnalyticalModelInternalPamamsLimit() {
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.LinkMiuName, new Tuple<Double,Double>(0.0075,0.25));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.ModeMiuName, new Tuple<Double,Double>(0.01,0.5));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRalphaName, new Tuple<Double,Double>(0.10,4.));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRbetaName, new Tuple<Double,Double>(1.,15.));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferalphaName, new Tuple<Double,Double>(0.25,5.));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferbetaName, new Tuple<Double,Double>(0.75,4.));
	}
	
	
	/**
	 * This method overlays transit vehicles on the road network
	 * @param network
	 * @param Schedule
	 */
	private void performTransitVehicleOverlay(AnalyticalModelNetwork network, TransitSchedule schedule,Vehicles vehicles,String timeBeanId) {
		for(TransitLine tl:schedule.getTransitLines().values()) {
			for(TransitRoute tr:tl.getRoutes().values()) {
				ArrayList<Id<Link>> links=new ArrayList<>(tr.getRoute().getLinkIds());
				for(Departure d:tr.getDepartures().values()) {
					if(d.getDepartureTime()>this.timeBeans.get(timeBeanId).getFirst() && d.getDepartureTime()<=this.timeBeans.get(timeBeanId).getSecond()) {
						for(Id<Link> linkId:links) {
							((CNLLink)network.getLinks().get(linkId)).addLinkTransitVolume(vehicles.getVehicles().get(d.getVehicleId()).getType().getPcuEquivalents());
							Double oldCap=this.totalPtCapacityOnLink.get(timeBeanId).get(linkId);
							VehicleCapacity cap=vehicles.getVehicles().get(d.getVehicleId()).getType().getCapacity();
							if(oldCap!=null) {
								this.totalPtCapacityOnLink.get(timeBeanId).put(linkId, oldCap+(cap.getSeats()+cap.getStandingRoom()));
							}else {
								this.totalPtCapacityOnLink.get(timeBeanId).put(linkId, (double) cap.getSeats()+cap.getStandingRoom());
							}
							}
					}
				}
			}
		}
		logger.info("Completed transit vehicle overlay.");
	}
	
	/**
	 * Running this method is mandatory to set up the initial population 
	 * TODO: finish implementing this function, Done???
	 * @param population
	 */
	public void populateModel(Scenario scenario, Map<String,FareCalculator> fareCalculator, MAASPackages packages) {
		this.population = scenario.getPopulation();
		this.scenario = scenario;
		SignalFlowReductionGenerator sg=new SignalFlowReductionGenerator(scenario);
		Network network = scenario.getNetwork();
		for(String s:this.timeBeans.keySet()) {
			this.networks.put(s, new CNLNetwork(network,sg));
			this.performTransitVehicleOverlay(this.networks.get(s),
					scenario.getTransitSchedule(),scenario.getTransitVehicles(),s);
			this.transitLinks.put(s,new ConcurrentHashMap<>());
			System.out.println("No of active signal link = "+sg.activeGc);
			sg.activeGc=0;
		}
		//We have to go through each person and get the corresponding transitLinks
		//This is costly, so lets do it in the update function
		this.farecalculators = fareCalculator;
		this.maasPakages = packages;
		this.ts = scenario.getTransitSchedule();
		
	}
	
	private OutputFlow singlePersonNL(Person person, LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams) {
		
		Map<String, Double> utilities = new HashMap<>();
		Map<String, Double> planProb = new HashMap<>();
		Map<String,Map<Id<Link>,Double>> linkFlow = new HashMap<>();
		Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow = new HashMap<>();
		Map<String, SimpleTranslatedPlan> trPlans = new HashMap<>();
		
		//Calculate the utility, Should we move the utility calculation part inside the simple translated plan itself? makes more sense. (April 2020)
		for(Plan plan:person.getPlans()) {
			double utility = 0;
			SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) plan.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);//extract the translated plan first
			trPlans.put(plan.toString(), trPlan);
			for(Activity ac:trPlan.getActivities()) {// for now this class is not implemented: done may 2 2020
				utility += this.calcActivityUtility(ac, this.scenario.getConfig(),person);
			}
			for(Entry<String, Map<Id<AnalyticalModelTransitRoute>, AnalyticalModelTransitRoute>> trRouteMap:trPlan.getTrroutes().entrySet()) {
				for(AnalyticalModelTransitRoute trRoute : trRouteMap.getValue().values()) {
					utility += trRoute.calcRouteUtility(params, anaParams, this.networks.get(trRouteMap.getKey()), this.farecalculators, this.timeBeans.get(trRouteMap.getKey()));
				}
				
				for(AnalyticalModelRoute route: trPlan.getRoutes().get(trRouteMap.getKey()).values()) {
					utility += route.calcRouteUtility(params, anaParams, this.networks.get(trRouteMap.getKey()), this.timeBeans.get(trRouteMap.getKey())); 
				}
			}
			utilities.put(plan.toString(),utility);
			
			// Maybe add the walk dis-utility?
			
		}
		
		//Apply the soft-max
		
		double maxUtil = Collections.max(utilities.values());
		double utilSum = 0;
		
		for(Entry<String, Double> d: utilities.entrySet()) {
			double v = Math.exp(d.getValue()-maxUtil);
			planProb.put(d.getKey(), v);
			utilSum += v;
		}
		
		for(Entry<String, Double> d: utilities.entrySet()) {
			double v = planProb.get(d.getKey())/utilSum;
			planProb.put(d.getKey(), v);
			trPlans.get(d.getKey()).setProbability(v);
		}
		
		// Collect the flow
		
		for(Entry<String, SimpleTranslatedPlan> plan:trPlans.entrySet()) {
			SimpleTranslatedPlan trPlan = plan.getValue();
			for(Entry<String, List<Id<Link>>> s: trPlan.getCarUsage().entrySet()) {				
				if(!linkFlow.containsKey(s.getKey()))linkFlow.put(s.getKey(), new HashMap<>());
				Map<Id<Link>,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(ss->ss, ss->planProb.get(plan.getKey())));
				flowMap.keySet().forEach((linkId)->linkFlow.get(s.getKey()).compute(linkId, (k,v)->(v==null)?flowMap.get(linkId):v+flowMap.get(linkId)));
			}
			
			for(Entry<String, List<TransitLink>> s:trPlan.getTransitUsage().entrySet()) {
				if(!transitLinkFlow.containsKey(s.getKey()))transitLinkFlow.put(s.getKey(), new HashMap<>());
				
				
				//This line adds the transitLinks to the class transitLinkMap. 
				s.getValue().stream().forEach((trLink)->//for each transitLinks in the transitLinkUsageMap in this plan,
				this.transitLinks.get(s.getKey())//get that timeBean's class transitLinkMap
				.compute(trLink.getTrLinkId(), (k,v)->{// check if the link is already there
							if(v==null)	{// if the link is not present in the class transit link map, i.e. the value returned is null,
								if(trLink instanceof TransitDirectLink) {// if the transit link is an instance of transitDirectLink
									((CNLTransitDirectLink)trLink).calcCapacityAndHeadway(this.timeBeans, s.getKey());// Calculate its headway and capacity
								}
								return trLink;//insert this newly seen transit link. 
								}
							return v;//else just leave it be, i.e. return the found transitLink
							}
						));
				//
				
				Map<Id<TransitLink>,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(TransitLink::getTrLinkId, ss->planProb.get(plan.getKey())));
				flowMap.keySet().forEach((trLinkId)->transitLinkFlow.get(s.getKey()).compute(trLinkId, (k,v)->(v==null)?flowMap.get(trLinkId):v+flowMap.get(trLinkId)));
			}
		}
		
		return new OutputFlow(linkFlow,transitLinkFlow);
	}
	
	//TODO: still do not take params in sub-population. We have to incorporate that 
	private OutputFlow performNetworkLoading(Population population, LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams) {
		
		List<Map<String,Map<Id<Link>, Double>>> linkVolumes=Collections.synchronizedList(new ArrayList<>());
		List<Map<String,Map<Id<TransitLink>, Double>>> linkTransitVolumes=Collections.synchronizedList(new ArrayList<>());
		
		Map<String,Map<Id<Link>,Double>> linkFlow = new HashMap<>();
		Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow = new HashMap<>();
		
		population.getPersons().values().parallelStream().forEach((person)->{
			OutputFlow flow= this.singlePersonNL(person, params, anaParams);
			linkVolumes.add(flow.linkFlow);
			linkTransitVolumes.add(flow.transitLinkFlow);
		});
		
		linkVolumes.stream().forEach((linkFlowMap)->{
			linkFlowMap.entrySet().stream().forEach((timeLinkFlowMap)->{
				if(!linkFlow.containsKey(timeLinkFlowMap.getKey())) {
					linkFlow.put(timeLinkFlowMap.getKey(), timeLinkFlowMap.getValue());
				}else {
					timeLinkFlowMap.getValue().entrySet().stream().forEach((map)->linkFlow.get(timeLinkFlowMap.getKey()).compute(map.getKey(), (k,v)->(v==null)?map.getValue():v+map.getValue()));
				}
			});
		});
		
		linkTransitVolumes.stream().forEach((linkFlowMap)->{
			linkFlowMap.entrySet().stream().forEach((timeLinkFlowMap)->{
				if(!transitLinkFlow.containsKey(timeLinkFlowMap.getKey())) {
					transitLinkFlow.put(timeLinkFlowMap.getKey(), timeLinkFlowMap.getValue());
				}else {
					timeLinkFlowMap.getValue().entrySet().stream().forEach((map)->transitLinkFlow.get(timeLinkFlowMap.getKey()).compute(map.getKey(), (k,v)->(v==null)?map.getValue():v+map.getValue()));
				}
			});
		});
		
		return new OutputFlow(linkFlow,transitLinkFlow);
	}
	
	/**
	 * 
	 * @param linkFlow the linkFlow to update
	 * @param transitLinkFlow the transitLinkFlow to update
	 * @param counter counter of the MSA iteration. Here, we assume that, the counter starts from 1. 
	 * @return if should stop msa step i.e. if model has converged
	 */
	private boolean updateVolume(Map<String,Map<Id<Link>,Double>>linkFlow, Map<String,Map<Id<TransitLink>,Double>>transitLinkFlow, int counter) {
		double squareSum=0;
		double linkAboveTol=0;
		double linkAbove1=0;
		
		Map<String,List<Tuple<AnalyticalModelLink,Double>>> linkFlowUpdates = new HashMap<>();
		Map<String,List<Tuple<TransitLink,Double>>> trLinkFlowUpdates = new HashMap<>();
		
		double error = 0;
		
		//Calculate the update  first
		for(Entry<String, Map<Id<Link>, Double>> linkFlowPerTimeBean:linkFlow.entrySet()) {
			linkFlowUpdates.put(linkFlowPerTimeBean.getKey(), new ArrayList<>());
			for(Entry<Id<Link>, Double> timeSpecificLinkFlow:linkFlowPerTimeBean.getValue().entrySet()){
				double newVolume=timeSpecificLinkFlow.getValue();
				AnalyticalModelLink link = ((AnalyticalModelLink) this.networks.get(linkFlowPerTimeBean.getKey()).getLinks().get(timeSpecificLinkFlow.getKey()));
				double oldVolume=link.getLinkCarVolume();
				double update = newVolume - oldVolume;
				linkFlowUpdates.get(linkFlowPerTimeBean.getKey()).add(new Tuple<>(link, update));
				error += update*update; 
				if(update>1)linkAbove1++;
				if(update/newVolume*100>this.tolleranceLink)linkAboveTol++;
			}
		}
		
		for(Entry<String, Map<Id<TransitLink>, Double>> linkFlowPerTimeBean:transitLinkFlow.entrySet()) {
			trLinkFlowUpdates.put(linkFlowPerTimeBean.getKey(), new ArrayList<>());
			for(Entry<Id<TransitLink>, Double> timeSpecificLinkFlow:linkFlowPerTimeBean.getValue().entrySet()){
				double newVolume=timeSpecificLinkFlow.getValue();
				TransitLink link = this.transitLinks.get(linkFlowPerTimeBean.getKey()).get(timeSpecificLinkFlow.getKey());
				double oldVolume=link.getPassangerCount();
				double update = newVolume - oldVolume;
				trLinkFlowUpdates.get(linkFlowPerTimeBean.getKey()).add(new Tuple<>(link, update));					
				error += update*update; 
				if(update>1)linkAbove1++;
				if(update/newVolume*100>this.tolleranceLink)linkAboveTol++;
			}
		}
		
		
		if(counter==1) {
			this.beta.clear();
			this.error.clear();
			this.beta.add(1.);
			this.error.add(error);//error is added in both case; but after cleaning it at counter = 1
		}else {
			this.error.add(error);//error is added in both case; but after cleaning at step 1
			if(this.error.get(counter-1)<this.error.get(counter-2)) {
				beta.add(beta.get(counter-2)+this.gammaMSA);
				this.consecutiveErrorIncrease = 0;
			}else {
				this.consecutiveErrorIncrease++;
				beta.add(beta.get(counter-2)+this.alphaMSA);
			}
		}
		
		logger.info("Error at iteration " + counter + " = "+error);
		logger.info("link above error 1 = " + linkAbove1);
		logger.info("Link above " + this.tolleranceLink + "percent error = " + linkAboveTol);
		logger.info("Consecutive sue error increase = " + this.consecutiveErrorIncrease);
		
		double counterPart=1/beta.get(counter-1);
		//counterPart=1./counter; //turn this on for normal msa
		
		
		//Calculate the flowSum, square sum and link sum in this group
		for(String timeId:linkFlowUpdates.keySet()) {
			for(Tuple<AnalyticalModelLink,Double> link:linkFlowUpdates.get(timeId)) {
				link.getFirst().addLinkCarVolume(counterPart*link.getSecond());
			}
		}
		
		for(String timeId:trLinkFlowUpdates.keySet()) {
			for(Tuple<TransitLink, Double> link:trLinkFlowUpdates.get(timeId)) {
				link.getFirst().addPassanger(counterPart*link.getSecond(),this.networks.get(timeId));
			}
		}
		squareSum = error;
		
		//Return if should stop
		if(squareSum < this.tollerance || linkAboveTol == 0 || linkAbove1== 0) {
			return true;
		}else {
			return false;
		}
	}
	
	
	
	public void performAssignment(Population population, LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams) {
		
		for(int counter = 1; counter < this.maxIter; counter++) {
			OutputFlow flow  = this.performNetworkLoading(population, params, anaParams);
			boolean shouldStop = this.updateVolume(flow.linkFlow, flow.transitLinkFlow, counter);
			if(shouldStop) {
				break;
			}
		}
	}
	
	
	/**
	 * Calculate some sort of simple activity utility
	 * @param activity
	 * @param config
	 * @return
	 */
	private double calcActivityUtility(Activity activity, Config config, Person person) {
		ScoringParameters scParam = new ScoringParameters.Builder(config.planCalcScore(), config.planCalcScore().getScoringParameters(null), config.scenario()).build();
		//First find the duration. As for now we switch off the departure time choice, The duration 
		//will only depend on the previous trip end time 
		ActivityUtilityParameters actParams = scParam.utilParams.get(activity.getType());
		double startTime = 0;
		double endTime = 24*3600;
		if(activity.getEndTime().isDefined()) endTime = activity.getEndTime().seconds();
		if(activity.getStartTime().isDefined()) startTime = activity.getStartTime().seconds();
		double duration = endTime - startTime;
		double typicalDuration = actParams.getTypicalDuration();
		double minDuration = actParams.getZeroUtilityDuration_h();
		/**
		 * This is the bare-bone activity utility from matsim book; refer to page 25, Ch:3 A Closer Look at Scoring 
		 */
		double utility = scParam.marginalUtilityOfPerforming_s*typicalDuration*Math.log(duration/minDuration);
		return utility;
	}

//----------------------getter setter------------------------
	
	public Map<String, Double> getDefaultParameters() {
		return Params;
	}

	public LinkedHashMap<String, Double> getInternalParamters() {
		return this.AnalyticalModelInternalParams;
	}


	public LinkedHashMap<String, Tuple<Double, Double>> getAnalyticalModelParamsLimit() {
		return AnalyticalModelParamsLimit;
	}
	
	
}

/**
 * This class is just an output class for assignment method
 * This will contain basically two maps
 * link flow map TimBean->(LinkId->linkFlow)
 * transit link flow map TimBean->(TransitLinkId->linkFlow)
 * @author ashraf
 *
 */
class OutputFlow{
	final Map<String,Map<Id<Link>,Double>> linkFlow;
	final Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow;
	
	public OutputFlow(Map<String,Map<Id<Link>,Double>> linkFlow, Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow) {
		this.linkFlow = linkFlow;
		this.transitLinkFlow = transitLinkFlow;
	}
	
	
	
	
}

