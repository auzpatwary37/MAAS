package optimizerAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;

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
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TimeUtils;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.Trip;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TripChain;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTripChain;

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
 * 				calculate leg Disutility (Use last iterations travel time for both car and transit)
 *  	
 *  		} 
 *  		
 *  		Perform simple logit to get the split (What about path size??) What should be a good measure between similarity between two plans
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
	
	Logger logger = Logger.getLogger(PersonPlanSueModel.class);
	
	private Population population;
	private Map<String,Tuple<Double,Double>>timeBeans;
	private Map<String,FareCalculator> farecalculators;
	private MAASPackages maasPakages;
	private Scenario scenario; 
	private TransitSchedule ts;
	private Map<String,Network> networks = new ConcurrentHashMap<>();;
	private Map<String,Map<Id<TransitLink>,TransitLink>> transitLinks = new ConcurrentHashMap<>();
	private Map<String,Double> defaultParameters;
	private Map<String,Double> internalParamters;
	
	private double alphaMSA=1.9;//parameter for decreasing MSA step size
	private double gammaMSA=.1;//parameter for decreasing MSA step size
	
	//other Parameters for the Calibration Process
	private double tollerance= 1;
	private double tolleranceLink=1;
	
	//Used Containers
	private Map<String,ArrayList<Double>> beta=new ConcurrentHashMap<>(); //This is related to weighted MSA of the SUE
	private Map<String,ArrayList<Double>> error=new ConcurrentHashMap<>();
	
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
	public PersonPlanSueModel(Map<String, Tuple<Double, Double>> timeBean) {
		this.timeBeans=timeBean;
		//this.defaultParameterInitiation(null);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			this.beta.put(timeBeanId, new ArrayList<Double>());
			this.error.put(timeBeanId, new ArrayList<Double>());
			
			//For result recording
			outputLinkTT.put(timeBeanId, new HashMap<>());
			outputTrLinkTT.put(timeBeanId, new HashMap<>());
			this.totalPtCapacityOnLink.put(timeBeanId, new HashMap<>());
			this.MTRCount.put(timeBeanId, new ConcurrentHashMap<>());
		}
		logger.info("Model created.");
	}
	
	/**
	 * This method overlays transit vehicles on the road network
	 * @param network
	 * @param Schedule
	 */
	public void performTransitVehicleOverlay(AnalyticalModelNetwork network, TransitSchedule schedule,Vehicles vehicles,String timeBeanId) {
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
	
	
	
	private void singlePersonNL(Id<Person> personId) {
		Person person = population.getPersons().get(personId);
		
		
	}
	
	

	
	private void createPlanLinkIncidence() {
		 
	}
	
}

