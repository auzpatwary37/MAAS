package elasticDemand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.ReplanningContext;
import com.google.common.collect.Sets;

import createBus.BusDataExtractor;

public class ActivityAdditionStrategyModuleV2 implements PlanStrategyModule{

	private int successfulCases= 0;
	private Map<String,Set<Coord>> activityLocationMap;
	private LinkedHashMap<String,Double> activityDurationMap;
	private Set<String> unmodifiableActivities;
	private Set<Plan> plans;
	private Network network;
	private static final Logger logger = Logger.getLogger(ActivityAdditionStrategyModuleV2.class);
	public ActivityAdditionStrategyModuleV2(Map<String,Set<Coord>> activityLocationMap, Map<String,Double> activityDurationMap,Set<String>unmodifiableActivities, Scenario scenario) {
		this.activityLocationMap = activityLocationMap;
		this.activityDurationMap =activityDurationMap.entrySet().stream()
                .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		this.unmodifiableActivities = unmodifiableActivities;
		this.network = scenario.getNetwork();
	}
	
	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		plans = new HashSet<>(); 
		successfulCases= 0;
	}

	@Override
	public void handlePlan(Plan plan) {
		plans.add(plan);
	}

	@Override
	public void finishReplanning() {
		this.plans.parallelStream().forEach(plan->{
			basePlan p = new basePlan(plan, activityDurationMap, unmodifiableActivities, activityLocationMap, network);
			p.generateModifiedPlan();
			if(p.ifSuccessful)successfulCases++;
		});
		logger.info("Successfully added activities to "+successfulCases + " cases out of "+this.plans.size()+" cases.");
		this.plans.clear();
	}
	
}

class basePlan{
	boolean ifSuccessful = false;
	private List<Activity> activities = new ArrayList<>();
	private List<Leg> legs = new ArrayList<>();
	private Plan plan;
	private Map<String,Double> activityDurations;
	private Set<String> unModifiableActs;
	private Map<String,Set<Coord>> activityLocationMap;
	private Network network;
	private static final Logger logger = Logger.getLogger(basePlan.class);
	public basePlan(Plan plan, Map<String,Double>actDuration, Set<String>unModifiableActs, Map<String,Set<Coord>> activityLocationMap, Network network) {
		this.plan = plan;
		this.activityDurations = actDuration;
		this.stripToBasePlan(plan);
		this.unModifiableActs = unModifiableActs;
		this.activityLocationMap = activityLocationMap;
		this.network = network;
	}
	
	private void stripToBasePlan(Plan plan) {
		boolean ptCont = false;
		for(PlanElement pe:plan.getPlanElements()) {
			if(pe instanceof Activity && !((Activity)pe).getType().equals("pt interaction")) {
				Activity a = (Activity)pe;
				this.activities.add(a);
				ptCont = false;
			}else if(pe instanceof Leg) {
				if(((Leg)pe).getMode().equals("transit_walk")&& ptCont == false){//pt start
					Leg leg = PopulationUtils.createLeg("pt");
					legs.add(leg);
					ptCont = true;
				}else if(((Leg)pe).getMode().equals("car")) {
					Leg leg = (Leg)pe;
					legs.add(leg);
				}	
			}
		}
		if(this.activities.size()!=this.legs.size()+1) {
			logger.debug("Debug here!!");
		}
	}
	
	public bestActivityInsertionLocationInfo generateActivityInsertionSlots() {//The integer will denote the no of activity after which the new activity will be inserted
		bestActivityInsertionLocationInfo bestSlot = null;
		Set<String> actTypeSet = new HashSet<>();
		this.activities.stream().forEach(a->actTypeSet.add(a.getType()));
		for(int i = 0; i<this.activities.size()-1; i++) {
			Activity beforeAct = this.activities.get(i);
			Activity afterAct = this.activities.get(i+1);
			double timeFromBeforeAct = 0;
			double timeFromAfterAct = 0;
			if(!this.unModifiableActs.contains(beforeAct.getType()) && !this.unModifiableActs.contains(afterAct.getType())) {//Means it is a location good for activity insertion
				double actStartTime = 0;
				double actEndTime = 24;
				if(beforeAct.getStartTime().isDefined())actStartTime = beforeAct.getStartTime().seconds();
				if(afterAct.getEndTime().isDefined())actEndTime = afterAct.getEndTime().seconds();
				timeFromBeforeAct = beforeAct.getEndTime().seconds()-actStartTime-this.activityDurations.get(beforeAct.getType())/2.;
				timeFromAfterAct = actEndTime-afterAct.getStartTime().seconds()-this.activityDurations.get(afterAct.getType())/2;
			}else if (!this.unModifiableActs.contains(beforeAct.getType()) && this.unModifiableActs.contains(afterAct.getType())) {
				double actStartTime = 0;
				if(beforeAct.getStartTime().isDefined())actStartTime = beforeAct.getStartTime().seconds();
				timeFromBeforeAct = beforeAct.getEndTime().seconds()-actStartTime-this.activityDurations.get(beforeAct.getType())/2.;
				timeFromAfterAct = 0;
			}else if(this.unModifiableActs.contains(beforeAct.getType()) && !this.unModifiableActs.contains(afterAct.getType())) {
				timeFromBeforeAct = 0;
				double actEndTime = 24;
				if(afterAct.getEndTime().isDefined())actEndTime = afterAct.getEndTime().seconds();
				timeFromAfterAct = actEndTime-afterAct.getStartTime().seconds()-this.activityDurations.get(afterAct.getType())/2;
			}
			if(timeFromBeforeAct<0)timeFromBeforeAct = 0;
			if(timeFromAfterAct<0)timeFromAfterAct = 0;
			String optimalAct = null;
			for(Entry<String, Double> s:this.activityDurations.entrySet()) {
				if(s.getValue()*.75<timeFromBeforeAct+timeFromAfterAct && !actTypeSet.contains(s.getKey()) && !this.unModifiableActs.contains(s.getKey())) {
					optimalAct = s.getKey();
					break;
				}
			}
			if(bestSlot==null)bestSlot =  new bestActivityInsertionLocationInfo(i,optimalAct,timeFromBeforeAct,timeFromAfterAct );
			else if(bestSlot.actDuration<timeFromBeforeAct+timeFromAfterAct)bestSlot =  new bestActivityInsertionLocationInfo(i,optimalAct,timeFromBeforeAct,timeFromAfterAct );
		}
		return  bestSlot;
	}
	
	public Plan generateModifiedPlan() {
		bestActivityInsertionLocationInfo info = this.generateActivityInsertionSlots();
		this.plan.getPlanElements().clear();
		this.plan.addActivity(this.activities.get(0));
		for(int i=0;i<this.activities.size()-1;i++) {
			if(info.actLocationIndex==i && info.actType!=null) {
				this.ifSuccessful = true;
				this.activities.get(i).setEndTime(this.activities.get(i).getEndTime().seconds()-info.timeFromBeforeAct);
				this.plan.addLeg(this.legs.get(i));
				final Coord c = this.activities.get(i).getCoord();
				final Coord nearest = Collections.min(this.activityLocationMap.get(info.actType), new Comparator<Coord>() {

					  public int compare(final Coord p1, final Coord p2) {
						  double d1 = NetworkUtils.getEuclideanDistance(p1, c);
						  double d2 = NetworkUtils.getEuclideanDistance(p2, c);
						  if(d1<d2) {
							  return -1;
						  }else if(d1==d2) {
							  return 0;
						  }else {
							  return 1;
						  }
					  }
					});
				//Coord coord = NetworkUtils.getNearestNode(this.activityLocationMap.get(actToInsert), act.getCoord()).getCoord();
				
				Activity actNew = PopulationUtils.createActivityFromCoord(info.actType, new Coord(nearest.getX(),nearest.getY()));
				actNew.setLinkId(BusDataExtractor.getNearestLinksExactlyByMath(network, actNew.getCoord(), 
						100.0, Sets.newHashSet(TransportMode.car), 70/3.6, new HashSet<>()).get(0));
				double startTime = 0;
				if(this.activities.get(i).getEndTime().isDefined()) {
					startTime = this.activities.get(i).getEndTime().seconds()+1.5*(NetworkUtils.getEuclideanDistance(this.activities.get(i).getCoord(), actNew.getCoord()))/(45*1000/3600);
				}else {
					startTime = this.legs.get(i).getDepartureTime().seconds()+1.5*(NetworkUtils.getEuclideanDistance(this.activities.get(i).getCoord(), actNew.getCoord()))/(45*1000/3600);
				}
				actNew.setStartTime(startTime);
				actNew.setEndTime(actNew.getStartTime().seconds()+this.activityDurations.get(actNew.getType())/2);
				this.plan.addActivity(actNew);
				this.plan.addLeg(PopulationUtils.createLeg(this.legs.get(i)));
				this.activities.get(i+1).setStartTime(this.activities.get(i+1).getStartTime().seconds()+info.timeFromAfterAct);
				this.plan.addActivity(this.activities.get(i+1));
			}else {
				this.plan.addLeg(this.legs.get(i));
				this.plan.addActivity(this.activities.get(i+1));
			}
		}
		
		return this.plan;
	}
	
	
}

class bestActivityInsertionLocationInfo{
	final int actLocationIndex;
	final String actType;
	final double actDuration;
	final double timeFromBeforeAct;
	final double timeFromAfterAct;
	
	public bestActivityInsertionLocationInfo(int insertionLocation, String actType, double tb,double taf) {
		this.actLocationIndex = insertionLocation;
		this.actType = actType;
		this.timeFromBeforeAct = tb;
		this.timeFromAfterAct = taf;
		this.actDuration = (tb+taf);
	}
}
