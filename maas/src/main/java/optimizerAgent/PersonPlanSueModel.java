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
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
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
	
	//______________________BackPropogationVariables______________________________________
	
	//We need a unique key for each plan. Let's assign one for each at the zero'th iteration
	
	// We need four incidence variables
	//1. link-plan incidence {linkID->Tuple<PlanId,numberOfTimesUsed>} 
	// [can be separated for physical links, tranistDirectLinks, transferLinks and fareLinks]
	//2. MaaS Package to plan incidence {packageId->Plan}
	//3. Transfer Link to transferLinks and directLinks
	
	Map<String,Map<Id<Link>,Map<String,Double>>> linkPlanIncidence = new ConcurrentHashMap<>();//Should be changed to HashMap after population is complete.
	Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkPlanIncidence = new ConcurrentHashMap<>();
	Map<String,Map<String,Map<String,Double>>> fareLinkPlanIncidence = new ConcurrentHashMap<>();
	
	Map<String,List<String>> maasPackagePlanIncidence = new ConcurrentHashMap<>();//done
	
	//Save plan probability
	
	Map<String,Double> planProbability = new ConcurrentHashMap<>();//done
	
	//Gradient Variable
	Map<String,Map<Id<Link>,Map<String,Double>>> linkGradient = new ConcurrentHashMap<>();
	Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkGradient = new ConcurrentHashMap<>();
	Map<String,Map<String,Map<String,Double>>> fareLinkGradient = new ConcurrentHashMap<>();
	
	Map<String,Map<Id<Link>,Map<String,Double>>> linkTravelTimeGradient = new ConcurrentHashMap<>();
	Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkTravelTimeGradient = new ConcurrentHashMap<>();
	
	Map<String,Map<String,Double>> planProbabilityGradient = new ConcurrentHashMap<>();
	Map<String,Map<String,Double>> pacakgeUserGradient = new ConcurrentHashMap<>();
	//___________________________FrontEndFunctionality____________________________________
	

	public Measurements performAssignment(Population population, LinkedHashMap<String,Double> params, Measurements originalMeasurements) {
		Measurements m = this.performAssignment(population, params,this.AnalyticalModelInternalParams, originalMeasurements);
		return m;
	}
	
	public SUEModelOutput performAssignment(Population population, LinkedHashMap<String,Double> params) {
		SUEModelOutput flow = this.performAssignment(population, params,this.AnalyticalModelInternalParams);
		return flow;
	}
	
	
	//___________________________________________________________________________________
	//Constructor
	
	public PersonPlanSueModel(Map<String, Tuple<Double, Double>> timeBean,Config config) {
		this.timeBeans=timeBean;
		//this.defaultParameterInitiation(null);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			this.linkPlanIncidence.put(timeBeanId, new ConcurrentHashMap<>());
			this.trLinkPlanIncidence.put(timeBeanId, new ConcurrentHashMap<>());
			this.fareLinkPlanIncidence.put(timeBeanId, new ConcurrentHashMap<>());

			
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
		//We have to go through each person and get the corresponding transitLinks
		//This is costly, so lets do it in the update function
		this.farecalculators = fareCalculator;
		this.maasPakages = packages;
		this.ts = scenario.getTransitSchedule();
		
	}
	
	private SUEModelOutput singlePersonNL(Person person, LinkedHashMap<String,Double> Oparams, LinkedHashMap<String,Double> anaParams, int counter) {
		
		//get the subpopulation
		String subpopulation = PopulationUtils.getSubpopulation(person);
		
		Map<String, Double> utilities = new HashMap<>();
		Map<String, Double> planProb = new HashMap<>();
		Map<String,Map<Id<Link>,Double>> linkFlow = new HashMap<>();
		Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow = new HashMap<>();
		Map<String,Map<String,Double>> fareLinkFlow = new HashMap<>();
		Map<String, SimpleTranslatedPlan> trPlans = new HashMap<>();
		
		//This should handle for the basic params per subpopulation
		LinkedHashMap<String,Double> params = this.handleBasicParams(Oparams, subpopulation, this.scenario.getConfig());
		int planNo = 0;
		//Calculate the utility, Should we move the utility calculation part inside the simple translated plan itself? makes more sense. (April 2020)
		for(Plan plan:person.getPlans()) {
			
			//Give an identifier to the plan. We will give a String identifier which will be saved as plan attribute
			String planKey = (String)plan.getAttributes().getAttribute(PersonPlanSueModel.PlanKeyIdentifierName);
			if(planKey == null) {
				planKey = person.getId().toString()+"_^_"+planNo;
				//plan.getAttributes().putAttribute(PersonPlanSueModel.PlanKeyIdentifierName, planKey);
				planNo++;
			}
			
			
			double utility = 0;
			//Add the MaaSPackage disutility
			MaaSPackage maas = this.maasPakages.getMassPackages().get(plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName));
			
			if(counter==1) {
				if(!this.maasPackagePlanIncidence.containsKey(maas.getId())) {
					this.maasPackagePlanIncidence.put(maas.getId(), new ArrayList<>());	
				}
				this.maasPackagePlanIncidence.get(maas.getId()).add(planKey);
			}
			
			Map<String,Object> additionalInfo = new HashMap<>();
			additionalInfo.put(MaaSUtil.CurrentSelectedMaaSPackageAttributeName, maas);
			if(maas!=null)utility+=params.get(CNLSUEModel.MarginalUtilityofMoneyName)*maas.getPackageCost();
			
			SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) plan.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);//extract the translated plan first
			trPlans.put(planKey, trPlan);
			for(Activity ac:trPlan.getActivities()) {// for now this class is not implemented: done may 2 2020
				utility += this.calcActivityUtility(ac, this.scenario.getConfig(),subpopulation);
			}
			for(Entry<String, Map<Id<AnalyticalModelTransitRoute>, AnalyticalModelTransitRoute>> trRouteMap:trPlan.getTrroutes().entrySet()) {
				for(AnalyticalModelTransitRoute trRoute : trRouteMap.getValue().values()) {
					utility += trRoute.calcRouteUtility(params, anaParams, this.networks.get(trRouteMap.getKey()), this.farecalculators,additionalInfo, this.timeBeans.get(trRouteMap.getKey()));
				}
				
				for(AnalyticalModelRoute route: trPlan.getRoutes().get(trRouteMap.getKey()).values()) {
					utility += route.calcRouteUtility(params, anaParams, this.networks.get(trRouteMap.getKey()), this.timeBeans.get(trRouteMap.getKey())); 
				}
			}
			utilities.put(planKey,utility);
			
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
			this.planProbability.put(d.getKey(), v);
		}
		
		// Collect the flow
		
		for(Entry<String, SimpleTranslatedPlan> plan:trPlans.entrySet()) {
			SimpleTranslatedPlan trPlan = plan.getValue();
			for(Entry<String, List<Id<Link>>> s: trPlan.getCarUsage().entrySet()) {	
				
				if(!linkFlow.containsKey(s.getKey()))linkFlow.put(s.getKey(), new HashMap<>());
				Map<Id<Link>,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(ss->ss, ss->planProb.get(plan.getKey()),(v1,v2)->(v1+v2)));
				flowMap.keySet().forEach((linkId)->linkFlow.get(s.getKey()).compute(linkId, (k,v)->(v==null)?flowMap.get(linkId):v+flowMap.get(linkId)));
				
				if(counter == 1) {
					s.getValue().forEach((link)->{
						if(!this.linkPlanIncidence.get(s.getKey()).containsKey(link))this.linkPlanIncidence.get(s.getKey()).put(link, new ConcurrentHashMap<>());
						Map<String, Double> incidenceMap = this.linkPlanIncidence.get(s.getKey()).get(link);
						incidenceMap.compute(plan.getKey(), (k,v)->(v==null)?1:v+1);
					});
				}
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
				if(!this.trLinkPlanIncidence.get(s.getKey()).containsKey(trLink.getTrLinkId())) {
					this.trLinkPlanIncidence.get(s.getKey()).put(trLink.getTrLinkId(), new ConcurrentHashMap<>());
				}
				Map<String, Double> incidenceMap = this.trLinkPlanIncidence.get(s.getKey()).get(trLink.getTrLinkId());
				if(counter == 1){
					incidenceMap.compute(plan.getKey(), (k,v)->(v==null)?1:v+1);
				}
				});
				//
				
				Map<Id<TransitLink>,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(TransitLink::getTrLinkId, ss->planProb.get(plan.getKey()),(v1,v2)->(v1+v2)));
				flowMap.keySet().forEach((trLinkId)->transitLinkFlow.get(s.getKey()).compute(trLinkId, (k,v)->(v==null)?flowMap.get(trLinkId):v+flowMap.get(trLinkId)));
			}
			
			for(Entry<String, List<FareLink>> s: trPlan.getFareLinkUsage().entrySet()) {				
				if(!fareLinkFlow.containsKey(s.getKey()))fareLinkFlow.put(s.getKey(), new HashMap<>());
				Map<String,Double>flowMap = s.getValue().stream().collect(Collectors.toMap(ss->ss.toString(), ss->planProb.get(plan.getKey()),(v1,v2)->(v1+v2)));
				flowMap.keySet().forEach((fareLink)->fareLinkFlow.get(s.getKey()).compute(fareLink, (k,v)->(v==null)?flowMap.get(fareLink):v+flowMap.get(fareLink)));
				if(counter == 1) {
					s.getValue().forEach((fl)->{
						if(!this.fareLinkPlanIncidence.get(s.getKey()).containsKey(fl.toString()))this.fareLinkPlanIncidence.get(s.getKey()).put(fl.toString(), new ConcurrentHashMap<>());
						Map<String, Double> incidenceMap = this.fareLinkPlanIncidence.get(s.getKey()).get(fl.toString());
						incidenceMap.compute(plan.getKey(), (k,v)->(v==null)?1:v+1);
					});
				}
			
			}
			
		}
		if(linkFlow==null||transitLinkFlow==null||fareLinkFlow==null) {
			System.out.println();
		}
		return new SUEModelOutput(linkFlow,transitLinkFlow, null, null, fareLinkFlow);
	}
	
	//TODO: still do not take params in sub-population. We have to incorporate that 
	private SUEModelOutput performNetworkLoading(Population population, LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams, int counter) {
		
		List<Map<String,Map<Id<Link>, Double>>> linkVolumes=Collections.synchronizedList(new ArrayList<>());
		List<Map<String,Map<Id<TransitLink>, Double>>> linkTransitVolumes=Collections.synchronizedList(new ArrayList<>());
		List<Map<String,Map<String,Double>>> fareLinkFlows=Collections.synchronizedList(new ArrayList<>());
		
		Map<String,Map<Id<Link>,Double>> linkFlow = new HashMap<>();
		Map<String,Map<Id<TransitLink>,Double>> transitLinkFlow = new HashMap<>();
		Map<String,Map<String,Double>> fareLinkFlow = new HashMap<>();
		
		population.getPersons().values().parallelStream().forEach((person)->{
			if(PopulationUtils.getSubpopulation(person)!=null && PopulationUtils.getSubpopulation(person).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName)) {
				return;
			}
			SUEModelOutput flow= this.singlePersonNL(person, params, anaParams,counter);
			linkVolumes.add(flow.getLinkVolume());
			linkTransitVolumes.add(flow.getLinkTransitVolume());
			fareLinkFlows.add(flow.getFareLinkVolume());
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
				if(!fareLinkFlow.containsKey(timeLinkFlowMap.getKey())) {
					fareLinkFlow.put(timeLinkFlowMap.getKey(), timeLinkFlowMap.getValue());
				}else {
					timeLinkFlowMap.getValue().entrySet().stream().forEach((map)->fareLinkFlow.get(timeLinkFlowMap.getKey()).compute(map.getKey(), (k,v)->(v==null)?map.getValue():v+map.getValue()));
				}
			});
		});
		
		return new SUEModelOutput(linkFlow,transitLinkFlow, null, null, fareLinkFlow);
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
			flow  = this.performNetworkLoading(population, params, anaParams,counter);
			boolean shouldStop = this.updateVolume(flow.getLinkVolume(), flow.getLinkTransitVolume(), counter);
			if(shouldStop) {
				break;
			}
		}
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
	private double calcActivityUtility(Activity activity, Config config, String subPopulation) {
		ScoringParameters scParam = new ScoringParameters.Builder(config.planCalcScore(), config.planCalcScore().getScoringParameters(subPopulation), config.scenario()).build();
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
		
		newParams.compute(CNLSUEModel.MarginalUtilityofTravelCarName,(k,v)->v==null?scParam.modeParams.get("car").marginalUtilityOfTraveling_s:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofDistanceCarName, (k,v)->v==null?scParam.modeParams.get("car").marginalUtilityOfDistance_m:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofMoneyName, (k,v)->v==null?scParam.marginalUtilityOfMoney:v);
		newParams.compute(CNLSUEModel.DistanceBasedMoneyCostCarName, (k,v)->v==null?scParam.modeParams.get("car").monetaryDistanceCostRate:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofTravelptName, (k,v)->v==null?scParam.modeParams.get("pt").marginalUtilityOfTraveling_s:v);
		newParams.compute(CNLSUEModel.MarginalUtilityOfDistancePtName, (k,v)->v==null?scParam.modeParams.get("pt").marginalUtilityOfDistance_m:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofWaitingName, (k,v)->v==null?scParam.marginalUtilityOfWaitingPt_s:v);
		newParams.compute(CNLSUEModel.UtilityOfLineSwitchName, (k,v)->v==null?scParam.utilityOfLineSwitch:v);
		newParams.compute(CNLSUEModel.MarginalUtilityOfWalkingName, (k,v)->v==null?scParam.modeParams.get("walk").marginalUtilityOfTraveling_s:v);
		newParams.compute(CNLSUEModel.DistanceBasedMoneyCostWalkName, (k,v)->v==null?scParam.modeParams.get("walk").monetaryDistanceCostRate:v);
		newParams.compute(CNLSUEModel.ModeConstantCarName, (k,v)->v==null?scParam.modeParams.get("car").constant:v);
		newParams.compute(CNLSUEModel.ModeConstantPtname, (k,v)->v==null?scParam.modeParams.get("pt").constant:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofPerformName, (k,v)->v==null?scParam.marginalUtilityOfPerforming_s:v);
		
		newParams.compute(CNLSUEModel.CapacityMultiplierName, (k,v)->v==null?config.qsim().getFlowCapFactor():v);
		
		return newParams;
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

