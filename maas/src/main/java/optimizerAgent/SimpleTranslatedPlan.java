package optimizerAgent;

import java.util.ArrayList;
import java.util.HashMap;
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
 * @author Ashraf
 *this class will be attached to all newly created Plans
 */
public class SimpleTranslatedPlan {
	public final static String SimplePlanAttributeName = "SIMPLE_PLAN";
	
	private final Map<String,Tuple<Double,Double>> timeBean;
	private Map<String,List<Id<Link>>> carUsage = new ConcurrentHashMap<>();
	private Map<String,List<TransitLink>> transitUsage = new ConcurrentHashMap<>();
	private Map<String,Map<Id<TransitLink>,TransitLink>> transitLinkMap = new ConcurrentHashMap<>();
	
	private Map<String,Map<Id<AnalyticalModelTransitRoute>,AnalyticalModelTransitRoute>> trroutes = new ConcurrentHashMap<>(); 
	private List<Activity> activities;
	double totalWalkDistance = 0;
	
	public SimpleTranslatedPlan(Map<String,Tuple<Double,Double>> timeBean, Plan plan, Scenario scenario) {
		this.timeBean=timeBean;
		
		for(String s:timeBean.keySet()) {
			this.carUsage.put(s, new ArrayList<>());
			this.transitUsage.put(s, new ArrayList<>());
			this.transitLinkMap.put(s, new ConcurrentHashMap<>());
			this.trroutes.put(s, new ConcurrentHashMap<>());
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
			}else if(trip.getTrRoute()!=null){
				CNLTransitRoute route = (CNLTransitRoute) trip.getTrRoute(); 
				for(Entry<Id<TransitLink>,TransitLink>trLink:route.getTransitLinks().entrySet()) {
					if(trLink.getValue() instanceof TransitDirectLink) {
						CNLTransitDirectLink link = (CNLTransitDirectLink) trLink.getValue();
						link.calcCapacityAndHeadway(this.timeBean, timeId);
					}
					this.transitUsage.get(timeId).add(trLink.getValue());
					transitLinkMap.get(timeId).put(trLink.getKey(),trLink.getValue());
				}
				this.totalWalkDistance += route.getRouteWalkingDistance();
			}else {
				//TODO: Calculate the walk distance and add it 
			}
		}
		
	}


	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}


	public Map<String, List<Id<Link>>> getCarUsage() {
		return carUsage;
	}


	public Map<String, List<TransitLink>> getTransitUsage() {
		return transitUsage;
	}


	public Map<String, Map<Id<TransitLink>, TransitLink>> getTransitLinkMap() {
		return transitLinkMap;
	}


	public List<Activity> getActivities() {
		return activities;
	}
	
	public static AttributeConverter<SimpleTranslatedPlan> getAttributeConverter(){
		return new AttributeConverter<SimpleTranslatedPlan>() {

			@Override
			public SimpleTranslatedPlan convert(String value) {
				//String[] part = value.split("___"); 
				return null;
			}

			@Override
			public String convertToString(Object o) {
			SimpleTranslatedPlan v = (SimpleTranslatedPlan)o;
				
				return "This Plan Contains a plan Translator Attrubute";
			}
			
		};
	}
}


