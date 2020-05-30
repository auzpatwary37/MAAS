package optimizerAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.AttributeConverter;

import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TimeUtils;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.Trip;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TripChain;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTripChain;

/**
 * 
 *@author Ashraf
 *
 *this class will be attached to all newly created Plans
 *This class holds the following informations
 *An activity list of the plan
 *the physical car link usage per time step by this plan (We assume the time step will not be shifted for now)
 *the transit direct and transfer links used by this plan 
 *The transit routes (can calculate utility)
 *The car routes (can calculate utility)
 *
 *Added the comments to make the class more readable 
 *
 */
public class SimpleTranslatedPlan {
	public final static String SimplePlanAttributeName = "SIMPLE_PLAN";
	
	// this is the time Bean to be used for the model
	private final Map<String,Tuple<Double,Double>> timeBean;
	
	// time step based use of car
	private Map<String,List<Id<Link>>> carUsage = new ConcurrentHashMap<>();
	
	//time step based list of transit links will be used for pooling
	private Map<String,List<TransitLink>> transitUsage = new ConcurrentHashMap<>();
	
	//this is time step specific transit link usage
	private Map<String,List<FareLink>> fareLinkUsage = new ConcurrentHashMap<>();
	
	//this is time step specific transit link usage
	private Map<String,Map<Id<TransitLink>,TransitLink>> transitLinkMap = new ConcurrentHashMap<>();
	
	// These are all the transit routes used in these plans (Routes can directly calculate utility)
	private Map<String,Map<Id<AnalyticalModelTransitRoute>,AnalyticalModelTransitRoute>> trroutes = new ConcurrentHashMap<>(); 
	
	// this is the list of all the car routes
	private Map<String, Map<Id<AnalyticalModelRoute>,AnalyticalModelRoute>> routes = new ConcurrentHashMap<>(); 
	
	private String maasPacakgeId =null;
	
	
	private List<Activity> activities;
	
	//the plans choice probability
	private double probability = 0;
	
	private String planKey;
	
	
	double totalWalkDistance = 0;
	
	public SimpleTranslatedPlan(Map<String,Tuple<Double,Double>> timeBean, Plan plan, Scenario scenario) {
		this.timeBean=timeBean;
		
		for(String s:timeBean.keySet()) {
			this.carUsage.put(s, new ArrayList<>());
			this.transitUsage.put(s, new ArrayList<>());
			this.transitLinkMap.put(s, new ConcurrentHashMap<>());
			this.trroutes.put(s, new ConcurrentHashMap<>());
			this.routes.put(s, new ConcurrentHashMap<>());
			this.fareLinkUsage.put(s, new ArrayList<>());
		}
		
		TripChain tripchain = new CNLTripChain(plan,scenario.getTransitSchedule(),scenario,scenario.getNetwork());
		this.activities=tripchain.getActivitylist();
		for(Trip trip:tripchain.getTrips()) {
			String timeId = TimeUtils.getTimeId(timeBean, trip.getStartTime());
			
			if(timeId == null) {
				System.out.println();
			}
			
			if(trip.getRoute()!=null) {
				CNLRoute route = (CNLRoute) trip.getRoute();
				this.carUsage.get(timeId).addAll(route.getLinkIds());
				this.routes.get(timeId).put(route.getRouteId(), route);
			}else if(trip.getTrRoute()!=null){
				
				CNLTransitRouteMaaS route = new CNLTransitRouteMaaS((CNLTransitRoute) trip.getTrRoute(), scenario, scenario.getTransitSchedule());
				for(Entry<Id<TransitLink>,TransitLink>trLink:route.getTransitLinks().entrySet()) {
					if(trLink.getValue() instanceof TransitDirectLink) {
						CNLTransitDirectLink link = (CNLTransitDirectLink) trLink.getValue();
						link.calcCapacityAndHeadway(this.timeBean, timeId);
					}
					this.transitUsage.get(timeId).add(trLink.getValue());
					transitLinkMap.get(timeId).put(trLink.getKey(),trLink.getValue());
				}
				
				for(FareLink fl:route.getFareLinks()) {
					this.fareLinkUsage.get(timeId).add(fl);
				}
				
				this.totalWalkDistance += route.getRouteWalkingDistance();
				this.trroutes.get(timeId).put(route.getTrRouteId(), route);
			}else {
				//TODO: Calculate the walk distance and add it 
				/*
				 * Do we actually need it? Maybe we do (April 2020)
				 */
			}
		}
		
	}

	/**
	 * 
	 * @return the default timeBeans 
	 */
	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}

	/**
	 * 
	 * @return a map of link ids used per time slot by this plan
	 */
	public Map<String, List<Id<Link>>> getCarUsage() {
		return carUsage;
	}

	/**
	 * Maybe not needed as there is already a map 
	 * @return A map of transit links used per time slot by this plan
	 * 
 	 */
	public Map<String, List<TransitLink>> getTransitUsage() {
		return transitUsage;
	}

	/**
	 * 
	 * @return a Map of all the transit link usage per time slot by this plan
	 */
	public Map<String, Map<Id<TransitLink>, TransitLink>> getTransitLinkMap() {
		return transitLinkMap;
	}


	/**
	 * 
	 * @return the list of activities
	 */
	public List<Activity> getActivities() {
		return activities;
	}
	
	/**
	 *  *** DOES NOT WORK*** (11 April 2020)
	 * @return This class returns an attribute converter to be used for writing and reading the simple translated plan attribute
	 * The methods are implemented for testing purpose, however, the attribute converter class is not implemented yet.
	 */
	public static AttributeConverter<SimpleTranslatedPlan> getAttributeConverter(){
		return new AttributeConverter<SimpleTranslatedPlan>() {

			@Override
			public SimpleTranslatedPlan convert(String value) {
				//String[] part = value.split("___"); 
				return null;
			}

			//TODO: implement this method
			@Override
			public String convertToString(Object o) {
			SimpleTranslatedPlan v = (SimpleTranslatedPlan)o;
				
				return "This Plan Contains a plan Translator Attrubute";
			}
			
		};
	}

	

	/**
	 * Returns the attribute name by which this attribute is stored in plan attribute map
	 * @return
	 */
	public static String getSimpleplanattributename() {
		return SimplePlanAttributeName;
	}


	/**
	 * 
	 * @return a map containing the transit routes used per time slot by this plan
	 */
	public Map<String, Map<Id<AnalyticalModelTransitRoute>, AnalyticalModelTransitRoute>> getTrroutes() {
		return trroutes;
	}


	/**
	 * 
	 * @return a map of car routes used by this plan
	 */
	public Map<String, Map<Id<AnalyticalModelRoute>, AnalyticalModelRoute>> getRoutes() {
		return routes;
	}


	/**
	 * 
	 * @return  the total walk distance by this plan
	 */
	public double getTotalWalkDistance() {
		return totalWalkDistance;
	}

	/**
	 * 
	 * @return this plan's choice probability
	 */
	public double getProbability() {
		return probability;
	}

	/**
	 * 
	 * @param probability the probability of choosing this plan
	 */
	public void setProbability(double probability) {
		this.probability = probability;
	}

	public Map<String, List<FareLink>> getFareLinkUsage() {
		return fareLinkUsage;
	}

	public String getPlanKey() {
		return planKey;
	}

	public void setPlanKey(String planKey) {
		this.planKey = planKey;
	}

	public String getMaasPacakgeId() {
		return maasPacakgeId;
	}

	public void setMaasPacakgeId(String maasPacakgeId) {
		this.maasPacakgeId = maasPacakgeId;
	}
	
	
	
	
}


