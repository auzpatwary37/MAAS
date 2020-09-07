package optimizerAgent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.jfree.util.Log;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitTransferLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitTransferLink;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SignalFlowReductionGenerator;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

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
 *  TODO: add a measurements based assignment function : commencing operation, maybe I will just add it to the previous commit. Ashraf May2,2020.
 *  Should be easier if we used the ScoringParameters from matsim group. 
 * 
 */
public class PersonPlanSueModel {
	
	
	//TODO: add sub-population support
	//
	
	Logger logger = Logger.getLogger(PersonPlanSueModel.class);
	
	private Population population;
	private Map<String,Tuple<Double,Double>>timeBeans;
	private Map<String,FareCalculator> farecalculators;
	private MaaSPackages maasPakages;
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
	private boolean emptyMeasurements = false;
	private int nonZeroPlanGrad = 0;
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
	public static final String PlanKeyIdentifierName = "planKey";
	private Map<Id<Person>,List<Plan>> feasibleplans = new HashMap<>();
	
	//______________________BackPropogationVariables______________________________________
	
	
	
	//We need a unique key for each plan. Let's assign one for each at the zero'th iteration
	
	// We need four incidence variables
	//1. link-plan incidence timeId-linkid-planid-times the link appeared in the plan in that time 
	// [can be separated for physical links, tranistDirectLinks, transferLinks and fareLinks]
	//2. MaaS Package to plan incidence {packageId->Plan}
	//3. Transfer Link to transferLinks and directLinks
	
	Map<String,Map<Id<Link>,Map<String,Double>>> linkPlanIncidence = new ConcurrentHashMap<>();//done
	Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkPlanIncidence = new ConcurrentHashMap<>();//done
	//timeId-maasPacakge-fareLinkid-planid-numofUsage
	Map<String,Map<String,Map<String,Map<String,Double>>>> fareLinkPlanIncidence = new ConcurrentHashMap<>();//done
	
	//should be packageId-list<planid>
	Map<String,List<String>> maasPackagePlanIncidence = new ConcurrentHashMap<>();//done
	
	//Save plan probability
	
	Map<String,Double> planProbability = new ConcurrentHashMap<>();//done
	
	//Gradient Keys
	Set<String> gradientKeys = new HashSet<>();
	
	//Gradient Variable
	Map<String,Map<Id<Link>,Map<String,Double>>> linkGradient = new ConcurrentHashMap<>();
	Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkGradient = new ConcurrentHashMap<>();
	//timeId-maasPacakge-fareLinkid-varkey-gradient
	Map<String,Map<String,Map<String,Map<String,Double>>>> fareLinkGradient = new ConcurrentHashMap<>();
	
	Map<String,Map<Id<Link>,Map<String,Double>>> linkTravelTimeGradient = new ConcurrentHashMap<>();
	Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkTravelTimeGradient = new ConcurrentHashMap<>();
	
	Map<String,Map<String,Double>> planProbabilityGradient = new ConcurrentHashMap<>();
	Map<String,Map<String,Double>> pacakgeUserGradient = new ConcurrentHashMap<>();
	
	Map<String,SimpleTranslatedPlan> plans = new HashMap<>();
	//___________________________FrontEndFunctionality____________________________________
	

	public Measurements performAssignment(Population population, LinkedHashMap<String,Double> params, Measurements originalMeasurements) {
		MaaSUtil.updateMaaSVariables(this.maasPakages, params);
		Measurements m = this.performAssignment(population, params,this.AnalyticalModelInternalParams, originalMeasurements);
		return m;
	}
	
	public SUEModelOutput performAssignment(Population population, LinkedHashMap<String,Double> params) {
		MaaSUtil.updateMaaSVariables(this.maasPakages, params);
		SUEModelOutput flow = this.performAssignment(population, params,this.AnalyticalModelInternalParams);
		return flow;
	} 
	
	private Map<Id<Person>, List<Plan>> extractFeasiblePlans(Population population) {
		for(Entry<Id<Person>, ? extends Person> p:population.getPersons().entrySet()) {
			List<Plan> plans = (List<Plan>) p.getValue().getAttributes().getAttribute(MaaSUtil.uniqueMaaSIncludedPlanAttributeName);
			this.feasibleplans.put(p.getKey(), plans);	
		}
		return feasibleplans;
	}
	
	
	//___________________________________________________________________________________
	//Constructor
	
	public PersonPlanSueModel(Map<String, Tuple<Double, Double>> timeBean,Config config) {
		this.timeBeans=timeBean;
		//this.defaultParameterInitiation(null);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			this.linkPlanIncidence.put(timeBeanId, new HashMap<>());
			this.trLinkPlanIncidence.put(timeBeanId, new HashMap<>());
			this.fareLinkPlanIncidence.put(timeBeanId, new HashMap<>());

			
			this.linkGradient.put(timeBeanId, new ConcurrentHashMap<>());
			this.trLinkGradient.put(timeBeanId, new ConcurrentHashMap<>());
			this.fareLinkGradient.put(timeBeanId, new ConcurrentHashMap<>());
			
			this.linkTravelTimeGradient.put(timeBeanId, new ConcurrentHashMap<>());
			this.trLinkTravelTimeGradient.put(timeBeanId, new ConcurrentHashMap<>());
			
			
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
		
		this.AnalyticalModelInternalParams.put(CNLSUEModel.LinkMiuName, 1.);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.ModeMiuName, 1.);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRalphaName, 0.15);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRbetaName, 4.);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferalphaName, 0.5);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferbetaName, 1.1);
		this.loadAnalyticalModelInternalPamamsLimit();
		
//		//Loads the External default Parameters
//		if(config==null) {
//			config=ConfigUtils.createConfig();
//		}
//		
//		This is not needed anymore as the parameters are now directly loaded from scoring param per subpopulation
//
//		this.Params.put(CNLSUEModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
//		this.Params.put(CNLSUEModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfDistance());
//		this.Params.put(CNLSUEModel.MarginalUtilityofMoneyName,config.planCalcScore().getMarginalUtilityOfMoney());
//		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateModeParams("car").getMonetaryDistanceRate());
//		this.Params.put(CNLSUEModel.MarginalUtilityofTravelptName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfTraveling());
//		this.Params.put(CNLSUEModel.MarginalUtilityOfDistancePtName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfDistance());
//		this.Params.put(CNLSUEModel.MarginalUtilityofWaitingName,config.planCalcScore().getMarginalUtlOfWaitingPt_utils_hr());
//		this.Params.put(CNLSUEModel.UtilityOfLineSwitchName,config.planCalcScore().getUtilityOfLineSwitch());
//		this.Params.put(CNLSUEModel.MarginalUtilityOfWalkingName, config.planCalcScore().getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
//		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostWalkName, config.planCalcScore().getOrCreateModeParams("walk").getMonetaryDistanceRate());
//		this.Params.put(CNLSUEModel.ModeConstantPtname,config.planCalcScore().getOrCreateModeParams("pt").getConstant());
//		this.Params.put(CNLSUEModel.ModeConstantCarName,config.planCalcScore().getOrCreateModeParams("car").getConstant());
//		this.Params.put(CNLSUEModel.MarginalUtilityofPerformName, config.planCalcScore().getPerforming_utils_hr());
//		this.Params.put(CNLSUEModel.CapacityMultiplierName, 1.0);
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
	public void populateModel(Scenario scenario, Map<String,FareCalculator> fareCalculator, MaaSPackages packages) {
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
		//System.out.println(this.scenario.getNetwork().getLinks().get(this.scenario.getNetwork().getLinks().keySet().toArray()[0]).getClass());
		this.farecalculators = fareCalculator;
		this.maasPakages = packages;
		this.ts = scenario.getTransitSchedule();
		this.extractFeasiblePlans(population);
	}
	

	private SUEModelOutput singlePersonNL(Person person, LinkedHashMap<String,Double> Oparams, LinkedHashMap<String,Double> anaParams, int counter) {
		
		//get the subpopulation
		String subpopulation = PopulationUtils.getSubpopulation(person);
		
		
		Map<String, Double> utilities = new HashMap<>();
		Map<String, Double> planProb = new HashMap<>();
		Map<String,Map<Id<Link>,Double>> linkFlow = new HashMap<>();
		Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow = new HashMap<>();
		Map<String,Map<String,Map<String,Double>>> fareLinkFlow = new HashMap<>();
		Map<String, SimpleTranslatedPlan> trPlans = new HashMap<>();
		
		//This should handle for the basic params per subpopulation
		long t1 = System.currentTimeMillis();
		LinkedHashMap<String,Double> params = this.handleBasicParams(Oparams, subpopulation, this.scenario.getConfig());
		t1 = System.currentTimeMillis()-t1;
		//Calculate the utility, Should we move the utility calculation part inside the simple translated plan itself? makes more sense. (April 2020)
		for(Plan plan:this.feasibleplans.get(person.getId())) {

			//Give an identifier to the plan. We will give a String identifier which will be saved as plan attribute
			SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) plan.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);//extract the translated plan first
			String planKey = trPlan.getPlanKey();
			double utility = 0;
			//Add the MaaSPackage disutility
			MaaSPackage maas = this.maasPakages.getMassPackages().get(plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName));
			
			Map<String,Object> additionalInfo = new HashMap<>();
			additionalInfo.put(MaaSUtil.CurrentSelectedMaaSPackageAttributeName, maas);
			if(maas!=null)
				utility-=params.get(CNLSUEModel.MarginalUtilityofMoneyName)*maas.getPackageCost();
			else{
				logger.debug("Debug");
			}
						
			trPlans.put(planKey, trPlan);
//			Activity f = trPlan.getActivities().get(0);
//			Activity l = trPlan.getActivities().get(trPlan.getActivities().size()-1);
//			for(Activity ac:trPlan.getActivities()) {// for now this class is not implemented: done may 2 2020
//				utility += this.calcActivityUtility(ac, this.scenario.getConfig(),subpopulation,f,l);
//			}
			
			if(Double.isNaN(utility)||!Double.isFinite(utility))
				logger.debug("utility is nan or infinite. Debug!!!");
			
			for(Entry<String, Map<Id<AnalyticalModelTransitRoute>, AnalyticalModelTransitRoute>> trRouteMap:trPlan.getTrroutes().entrySet()) {
				for(AnalyticalModelTransitRoute trRoute : trRouteMap.getValue().values()) {
					utility += trRoute.calcRouteUtility(params, anaParams, this.networks.get(trRouteMap.getKey()),this.transitLinks.get(trRouteMap.getKey()), this.farecalculators,additionalInfo, this.timeBeans.get(trRouteMap.getKey()));
				}
				
				if(Double.isNaN(utility)||!Double.isFinite(utility))
					logger.debug("utility is nan or infinite. Debug!!!");
			}
			
			for(Entry<String, Map<Id<AnalyticalModelRoute>, AnalyticalModelRoute>> trRouteMap:trPlan.getRoutes().entrySet()) {
				
				for(AnalyticalModelRoute route: trPlan.getRoutes().get(trRouteMap.getKey()).values()) {
					utility += route.calcRouteUtility(params, anaParams, this.networks.get(trRouteMap.getKey()), this.timeBeans.get(trRouteMap.getKey())); 
				}
				
				if(Double.isNaN(utility)||!Double.isFinite(utility))
					logger.debug("utility is nan or infinite. Debug!!!");
			}
			
			utilities.put(planKey,utility);
			
			
		}
		if(utilities.size()!=this.feasibleplans.get(person.getId()).size()) {
			throw new IllegalArgumentException("Not same dimension. Please check");
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
			if(Double.isNaN(v))
				logger.debug("Probability is nan. Debug!!!");
			this.planProbability.put(d.getKey(), v);
		}
		
		// Collect the flow
		
		for(Entry<String, SimpleTranslatedPlan> plan:trPlans.entrySet()) {
			SimpleTranslatedPlan trPlan = plan.getValue();
			for(Entry<String, List<Id<Link>>> s: trPlan.getCarUsage().entrySet()) {	
				
				if(!linkFlow.containsKey(s.getKey()))linkFlow.put(s.getKey(), new HashMap<>());
				Map<Id<Link>,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(ss->ss, ss->planProb.get(plan.getKey()),(v1,v2)->(v1+v2)));
				flowMap.keySet().forEach((linkId)->linkFlow.get(s.getKey()).compute(linkId, (k,v)->(v==null)?flowMap.get(linkId):v+flowMap.get(linkId)));
			}
			
			for(Entry<String, List<TransitLink>> s:trPlan.getTransitUsage().entrySet()) {
				if(!transitLinkFlow.containsKey(s.getKey()))transitLinkFlow.put(s.getKey(), new HashMap<>());
				
				
				//This line adds the transitLinks to the class transitLinkMap. 
				s.getValue().stream().forEach((trLink)->{//for each transitLinks in the transitLinkUsageMap in this plan,
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
						);
				
				});
				//
				
				Map<Id<TransitLink>,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(TransitLink::getTrLinkId, ss->planProb.get(plan.getKey()),(v1,v2)->(v1+v2)));
				flowMap.keySet().forEach((trLinkId)->transitLinkFlow.get(s.getKey()).compute(trLinkId, (k,v)->(v==null)?flowMap.get(trLinkId):v+flowMap.get(trLinkId)));
			}
			
			for(Entry<String, List<FareLink>> s: trPlan.getFareLinkUsage().entrySet()) {				
				if(!fareLinkFlow.containsKey(s.getKey()))fareLinkFlow.put(s.getKey(), new HashMap<>());
				String packageId = trPlan.getMaasPacakgeId();
				if(!fareLinkFlow.get(s.getKey()).containsKey(packageId)) {
					fareLinkFlow.get(s.getKey()).put(packageId, new HashMap<>());
				}
				
				Map<String,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(ss->ss.toString(), ss->planProb.get(plan.getKey()),(v1,v2)->(v1+v2)));
				flowMap.keySet().forEach((fareLink)->fareLinkFlow.get(s.getKey()).get(packageId).compute(fareLink, (k,v)->(v==null)?flowMap.get(fareLink):v+flowMap.get(fareLink)));
			}
			
		}
		if(linkFlow==null||transitLinkFlow==null||fareLinkFlow==null) {
			logger.debug("flows are null");
		}
		SUEModelOutput out = new SUEModelOutput(linkFlow,transitLinkFlow, null, null, null);
		out.setMaaSSpecificFareLinkFlow(fareLinkFlow);
		return out;
	}
	
	private void createIncidenceMaps(Population population) {
		for(Person p:population.getPersons().values()) {
			int planNo = 0;
			if(!(PopulationUtils.getSubpopulation(p)).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				for(Plan plan:this.feasibleplans.get(p.getId())) {
					
					MaaSPackage maas = this.maasPakages.getMassPackages().get(plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName));
					SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) plan.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);
					String planKey = new String(p.getId().toString()+"_^_"+planNo);
					
					
					//if(trPlan==null) {
						//System.out.println("I am currently just applying a patch. Please fix it asap.");
						trPlan = new SimpleTranslatedPlan(timeBeans, plan, scenario);
						
						plan.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, trPlan);
					//}
					//System.out.println(this.scenario.getNetwork().getLinks().get(this.scenario.getNetwork().getLinks().keySet().toArray()[0]).getClass());	
					trPlan.setPlanKey(planKey);
					this.plans.put(planKey, trPlan);
					planNo++;
					if(maas!=null) {trPlan.setMaasPacakgeId(maas.getId());}
					else {trPlan.setMaasPacakgeId(MaaSUtil.nullMaaSPacakgeKeyName);}
					if(!this.maasPackagePlanIncidence.containsKey(trPlan.getMaasPacakgeId())) {
						this.maasPackagePlanIncidence.put(trPlan.getMaasPacakgeId(), new ArrayList<>());	
					}
					this.maasPackagePlanIncidence.get(trPlan.getMaasPacakgeId()).add(planKey);
					
					for(Entry<String, List<Id<Link>>> s: trPlan.getCarUsage().entrySet()) {	
						for(Id<Link> link:s.getValue()){
							if(!this.linkPlanIncidence.get(s.getKey()).containsKey(link))this.linkPlanIncidence.get(s.getKey()).put(link, new HashMap<>());
							Map<String, Double> incidenceMap = this.linkPlanIncidence.get(s.getKey()).get(link);
							incidenceMap.compute(planKey, (k,v)->(v==null)?1:v+1);
						};
					}
					
					for(Entry<String, List<TransitLink>> s:trPlan.getTransitUsage().entrySet()) {
						for(TransitLink trLink:s.getValue()){
							if(!this.trLinkPlanIncidence.get(s.getKey()).containsKey(trLink.getTrLinkId())) {
								this.trLinkPlanIncidence.get(s.getKey()).put(trLink.getTrLinkId(), new HashMap<>());
							}
							Map<String, Double> incidenceMap = this.trLinkPlanIncidence.get(s.getKey()).get(trLink.getTrLinkId());
							incidenceMap.compute(planKey, (k,v)->(v==null)?1:v+1);
							this.transitLinks.get(s.getKey()).put(trLink.getTrLinkId(), trLink);
						}
					}
					
					for(Entry<String, List<FareLink>> s: trPlan.getFareLinkUsage().entrySet()) {
						if(trPlan.getMaasPacakgeId().equals(MaaSUtil.nullMaaSPacakgeKeyName))
							logger.debug("NoMass");
						if(!this.fareLinkPlanIncidence.get(s.getKey()).containsKey(trPlan.getMaasPacakgeId())) {
							this.fareLinkPlanIncidence.get(s.getKey()).put(trPlan.getMaasPacakgeId(), new HashMap<>());
						}
						
						for(FareLink fl:s.getValue()){
							if(!this.fareLinkPlanIncidence.get(s.getKey()).get(trPlan.getMaasPacakgeId()).containsKey(fl.toString())) {
								this.fareLinkPlanIncidence.get(s.getKey()).get(trPlan.getMaasPacakgeId()).put(fl.toString(), new HashMap<>());
							}
							Map<String, Double> incidenceMap = this.fareLinkPlanIncidence.get(s.getKey()).get(trPlan.getMaasPacakgeId()).get(fl.toString());
							incidenceMap.compute(planKey, (k,v)->(v==null)?1:v+1);
						}
						
					}
				}
			}
		}
	}
	
	//TODO: still do not take params in sub-population. We have to incorporate that 
	private SUEModelOutput performNetworkLoading(Population population, LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams, int counter) {		
		
		List<Map<String,Map<Id<Link>, Double>>> linkVolumes=Collections.synchronizedList(new ArrayList<>());
		List<Map<String,Map<Id<TransitLink>, Double>>> linkTransitVolumes=Collections.synchronizedList(new ArrayList<>());
		List<Map<String,Map<String,Map<String,Double>>>> fareLinkFlows=Collections.synchronizedList(new ArrayList<>());
		
		Map<String,Map<Id<Link>,Double>> linkFlow = new HashMap<>();
		Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow = new HashMap<>();
		Map<String,Map<String,Map<String,Double>>> fareLinkFlow = new HashMap<>();
		
		
		population.getPersons().values().parallelStream().forEach((person)->{
			if(!PopulationUtils.getSubpopulation(person).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
			SUEModelOutput flow= this.singlePersonNL(person, params, anaParams,counter);
			linkVolumes.add(flow.getLinkVolume());
			linkTransitVolumes.add(flow.getLinkTransitVolume());
			fareLinkFlows.add(flow.getMaaSSpecificFareLinkFlow());
			}
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
		
		fareLinkFlows.stream().forEach((linkFlowMap)->{
			linkFlowMap.entrySet().stream().forEach((timeLinkFlowMap)->{
				if(!fareLinkFlow.containsKey(timeLinkFlowMap.getKey()))fareLinkFlow.put(timeLinkFlowMap.getKey(), new HashMap<>());
				timeLinkFlowMap.getValue().entrySet().stream().forEach(maasLinkFlowMap->{
				//	if(!fareLinkFlow.get(timeLinkFlowMap.getKey()).containsKey(maasLinkFlowMap.getKey()))fareLinkFlow.get(timeLinkFlowMap.getKey()).put(maasLinkFlowMap.getKey(), new HashMap<>());
					if(!fareLinkFlow.get(timeLinkFlowMap.getKey()).containsKey(maasLinkFlowMap.getKey())) {
						fareLinkFlow.get(timeLinkFlowMap.getKey()).put(maasLinkFlowMap.getKey(),maasLinkFlowMap.getValue());
					}else {
						maasLinkFlowMap.getValue().entrySet().stream().forEach((map)->fareLinkFlow.get(timeLinkFlowMap.getKey()).get(maasLinkFlowMap.getKey()).compute(map.getKey(), (k,v)->(v==null)?map.getValue():v+map.getValue()));
					}
				});
			});
		});
		
		SUEModelOutput out = new SUEModelOutput(linkFlow,transitLinkFlow, null, null, null);
		out.setMaaSSpecificFareLinkFlow(fareLinkFlow);
		
		return out;
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
	
	
	
	private SUEModelOutput performAssignment(Population population, LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams) {
		SUEModelOutput flow = null;
		for(int counter = 1; counter < this.maxIter; counter++) {
			if(counter == 1) {
				this.createIncidenceMaps(population);
				}
			long t1 = System.currentTimeMillis();
			flow  = this.performNetworkLoading(population, params, anaParams,counter);//link incidences are ready after this step
			logger.info("Finished network loading. Time required = "+(System.currentTimeMillis()-t1)+"ms.");
			t1 = System.currentTimeMillis();
			this.caclulateGradient(population, counter, params, anaParams);
			logger.info("Finished calculating gradient. Time required = "+(System.currentTimeMillis()-t1)+"ms.");
			t1 = System.currentTimeMillis();
			boolean shouldStop = this.updateVolume(flow.getLinkVolume(), flow.getLinkTransitVolume(), counter);//transit link dependencies are ready after this step
			logger.info("Finished flow update. Time required = "+(System.currentTimeMillis()-t1)+"ms.");
			if(shouldStop) {
				break;
			}
		}
		flow.setMaaSPackageUsage(this.calculateMaaSPackageUsage());
		return flow;
	}
	
	
	private Measurements performAssignment(Population population, LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams, Measurements originalMeasurements) {
		Measurements measurementsToUpdate = null;
		SUEModelOutput flow = this.performAssignment(population, params, anaParams);
		
		if(originalMeasurements==null) {//for now we just add the fare link and link volume for a null measurements
			this.emptyMeasurements=true;
			measurementsToUpdate=Measurements.createMeasurements(this.timeBeans);
			//create and insert link volume measurement
			for(Entry<String, Map<Id<Link>, Double>> timeFlow:flow.getLinkVolume().entrySet()) {
				for(Entry<Id<Link>, Double> link:timeFlow.getValue().entrySet()) {
					Id<Measurement> mid = Id.create(link.getKey().toString(), Measurement.class);
					if(measurementsToUpdate.getMeasurements().containsKey(mid)) {
						measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
					}else {
						measurementsToUpdate.createAnadAddMeasurement(mid.toString(), MeasurementType.linkVolume);
						List<Id<Link>> links = new ArrayList<>();
						links.add(link.getKey());
						measurementsToUpdate.getMeasurements().get(mid).setAttribute(Measurement.linkListAttributeName, links);
						measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
					}
				}
			}
			
			for(Entry<String, Map<String, Double>> timeFlow:flow.getFareLinkVolume().entrySet()) {
				for(Entry<String, Double> link:timeFlow.getValue().entrySet()) {
					Id<Measurement> mid = Id.create(link.getKey().toString(), Measurement.class);
					if(measurementsToUpdate.getMeasurements().containsKey(mid)) {
						measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
					}else {
						measurementsToUpdate.createAnadAddMeasurement(mid.toString(), MeasurementType.fareLinkVolume);
						measurementsToUpdate.getMeasurements().get(mid).setAttribute(Measurement.FareLinkAttributeName, new FareLink(link.getKey()));
						measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
					}
				}
			}
		}else {
			measurementsToUpdate=originalMeasurements.clone();
			measurementsToUpdate.resetMeasurements();
			measurementsToUpdate.updateMeasurements(flow, null, null);
		}
		return measurementsToUpdate;
	}
	
	/**
	 * Calculate some sort of simple activity utility
	 * @param activity
	 * @param config
	 * @return
	 */
	private double calcActivityUtility(Activity activity, Config config, String subPopulation, Activity firstActivity, Activity lastActivity) {
		ScoringParameters scParam = new ScoringParameters.Builder(config.planCalcScore(), config.planCalcScore().getScoringParameters(subPopulation), config.scenario()).build();
		//First find the duration. As for now we switch off the departure time choice, The duration 
		//will only depend on the previous trip end time 
		ActivityUtilityParameters actParams = scParam.utilParams.get(activity.getType());
		double startTime = 0;
		double endTime = 24*3600;
		if(lastActivity.getEndTime().isDefined())startTime = lastActivity.getEndTime().seconds();
		if(firstActivity.getEndTime().isDefined())endTime = firstActivity.getEndTime().seconds();
		if(activity.getEndTime().isDefined()) endTime = activity.getEndTime().seconds();
		if(activity.getStartTime().isDefined()) startTime = activity.getStartTime().seconds();
		double duration = endTime - startTime;
		if(duration<0)duration=0;
		double typicalDuration = actParams.getTypicalDuration();
		double minDuration = actParams.getZeroUtilityDuration_h();
		/**
		 * This is the bare-bone activity utility from matsim book; refer to page 25, Ch:3 A Closer Look at Scoring 
		 */
		double utility = scParam.marginalUtilityOfPerforming_s*typicalDuration*Math.log((duration+1)/(minDuration*3600));
		if(!Double.isFinite(utility)||Double.isNaN(utility))
			logger.debug("Utility is nan or infinity. Debug!!!");
		utility = 0;// Change this
		return utility;
		
		
	}

	
	private LinkedHashMap<String,Double> handleBasicParams(LinkedHashMap<String,Double> params, String subPopulation, Config config){
		LinkedHashMap<String,Double> newParams = new LinkedHashMap<>();
		// Handle the original params first
		for(String s:params.keySet()) {
			if(subPopulation!=null && (s.contains(subPopulation)||s.contains("All"))) {
				newParams.put(s.split(" ")[1],params.get(s));
			}else if (subPopulation == null) {
				newParams.put(s, params.get(s));
			}
		}
		ScoringParameters scParam = new ScoringParameters.Builder(config.planCalcScore(), config.planCalcScore().getScoringParameters(subPopulation), config.scenario()).build();
		
		newParams.compute(CNLSUEModel.MarginalUtilityofTravelCarName,(k,v)->v==null?scParam.modeParams.get("car").marginalUtilityOfTraveling_s*3600:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofDistanceCarName, (k,v)->v==null?scParam.modeParams.get("car").marginalUtilityOfDistance_m:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofMoneyName, (k,v)->v==null?scParam.marginalUtilityOfMoney:v);
		newParams.compute(CNLSUEModel.DistanceBasedMoneyCostCarName, (k,v)->v==null?scParam.modeParams.get("car").monetaryDistanceCostRate:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofTravelptName, (k,v)->v==null?scParam.modeParams.get("pt").marginalUtilityOfTraveling_s*3600:v);
		newParams.compute(CNLSUEModel.MarginalUtilityOfDistancePtName, (k,v)->v==null?scParam.modeParams.get("pt").marginalUtilityOfDistance_m:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofWaitingName, (k,v)->v==null?scParam.marginalUtilityOfWaitingPt_s*3600:v);
		newParams.compute(CNLSUEModel.UtilityOfLineSwitchName, (k,v)->v==null?scParam.utilityOfLineSwitch:v);
		newParams.compute(CNLSUEModel.MarginalUtilityOfWalkingName, (k,v)->v==null?scParam.modeParams.get("walk").marginalUtilityOfTraveling_s*3600:v);
		newParams.compute(CNLSUEModel.DistanceBasedMoneyCostWalkName, (k,v)->v==null?scParam.modeParams.get("walk").monetaryDistanceCostRate:v);
		newParams.compute(CNLSUEModel.ModeConstantCarName, (k,v)->v==null?scParam.modeParams.get("car").constant:v);
		newParams.compute(CNLSUEModel.ModeConstantPtname, (k,v)->v==null?scParam.modeParams.get("pt").constant:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofPerformName, (k,v)->v==null?scParam.marginalUtilityOfPerforming_s*3600:v);
		
		newParams.compute(CNLSUEModel.CapacityMultiplierName, (k,v)->v==null?config.qsim().getFlowCapFactor():v);
		
		return newParams;
	}
	
	/**
	 * This function will initialize all gradients with zero
	 * @param Oparams
	 * Be very careful while using the function as it uses all the incidences, which are not ready until the first iteration. The updating can be avoided though.
	 * 
	 * TODO: Should I use ConcurrentHashMap instead of HashMap?
	 * TODO: Should I use parallelStream instead of Stream?
	 */
	public void initializeGradients(LinkedHashMap<String,Double> Oparams) {
		Map<String,Double> zeroGrad = new HashMap<>();
		Oparams.keySet().forEach(k->{
			if(k.contains(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript)||k.contains(MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript)){
				this.gradientKeys.add(k);
				zeroGrad.put(k, 0.);
			}
		});
		
		for(String timeId:this.timeBeans.keySet()) {
			this.linkGradient.get(timeId).putAll(this.linkPlanIncidence.get(timeId).keySet().stream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
			this.linkTravelTimeGradient.get(timeId).putAll(this.linkPlanIncidence.get(timeId).keySet().stream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
			this.trLinkGradient.get(timeId).putAll(this.trLinkPlanIncidence.get(timeId).keySet().stream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
			this.trLinkTravelTimeGradient.get(timeId).putAll(this.trLinkPlanIncidence.get(timeId).keySet().stream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
			for(String packageId:this.maasPackagePlanIncidence.keySet()) {
				this.fareLinkGradient.get(timeId).put(packageId, new HashMap<>());
				this.fareLinkGradient.get(timeId).get(packageId).putAll(this.fareLinkPlanIncidence.get(timeId).get(packageId).keySet().stream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
			}
			
		}
	
		for(String planKey:this.planProbability.keySet()) {
			this.planProbabilityGradient.put(planKey, new HashMap<>(zeroGrad));
		}
		
		logger.info("Finished initializing gradients");
	}
	
	/**
	 * This is a huge step forward. This class can calculate gradient using backpropagation
	 * Still not checked
	 * @param population
	 * @param counter
	 * @param Oparams
	 * @param anaParam
	 */
	public void caclulateGradient(Population population, int counter, LinkedHashMap<String,Double> Oparams, LinkedHashMap<String,Double>anaParam) {
		this.nonZeroPlanGrad=0;
		
		if(counter == 1) {
			this.initializeGradients(Oparams);
		}else {
			//Calculate the travel time gradients
			for(Entry<String, Map<Id<Link>, Map<String, Double>>> timeMap:this.linkTravelTimeGradient.entrySet()) {
				//for(Entry<Id<Link>,Map<String,Double>> linkGradientMap:timeMap.getValue().entrySet()) {
				timeMap.getValue().entrySet().parallelStream().forEach(linkGradientMap->{		
					CNLLink link = (CNLLink) this.networks.get(timeMap.getKey()).getLinks().get(linkGradientMap.getKey());
					if(link.getAllowedModes().contains("train"))return;
					for(Entry<String, Double> var:linkGradientMap.getValue().entrySet()) {
						double flow = link.getLinkCarVolume()+link.getLinkTransitVolume();
						double t_0 = link.getLength()/link.getFreespeed();//should be in sec
						double cap = link.getCapacity()*(this.timeBeans.get(timeMap.getKey()).getSecond()-this.timeBeans.get(timeMap.getKey()).getFirst())/3600;
						double beta = anaParam.get(PersonPlanSueModel.BPRbetaName);
						double grad = anaParam.get(PersonPlanSueModel.BPRalphaName)*beta*t_0/Math.pow(cap, beta)*Math.pow(flow,beta-1)*this.linkGradient.get(timeMap.getKey()).get(link.getId()).get(var.getKey());
						this.linkTravelTimeGradient.get(timeMap.getKey()).get(link.getId()).put(var.getKey(),grad);
					}
				});
			}
			
			this.trLinkTravelTimeGradient.entrySet().parallelStream().forEach(timeMap->{
//			for(Entry<String, Map<Id<TransitLink>, Map<String, Double>>> timeMap:this.trLinkTravelTimeGradient.entrySet()) {
				//for(Entry<Id<TransitLink>,Map<String,Double>> linkGradientMap:timeMap.getValue().entrySet()) {
				timeMap.getValue().entrySet().stream().forEach(linkGradientMap->{	
					TransitLink link = this.transitLinks.get(timeMap.getKey()).get(linkGradientMap.getKey());
					for(Entry<String, Double> var:linkGradientMap.getValue().entrySet()) {
						
						if(link instanceof TransitDirectLink) {
							CNLTransitDirectLink dlink = (CNLTransitDirectLink)link;
							double grad = 0;
							for(Id<Link> linkId:dlink.getLinkList()) {
								String timeId = timeMap.getKey();
								if(this.linkTravelTimeGradient.get(timeMap.getKey()).get(linkId)==null) {//As we have used the link plan incidence to loop, there might be some link not used by any plan.
									//For these links, no matter what the decision variables are, the flow will not change (flows are from transit vehicle flow only). So, for these links, we can assume the gradient
									//to be zero.
									logger.debug("Dead link here. Putting gradient = 0.");
									
								}else {
									grad+=this.linkTravelTimeGradient.get(timeMap.getKey()).get(linkId).get(var.getKey());
								}
								
							}
							if(Double.isNaN(grad))
								logger.debug("Debug point. Gradient is NAN");
							this.trLinkTravelTimeGradient.get(timeMap.getKey()).get(dlink.getTrLinkId()).put(var.getKey(), grad);
						}else if(link instanceof TransitTransferLink){
							CNLTransitTransferLink transferLink = (CNLTransitTransferLink)link;
							CNLTransitDirectLink dlink = transferLink.getNextdLink();
							double grad = 0;
							if(dlink != null) {//For an alighting link only (the last transfer leg) the next dlink is null. 
								//The gradient for this link's travel time is zero as the waiting time for a alighting only link is always zero.
								CNLLink plink = (CNLLink) this.networks.get(timeMap.getKey()).getLinks().get(transferLink.getStartingLinkId());
								double headway = dlink.getHeadway();
								double cap = dlink.getCapacity();
								double freq = dlink.getFrequency();
								double beta = anaParam.get(PersonPlanSueModel.TransferbetaName);
								double passengerTobeBorded = transferLink.getPassangerCount();//This should not be necessary
								double passengerOnBord = plink.getTransitPassengerVolume(dlink.getLineId()+"_"+dlink.getRouteId());
								//double volume = passengerTobeBorded+passengerOnBord;
								double volume = passengerOnBord;
								double grad1 = beta*headway/Math.pow(cap*freq, beta)*Math.pow(volume, beta-1);//if both the second and first term is 
								if(Double.isInfinite(grad1)||Double.isNaN(grad1))grad1 = 0;
								double grad2 = this.trLinkGradient.get(timeMap.getKey()).get(transferLink.getTrLinkId()).get(var.getKey());
								
								for(Id<TransitLink> l:transferLink.getIncidentLinkIds()){
									grad2+=this.trLinkGradient.get(timeMap.getKey()).get(l).get(var.getKey());
								}
								if(grad2!=0) {
									logger.debug("Debug here");
								}
								grad = grad1*grad2;
							}else {
								grad = 0;
							}
							if(Double.isNaN(grad))
								logger.debug("Debug point. Gradient is NAN");
							this.trLinkTravelTimeGradient.get(timeMap.getKey()).get(transferLink.getTrLinkId()).put(var.getKey(),grad);
						}
					}
				});
			});
			
		}
		//Now we calculate the plan probability gradients
		double d = 0;
		for(double dd:this.planProbability.values()) {
			if(Double.isNaN(dd))d++;
		}
		Set<String> planIds = new HashSet<>();
		for(Entry<String, Map<Id<Link>, Map<String, Double>>> ddd:this.linkPlanIncidence.entrySet()) {
			for(Entry<Id<Link>, Map<String, Double>> dd:ddd.getValue().entrySet()) {
				planIds.addAll(dd.getValue().keySet());
			}
		}
		
		Map<String,Integer> subPopCount = new HashMap<>();
		double totalPlan = 0;
		for(Person p:population.getPersons().values()) {
			subPopCount.compute(PopulationUtils.getSubpopulation(p),(k,v)->v==null?p.getPlans().size():v+p.getPlans().size());
			totalPlan+=p.getPlans().size();
		}
		logger.debug("Total nan plan probability = "+d);
		logger.debug("Total plans= "+this.plans.size());
		logger.debug("Total plans= "+totalPlan);
		
		
		population.getPersons().entrySet().parallelStream().forEach(p->{
			//get the subpopulation
			String subpopulation = PopulationUtils.getSubpopulation(p.getValue());
			if(subpopulation.equals(MaaSUtil.MaaSOperatorAgentSubPopulationName))return;
			LinkedHashMap<String,Double> params = this.handleBasicParams(Oparams, subpopulation, this.scenario.getConfig());
			Map<String,Map<String,Double>> utilityGradient = new HashMap<>();
			Map<String,Double> sumTerm = this.gradientKeys.stream().collect(Collectors.toMap(k->k, k->0.));
			for(Plan plan:this.feasibleplans.get(p.getValue().getId())) {
				for(String var:this.gradientKeys) {
					SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) plan.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);
					//extract the translated plan first
					
					String planKey = trPlan.getPlanKey();
					double planGradient = 0;
					
					if(MaaSUtil.ifMaaSPackageCostVariableDetails(var) && trPlan.getMaasPacakgeId().equals(MaaSUtil.retrievePackageId(var))) {
						planGradient-=params.get(CNLSUEModel.MarginalUtilityofMoneyName);//This should be minus not plus?
					}
					
					for(Entry<String, Map<Id<AnalyticalModelTransitRoute>, AnalyticalModelTransitRoute>> trRouteMap:trPlan.getTrroutes().entrySet()) {
						
						//calculate the auto route utility gradient first
						for(AnalyticalModelRoute route: trPlan.getRoutes().get(trRouteMap.getKey()).values()) {
							double routeGradient = 0;
							for(Id<Link>linkId: route.getLinkIds()) {
								routeGradient+=this.linkTravelTimeGradient.get(trRouteMap.getKey()).get(linkId).get(var);
							}
							routeGradient*=(params.get(CNLSUEModel.MarginalUtilityofTravelCarName)/3600.-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.);
							if(Double.isNaN(routeGradient))
								logger.debug("Debug point. Gradient is NAN");
							planGradient+=routeGradient;
						}
						if(Double.isNaN(planGradient))
							logger.debug("Debug point. Gradient is NAN..");
						
						for(AnalyticalModelTransitRoute trRoute : trRouteMap.getValue().values()) {
							double routeGradient = 0;
							double routeGradientDlink = 0;
							double routeGradientTRLink = 0;
							if(MaaSUtil.ifFareLinkVariableDetails(var)) {
								String fl = MaaSUtil.retrieveFareLink(var);
								if(trRoute.getFareLinks().contains(new FareLink(fl)))
									routeGradient-=params.get(CNLSUEModel.MarginalUtilityofMoneyName);//Should It be minus
							}
							for(TransitDirectLink dlink:trRoute.getTransitDirectLinks()) {
								routeGradientDlink += this.trLinkTravelTimeGradient.get(trRouteMap.getKey()).get(dlink.getTrLinkId()).get(var);
							}
							routeGradientDlink*=params.get(CNLSUEModel.MarginalUtilityofTravelptName)/3600.-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.;
							for(TransitTransferLink trlink:trRoute.getTransitTransferLinks()) {
								routeGradientTRLink += this.trLinkTravelTimeGradient.get(trRouteMap.getKey()).get(trlink.getTrLinkId()).get(var);
							}
							routeGradientTRLink*=params.get(CNLSUEModel.MarginalUtilityofWaitingName)/3600.-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.;
							double grad = routeGradient+routeGradientDlink+routeGradientTRLink;
							if(counter==1 && grad!=0) {
								logger.debug("Bug Here");
							}
							if(Double.isNaN(grad))
								logger.debug("Debug point. Gradient is NAN");
							
							planGradient+=grad;
							if(Double.isNaN(planGradient))
								logger.debug("Debug point. Gradient is NAN..");
						}
					}
					
					if(!utilityGradient.containsKey(planKey))utilityGradient.put(planKey, new HashMap<>());
					utilityGradient.get(planKey).put(var, planGradient);
					double planProb = this.planProbability.get(planKey);
					sumTerm.compute(var, (k,v)->v=v+planProb*utilityGradient.get(planKey).get(var));
				}
			}
			for(Plan plan:this.feasibleplans.get(p.getValue().getId())) {
				for(String var:this.gradientKeys) {
					SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) plan.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);
					//extract the translated plan first
					
					String planKey = trPlan.getPlanKey();
					double planProb = this.planProbability.get(planKey);
					double planGradient = planProb*(utilityGradient.get(planKey).get(var)-sumTerm.get(var));
					if(planGradient!=0)
						{
						this.nonZeroPlanGrad++;
						}
					if(Double.isNaN(planGradient))
						logger.debug("Debug point. Gradient is NAN");
					
					this.planProbabilityGradient.get(planKey).put(var, planGradient);
					}
				}
		});
		
		logger.info("Non zero plan probability gradient = "+this.nonZeroPlanGrad);
		
		//finally the link volume and MaaSPackage usage gradient update
		
		
		for(Entry<String, Map<Id<Link>, Map<String, Double>>> timeMap:this.linkPlanIncidence.entrySet()) {
			timeMap.getValue().entrySet().parallelStream().forEach(linkId->{
				for(String var:this.gradientKeys) {
					double grad = 0;
					for(Entry<String, Double> planInc:linkId.getValue().entrySet()) {
						if(this.planProbabilityGradient.get(planInc.getKey())==null) {
							logger.debug("Debug Here");
							System.out.println("link = "+linkId.getKey());
							System.out.println("planId = "+planInc.getKey());
							System.out.println("is the key present? "+ this.planProbabilityGradient.containsKey(planInc.getKey()));
							System.out.println("is the key present? "+this.planProbability.containsKey(planInc.getKey()));
							
						}
						grad+=this.planProbabilityGradient.get(planInc.getKey()).get(var)*planInc.getValue();
					}
					this.linkGradient.get(timeMap.getKey()).get(linkId.getKey()).put(var, grad);
				}
			});
		}
		
		for(Entry<String, Map<Id<TransitLink>, Map<String, Double>>> timeMap:this.trLinkPlanIncidence.entrySet()) {
			timeMap.getValue().entrySet().parallelStream().forEach(linkId->{
				for(String var:this.gradientKeys) {
					double grad = 0;
					for(Entry<String, Double> planInc:linkId.getValue().entrySet()) {
						grad+=this.planProbabilityGradient.get(planInc.getKey()).get(var)*planInc.getValue();
					}
					this.trLinkGradient.get(timeMap.getKey()).get(linkId.getKey()).put(var, grad);
				}
			});
		}
		
		for(Entry<String, Map<String, Map<String, Map<String, Double>>>> timeMap:this.fareLinkPlanIncidence.entrySet()) {
			for(Entry<String, Map<String, Map<String, Double>>> maasMap:timeMap.getValue().entrySet()) {
				maasMap.getValue().entrySet().parallelStream().forEach(linkId->{
					for(String var:this.gradientKeys) {
						double grad = 0;
						for(Entry<String, Double> planInc:linkId.getValue().entrySet()) {
							grad+=this.planProbabilityGradient.get(planInc.getKey()).get(var)*planInc.getValue();
						}
						if(this.fareLinkGradient.get(timeMap.getKey()).get(maasMap.getKey())==null) {
							String timeId = timeMap.getKey();
							String maasKey = maasMap.getKey();
							logger.debug(timeId+" and "+maasKey+" is not present in fareLinkGradient.");
						}
						this.fareLinkGradient.get(timeMap.getKey()).get(maasMap.getKey()).get(linkId.getKey()).put(var, grad);
					}
				});
			}
		}
		
		for(Entry<String, List<String>> packageIncidence:this.maasPackagePlanIncidence.entrySet()) {
			for(String var:this.gradientKeys) {
				double grad = 0;
				for(String planId:packageIncidence.getValue()) {
					grad+=this.planProbabilityGradient.get(planId).get(var);
				}
				if(!this.pacakgeUserGradient.containsKey(packageIncidence.getKey()))this.pacakgeUserGradient.put(packageIncidence.getKey(), new ConcurrentHashMap<>());
				this.pacakgeUserGradient.get(packageIncidence.getKey()).put(var, grad);
			}
		}
		logger.info("Finished Gradient Calculation.");
	}
	
	private Map<String,Double> calculateMaaSPackageUsage() {
		Map<String,Double>packageUsage = new HashMap<>();
		for(Entry<String, List<String>> packageDetails:this.maasPackagePlanIncidence.entrySet()) {
			double volume = 0;
			for(String plan:packageDetails.getValue())volume+=this.planProbability.get(plan);
			packageUsage.put(packageDetails.getKey(), volume);
		}
		return packageUsage;
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

	public Map<String, Map<Id<Link>, Map<String, Double>>> getLinkGradient() {
		return linkGradient;
	}

	public Map<String, Map<Id<TransitLink>, Map<String, Double>>> getTrLinkGradient() {
		return trLinkGradient;
	}

	public Map<String, Map<Id<Link>, Map<String, Double>>> getLinkTravelTimeGradient() {
		return linkTravelTimeGradient;
	}

	public Map<String, Map<Id<TransitLink>, Map<String, Double>>> getTrLinkTravelTimeGradient() {
		return trLinkTravelTimeGradient;
	}

	public Map<String, Map<String, Double>> getPlanProbabilityGradient() {
		return planProbabilityGradient;
	}

	public Map<String, Map<String, Double>> getPacakgeUserGradient() {
		return pacakgeUserGradient;
	}

	public Map<String, Map<String, Map<String, Map<String, Double>>>> getFareLinkGradient() {
		return fareLinkGradient;
	}
	
	
}



